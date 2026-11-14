package com.college.bustracker.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class LiveLocation(
    val busId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Float = 0.0f,
    val bearing: Float = 0.0f,
    val trackingStatus: String = "INACTIVE", // ACTIVE, INACTIVE, WAITING_FOR_GPS
    val mode: String = "MANUAL",             // AUTO, MANUAL
    val routeId: String = "",
    val driverId: String = "",
    val sourceType: String = "PRIMARY_DRIVER", // PRIMARY_DRIVER, SECONDARY_STUDENT_FALLBACK
    val operatorEpoch: Long? = null,
    val temporaryAssignmentId: String? = null,
    val lastUpdated: Long = 0L
) {
    /**
     * Considers the coordinate stale if no update has arrived in over 90 seconds.
     */
    fun isStale(timeoutMillis: Long = 90_000L, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (lastUpdated <= 0L) return true
        val diff = (nowMillis - lastUpdated).coerceAtLeast(0L)
        return diff > timeoutMillis
    }

    @get:Exclude
    val isActive: Boolean
        get() = trackingStatus == "ACTIVE" && !isStale()
}

/**
 * High-fidelity 4-state tracking status for UI presentation.
 */
enum class TrackingDisplayState {
    ACTIVE,          // Tracking live, coordinates valid, updated within 90s (Green)
    WAITING_FOR_GPS, // Driver tracking started, awaiting valid GPS fix (Orange)
    STALE,           // Previously active, but no update for > 90s (Amber)
    INACTIVE         // Driver stopped or tracking idle (Gray)
}

fun LiveLocation?.getDisplayState(nowMillis: Long = System.currentTimeMillis()): TrackingDisplayState {
    if (this == null) return TrackingDisplayState.INACTIVE
    return when (trackingStatus) {
        "WAITING_FOR_GPS" -> TrackingDisplayState.WAITING_FOR_GPS
        "STALE" -> TrackingDisplayState.STALE
        "ACTIVE" -> {
            if (latitude == 0.0 && longitude == 0.0) {
                TrackingDisplayState.WAITING_FOR_GPS
            } else if (lastUpdated <= 0L) {
                TrackingDisplayState.STALE
            } else {
                val elapsed = (nowMillis - lastUpdated).coerceAtLeast(0L)
                if (elapsed > 90_000L) {
                    TrackingDisplayState.STALE
                } else {
                    TrackingDisplayState.ACTIVE
                }
            }
        }
        else -> TrackingDisplayState.INACTIVE
    }
}
