package com.college.bustracker.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of the velocity-adaptive throttling evaluation.
 */
data class ThrottlerDecision(
    val shouldDispatch: Boolean,
    val isStationaryState: Boolean,
    val updatedStationaryStartTime: Long,
    val overrideSpeedZero: Boolean
)

/**
 * Velocity-Adaptive RTDB Throttler.
 *
 * Reduces unnecessary RTDB write volume and battery drain when buses are stuck in heavy traffic
 * or idling at stops:
 * - Moving State (speed >= 1.5 m/s or heading change > 15°): Streams every 4 seconds.
 * - Stationary State (speed < 1.5 m/s for > 15s): Throttles to a 20-second heartbeat (speed forced to 0).
 * - Immediate Wake-Up: As soon as speed exceeds 1.5 m/s again, instantly resumes 4-second streaming.
 */
object LocationThrottler {

    const val SPEED_THRESHOLD_MPS = 1.5f              // 1.5 m/s (~5.4 km/h) threshold
    const val MOVING_INTERVAL_MS = 4_000L              // 4 seconds while moving
    const val STATIONARY_HEARTBEAT_MS = 20_000L        // 20-second heartbeat when stationary
    const val STATIONARY_QUALIFY_DURATION_MS = 15_000L // 15 seconds below threshold before entering stationary throttle
    const val HEADING_CHANGE_THRESHOLD_DEG = 15.0f     // 15° heading delta triggers immediate update
    const val MIN_DEBOUNCE_MS = 3_000L                 // Hard minimum debounce to prevent packet storms

    /**
     * Calculates the great-circle distance between two points in meters using the Haversine formula.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
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
     * Velocity-adaptive dispatch decision.
     */
    fun shouldDispatchAdaptive(
        lastSentLat: Double,
        lastSentLng: Double,
        lastSentTimestamp: Long,
        lastSentBearing: Float,
        newLat: Double,
        newLng: Double,
        newTimestamp: Long,
        newBearing: Float,
        speedMps: Float,
        stationaryStartTime: Long
    ): ThrottlerDecision {
        // First update must always be sent immediately
        if (lastSentTimestamp <= 0L) {
            return ThrottlerDecision(
                shouldDispatch = true,
                isStationaryState = speedMps < SPEED_THRESHOLD_MPS,
                updatedStationaryStartTime = if (speedMps < SPEED_THRESHOLD_MPS) newTimestamp else 0L,
                overrideSpeedZero = false
            )
        }

        val elapsed = newTimestamp - lastSentTimestamp
        val isMoving = speedMps >= SPEED_THRESHOLD_MPS
        val headingDelta = abs(normalizeAngleDiff(newBearing, lastSentBearing))

        if (isMoving) {
            // IMMEDIATE WAKE-UP: Reset stationary timer
            val isHeadingTurn = headingDelta > HEADING_CHANGE_THRESHOLD_DEG && elapsed >= MIN_DEBOUNCE_MS
            val isIntervalReady = elapsed >= MOVING_INTERVAL_MS

            val dispatch = isHeadingTurn || isIntervalReady
            return ThrottlerDecision(
                shouldDispatch = dispatch,
                isStationaryState = false,
                updatedStationaryStartTime = 0L,
                overrideSpeedZero = false
            )
        } else {
            // Speed is below 1.5 m/s
            val currentStationaryStart = if (stationaryStartTime > 0L) stationaryStartTime else newTimestamp
            val stationaryDuration = newTimestamp - currentStationaryStart
            val isStationaryThrottled = stationaryDuration >= STATIONARY_QUALIFY_DURATION_MS

            return if (isStationaryThrottled) {
                // Stationary for > 15 seconds: Throttle to 20-second heartbeat with speed = 0
                val dispatch = elapsed >= STATIONARY_HEARTBEAT_MS
                ThrottlerDecision(
                    shouldDispatch = dispatch,
                    isStationaryState = true,
                    updatedStationaryStartTime = currentStationaryStart,
                    overrideSpeedZero = true
                )
            } else {
                // Still in transition (< 15s stationary): Maintain 4-second streaming cadence
                val dispatch = elapsed >= MOVING_INTERVAL_MS
                ThrottlerDecision(
                    shouldDispatch = dispatch,
                    isStationaryState = false,
                    updatedStationaryStartTime = currentStationaryStart,
                    overrideSpeedZero = false
                )
            }
        }
    }

    /**
     * Backward-compatible helper for legacy callers.
     */
    fun shouldDispatchUpdate(
        lastSentLat: Double,
        lastSentLng: Double,
        lastSentTimestamp: Long,
        newLat: Double,
        newLng: Double,
        newTimestamp: Long,
        speedMps: Float
    ): Boolean {
        return shouldDispatchAdaptive(
            lastSentLat = lastSentLat,
            lastSentLng = lastSentLng,
            lastSentTimestamp = lastSentTimestamp,
            lastSentBearing = 0f,
            newLat = newLat,
            newLng = newLng,
            newTimestamp = newTimestamp,
            newBearing = 0f,
            speedMps = speedMps,
            stationaryStartTime = 0L
        ).shouldDispatch
    }
}
