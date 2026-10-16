package com.college.bustracker.util

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.TrackingDisplayState
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
    private const val NOMINAL_URBAN_SPEED_KMH = 20.0f
    private const val STOP_ARRIVAL_RADIUS_METERS = 45.0
    const val DEFAULT_INTERMEDIATE_DWELL_SECONDS = 30.0
    private const val MAX_REALISTIC_ETA_MINUTES = 180
    private const val SPEED_SMOOTHING_ALPHA = 0.40f

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
     * Determines which stop is next along the route based on:
     * - Current bus coordinates
     * - Ordered stop sequence
     * - Orthogonal projection onto route segments
     * - Bus speed and heading (if moving)
     * - Monotonic forward progression
     */
    fun findNextStopIndex(
        busLat: Double,
        busLon: Double,
        busSpeedMs: Float,
        busBearing: Float,
        stops: List<BusStop>,
        previousPassedIndex: Int = -1
    ): Int {
        if (stops.isEmpty()) return -1
        if (stops.size == 1) return 0

        val minAllowedIndex = if (previousPassedIndex in 0 until stops.size - 1) {
            previousPassedIndex
        } else {
            0
        }

        // 1. Direct proximity check: Is the bus currently at a stop?
        for (i in minAllowedIndex until stops.size) {
            val d = calculateDistanceMeters(busLat, busLon, stops[i].latitude, stops[i].longitude)
            if (d <= STOP_ARRIVAL_RADIUS_METERS) {
                if (i == stops.lastIndex) {
                    return i
                }
                val nextStop = stops[i + 1]
                val dNext = calculateDistanceMeters(busLat, busLon, nextStop.latitude, nextStop.longitude)
                val stopToNextStopDist = calculateDistanceMeters(
                    stops[i].latitude, stops[i].longitude,
                    nextStop.latitude, nextStop.longitude
                )

                val isMovingTowardsNext = if (busBearing > 0.0f && busSpeedMs > 1.2f) {
                    val bearingToNext = calculateBearing(busLat, busLon, nextStop.latitude, nextStop.longitude)
                    Math.abs(normalizeAngleDiff(busBearing, bearingToNext)) <= 90.0f
                } else {
                    dNext < stopToNextStopDist
                }

                return if (isMovingTowardsNext && busSpeedMs > 1.2f) {
                    i + 1
                } else {
                    i
                }
            }
        }

        // 2. Segment-based orthogonal projection along upcoming consecutive stops
        var bestSegmentIndex = -1
        var bestSegmentDistance = Double.MAX_VALUE

        for (i in minAllowedIndex until stops.size - 1) {
            val stopA = stops[i]
            val stopB = stops[i + 1]

            val segLen = calculateDistanceMeters(stopA.latitude, stopA.longitude, stopB.latitude, stopB.longitude)
            if (segLen < 1.0) continue // Skip overlapping coordinates

            val midLat = Math.toRadians((stopA.latitude + stopB.latitude) / 2.0)
            val dx = (stopB.longitude - stopA.longitude) * cos(midLat) * 111320.0
            val dy = (stopB.latitude - stopA.latitude) * 111320.0
            val segLenSq = dx * dx + dy * dy

            val px = (busLon - stopA.longitude) * cos(midLat) * 111320.0
            val py = (busLat - stopA.latitude) * 111320.0
            val t = (px * dx + py * dy) / segLenSq

            if (t in -0.05..1.05) {
                val clampedT = t.coerceIn(0.0, 1.0)
                val projLat = stopA.latitude + clampedT * (stopB.latitude - stopA.latitude)
                val projLon = stopA.longitude + clampedT * (stopB.longitude - stopA.longitude)
                val crossTrackDist = calculateDistanceMeters(busLat, busLon, projLat, projLon)

                val distToStopA = calculateDistanceMeters(busLat, busLon, stopA.latitude, stopA.longitude)
                val distToStopB = calculateDistanceMeters(busLat, busLon, stopB.latitude, stopB.longitude)

                // Must be within realistic road corridor (<= 250m) and near the actual segment
                if (crossTrackDist <= 250.0 && distToStopA <= segLen + 300.0 && distToStopB <= segLen + 300.0) {
                    val headingPenalty = if (busBearing > 0.0f && busSpeedMs > 1.5f) {
                        val segBearing = calculateBearing(stopA.latitude, stopA.longitude, stopB.latitude, stopB.longitude)
                        val angleDiff = Math.abs(normalizeAngleDiff(busBearing, segBearing))
                        if (angleDiff > 100.0f) 500.0 else 0.0
                    } else 0.0

                    // Small preference to earlier segments to prevent leaping ahead across route loops
                    val sequencePenalty = (i - minAllowedIndex) * 20.0
                    val effectiveDist = crossTrackDist + headingPenalty + sequencePenalty

                    if (effectiveDist < bestSegmentDistance) {
                        bestSegmentDistance = effectiveDist
                        bestSegmentIndex = i
                    }
                }
            }
        }

        if (bestSegmentIndex != -1) {
            val targetStopIndex = bestSegmentIndex + 1
            return max(targetStopIndex, minAllowedIndex)
        }

        // 3. Fallback: Distance-based search honoring progression
        var closestForwardIndex = max(0, minAllowedIndex)
        var minDistance = Double.MAX_VALUE

        for (i in minAllowedIndex until stops.size) {
            val d = calculateDistanceMeters(busLat, busLon, stops[i].latitude, stops[i].longitude)
            if (d < minDistance) {
                minDistance = d
                closestForwardIndex = i
            }
        }

        return closestForwardIndex
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
        isDetourActive: Boolean = false
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
            busLat, busLon, busSpeedMs, busBearing, stops, previousPassedIndex
        )

        // 1. Exponential Moving Average for speed smoothing
        val smoothedSpeedMs = if (previousSpeedMs >= 0.0f) {
            SPEED_SMOOTHING_ALPHA * busSpeedMs + (1.0f - SPEED_SMOOTHING_ALPHA) * previousSpeedMs
        } else {
            busSpeedMs
        }
        val liveSpeedKmh = smoothedSpeedMs * 3.6f

        // 2. Context-aware stationary vs traffic detection
        val distToClosestStop = stops.minOfOrNull {
            calculateDistanceMeters(busLat, busLon, it.latitude, it.longitude)
        } ?: Double.MAX_VALUE
        val isAtAnyStop = distToClosestStop <= STOP_ARRIVAL_RADIUS_METERS

        // 2. Context-aware transit speed:
        // For distances > 500m, momentary crawl speed must not blow up the ETA across entire route.
        // Blend live speed with urban transit nominal baseline (22 km/h).
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
        val effectiveSpeedMs = max(effectiveSpeedKmh / 3.6, 4.5) // Lower bounded at 4.5 m/s (~16.2 km/h) to prevent runaway ETA

        // Terminal check: Bus arrived at the final destination
        val distToFinalStop = calculateDistanceMeters(
            busLat, busLon,
            stops.last().latitude, stops.last().longitude
        )
        val isAtFinalStop = (nextIndex == stops.lastIndex) &&
                (distToFinalStop <= STOP_ARRIVAL_RADIUS_METERS) &&
                (liveSpeedKmh < 3.0f)

        if (isAtFinalStop) {
            val finalStopEta = StopEta(
                stop = stops.last(),
                etaMinutes = 0,
                etaFormatted = "Arrived",
                distanceMeters = distToFinalStop,
                distanceFormatted = "At stop",
                isPassed = false,
                isNext = true
            )
            val upcoming = stops.mapIndexed { idx, stop ->
                if (idx < stops.lastIndex) {
                    StopEta(stop, null, "Passed", 0.0, "Passed", isPassed = true, isNext = false)
                } else {
                    finalStopEta
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

        for (i in stops.indices) {
            val stop = stops[i]
            when {
                i < nextIndex -> {
                    // Already passed
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

                    // If detour is active with verified road distance, prioritize real road travel distance
                    val dist = if (isDetourActive && detourRoadDistanceMeters != null && detourRoadDistanceMeters > 0.0) {
                        detourRoadDistanceMeters
                    } else if (nextIndex > 0 && nextIndex < stops.size) {
                        val prevStop = stops[nextIndex - 1]
                        val segLen = calculateDistanceMeters(prevStop.latitude, prevStop.longitude, stop.latitude, stop.longitude)
                        if (segLen > 10.0) {
                            val midLat = Math.toRadians((prevStop.latitude + stop.latitude) / 2.0)
                            val dx = (stop.longitude - prevStop.longitude) * cos(midLat) * 111320.0
                            val dy = (stop.latitude - prevStop.latitude) * 111320.0
                            val segLenSq = dx * dx + dy * dy
                            val px = (busLon - prevStop.longitude) * cos(midLat) * 111320.0
                            val py = (busLat - prevStop.latitude) * 111320.0
                            val t = (px * dx + py * dy) / segLenSq

                            if (t in 0.0..1.0) {
                                val projLat = prevStop.latitude + t * (stop.latitude - prevStop.latitude)
                                val projLon = prevStop.longitude + t * (stop.longitude - prevStop.longitude)
                                val crossTrack = calculateDistanceMeters(busLat, busLon, projLat, projLon)
                                if (crossTrack <= 250.0) {
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

                    val seconds = dist / effectiveSpeedMs
                    val (etaMins, etaFmt) = formatEta(seconds, dist, previousEtaMinutes)
                    lastValidMinutes = etaMins ?: 0

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
                    // Subsequent upcoming stops: hop-by-hop geodesic summation
                    val prevStop = stops[i - 1]
                    val hopDist = calculateDistanceMeters(
                        prevStop.latitude, prevStop.longitude,
                        stop.latitude, stop.longitude
                    )
                    cumulativeDistance += hopDist

                    val intermediateStopsCount = i - nextIndex
                    val dwellSeconds = intermediateStopsCount * calibratedDwellSeconds
                    val travelSeconds = cumulativeDistance / effectiveSpeedMs
                    val totalSeconds = travelSeconds + dwellSeconds

                    var (etaMins, etaFmt) = formatEta(totalSeconds, cumulativeDistance, null)

                    // Ensure strictly monotonic non-decreasing progression for subsequent stops
                    if (etaMins != null && etaMins < lastValidMinutes) {
                        etaMins = lastValidMinutes + 1
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

        val nextStopEta = resultStops.find { it.isNext }
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

        // Apply jitter stabilization only for minor fluctuations (<= 5 min)
        // If delta is larger (stop changed, traffic cleared, driver accelerated), track rawMinutes directly to prevent trap
        val stabilizedMinutes = if (previousMinutes != null && previousMinutes > 0 && Math.abs(rawMinutes - previousMinutes) <= 5) {
            val delta = rawMinutes - previousMinutes
            when {
                delta > 1 -> previousMinutes + 1
                delta < -2 -> previousMinutes - 1
                else -> rawMinutes
            }
        } else {
            rawMinutes
        }

        val boundedMinutes = stabilizedMinutes.coerceIn(1, MAX_REALISTIC_ETA_MINUTES)
        return boundedMinutes to "~$boundedMinutes min"
    }
}
