package com.college.bustracker.data.repository

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import com.google.firebase.firestore.SetOptions

class RouteRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val _localRoutes = MutableStateFlow<List<Route>>(emptyList())

    fun getCachedRoutes(): List<Route> = _localRoutes.value
    fun getCachedRoute(routeId: String): Route? = _localRoutes.value.find { it.routeId == routeId }

    fun getRoutesFlow(): Flow<List<Route>> = callbackFlow {
        trySend(_localRoutes.value)

        val registration = firestore.collection("routes")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(_localRoutes.value)
                    return@addSnapshotListener
                }
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

        awaitClose { registration.remove() }
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
