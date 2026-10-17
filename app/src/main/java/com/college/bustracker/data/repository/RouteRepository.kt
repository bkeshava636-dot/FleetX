package com.college.bustracker.data.repository

import android.content.Context
import android.util.Log
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import com.google.firebase.firestore.SetOptions

class RouteRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "RouteRepository"
        private const val PREFS_CACHE = "firestore_cache_metadata"
        private const val KEY_LAST_ROUTE_SYNC_MS = "last_route_sync_ms"
        private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours
    }

    private val _localRoutes = MutableStateFlow<List<Route>>(emptyList())

    fun getCachedRoutes(): List<Route> = _localRoutes.value
    fun getCachedRoute(routeId: String): Route? = _localRoutes.value.find { it.routeId == routeId }

    fun getRoutesFlow(forceRefresh: Boolean = false): Flow<List<Route>> = callbackFlow {
        // 1. Immediately emit in-memory cached routes if available
        if (_localRoutes.value.isNotEmpty()) {
            trySend(_localRoutes.value)
        }

        val context = try { com.college.bustracker.CollegeBusApp.instance } catch (_: Exception) { null }
        val prefs = context?.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val lastSyncMs = prefs?.getLong(KEY_LAST_ROUTE_SYNC_MS, 0L) ?: 0L
        val now = System.currentTimeMillis()
        val isCacheValid = !forceRefresh && (now - lastSyncMs < CACHE_TTL_MS)

        var cacheLoaded = false
        if (isCacheValid) {
            try {
                val cacheSnap = firestore.collection("routes").get(Source.CACHE).await()
                if (cacheSnap != null && !cacheSnap.isEmpty) {
                    val list = cacheSnap.documents.mapNotNull { doc ->
                        val r = doc.toObject(Route::class.java)
                        if (r != null) {
                            val finalId = if (r.routeId.isNotBlank()) r.routeId else doc.id
                            val finalName = if (r.name.isNotBlank()) r.name else doc.id
                            r.copy(routeId = finalId, name = finalName)
                        } else null
                    }
                    if (list.isNotEmpty()) {
                        _localRoutes.value = list
                        trySend(list)
                        cacheLoaded = true
                        Log.d(TAG, "FIRESTORE_CACHE_HIT: Loaded ${list.size} routes from Source.CACHE (0 server reads)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FIRESTORE_CACHE_MISS: Source.CACHE read failed (${e.message}), falling back to SERVER")
            }
        }

        if (!cacheLoaded) {
            try {
                val serverSnap = firestore.collection("routes").get(Source.SERVER).await()
                val list = serverSnap.documents.mapNotNull { doc ->
                    val r = doc.toObject(Route::class.java)
                    if (r != null) {
                        val finalId = if (r.routeId.isNotBlank()) r.routeId else doc.id
                        val finalName = if (r.name.isNotBlank()) r.name else doc.id
                        r.copy(routeId = finalId, name = finalName)
                    } else null
                }
                _localRoutes.value = list
                prefs?.edit()?.putLong(KEY_LAST_ROUTE_SYNC_MS, now)?.apply()
                trySend(list)
                Log.d(TAG, "FIRESTORE_SERVER_SYNC: Synced ${list.size} routes from Source.SERVER")
            } catch (e: Exception) {
                Log.e(TAG, "FIRESTORE_SERVER_ERROR: Failed to fetch routes from SERVER: ${e.message}")
                if (_localRoutes.value.isNotEmpty()) {
                    trySend(_localRoutes.value)
                }
            }
        }

        // Attach snapshot listener only on explicit forceRefresh (e.g. Admin editing routes)
        val registration = if (forceRefresh) {
            firestore.collection("routes").addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val r = doc.toObject(Route::class.java)
                        if (r != null) {
                            val finalId = if (r.routeId.isNotBlank()) r.routeId else doc.id
                            val finalName = if (r.name.isNotBlank()) r.name else doc.id
                            r.copy(routeId = finalId, name = finalName)
                        } else null
                    }
                    _localRoutes.value = list
                    trySend(list)
                }
            }
        } else null

        awaitClose { registration?.remove() }
    }

    suspend fun saveRoute(route: Route) {
        val current = _localRoutes.value.toMutableList()
        val idx = current.indexOfFirst { it.routeId == route.routeId }
        if (idx >= 0) {
            current[idx] = route
        } else {
            current.add(route)
        }
        _localRoutes.value = current

        try {
            kotlinx.coroutines.withTimeoutOrNull(6000L) {
                firestore.collection("routes").document(route.routeId).set(route).await()
            }
        } catch (ignored: Exception) {}
    }

    suspend fun updateStops(routeId: String, stops: List<BusStop>) {
        if (routeId.isBlank()) return
        val current = _localRoutes.value.map {
            if (it.routeId == routeId) it.copy(stops = stops) else it
        }
        _localRoutes.value = current
        try {
            kotlinx.coroutines.withTimeoutOrNull(6000L) {
                val stopsMap = mapOf(
                    "routeId" to routeId,
                    "stops" to stops
                )
                firestore.collection("routes").document(routeId).set(stopsMap, SetOptions.merge()).await()
            }
        } catch (ignored: Exception) {}
    }

    suspend fun updateSchedule(routeId: String, schedule: RouteSchedule): Result<Unit> {
        if (routeId.isBlank()) {
            return Result.failure(IllegalArgumentException("Route ID cannot be blank"))
        }
        val current = _localRoutes.value.map {
            if (it.routeId == routeId) it.copy(schedules = schedule) else it
        }
        _localRoutes.value = current
        return try {
            val success = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                val scheduleMap = mapOf(
                    "routeId" to routeId,
                    "schedules" to mapOf(
                        "morning" to mapOf(
                            "startTime" to schedule.morning.startTime,
                            "endTime" to schedule.morning.endTime,
                            "enabled" to schedule.morning.enabled
                        ),
                        "evening" to mapOf(
                            "startTime" to schedule.evening.startTime,
                            "endTime" to schedule.evening.endTime,
                            "enabled" to schedule.evening.enabled
                        )
                    )
                )
                firestore.collection("routes").document(routeId).set(scheduleMap, SetOptions.merge()).await()
                true
            }
            if (success == true) Result.success(Unit) else Result.failure(Exception("Network timeout saving schedule"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSchedulePdf(routeId: String, pdfUrl: String?, pdfName: String?): Result<Unit> {
        if (routeId.isBlank()) {
            return Result.failure(IllegalArgumentException("Route ID cannot be blank"))
        }
        val current = _localRoutes.value.map {
            if (it.routeId == routeId) it.copy(schedulePdfUrl = pdfUrl, schedulePdfName = pdfName) else it
        }
        _localRoutes.value = current
        return try {
            val success = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                val data = mapOf(
                    "routeId" to routeId,
                    "schedulePdfUrl" to pdfUrl,
                    "schedulePdfName" to pdfName
                )
                firestore.collection("routes").document(routeId).set(data, SetOptions.merge()).await()
                true
            }
            if (success == true) Result.success(Unit) else Result.failure(Exception("Network timeout saving PDF"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoute(routeId: String) {
        val current = _localRoutes.value.toMutableList()
        current.removeAll { it.routeId == routeId }
        _localRoutes.value = current

        try {
            kotlinx.coroutines.withTimeoutOrNull(6000L) {
                firestore.collection("routes").document(routeId).delete().await()
            }
        } catch (ignored: Exception) {}
    }

    suspend fun toggleRouteActive(routeId: String, active: Boolean) {
        val current = _localRoutes.value.map {
            if (it.routeId == routeId) it.copy(active = active) else it
        }
        _localRoutes.value = current
        val routeDoc = current.find { it.routeId == routeId }
        try {
            kotlinx.coroutines.withTimeoutOrNull(6000L) {
                if (routeDoc != null) {
                    firestore.collection("routes").document(routeId).set(routeDoc, SetOptions.merge()).await()
                } else {
                    firestore.collection("routes").document(routeId).update("active", active).await()
                }
            }
        } catch (ignored: Exception) {}
    }

    suspend fun updateRouteDetails(routeId: String, name: String, description: String, active: Boolean) {
        val current = _localRoutes.value.map {
            if (it.routeId == routeId) it.copy(name = name, description = description, active = active) else it
        }
        _localRoutes.value = current
        val routeDoc = current.find { it.routeId == routeId }
        try {
            kotlinx.coroutines.withTimeoutOrNull(6000L) {
                if (routeDoc != null) {
                    firestore.collection("routes").document(routeId).set(routeDoc, SetOptions.merge()).await()
                } else {
                    val map = mapOf(
                        "name" to name,
                        "description" to description,
                        "active" to active
                    )
                    firestore.collection("routes").document(routeId).set(map, SetOptions.merge()).await()
                }
            }
        } catch (ignored: Exception) {}
    }
}
