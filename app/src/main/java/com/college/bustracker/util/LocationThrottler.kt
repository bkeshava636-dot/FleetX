package com.college.bustracker.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationThrottler {

    const val MIN_MOVING_INTERVAL_MS = 8_000L   // 8 seconds while moving
    const val STATIONARY_INTERVAL_MS = 25_000L   // 25 seconds when stationary
    const val MAX_HEARTBEAT_INTERVAL_MS = 30_000L // 30 seconds forced heartbeat
    const val SPEED_MOVING_THRESHOLD_MPS = 1.0f  // 1 m/s (~3.6 km/h)
    const val DISPLACEMENT_THRESHOLD_METERS = 10.0 // 10 meters

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
     * Decides whether a new GPS coordinate should be transmitted to the backend.
     * Prevents spamming the database while stationary, conserving battery and Firebase bandwidth.
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
        // First update must always be sent
        if (lastSentTimestamp <= 0L) return true

        val elapsed = newTimestamp - lastSentTimestamp
        if (elapsed < 3_000L) {
            // Absolute minimum debounce (never send faster than 3 seconds)
            return false
        }

        // Forced heartbeat
        if (elapsed >= MAX_HEARTBEAT_INTERVAL_MS) {
            return true
        }

        val distance = calculateDistanceMeters(lastSentLat, lastSentLng, newLat, newLng)
        val isMoving = speedMps > SPEED_MOVING_THRESHOLD_MPS || distance > DISPLACEMENT_THRESHOLD_METERS

        return if (isMoving) {
            elapsed >= MIN_MOVING_INTERVAL_MS
        } else {
            elapsed >= STATIONARY_INTERVAL_MS
        }
    }
}
