package com.college.bustracker.data.repository

import android.content.Context
import android.util.Log
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import org.json.JSONObject

/**
 * Fast cold launch seed provider.
 * Loads static fleet information bundled in assets/bundled_fleet_seed.json
 * when Firestore or disk cache is empty or slow during initial application launch.
 */
object BundledFleetSeed {

    private const val TAG = "BundledFleetSeed"
    private const val ASSET_FILE = "bundled_fleet_seed.json"

    private var cachedBuses: List<Bus>? = null
    private var cachedRoutes: List<Route>? = null

    @Synchronized
    fun getFallbackBuses(context: Context?): List<Bus> {
        if (cachedBuses != null) return cachedBuses!!
        if (context == null) return emptyList()

        return try {
            val jsonString = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val busesArray = root.optJSONArray("buses") ?: return emptyList()

            val list = mutableListOf<Bus>()
            for (i in 0 until busesArray.length()) {
                val bObj = busesArray.getJSONObject(i)
                list.add(
                    Bus(
                        busId = bObj.optString("busId"),
                        busNumber = bObj.optString("busNumber"),
                        routeId = bObj.optString("routeId"),
                        capacity = bObj.optInt("capacity", 50),
                        active = bObj.optBoolean("active", true)
                    )
                )
            }
            cachedBuses = list
            Log.i(TAG, "Loaded ${list.size} fallback buses from bundled seed in assets")
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bundled fallback buses: ${e.message}")
            emptyList()
        }
    }

    @Synchronized
    fun getFallbackRoutes(context: Context?): List<Route> {
        if (cachedRoutes != null) return cachedRoutes!!
        if (context == null) return emptyList()

        return try {
            val jsonString = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val routesArray = root.optJSONArray("routes") ?: return emptyList()

            val list = mutableListOf<Route>()
            for (i in 0 until routesArray.length()) {
                val rObj = routesArray.getJSONObject(i)
                val stopsArray = rObj.optJSONArray("stops")
                val stopsList = mutableListOf<BusStop>()
                if (stopsArray != null) {
                    for (j in 0 until stopsArray.length()) {
                        val sObj = stopsArray.getJSONObject(j)
                        stopsList.add(
                            BusStop(
                                stopId = sObj.optString("stopId"),
                                name = sObj.optString("name"),
                                latitude = sObj.optDouble("latitude", 0.0),
                                longitude = sObj.optDouble("longitude", 0.0),
                                order = sObj.optInt("order", j + 1),
                                active = sObj.optBoolean("active", true),
                                visibleOnMap = sObj.optBoolean("visibleOnMap", true)
                            )
                        )
                    }
                }

                val schedObj = rObj.optJSONObject("schedules")
                val morningObj = schedObj?.optJSONObject("morning")
                val eveningObj = schedObj?.optJSONObject("evening")

                val schedule = RouteSchedule(
                    morning = ScheduleWindow(
                        startTime = morningObj?.optString("startTime", "08:10") ?: "08:10",
                        endTime = morningObj?.optString("endTime", "08:45") ?: "08:45",
                        enabled = morningObj?.optBoolean("enabled", true) ?: true
                    ),
                    evening = ScheduleWindow(
                        startTime = eveningObj?.optString("startTime", "16:45") ?: "16:45",
                        endTime = eveningObj?.optString("endTime", "17:30") ?: "17:30",
                        enabled = eveningObj?.optBoolean("enabled", true) ?: true
                    )
                )

                list.add(
                    Route(
                        routeId = rObj.optString("routeId"),
                        name = rObj.optString("name"),
                        description = rObj.optString("description"),
                        active = rObj.optBoolean("active", true),
                        busId = rObj.optString("busId"),
                        stops = stopsList,
                        schedules = schedule
                    )
                )
            }
            cachedRoutes = list
            Log.i(TAG, "Loaded ${list.size} fallback routes from bundled seed in assets")
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bundled fallback routes: ${e.message}")
            emptyList()
        }
    }
}
