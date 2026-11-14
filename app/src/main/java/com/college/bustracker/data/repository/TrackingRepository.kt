package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.BusTravelPath
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.google.android.gms.maps.model.LatLng
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class TrackingRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    private val liveLocationsRef = database.getReference("live_locations")
    private val locationSourcesRef = database.getReference("location_sources")
    private val travelPathsRef = database.getReference("bus_travel_paths")

    private val _cachedTravelPaths = MutableStateFlow<Map<String, BusTravelPath>>(emptyMap())
    private val busUploadLocks = ConcurrentHashMap<String, Mutex>()
    private val busUploadSequenceGenerators = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    private val busLastStartedSequences = ConcurrentHashMap<String, Long>()
    private val busLastCompletedSequences = ConcurrentHashMap<String, Long>()

    private var lastErrorLogTime = 0L
    private val _trackingError = MutableStateFlow<String?>(null)
    val trackingError: StateFlow<String?> = _trackingError.asStateFlow()

    // In-memory cache for live locations
    private val _localLocations = MutableStateFlow<Map<String, LiveLocation>>(emptyMap())

    // Offline buffer for unsent coordinates during connectivity dropouts
    private val offlineBuffer = java.util.concurrent.ConcurrentHashMap<String, LiveLocation>()

    // Server time offset tracking from RTDB /.info/serverTimeOffset
    private var serverTimeOffsetMillis: Long = 0L

    init {
        try {
            database.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    serverTimeOffsetMillis = (snapshot.value as? Number)?.toLong() ?: 0L
                    Log.d("BusTrackerGPS", "RTDB serverTimeOffset updated: ${serverTimeOffsetMillis}ms")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w("BusTrackerGPS", "Failed to listen to serverTimeOffset: ${error.message}")
                }
            })
        } catch (ignored: Exception) {}
    }

    fun getEstimatedServerTime(): Long = System.currentTimeMillis() + serverTimeOffsetMillis

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
        val effectiveTimestamp = if (baseLoc.lastUpdated <= 0L) getEstimatedServerTime() else baseLoc.lastUpdated
        val locationToUse = baseLoc.copy(
            trackingStatus = sanitizedStatus,
            lastUpdated = effectiveTimestamp
        )

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
        val now = getEstimatedServerTime()
        val iterator = offlineBuffer.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val busId = entry.key
            val loc = entry.value
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
        pendingRetries.remove(busId)?.cancel()
        offlineBuffer.remove(busId)
        val cached = _localLocations.value[busId]
        val inactive = LiveLocation(
            busId = busId,
            latitude = cached?.latitude ?: 0.0,
            longitude = cached?.longitude ?: 0.0,
            trackingStatus = "INACTIVE",
            driverId = driverId,
            lastUpdated = getEstimatedServerTime()
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

    /**
     * Retrieves the in-memory or persisted disk-cached travel path for a bus.
     * Represents the real physical road trajectory the bus travels most of the time.
     * If expectedRouteId is provided, validates that the cached path belongs to that route.
     */
    fun getCachedTravelPath(busId: String, expectedRouteId: String? = null): List<LatLng>? {
        if (busId.isBlank()) return null
        val inMemory = _cachedTravelPaths.value[busId]
        if (inMemory != null) {
            if (expectedRouteId.isNullOrBlank() || inMemory.routeId.isBlank() || inMemory.routeId == expectedRouteId) {
                return inMemory.points
            }
            return null
        }
        val diskLoaded = loadTravelPathFromDisk(busId)
        if (diskLoaded != null && diskLoaded.points.isNotEmpty()) {
            val updated = _cachedTravelPaths.value.toMutableMap()
            updated[busId] = diskLoaded
            _cachedTravelPaths.value = updated
            if (expectedRouteId.isNullOrBlank() || diskLoaded.routeId.isBlank() || diskLoaded.routeId == expectedRouteId) {
                return diskLoaded.points
            }
            return null
        }
        return null
    }

    /**
     * Observes real-time updates to the bus's traveled route path from Firebase RTDB.
     * If expectedRouteId is provided, ignores updates for different routes to avoid stale route display.
     */
    fun getTravelPathFlow(busId: String, expectedRouteId: String? = null): Flow<List<LatLng>> = callbackFlow {
        if (busId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // 1. Immediately emit cached path for zero latency
        getCachedTravelPath(busId, expectedRouteId)?.let { trySend(it) }

        val pathRef = travelPathsRef.child(busId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Node removed or does not exist: clear in-memory & disk caches and emit empty path
                    val updated = _cachedTravelPaths.value.toMutableMap()
                    if (updated.remove(busId) != null) {
                        _cachedTravelPaths.value = updated
                    }
                    deleteTravelPathFromDisk(busId)
                    trySend(emptyList())
                    return
                }
                val routeId = snapshot.child("routeId").value?.toString() ?: ""
                val direction = snapshot.child("direction").value?.toString() ?: "STOPS_TO_COLLEGE"
                val lastUpdated = (snapshot.child("lastUpdated").value as? Number)?.toLong() ?: 0L

                val pointsList = mutableListOf<LatLng>()
                val pointsSnap = snapshot.child("points")
                for (ptSnap in pointsSnap.children) {
                    val lat = (ptSnap.child("lat").value as? Number)?.toDouble()
                        ?: (ptSnap.child("latitude").value as? Number)?.toDouble()
                    val lng = (ptSnap.child("lng").value as? Number)?.toDouble()
                        ?: (ptSnap.child("longitude").value as? Number)?.toDouble()
                    if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
                        pointsList.add(LatLng(lat, lng))
                    }
                }
                if (pointsList.isNotEmpty()) {
                    // Ensure canonical STOPS_TO_COLLEGE orientation (Stop 1 -> Campus)
                    val canonicalPoints = if (direction == "COLLEGE_TO_STOPS") pointsList.reversed() else pointsList
                    val record = BusTravelPath(
                        busId = busId,
                        routeId = routeId,
                        direction = "STOPS_TO_COLLEGE",
                        points = canonicalPoints,
                        lastUpdated = lastUpdated
                    )
                    val updated = _cachedTravelPaths.value.toMutableMap()
                    updated[busId] = record
                    _cachedTravelPaths.value = updated
                    saveTravelPathToDisk(busId, routeId, "STOPS_TO_COLLEGE", canonicalPoints)

                    if (expectedRouteId.isNullOrBlank() || routeId.isBlank() || routeId == expectedRouteId) {
                        trySend(canonicalPoints)
                    } else {
                        trySend(emptyList())
                    }
                } else {
                    // Node exists but contains no valid points: clear caches and emit emptyList()
                    val updated = _cachedTravelPaths.value.toMutableMap()
                    if (updated.remove(busId) != null) {
                        _cachedTravelPaths.value = updated
                    }
                    deleteTravelPathFromDisk(busId)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("TrackingRepository", "Failed to listen to travel path for bus $busId: ${error.message}")
            }
        }

        pathRef.addValueEventListener(listener)
        awaitClose { pathRef.removeEventListener(listener) }
    }

    /**
     * Publishes the bus's recorded travel path to Firebase RTDB and saves it to local disk cache.
     * Decimates and filters jitter to produce a clean, road-accurate polyline.
     * Normalizes the points to canonical stop sequence (STOPS_TO_COLLEGE, Stop 1 -> Campus).
     */
    fun updateTravelPath(
        busId: String,
        routeId: String,
        points: List<LatLng>,
        direction: String = "STOPS_TO_COLLEGE",
        onSuccess: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null
    ) {
        if (busId.isBlank() || routeId.isBlank() || points.isEmpty()) {
            onFailure?.invoke()
            return
        }
        val callSequence = busUploadSequenceGenerators.computeIfAbsent(busId) {
            java.util.concurrent.atomic.AtomicLong(0)
        }.incrementAndGet()

        repositoryScope.launch {
            try {
                val validPoints = points.filter { it.latitude != 0.0 || it.longitude != 0.0 }
                if (validPoints.isEmpty()) {
                    onFailure?.invoke()
                    return@launch
                }

                val optimized = optimizeTravelPath(validPoints)
                if (optimized.isEmpty()) {
                    onFailure?.invoke()
                    return@launch
                }

                // Canonical sequence normalization:
                // If recorded during COLLEGE_TO_STOPS, bus was driving in reverse of the stop sequence,
                // so we invert points so the master route polyline is consistently Stop 1 -> Campus.
                val canonicalPoints = if (direction == "COLLEGE_TO_STOPS") {
                    optimized.reversed()
                } else {
                    optimized
                }

                val pointsData = canonicalPoints.map { pt ->
                    mapOf("lat" to pt.latitude, "lng" to pt.longitude)
                }

                val lock = busUploadLocks.computeIfAbsent(busId) { Mutex() }
                lock.withLock {
                    val lastStarted = busLastStartedSequences[busId] ?: 0L
                    if (callSequence < lastStarted) {
                        onFailure?.invoke()
                        return@withLock
                    }
                    busLastStartedSequences[busId] = callSequence

                    val now = System.currentTimeMillis()
                    val payload = hashMapOf<String, Any>(
                        "busId" to busId,
                        "routeId" to routeId,
                        "direction" to "STOPS_TO_COLLEGE",
                        "lastUpdated" to now,
                        "points" to pointsData
                    )

                    travelPathsRef.child(busId).setValue(payload).addOnSuccessListener {
                        val isLatest = callSequence >= (busLastStartedSequences[busId] ?: 0L) &&
                                       callSequence >= (busLastCompletedSequences[busId] ?: 0L)
                        if (isLatest) {
                            busLastCompletedSequences[busId] = callSequence
                            saveTravelPathToDisk(busId, routeId, "STOPS_TO_COLLEGE", canonicalPoints)
                            val record = BusTravelPath(
                                busId = busId,
                                routeId = routeId,
                                direction = "STOPS_TO_COLLEGE",
                                points = canonicalPoints,
                                lastUpdated = now
                            )
                            val updated = _cachedTravelPaths.value.toMutableMap()
                            updated[busId] = record
                            _cachedTravelPaths.value = updated
                            Log.d("TrackingRepository", "Published normalized travel path for bus $busId (${canonicalPoints.size} points)")
                        }
                        onSuccess?.invoke()
                    }.addOnFailureListener { e ->
                        Log.w("TrackingRepository", "Failed to upload travel path for bus $busId: ${e.message}")
                        onFailure?.invoke()
                    }
                }
            } catch (e: Exception) {
                Log.w("TrackingRepository", "Failed to upload travel path for bus $busId: ${e.message}")
                onFailure?.invoke()
            }
        }
    }

    /**
     * Filters out GPS jitter and decimates dense tracking fixes down to a smooth, road-accurate polyline.
     */
    fun optimizeTravelPath(points: List<LatLng>): List<LatLng> {
        if (points.size <= 2) return points
        val filtered = mutableListOf<LatLng>()
        filtered.add(points.first())
        var lastAdded = points.first()

        for (i in 1 until points.size - 1) {
            val pt = points[i]
            val dist = com.college.bustracker.util.EtaEngine.calculateDistanceMeters(
                lastAdded.latitude, lastAdded.longitude,
                pt.latitude, pt.longitude
            )
            // Retain point if moved >= 18 meters from last recorded point
            if (dist >= 18.0) {
                filtered.add(pt)
                lastAdded = pt
            }
        }

        if (filtered.last() != points.last()) {
            filtered.add(points.last())
        }
        return filtered
    }

    private fun getTravelPathCacheFile(busId: String): java.io.File? {
        return try {
            val context = com.college.bustracker.CollegeBusApp.instance
            val dir = java.io.File(context.filesDir, "travel_paths")
            if (!dir.exists()) dir.mkdirs()
            val safeName = java.net.URLEncoder.encode(busId, "UTF-8")
            java.io.File(dir, "bus_$safeName.json")
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteTravelPathFromDisk(busId: String) {
        try {
            val file = getTravelPathCacheFile(busId)
            if (file?.exists() == true) {
                file.delete()
            }
        } catch (ignored: Exception) {}
    }

    private fun saveTravelPathToDisk(busId: String, routeId: String, direction: String, points: List<LatLng>) {
        try {
            val file = getTravelPathCacheFile(busId) ?: return
            val json = org.json.JSONObject()
            json.put("busId", busId)
            json.put("routeId", routeId)
            json.put("direction", direction)
            json.put("lastUpdated", System.currentTimeMillis())
            val ptsArray = org.json.JSONArray()
            for (pt in points) {
                val coord = org.json.JSONArray()
                coord.put(pt.latitude)
                coord.put(pt.longitude)
                ptsArray.put(coord)
            }
            json.put("points", ptsArray)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.w("TrackingRepository", "Failed to cache travel path to disk: ${e.message}")
        }
    }

    private fun loadTravelPathFromDisk(busId: String): BusTravelPath? {
        try {
            val file = getTravelPathCacheFile(busId) ?: return null
            if (!file.exists()) return null
            val content = file.readText()
            val json = org.json.JSONObject(content)
            val storedBusId = json.optString("busId", "")
            if (storedBusId != busId) return null
            val routeId = json.optString("routeId", "")
            val direction = json.optString("direction", "STOPS_TO_COLLEGE")
            val lastUpdated = json.optLong("lastUpdated", 0L)
            val ptsArray = json.optJSONArray("points") ?: return null
            val list = mutableListOf<LatLng>()
            for (i in 0 until ptsArray.length()) {
                val coord = ptsArray.getJSONArray(i)
                list.add(LatLng(coord.getDouble(0), coord.getDouble(1)))
            }
            return if (list.isNotEmpty()) BusTravelPath(busId, routeId, direction, list, lastUpdated) else null
        } catch (e: Exception) {
            return null
        }
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
