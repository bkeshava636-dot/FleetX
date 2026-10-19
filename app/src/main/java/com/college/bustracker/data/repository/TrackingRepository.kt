package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TrackingRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    private val liveLocationsRef = database.getReference("live_locations")
    private val locationSourcesRef = database.getReference("location_sources")

    private var lastErrorLogTime = 0L
    private val _trackingError = MutableStateFlow<String?>(null)
    val trackingError: StateFlow<String?> = _trackingError.asStateFlow()

    // In-memory cache for live locations
    private val _localLocations = MutableStateFlow<Map<String, LiveLocation>>(emptyMap())

    // Offline buffer for unsent coordinates during connectivity dropouts
    private val offlineBuffer = mutableMapOf<String, LiveLocation>()

    fun getCachedLiveLocation(busId: String): LiveLocation? = _localLocations.value[busId]

    fun getLiveLocationFlow(busId: String): Flow<LiveLocation> = callbackFlow {
        // Send existing local state immediately
        val cached = _localLocations.value[busId] ?: LiveLocation(busId = busId)
        trySend(cached)

        val busRef = liveLocationsRef.child(busId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loc = try {
                    snapshot.getValue(LiveLocation::class.java)
                } catch (e: Exception) {
                    Log.w("BusTrackerGPS", "LIVE_LOCATION_MAPPING_WARNING: Reflection deserialization failed for bus $busId, fallback to manual parse: ${e.message}")
                    null
                } ?: parseLiveLocationSnapshot(snapshot, busId)

                if (loc != null) {
                    Log.i("BusTrackerGPS", "LIVE_LOCATION_CALLBACK: busId=$busId, lat=${loc.latitude}, lon=${loc.longitude}, lastUpdated=${loc.lastUpdated}")
                    Log.i("BusTrackerGPS", "STUDENT_LIVE_LOCATION_RECEIVED: busId=${loc.busId}, latitude=${loc.latitude}, longitude=${loc.longitude}, lastUpdated=${loc.lastUpdated}")

                    if (loc.isStale()) {
                        Log.w("BusTrackerGPS", "GPS_STALE: location for bus ${loc.busId} exceeds 90s stale threshold")
                    }
                    val map = _localLocations.value.toMutableMap()
                    map[busId] = loc
                    _localLocations.value = map
                    trySend(loc)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BusTrackerGPS", "STUDENT_LOCATION_LISTENER_ERROR: code=${error.code}, message=${error.message}, details=${error.details}")
                // Fall back to memory state
                _localLocations.value[busId]?.let { trySend(it) }
            }
        }

        busRef.addValueEventListener(listener)
        awaitClose { busRef.removeEventListener(listener) }
    }

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.Job())
    private val pendingRetries = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    private fun isTransientError(code: Int): Boolean {
        return code == DatabaseError.NETWORK_ERROR ||
               code == DatabaseError.DISCONNECTED ||
               code == DatabaseError.OPERATION_FAILED ||
               code == DatabaseError.UNAVAILABLE ||
               code == DatabaseError.WRITE_CANCELED ||
               code == DatabaseError.EXPIRED_TOKEN
    }

    private fun scheduleWriteRetry(
        locationToUse: LiveLocation,
        attempt: Int,
        lastErrorCode: Int,
        lastErrorMessage: String
    ) {
        val busId = locationToUse.busId
        if (attempt > 3) {
            offlineBuffer[busId] = locationToUse
            com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbRetryExhausted(
                context = com.college.bustracker.CollegeBusApp.instance,
                busId = busId,
                totalAttempts = 3,
                lastErrorCode = lastErrorCode,
                lastErrorMessage = lastErrorMessage
            )
            return
        }

        val backoffDelayMs = 1000L * (1L shl (attempt - 1)) // 1000ms, 2000ms, 4000ms
        com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbRetryScheduled(
            context = com.college.bustracker.CollegeBusApp.instance,
            busId = busId,
            attempt = attempt,
            delayMs = backoffDelayMs,
            reason = "Transient RTDB write error ($lastErrorCode)"
        )

        pendingRetries[busId]?.cancel()
        val job = repositoryScope.launch {
            kotlinx.coroutines.delay(backoffDelayMs)
            val currentCached = _localLocations.value[busId]
            // Monotonicity check: abort retry if cache already has a newer fix
            if (currentCached != null && currentCached.lastUpdated > locationToUse.lastUpdated) {
                Log.d("BusTrackerGPS", "GPS_RETRY_SKIPPED: newer fix already available for $busId")
                return@launch
            }

            val retryStart = System.currentTimeMillis()
            try {
                liveLocationsRef.child(busId).setValue(locationToUse) { error, _ ->
                    val retryLatency = System.currentTimeMillis() - retryStart
                    if (error != null) {
                        val isTransient = isTransientError(error.code)
                        if (isTransient) {
                            scheduleWriteRetry(locationToUse, attempt + 1, error.code, error.message)
                        } else {
                            offlineBuffer[busId] = locationToUse
                            com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbWriteFailed(
                                context = com.college.bustracker.CollegeBusApp.instance,
                                busId = busId,
                                errorCode = error.code,
                                errorMessage = error.message,
                                latencyMs = retryLatency,
                                isRetryable = false,
                                attempt = attempt
                            )
                        }
                    } else {
                        pendingRetries.remove(busId)
                        com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbRetrySuccess(
                            context = com.college.bustracker.CollegeBusApp.instance,
                            busId = busId,
                            attempt = attempt,
                            latencyMs = retryLatency
                        )
                        _trackingError.value = null
                    }
                }
            } catch (e: Exception) {
                scheduleWriteRetry(locationToUse, attempt + 1, -1, e.message ?: "Exception in retry")
            }
        }
        pendingRetries[busId] = job
    }

    fun updateLiveLocation(location: LiveLocation, hasInternet: Boolean = true) {
        val busId = location.busId
        if (busId.isBlank()) return

        // 1. 0,0 Coordinate protection: NEVER allow writing ACTIVE status with (0.0, 0.0) coordinates
        if (location.trackingStatus == "ACTIVE" && (location.latitude == 0.0 || location.longitude == 0.0)) {
            Log.w("BusTrackerGPS", "GPS_REJECTED: cannot write ACTIVE status with (0,0) coordinates for bus $busId")
            return
        }

        // 2. Preserve previous coordinates if new status is WAITING_FOR_GPS/INACTIVE with 0.0 coordinates
        val baseLoc = if (location.latitude == 0.0 && location.longitude == 0.0) {
            val cached = _localLocations.value[busId]
            if (cached != null && (cached.latitude != 0.0 || cached.longitude != 0.0)) {
                location.copy(latitude = cached.latitude, longitude = cached.longitude)
            } else {
                location
            }
        } else {
            location
        }

        // 2b. Sanitize trackingStatus for RTDB validation: only validated statuses are permitted
        val sanitizedStatus = when (baseLoc.trackingStatus) {
            "ACTIVE", "INACTIVE", "WAITING_FOR_GPS", "STALE", "ARRIVED_AT_CAMPUS" -> baseLoc.trackingStatus
            else -> "WAITING_FOR_GPS"
        }
        val locationToUse = if (baseLoc.trackingStatus != sanitizedStatus) {
            baseLoc.copy(trackingStatus = sanitizedStatus)
        } else {
            baseLoc
        }

        // 3. Duplicate prevention: avoid writing identical coordinate if dispatched within 3 seconds
        val lastLoc = _localLocations.value[busId]
        if (lastLoc != null &&
            lastLoc.latitude == locationToUse.latitude &&
            lastLoc.longitude == locationToUse.longitude &&
            lastLoc.trackingStatus == locationToUse.trackingStatus &&
            (locationToUse.lastUpdated - lastLoc.lastUpdated) < 3_000L
        ) {
            Log.d("BusTrackerGPS", "GPS_REJECTED: duplicate write within 3s for bus $busId")
            return
        }

        // Always update local cache
        val current = _localLocations.value.toMutableMap()
        current[busId] = locationToUse
        _localLocations.value = current

        if (hasInternet) {
            // Flush any buffered coordinate first
            offlineBuffer.remove(busId)
            try {
                Log.i("BusTrackerGPS", "[AUTO-TRACE] Firebase location write attempted: busId=$busId")
                val writeStart = System.currentTimeMillis()
                com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbWriteAttempted(com.college.bustracker.CollegeBusApp.instance, busId)

                liveLocationsRef.child(busId).setValue(locationToUse) { error, _ ->
                    val writeLatency = System.currentTimeMillis() - writeStart
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val hasAuth = auth != null
                    if (error != null) {
                        Log.e("BusTrackerGPS", "[AUTO-TRACE] Firebase location write failed: code=${error.code}, msg=${error.message}")
                        val isTransient = isTransientError(error.code)
                        val isPermanent = (error.code == DatabaseError.PERMISSION_DENIED)
                        if (error.code == DatabaseError.EXPIRED_TOKEN) {
                            try { auth?.getIdToken(true) } catch (ignored: Exception) {}
                        }
                        com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbWriteFailed(
                            context = com.college.bustracker.CollegeBusApp.instance,
                            busId = busId,
                            errorCode = error.code,
                            errorMessage = error.message,
                            latencyMs = writeLatency,
                            exceptionClass = "DatabaseError",
                            hasAuthUser = hasAuth,
                            isNetworkConnected = hasInternet,
                            isRetryable = isTransient,
                            attempt = 1,
                            hasOperatorEpoch = (locationToUse.operatorEpoch != null)
                        )
                        val now = System.currentTimeMillis()
                        if (now - lastErrorLogTime > 10_000L) {
                            lastErrorLogTime = now
                            Log.e("BusTrackerGPS", "RTDB_WRITE_REJECTED: busId=$busId write failed with code ${error.code}: ${error.message}")
                        }
                        val userMsg = if (isPermanent) {
                            "Tracking permission denied for $busId. Check driver bus assignment."
                        } else {
                            "Live tracking error: ${error.message}"
                        }
                        _trackingError.value = userMsg
                        com.college.bustracker.service.TrackingServiceHelper.updateServiceState(
                            isTracking = true,
                            busId = busId,
                            gps = if (isPermanent) "Permission Denied" else "Sync Error",
                            timestamp = locationToUse.lastUpdated
                        )
                        if (isTransient) {
                            scheduleWriteRetry(locationToUse, 1, error.code, error.message)
                        }
                    } else {
                        Log.i("BusTrackerGPS", "[AUTO-TRACE] Firebase location write succeeded: busId=$busId, lat=${locationToUse.latitude}, lng=${locationToUse.longitude}")
                        pendingRetries.remove(busId)?.cancel()
                        com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbWriteSuccess(
                            context = com.college.bustracker.CollegeBusApp.instance,
                            busId = busId,
                            latencyMs = writeLatency
                        )
                        if (_trackingError.value != null) {
                            _trackingError.value = null
                        }
                    }
                }
                Log.i(
                    "BusTrackerGPS",
                    "GPS_UPLOADED: busId=$busId, lat=${locationToUse.latitude}, lng=${locationToUse.longitude}, t=${locationToUse.lastUpdated}"
                )
            } catch (e: Exception) {
                com.college.bustracker.diagnostics.GpsDiagnosticManager.onRtdbWriteFailed(
                    context = com.college.bustracker.CollegeBusApp.instance,
                    busId = busId,
                    errorCode = -1,
                    errorMessage = e.message ?: "Exception in setValue",
                    exceptionClass = e.javaClass.simpleName,
                    isRetryable = false
                )
                offlineBuffer[busId] = locationToUse
                Log.w("BusTrackerGPS", "GPS_BUFFERED: write failed, buffered busId=$busId")
            }
        } else {
            // Buffer locally until network returns
            offlineBuffer[busId] = locationToUse
            Log.w("BusTrackerGPS", "GPS_BUFFERED: offline mode, buffered busId=$busId")
        }
    }

    fun flushOfflineBuffer() {
        if (offlineBuffer.isEmpty()) return
        val count = offlineBuffer.size
        val now = System.currentTimeMillis()
        val iterator = offlineBuffer.iterator()
        while (iterator.hasNext()) {
            val (busId, loc) = iterator.next()
            val current = _localLocations.value[busId]
            // Monotonicity check: don't overwrite if cache already has a newer fix
            if (current != null && current.lastUpdated > loc.lastUpdated) {
                Log.d("BusTrackerGPS", "GPS_BUFFER_SKIPPED: current cache fix (${current.lastUpdated}) is newer than buffered fix (${loc.lastUpdated})")
                iterator.remove()
                continue
            }
            val locToUpload = if ((now - loc.lastUpdated) < 180_000L) {
                loc.copy(lastUpdated = now)
            } else {
                loc
            }
            try {
                liveLocationsRef.child(busId).setValue(locToUpload) { error, _ ->
                    if (error != null) {
                        val errNow = System.currentTimeMillis()
                        if (errNow - lastErrorLogTime > 10_000L) {
                            lastErrorLogTime = errNow
                            Log.e("BusTrackerGPS", "RTDB_FLUSH_REJECTED: busId=$busId write failed with code ${error.code}: ${error.message}")
                        }
                    }
                }
                val map = _localLocations.value.toMutableMap()
                map[busId] = locToUpload
                _localLocations.value = map
                Log.i("BusTrackerGPS", "GPS_UPLOADED: (from buffer) busId=$busId, t=${locToUpload.lastUpdated}")
            } catch (ignored: Exception) {}
            iterator.remove()
        }
        Log.i("BusTrackerGPS", "GPS_NETWORK_RESTORED: flushed $count buffered location(s)")
    }

    fun stopTracking(busId: String, driverId: String = "") {
        val cached = _localLocations.value[busId]
        val inactive = LiveLocation(
            busId = busId,
            latitude = cached?.latitude ?: 0.0,
            longitude = cached?.longitude ?: 0.0,
            trackingStatus = "INACTIVE",
            driverId = driverId,
            lastUpdated = System.currentTimeMillis()
        )
        val current = _localLocations.value.toMutableMap()
        current[busId] = inactive
        _localLocations.value = current

        try {
            liveLocationsRef.child(busId).setValue(inactive)
        } catch (ignored: Exception) {}
    }

    /**
     * Contributes a secondary student location fix to location_sources/{busId}/{sourceId}.
     */
    fun contributeStudentLocation(source: LocationSource) {
        if (source.busId.isBlank() || source.sourceId.isBlank()) return
        if (source.latitude == 0.0 && source.longitude == 0.0) {
            Log.w("BusTrackerGPS", "STUDENT_GPS_REJECTED: zero coordinates (0,0)")
            return
        }
        try {
            locationSourcesRef.child(source.busId).child(source.sourceId).setValue(source) { error, _ ->
                if (error != null) {
                    Log.e("BusTrackerGPS", "STUDENT_GPS_ERROR: Rejected with code ${error.code}: ${error.message}")
                } else {
                    Log.d("BusTrackerGPS", "STUDENT_GPS_CONTRIBUTED: bus=${source.busId}, source=${source.sourceId}, acc=${source.accuracy}m")
                }
            }
        } catch (e: Exception) {
            Log.w("BusTrackerGPS", "STUDENT_GPS_ERROR: Failed to contribute location: ${e.message}")
        }
    }

    /**
     * Removes the student's contributor node from location_sources/{busId}/{sourceId} upon opt-out.
     */
    fun removeStudentLocation(busId: String, sourceId: String) {
        if (busId.isBlank() || sourceId.isBlank()) return
        try {
            locationSourcesRef.child(busId).child(sourceId).removeValue()
            Log.d("BusTrackerGPS", "STUDENT_GPS_REMOVED: bus=$busId, source=$sourceId")
        } catch (e: Exception) {
            Log.w("BusTrackerGPS", "STUDENT_GPS_ERROR: Failed to remove contributor: ${e.message}")
        }
    }

    /**
     * Observes all active contributor sources for a bus at location_sources/{busId}.
     */
    fun getLocationSourcesFlow(busId: String): Flow<List<LocationSource>> = callbackFlow {
        if (busId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val busSourcesRef = locationSourcesRef.child(busId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<LocationSource>()
                for (child in snapshot.children) {
                    val src = child.getValue(LocationSource::class.java)
                    if (src != null) {
                        list.add(src)
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }

        busSourcesRef.addValueEventListener(listener)
        awaitClose { busSourcesRef.removeEventListener(listener) }
    }

    companion object {
        fun parseLiveLocationSnapshot(snapshot: DataSnapshot, fallbackBusId: String): LiveLocation? {
            if (!snapshot.exists()) return null
            return try {
                val busId = snapshot.child("busId").getValue(String::class.java) ?: fallbackBusId
                val lat = (snapshot.child("latitude").value as? Number)?.toDouble() ?: 0.0
                val lon = (snapshot.child("longitude").value as? Number)?.toDouble() ?: 0.0
                val speed = (snapshot.child("speed").value as? Number)?.toFloat() ?: 0.0f
                val bearing = (snapshot.child("bearing").value as? Number)?.toFloat() ?: 0.0f
                val status = snapshot.child("trackingStatus").getValue(String::class.java) ?: "INACTIVE"
                val mode = snapshot.child("mode").getValue(String::class.java) ?: "MANUAL"
                val routeId = snapshot.child("routeId").getValue(String::class.java) ?: ""
                val driverId = snapshot.child("driverId").getValue(String::class.java) ?: ""
                val sourceType = snapshot.child("sourceType").getValue(String::class.java) ?: "PRIMARY_DRIVER"
                val epoch = (snapshot.child("operatorEpoch").value as? Number)?.toLong()
                val tempId = snapshot.child("temporaryAssignmentId").getValue(String::class.java)
                val lastUpdated = (snapshot.child("lastUpdated").value as? Number)?.toLong() ?: 0L

                LiveLocation(
                    busId = busId,
                    latitude = lat,
                    longitude = lon,
                    speed = speed,
                    bearing = bearing,
                    trackingStatus = status,
                    mode = mode,
                    routeId = routeId,
                    driverId = driverId,
                    sourceType = sourceType,
                    operatorEpoch = epoch,
                    temporaryAssignmentId = tempId,
                    lastUpdated = lastUpdated
                )
            } catch (e: Exception) {
                Log.e("BusTrackerGPS", "STUDENT_LOCATION_PARSE_ERROR: busId=$fallbackBusId, err=${e.message}")
                null
            }
        }
    }
}
