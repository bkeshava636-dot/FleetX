package com.college.bustracker.util

import android.util.Log
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.BusStop
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Result of road-snapped route calculation.
 */
data class RoadRouteResult(
    val points: List<LatLng>,
    val totalDistanceMeters: Double,
    val isSnappedToRoads: Boolean
)

/**
 * Result of live detour path calculation from current bus position to the target stop.
 */
data class DetourRouteResult(
    val points: List<LatLng>,
    val roadDistanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Zero-Cost Road Routing Engine.
 *
 * Snaps stop-to-stop paths to real streets, highways, and turns using OSRM driving geometry.
 * Computes live detour routes when the bus changes course, dynamically updating ETAs.
 * Features in-memory caching and graceful fallback to direct Euclidean chords.
 */
object RoadRoutingEngine {

    private const val TAG = "RoadRoutingEngine"
    private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving/"
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 6000

    // In-memory cache for static route paths: Key -> Signature of stops
    private val routePathCache = mutableMapOf<String, RoadRouteResult>()

    // Detour throttling state
    private var lastDetourQueryTimeMs = 0L
    private var lastDetourBusLat = 0.0
    private var lastDetourBusLon = 0.0
    private var lastDetourStopId: String? = null
    private var cachedDetourResult: DetourRouteResult? = null

    /**
     * Computes the road-snapped polyline for a list of consecutive bus stops.
     * Caches results so each route is queried at most once per session.
     */
    suspend fun getRouteRoadPath(stops: List<BusStop>): RoadRouteResult = withContext(Dispatchers.IO) {
        val physicalStops = stops.filter { it.hasCoordinates }
        if (physicalStops.isEmpty()) {
            return@withContext RoadRouteResult(emptyList(), 0.0, false)
        }
        if (physicalStops.size == 1) {
            val pt = LatLng(physicalStops[0].latitude, physicalStops[0].longitude)
            return@withContext RoadRouteResult(listOf(pt), 0.0, false)
        }

        // Cache key based on stops IDs and coordinates
        val cacheKey = physicalStops.joinToString(";") { "${it.stopId}_${"%.4f".format(it.latitude)}_${"%.4f".format(it.longitude)}" }
        routePathCache[cacheKey]?.let {
            return@withContext it
        }

        // Check persistent disk cache before hitting network
        loadFromDiskCache(cacheKey)?.let { diskCached ->
            routePathCache[cacheKey] = diskCached
            return@withContext diskCached
        }

        val directPoints = physicalStops.map { LatLng(it.latitude, it.longitude) }
        val directDistance = computeDirectPolylineDistance(directPoints)

        try {
            // OSRM format: lon1,lat1;lon2,lat2;...;lonN,latN
            val coordString = physicalStops.joinToString(";") { "${it.longitude},${it.latitude}" }
            val requestUrl = "$OSRM_BASE_URL$coordString?overview=full&geometries=geojson"

            val url = URL(requestUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "FleetX-Transit/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                if (json.optString("code") == "Ok") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val firstRoute = routes.getJSONObject(0)
                        val roadDistance = firstRoute.optDouble("distance", directDistance)
                        val geometry = firstRoute.getJSONObject("geometry")
                        val coordsArray = geometry.getJSONArray("coordinates")

                        val roadPoints = ArrayList<LatLng>(coordsArray.length())
                        for (i in 0 until coordsArray.length()) {
                            val coord = coordsArray.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            roadPoints.add(LatLng(lat, lon))
                        }

                        if (roadPoints.isNotEmpty()) {
                            Log.d(TAG, "Road path snapped successfully for ${stops.size} stops (${roadPoints.size} road points, ${roadDistance.toInt()}m)")
                            val result = RoadRouteResult(roadPoints, roadDistance, true)
                            routePathCache[cacheKey] = result
                            saveToDiskCache(cacheKey, result)
                            return@withContext result
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OSRM road snapping failed, falling back to direct stop lines: ${e.message}")
        }

        // Fallback: direct line between stops
        val fallback = RoadRouteResult(directPoints, directDistance, false)
        routePathCache[cacheKey] = fallback
        fallback
    }

    /**
     * Dynamically computes the driving road path from current bus coordinates to the target stop.
     * Throttled to avoid overwhelming the network while the bus is driving.
     */
    suspend fun getDetourPath(
        busLat: Double,
        busLon: Double,
        targetStop: BusStop,
        forceRefresh: Boolean = false
    ): DetourRouteResult? = withContext(Dispatchers.IO) {
        if (busLat == 0.0 || busLon == 0.0) return@withContext null
        val targetLat = targetStop.latitude
        val targetLon = targetStop.longitude
        if (targetLat == 0.0 || targetLon == 0.0) return@withContext null

        val now = System.currentTimeMillis()
        val distMovedFromLastQuery = EtaEngine.calculateDistanceMeters(lastDetourBusLat, lastDetourBusLon, busLat, busLon)
        val timeSinceLastQuery = now - lastDetourQueryTimeMs

        // Throttling: reuse cached result if queried within 12 seconds and bus moved < 100 meters
        if (!forceRefresh && cachedDetourResult != null && lastDetourStopId == targetStop.stopId) {
            if (timeSinceLastQuery < 12_000L && distMovedFromLastQuery < 100.0) {
                return@withContext cachedDetourResult
            }
        }

        val directDist = EtaEngine.calculateDistanceMeters(busLat, busLon, targetLat, targetLon)
        val defaultDuration = directDist / 8.33 // assuming ~30 km/h urban speed

        try {
            val coordString = "$busLon,$busLat;$targetLon,$targetLat"
            val requestUrl = "$OSRM_BASE_URL$coordString?overview=full&geometries=geojson"

            val url = URL(requestUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "FleetX-Transit/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                if (json.optString("code") == "Ok") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val firstRoute = routes.getJSONObject(0)
                        val roadDist = firstRoute.optDouble("distance", directDist)
                        val durationSec = firstRoute.optDouble("duration", defaultDuration)
                        val geometry = firstRoute.getJSONObject("geometry")
                        val coordsArray = geometry.getJSONArray("coordinates")

                        val roadPoints = ArrayList<LatLng>(coordsArray.length())
                        for (i in 0 until coordsArray.length()) {
                            val coord = coordsArray.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            roadPoints.add(LatLng(lat, lon))
                        }

                        if (roadPoints.isNotEmpty()) {
                            val result = DetourRouteResult(roadPoints, roadDist, durationSec)
                            lastDetourQueryTimeMs = now
                            lastDetourBusLat = busLat
                            lastDetourBusLon = busLon
                            lastDetourStopId = targetStop.stopId
                            cachedDetourResult = result
                            Log.d(TAG, "Detour path computed: ${roadDist.toInt()}m, ${durationSec.toInt()}s (${roadPoints.size} road points)")
                            return@withContext result
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Detour road calculation failed: ${e.message}")
        }

        // Fallback: direct line from bus to target stop
        val fallback = DetourRouteResult(
            points = listOf(LatLng(busLat, busLon), LatLng(targetLat, targetLon)),
            roadDistanceMeters = directDist,
            durationSeconds = defaultDuration
        )
        cachedDetourResult = fallback
        lastDetourQueryTimeMs = now
        lastDetourBusLat = busLat
        lastDetourBusLon = busLon
        lastDetourStopId = targetStop.stopId
        fallback
    }

    /**
     * Checks if the bus has deviated from the planned route path corridor (beyond threshold meters).
     */
    fun isBusOffRoute(
        busLat: Double,
        busLon: Double,
        routePath: List<LatLng>,
        thresholdMeters: Double = 95.0
    ): Boolean {
        if (routePath.isEmpty() || busLat == 0.0 || busLon == 0.0) return false

        var minDistance = Double.MAX_VALUE
        for (i in 0 until routePath.size - 1) {
            val p1 = routePath[i]
            val p2 = routePath[i + 1]

            val segLen = EtaEngine.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            if (segLen < 1.0) continue

            val midLat = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
            val dx = (p2.longitude - p1.longitude) * kotlin.math.cos(midLat) * 111320.0
            val dy = (p2.latitude - p1.latitude) * 111320.0
            val segLenSq = dx * dx + dy * dy

            val px = (busLon - p1.longitude) * kotlin.math.cos(midLat) * 111320.0
            val py = (busLat - p1.latitude) * 111320.0
            val t = ((px * dx + py * dy) / segLenSq).coerceIn(0.0, 1.0)

            val projLat = p1.latitude + t * (p2.latitude - p1.latitude)
            val projLon = p1.longitude + t * (p2.longitude - p1.longitude)
            val d = EtaEngine.calculateDistanceMeters(busLat, busLon, projLat, projLon)

            if (d < minDistance) {
                minDistance = d
                if (minDistance <= thresholdMeters) {
                    return false // Within corridor
                }
            }
        }

        return minDistance > thresholdMeters
    }

    private fun computeDirectPolylineDistance(points: List<LatLng>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += EtaEngine.calculateDistanceMeters(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return total
    }

    private fun getDiskCacheFile(cacheKey: String): File? {
        return try {
            val app = CollegeBusApp.instance
            val dir = File(app.filesDir, "routes_osrm_cache")
            if (!dir.exists()) dir.mkdirs()
            val safeHash = abs(cacheKey.hashCode())
            File(dir, "osrm_$safeHash.json")
        } catch (e: Exception) {
            null
        }
    }

    private fun loadFromDiskCache(cacheKey: String): RoadRouteResult? {
        val file = getDiskCacheFile(cacheKey) ?: return null
        if (!file.exists()) return null
        return try {
            val jsonStr = file.readText()
            val json = JSONObject(jsonStr)
            val distance = json.optDouble("distance", 0.0)
            val isSnapped = json.optBoolean("snapped", true)
            val coordsArray = json.getJSONArray("points")
            val points = ArrayList<LatLng>(coordsArray.length())
            for (i in 0 until coordsArray.length()) {
                val pt = coordsArray.getJSONArray(i)
                points.add(LatLng(pt.getDouble(0), pt.getDouble(1)))
            }
            if (points.isNotEmpty()) {
                Log.d(TAG, "Loaded road route from disk cache (${file.name}, ${points.size} pts)")
                RoadRouteResult(points, distance, isSnapped)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Error loading route from disk cache: ${e.message}")
            null
        }
    }

    private fun saveToDiskCache(cacheKey: String, result: RoadRouteResult) {
        if (!result.isSnappedToRoads || result.points.isEmpty()) return
        val file = getDiskCacheFile(cacheKey) ?: return
        try {
            val json = JSONObject()
            json.put("distance", result.totalDistanceMeters)
            json.put("snapped", result.isSnappedToRoads)
            val pointsArray = JSONArray()
            for (pt in result.points) {
                val coord = JSONArray()
                coord.put(pt.latitude)
                coord.put(pt.longitude)
                pointsArray.put(coord)
            }
            json.put("points", pointsArray)
            file.writeText(json.toString())
            Log.d(TAG, "Saved road route to disk cache (${file.name})")
        } catch (e: Exception) {
            Log.w(TAG, "Error saving route to disk cache: ${e.message}")
        }
    }

    fun clearCache() {
        routePathCache.clear()
        cachedDetourResult = null
        lastDetourQueryTimeMs = 0L
    }
}
