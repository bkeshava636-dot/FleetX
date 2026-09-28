package com.college.bustracker.data.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * Data model for a location contributor node at location_sources/{busId}/{sourceId}.
 * Used by secondary contributors (students opted into ride assistance) and primary drivers.
 */
@IgnoreExtraProperties
data class LocationSource(
    val busId: String = "",
    val sourceId: String = "",
    val sourceType: String = "SECONDARY_STUDENT", // "PRIMARY_DRIVER" or "SECONDARY_STUDENT"
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val speed: Float = 0.0f,
    val bearing: Float = 0.0f,
    val timestamp: Long = 0L,
    val active: Boolean = true
) {
    /**
     * Validates whether a contributor fix is fresh, accurate, and within physical bounds.
     */
    fun isValid(
        maxAccuracyMeters: Float = 40.0f,
        maxAgeMs: Long = 30_000L,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!active) return false
        if (busId.isBlank() || sourceId.isBlank()) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        if (accuracy <= 0.0f || accuracy > maxAccuracyMeters) return false
        if (timestamp <= 0L || (now - timestamp) > maxAgeMs) return false
        return true
    }
}
