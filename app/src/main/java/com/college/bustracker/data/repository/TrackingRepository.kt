package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

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
                val loc = snapshot.getValue(LiveLocation::class.java)
                if (loc != null) {
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
                // Fall back to memory state
                _localLocations.value[busId]?.let { trySend(it) }
            }
        }

        busRef.addValueEventListener(listener)
        awaitClose { busRef.removeEventListener(listener) }
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
        val locationToUse = if (location.latitude == 0.0 && location.longitude == 0.0) {
            val cached = _localLocations.value[busId]
            if (cached != null && (cached.latitude != 0.0 || cached.longitude != 0.0)) {
                location.copy(latitude = cached.latitude, longitude = cached.longitude)
            } else {
                location
            }
        } else {
            location
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
                liveLocationsRef.child(busId).setValue(locationToUse) { error, _ ->
                    if (error != null) {
                        Log.e("BusTrackerGPS", "[AUTO-TRACE] Firebase location write failed: code=${error.code}, msg=${error.message}")
                        val now = System.currentTimeMillis()
                        if (now - lastErrorLogTime > 10_000L) {
                            lastErrorLogTime = now
                            Log.e("BusTrackerGPS", "RTDB_WRITE_REJECTED: busId=$busId write failed with code ${error.code}: ${error.message}")
                        }
                        val userMsg = if (error.code == DatabaseError.PERMISSION_DENIED) {
                            "Tracking permission denied for $busId. Check driver bus assignment."
                        } else {
                            "Live tracking error: ${error.message}"
                        }
                        _trackingError.value = userMsg
                        com.college.bustracker.service.TrackingServiceHelper.updateServiceState(
                            isTracking = true,
                            busId = busId,
                            gps = if (error.code == DatabaseError.PERMISSION_DENIED) "Permission Denied" else "Sync Error",
                            timestamp = locationToUse.lastUpdated
                        )
                    } else {
                        Log.i("BusTrackerGPS", "[AUTO-TRACE] Firebase location write succeeded: busId=$busId, lat=${locationToUse.latitude}, lng=${locationToUse.longitude}")
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
            locationSourcesRef.child(source.busId).child(source.sourceId).setValue(source)
            Log.d("BusTrackerGPS", "STUDENT_GPS_CONTRIBUTED: bus=${source.busId}, source=${source.sourceId}, acc=${source.accuracy}m")
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
}
