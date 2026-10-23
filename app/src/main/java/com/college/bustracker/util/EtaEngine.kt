package com.college.bustracker.util

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.TrackingDisplayState
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Data class representing the estimated arrival information for a specific bus stop.
 */
data class StopEta(
    val stop: BusStop,
    val etaMinutes: Int?,
    val etaFormatted: String,      // "~5 min", "< 1 min", "Arriving now", "—"
    val distanceMeters: Double,
    val distanceFormatted: String,  // "1.2 km away", "350 m away", "—"
    val isPassed: Boolean = false,
    val isNext: Boolean = false
)

/**
 * Overall ETA result for the active route.
 */
data class RouteEtaResult(
    val nextStopEta: StopEta?,
    val upcomingStops: List<StopEta>,
    val status: EtaStatus,
    val statusMessage: String,
    val isReliable: Boolean
)

/**
 * High-level status flags for ETA presentation.
 */
enum class EtaStatus {
    NORMAL,
    BUS_NOT_STARTED,
    WAITING_FOR_GPS,
    STALE,
    ARRIVED_FINAL_STOP,
    NO_ROUTE,
    UNAVAILABLE,
    DETOUR_ACTIVE
}

/**
 * Zero-Cost In-App ETA and Next Stop Detection Engine.
 *
 * Implements pure geodesic segment projection, monotonic stop progression,
 * live speed fusion with urban transit baselines, intermediate dwell compensation,
 * and graceful fallback states with zero external paid routing APIs.
 */
object EtaEngine {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val NOMINAL_URBAN_SPEED_KMH = 22.0f
    private const val NOMINAL_URBAN_SPEED_MS = 22.0 / 3.6 // ~6.11 m/s
    private const val MIN_TRANSIT_SPEED_MS = 3.5 // ~12.6 km/h minimum floor for distances > 50m
    const val SPATIAL_DECAY_DISTANCE_METERS = 800.0 // Distance scale where live speed dominates vs nominal
    const val BETA_DOWN = 0.70 // Fast response when bus accelerates/approaches
    const val BETA_UP = 0.25 // Slow response when bus halts at signals to prevent wild jitter
    private const val STOP_ARRIVAL_RADIUS_METERS = 80.0
    const val DEFAULT_INTERMEDIATE_DWELL_SECONDS = 30.0
    private const val MAX_REALISTIC_ETA_MINUTES = 180
    private const val SPEED_SMOOTHING_ALPHA = 0.40f

    /**
     * Exponential Spatial Decay speed weighting:
     * Alpha(d) = exp(-d / d0). Near bus (d < 300m), live speed dominates.
     * Further ahead (d > 1km), nominal transit speed (22 km/h) smoothly dominates.
     */
    fun calculateSpatiallyDecayedSpeedMs(distanceMeters: Double, liveSpeedMs: Float): Double {
        val clampedLiveSpeed = if (liveSpeedMs in 1.0f..35.0f) liveSpeedMs.toDouble() else NOMINAL_URBAN_SPEED_MS
        val alpha = kotlin.math.exp(-distanceMeters / SPATIAL_DECAY_DISTANCE_METERS)
        val blendedSpeed = alpha * clampedLiveSpeed + (1.0 - alpha) * NOMINAL_URBAN_SPEED_MS
        return max(blendedSpeed, MIN_TRANSIT_SPEED_MS)
    }

    /**
     * Calculates the great-circle distance between two geographic coordinates using the Haversine formula.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Computes the initial bearing from point (lat1, lon1) to point (lat2, lon2) in degrees [0, 360).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        val bearing = (Math.toDegrees(theta) + 360.0) % 360.0
        return bearing.toFloat()
    }

    /**
     * Normalizes an angular difference to the range [-180, 180].
     */
    fun normalizeAngleDiff(angle1: Float, angle2: Float): Float {
        var diff = (angle1 - angle2) % 360.0f
        if (diff < -180.0f) diff += 360.0f
        if (diff > 180.0f) diff -= 360.0f
        return diff
    }

    /**
     * Formats distance in meters into human-friendly strings: e.g. "350 m away", "1.2 km away".
     */
    fun formatDistance(meters: Double): String {
        if (meters <= 0.0) return "—"
        return if (meters < 1000.0) {
            "${meters.roundToInt()} m away"
        } else {
            String.format(Locale.US, "%.1f km away", meters / 1000.0)
        }
    }

    /**
     * Projects a point (lat, lon) onto a road polyline represented by coordinate pairs (latitude, longitude).
     * Returns Pair(cumulativeDistanceAlongPolylineMeters, crossTrackDistanceMeters).
     */
    fun projectPointOntoCoordinates(lat: Double, lon: Double, coordinates: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (coordinates.isEmpty()) return Pair(0.0, Double.MAX_VALUE)
        if (coordinates.size == 1) {
            val dist = calculateDistanceMeters(lat, lon, coordinates[0].first, coordinates[0].second)
            return Pair(0.0, dist)
        }

        var cumulativeDist = 0.0
        var bestCrossTrack = Double.MAX_VALUE
        var bestPolylineDist = 0.0

        for (i in 0 until coordinates.size - 1) {
            val p1 = coordinates[i]
            val p2 = coordinates[i + 1]
            val segLen = calculateDistanceMeters(p1.first, p1.second, p2.first, p2.second)
            if (segLen < 0.5) continue

            val midLat = Math.toRadians((p1.first + p2.first) / 2.0)
            val dx = (p2.second - p1.second) * cos(midLat) * 111320.0
            val dy = (p2.first - p1.first) * 111320.0
            val segLenSq = dx * dx + dy * dy

            val px = (lon - p1.second) * cos(midLat) * 111320.0
            val py = (lat - p1.first) * 111320.0
            val t = if (segLenSq > 0.0) ((px * dx + py * dy) / segLenSq).coerceIn(0.0, 1.0) else 0.0

            val projLat = p1.first + t * (p2.first - p1.first)
            val projLon = p1.second + t * (p2.second - p1.second)
            val crossTrack = calculateDistanceMeters(lat, lon, projLat, projLon)

            if (crossTrack < bestCrossTrack) {
                bestCrossTrack = crossTrack
                bestPolylineDist = cumulativeDist + t * segLen
            }
            cumulativeDist += segLen
        }

        return Pair(bestPolylineDist, bestCrossTrack)
    }

    /**
     * Projects a point (lat, lon) onto a road polyline of Google Maps LatLng points.
     */
    fun projectPointOntoPolyline(lat: Double, lon: Double, polyline: List<LatLng>): Pair<Double, Double> {
        return projectPointOntoCoordinates(lat, lon, polyline.map { Pair(it.latitude, it.longitude) })
    }

    /**
     * Determines which stop is next along the route based on:
     * - Current bus coordinates
     * - Ordered stop sequence (filtering out stops without coordinates)
     * - Optional road-snapped polyline projection (OSRM road path)
     * - Orthogonal projection onto route segments
     * - Bus speed and heading (rejecting stops behind the bus)
     * - Monotonic forward progression
     */
    fun findNextStopIndex(
        busLat: Double,
        busLon: Double,
        busSpeedMs: Float,
        busBearing: Float,
        stops: List<BusStop>,
        previousPassedIndex: Int = -1,
        routeRoadPolyline: List<LatLng>? = null,
        routeRoadCoordinates: List<Pair<Double, Double>>? = null
    ): Int {
        if (stops.isEmpty()) return -1
        if (stops.size == 1) return 0

        val physicalIndices = stops.indices.filter { stops[it].hasCoordinates }
        if (physicalIndices.isEmpty()) return -1
        if (physicalIndices.size == 1) return physicalIndices[0]

        val minAllowedIndex = if (previousPassedIndex in 0 until stops.size - 1) {
            previousPassedIndex
        } else {
            0
        }

        val candidateIndices = physicalIndices.filter { it >= minAllowedIndex }.ifEmpty {
            listOf(physicalIndices.last())
        }

        // Method 1: Road-snapped 1D Polyline Projection
        val roadCoords: List<Pair<Double, Double>>? = routeRoadCoordinates ?: routeRoadPolyline?.map { Pair(it.latitude, it.longitude) }
        if (roadCoords != null && roadCoords.size >= 2) {
            val (busDistOnRoad, busCrossTrack) = projectPointOntoCoordinates(busLat, busLon, roadCoords)
            if (busCrossTrack <= 160.0) {
                for (origIdx in candidateIndices) {
                    val stop = stops[origIdx]
                    val (stopDistOnRoad, _) = projectPointOntoCoordinates(stop.latitude, stop.longitude, roadCoords)
                    // If stop is at or ahead of bus (with 40m arrival margin), this is the next stop!
                    if (stopDistOnRoad >= busDistOnRoad - 40.0) {
                        return origIdx
                    }
                }
                return candidateIndices.last()
            }
        }

        // Method 2: Direct Proximity Check (Is bus arriving at or departing a stop?)
        for (i in candidateIndices.indices) {
            val origIdx = candidateIndices[i]
            val stop = stops[origIdx]
            val d = calculateDistanceMeters(busLat, busLon, stop.latitude, stop.longitude)
            if (d <= STOP_ARRIVAL_RADIUS_METERS) {
                if (i == candidateIndices.lastIndex) {
                    return origIdx
                }
                val nextOrigIdx = candidateIndices[i + 1]
                val nextStop = stops[nextOrigIdx]
                val dNext = calculateDistanceMeters(busLat, busLon, nextStop.latitude, nextStop.longitude)
                val stopToNextStopDist = calculateDistanceMeters(
                    stop.latitude, stop.longitude,
                    nextStop.latitude, nextStop.longitude
                )

                val isMovingTowardsNext = if (busBearing > 0.0f && busSpeedMs > 1.0f) {
                    val bearingToNext = calculateBearing(busLat, busLon, nextStop.latitude, nextStop.longitude)
                    Math.abs(normalizeAngleDiff(busBearing, bearingToNext)) <= 100.0f
                } else {
                    dNext < stopToNextStopDist * 0.95
                }

                return if (isMovingTowardsNext && busSpeedMs > 1.0f) {
                    nextOrigIdx
                } else {
                    origIdx
                }
            }
        }

        // Method 3: Segment-based orthogonal projection along consecutive physical stops
        var bestCandidateSegmentIdx = -1
        var bestSegmentDistance = Double.MAX_VALUE

        for (i in 0 until candidateIndices.size - 1) {
            val stopA = stops[candidateIndices[i]]
            val stopB = stops[candidateIndices[i + 1]]

            val segLen = calculateDistanceMeters(stopA.latitude, stopA.longitude, stopB.latitude, stopB.longitude)
            if (segLen < 1.0) continue

            val midLat = Math.toRadians((stopA.latitude + stopB.latitude) / 2.0)
            val dx = (stopB.longitude - stopA.longitude) * cos(midLat) * 111320.0
            val dy = (stopB.latitude - stopA.latitude) * 111320.0
            val segLenSq = dx * dx + dy * dy

            val px = (busLon - stopA.longitude) * cos(midLat) * 111320.0
            val py = (busLat - stopA.latitude) * 111320.0
            val t = (px * dx + py * dy) / segLenSq

            val maxCorridor = max(450.0, segLen * 0.45)
            if (t in -0.05..1.05) {
                val clampedT = t.coerceIn(0.0, 1.0)
                val projLat = stopA.latitude + clampedT * (stopB.latitude - stopA.latitude)
                val projLon = stopA.longitude + clampedT * (stopB.longitude - stopA.longitude)
                val crossTrackDist = calculateDistanceMeters(busLat, busLon, projLat, projLon)

                val distToStopA = calculateDistanceMeters(busLat, busLon, stopA.latitude, stopA.longitude)
                val distToStopB = calculateDistanceMeters(busLat, busLon, stopB.latitude, stopB.longitude)

                if (crossTrackDist <= maxCorridor && distToStopA <= segLen + 400.0 && distToStopB <= segLen + 400.0) {
                    val headingPenalty = if (busBearing > 0.0f && busSpeedMs > 1.2f) {
                        val segBearing = calculateBearing(stopA.latitude, stopA.longitude, stopB.latitude, stopB.longitude)
                        val angleDiff = Math.abs(normalizeAngleDiff(busBearing, segBearing))
                        if (angleDiff > 105.0f) 500.0 else 0.0
                    } else 0.0

                    val sequencePenalty = i * 20.0
                    val effectiveDist = crossTrackDist + headingPenalty + sequencePenalty

                    if (effectiveDist < bestSegmentDistance) {
                        bestSegmentDistance = effectiveDist
                        bestCandidateSegmentIdx = i
                    }
                }
            }
        }

        if (bestCandidateSegmentIdx != -1) {
            val stopA = stops[candidateIndices[bestCandidateSegmentIdx]]
            val stopB = stops[candidateIndices[bestCandidateSegmentIdx + 1]]
            val midLat = Math.toRadians((stopA.latitude + stopB.latitude) / 2.0)
            val dx = (stopB.longitude - stopA.longitude) * cos(midLat) * 111320.0
            val dy = (stopB.latitude - stopA.latitude) * 111320.0
            val segLenSq = max(1.0, dx * dx + dy * dy)
            val px = (busLon - stopA.longitude) * cos(midLat) * 111320.0
            val py = (busLat - stopA.latitude) * 111320.0
            val t = (px * dx + py * dy) / segLenSq

            val distToStopA = calculateDistanceMeters(busLat, busLon, stopA.latitude, stopA.longitude)
            return if (t > 0.05 || distToStopA > 60.0) {
                candidateIndices[bestCandidateSegmentIdx + 1]
            } else {
                candidateIndices[bestCandidateSegmentIdx]
            }
        }

        // Method 4: Distance-based search rejecting stops behind the bus
        var bestIdx = candidateIndices.last()
        var minDistance = Double.MAX_VALUE

        for (i in candidateIndices.indices) {
            val origIdx = candidateIndices[i]
            val stop = stops[origIdx]
            val d = calculateDistanceMeters(busLat, busLon, stop.latitude, stop.longitude)

            var isBehind = false
            if (i < candidateIndices.lastIndex) {
                val nextStop = stops[candidateIndices[i + 1]]
                val midLat = Math.toRadians((stop.latitude + nextStop.latitude) / 2.0)
                val dx = (nextStop.longitude - stop.longitude) * cos(midLat) * 111320.0
                val dy = (nextStop.latitude - stop.latitude) * 111320.0
                val segLenSq = max(1.0, dx * dx + dy * dy)
                val px = (busLon - stop.longitude) * cos(midLat) * 111320.0
                val py = (busLat - stop.latitude) * 111320.0
                val t = (px * dx + py * dy) / segLenSq
                if (t > 0.08 && d > 60.0) {
                    isBehind = true
                }
            }

            if (!isBehind && busBearing > 0.0f && busSpeedMs > 1.0f) {
                val bearingToStop = calculateBearing(busLat, busLon, stop.latitude, stop.longitude)
                val angleDiff = Math.abs(normalizeAngleDiff(busBearing, bearingToStop))
                if (angleDiff > 95.0f && d > 60.0) {
                    isBehind = true
                }
            }

            if (!isBehind && d < minDistance) {
                minDistance = d
                bestIdx = origIdx
            }
        }

        return bestIdx
    }

    /**
     * Computes the complete Route ETA result including Next Stop ETA and all Upcoming Stops ETAs.
     */
    fun computeRouteEta(
        busLat: Double,
        busLon: Double,
        busSpeedMs: Float,
        busBearing: Float,
        trackingState: TrackingDisplayState,
        stops: List<BusStop>,
        previousPassedIndex: Int = -1,
        previousSpeedMs: Float = -1.0f,
        previousEtaMinutes: Int? = null,
        calibratedDwellSeconds: Double = DEFAULT_INTERMEDIATE_DWELL_SECONDS,
        detourRoadDistanceMeters: Double? = null,
        isDetourActive: Boolean = false,
        routeRoadPolyline: List<LatLng>? = null
    ): RouteEtaResult {
        if (stops.isEmpty()) {
            return RouteEtaResult(
                nextStopEta = null,
                upcomingStops = emptyList(),
                status = EtaStatus.NO_ROUTE,
                statusMessage = "ETA unavailable",
                isReliable = false
            )
        }

        // Handle inactive or waiting states
        if (trackingState == TrackingDisplayState.INACTIVE) {
            val firstStop = stops.first()
            val dummyNext = StopEta(
                stop = firstStop,
                etaMinutes = null,
                etaFormatted = "—",
                distanceMeters = 0.0,
                distanceFormatted = "—",
                isPassed = false,
                isNext = true
            )
            return RouteEtaResult(
                nextStopEta = dummyNext,
                upcomingStops = stops.map {
                    StopEta(it, null, "—", 0.0, "—", isPassed = false, isNext = it.stopId == firstStop.stopId)
                },
                status = EtaStatus.BUS_NOT_STARTED,
                statusMessage = "Bus has not started",
                isReliable = false
            )
        }

        if (trackingState == TrackingDisplayState.WAITING_FOR_GPS || (busLat == 0.0 && busLon == 0.0)) {
            val firstStop = stops.first()
            val dummyNext = StopEta(
                stop = firstStop,
                etaMinutes = null,
                etaFormatted = "—",
                distanceMeters = 0.0,
                distanceFormatted = "—",
                isPassed = false,
                isNext = true
            )
            return RouteEtaResult(
                nextStopEta = dummyNext,
                upcomingStops = stops.map {
                    StopEta(it, null, "—", 0.0, "—", isPassed = false, isNext = it.stopId == firstStop.stopId)
                },
                status = EtaStatus.WAITING_FOR_GPS,
                statusMessage = "Waiting for live location",
                isReliable = false
            )
        }

        val nextIndex = findNextStopIndex(
            busLat, busLon, busSpeedMs, busBearing, stops, previousPassedIndex, routeRoadPolyline
        )

        // 1. Exponential Moving Average for speed smoothing
        val smoothedSpeedMs = if (previousSpeedMs >= 0.0f) {
            SPEED_SMOOTHING_ALPHA * busSpeedMs + (1.0f - SPEED_SMOOTHING_ALPHA) * previousSpeedMs
        } else {
            busSpeedMs
        }
        val liveSpeedKmh = smoothedSpeedMs * 3.6f

        // 2. Context-aware transit speed:
        val effectiveSpeedKmh = when {
            liveSpeedKmh in 12.0f..65.0f -> {
                0.50f * liveSpeedKmh + 0.50f * NOMINAL_URBAN_SPEED_KMH
            }
            liveSpeedKmh > 65.0f -> {
                40.0f
            }
            else -> {
                NOMINAL_URBAN_SPEED_KMH
            }
        }
        val effectiveSpeedMs = max(effectiveSpeedKmh / 3.6, 4.5)

        // Terminal check: Bus arrived at the final destination
        val physicalStops = stops.filter { it.hasCoordinates }
        val lastPhysicalStop = physicalStops.lastOrNull() ?: stops.last()
        val distToFinalStop = calculateDistanceMeters(
            busLat, busLon,
            lastPhysicalStop.latitude, lastPhysicalStop.longitude
        )
        val isAtFinalStop = (nextIndex == stops.indexOfFirst { it.stopId == lastPhysicalStop.stopId }) &&
                (distToFinalStop <= STOP_ARRIVAL_RADIUS_METERS) &&
                (liveSpeedKmh < 3.0f)

        if (isAtFinalStop) {
            val finalStopEta = StopEta(
                stop = lastPhysicalStop,
                etaMinutes = 0,
                etaFormatted = "Arrived",
                distanceMeters = distToFinalStop,
                distanceFormatted = "At stop",
                isPassed = false,
                isNext = true
            )
            val upcoming = stops.mapIndexed { idx, stop ->
                if (stop.stopId == lastPhysicalStop.stopId) {
                    finalStopEta
                } else if (!stop.hasCoordinates) {
                    StopEta(stop, null, "—", 0.0, "—", isPassed = true, isNext = false)
                } else {
                    StopEta(stop, null, "Passed", 0.0, "Passed", isPassed = true, isNext = false)
                }
            }
            return RouteEtaResult(
                nextStopEta = finalStopEta,
                upcomingStops = upcoming,
                status = EtaStatus.ARRIVED_FINAL_STOP,
                statusMessage = "At Final Destination",
                isReliable = true
            )
        }

        // Compute ETAs for upcoming stops along the route sequence
        val resultStops = mutableListOf<StopEta>()
        var cumulativeDistance = 0.0
        var lastValidMinutes = 0
        var prevPhysicalStop: BusStop? = null

        for (i in stops.indices) {
            val stop = stops[i]
            if (!stop.hasCoordinates) {
                val isPassed = (i < nextIndex)
                resultStops.add(
                    StopEta(
                        stop = stop,
                        etaMinutes = null,
                        etaFormatted = "—",
                        distanceMeters = 0.0,
                        distanceFormatted = "—",
                        isPassed = isPassed,
                        isNext = false
                    )
                )
                continue
            }

            when {
                i < nextIndex -> {
                    // Already passed
                    prevPhysicalStop = stop
                    resultStops.add(
                        StopEta(
                            stop = stop,
                            etaMinutes = null,
                            etaFormatted = "Passed",
                            distanceMeters = 0.0,
                            distanceFormatted = "Passed",
                            isPassed = true,
                            isNext = false
                        )
                    )
                }
                i == nextIndex -> {
                    // Direct distance from live bus marker to this next stop
                    val directDist = calculateDistanceMeters(busLat, busLon, stop.latitude, stop.longitude)

                    val dist = if (isDetourActive && detourRoadDistanceMeters != null && detourRoadDistanceMeters > 0.0) {
                        detourRoadDistanceMeters
                    } else if (prevPhysicalStop != null) {
                        val segLen = calculateDistanceMeters(prevPhysicalStop.latitude, prevPhysicalStop.longitude, stop.latitude, stop.longitude)
                        if (segLen > 10.0) {
                            val midLat = Math.toRadians((prevPhysicalStop.latitude + stop.latitude) / 2.0)
                            val dx = (stop.longitude - prevPhysicalStop.longitude) * cos(midLat) * 111320.0
                            val dy = (stop.latitude - prevPhysicalStop.latitude) * 111320.0
                            val segLenSq = dx * dx + dy * dy
                            val px = (busLon - prevPhysicalStop.longitude) * cos(midLat) * 111320.0
                            val py = (busLat - prevPhysicalStop.latitude) * 111320.0
                            val t = (px * dx + py * dy) / segLenSq

                            if (t in 0.0..1.0) {
                                val projLat = prevPhysicalStop.latitude + t * (stop.latitude - prevPhysicalStop.latitude)
                                val projLon = prevPhysicalStop.longitude + t * (stop.longitude - prevPhysicalStop.longitude)
                                val crossTrack = calculateDistanceMeters(busLat, busLon, projLat, projLon)
                                if (crossTrack <= 450.0) {
                                    val remainingChord = (1.0 - t) * segLen
                                    max(directDist, sqrt(crossTrack * crossTrack + remainingChord * remainingChord))
                                } else {
                                    directDist
                                }
                            } else {
                                directDist
                            }
                        } else {
                            directDist
                        }
                    } else {
                        directDist
                    }
                    cumulativeDistance = dist

                    val segSpeedMs = calculateSpatiallyDecayedSpeedMs(dist, smoothedSpeedMs)
                    val seconds = dist / segSpeedMs
                    val (etaMins, etaFmt) = formatEta(seconds, dist, previousEtaMinutes)
                    lastValidMinutes = etaMins ?: 0
                    prevPhysicalStop = stop

                    resultStops.add(
                        StopEta(
                            stop = stop,
                            etaMinutes = etaMins,
                            etaFormatted = etaFmt,
                            distanceMeters = dist,
                            distanceFormatted = formatDistance(dist),
                            isPassed = false,
                            isNext = true
                        )
                    )
                }
                else -> {
                    // Subsequent upcoming stops
                    val hopDist = if (prevPhysicalStop != null) {
                        calculateDistanceMeters(
                            prevPhysicalStop.latitude, prevPhysicalStop.longitude,
                            stop.latitude, stop.longitude
                        )
                    } else {
                        calculateDistanceMeters(busLat, busLon, stop.latitude, stop.longitude)
                    }
                    cumulativeDistance += hopDist
                    prevPhysicalStop = stop

                    val intermediateStopsCount = i - nextIndex
                    val dwellSeconds = intermediateStopsCount * calibratedDwellSeconds
                    val segSpeedMs = calculateSpatiallyDecayedSpeedMs(cumulativeDistance, smoothedSpeedMs)
                    val travelSeconds = cumulativeDistance / segSpeedMs
                    val totalSeconds = travelSeconds + dwellSeconds

                    var (etaMins, etaFmt) = formatEta(totalSeconds, cumulativeDistance, null)

                    val minAllowedEta = lastValidMinutes + max(1, (calibratedDwellSeconds / 60.0).roundToInt())
                    if (etaMins != null && etaMins < minAllowedEta) {
                        etaMins = minAllowedEta
                        etaFmt = "~$etaMins min"
                    }
                    if (etaMins != null) {
                        lastValidMinutes = etaMins
                    }

                    resultStops.add(
                        StopEta(
                            stop = stop,
                            etaMinutes = etaMins,
                            etaFormatted = etaFmt,
                            distanceMeters = cumulativeDistance,
                            distanceFormatted = formatDistance(cumulativeDistance),
                            isPassed = false,
                            isNext = false
                        )
                    )
                }
            }
        }

        val nextStopEta = resultStops.find { it.isNext } ?: resultStops.firstOrNull { it.stop.hasCoordinates && !it.isPassed } ?: resultStops.firstOrNull()

        val status = when {
            trackingState == TrackingDisplayState.STALE -> EtaStatus.STALE
            isDetourActive -> EtaStatus.DETOUR_ACTIVE
            else -> EtaStatus.NORMAL
        }
        val statusMessage = when {
            trackingState == TrackingDisplayState.STALE -> "Location is stale"
            isDetourActive -> "Detour active • Dynamic ETA"
            else -> "Active"
        }

        return RouteEtaResult(
            nextStopEta = nextStopEta,
            upcomingStops = resultStops,
            status = status,
            statusMessage = statusMessage,
            isReliable = (status == EtaStatus.NORMAL || status == EtaStatus.DETOUR_ACTIVE)
        )
    }

    fun formatEta(
        seconds: Double,
        distanceMeters: Double,
        previousMinutes: Int? = null
    ): Pair<Int, String> {
        if (distanceMeters <= STOP_ARRIVAL_RADIUS_METERS) {
            return 0 to "Arriving now"
        }
        if (seconds < 60.0) {
            return 1 to "< 1 min"
        }
        val rawMinutes = max(1, (seconds / 60.0).roundToInt()).coerceAtMost(MAX_REALISTIC_ETA_MINUTES)

        // Asymmetric Deadband Filter:
        // When bus accelerates / approaches: beta_down = 0.70 (fast tracking)
        // When bus temporarily stops at signals / pick-ups: beta_up = 0.25 (gentle rise, prevents runaway spikes)
        val stabilizedMinutes = if (previousMinutes != null && previousMinutes > 0 && Math.abs(rawMinutes - previousMinutes) <= 6) {
            val delta = rawMinutes - previousMinutes
            if (delta < 0) {
                val filtered = Math.round(BETA_DOWN * rawMinutes + (1.0 - BETA_DOWN) * previousMinutes).toInt()
                min(previousMinutes - 1, max(rawMinutes, filtered))
            } else if (delta > 0) {
                val filtered = Math.round(BETA_UP * rawMinutes + (1.0 - BETA_UP) * previousMinutes).toInt()
                max(previousMinutes, min(rawMinutes, filtered))
            } else {
                rawMinutes
            }
        } else {
            rawMinutes
        }

        val boundedMinutes = stabilizedMinutes.coerceIn(1, MAX_REALISTIC_ETA_MINUTES)
        return boundedMinutes to "~$boundedMinutes min"
    }
}
