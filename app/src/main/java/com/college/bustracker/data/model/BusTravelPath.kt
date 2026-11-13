package com.college.bustracker.data.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Encapsulates the optimized road trajectory a bus actually travels most of the time.
 * Stored canonically in STOPS_TO_COLLEGE sequence (Stop 1 -> Campus) for deterministic rendering
 * and projection along route in EtaEngine.
 */
@IgnoreExtraProperties
data class BusTravelPath(
    val busId: String = "",
    val routeId: String = "",
    val direction: String = "STOPS_TO_COLLEGE",
    val points: List<LatLng> = emptyList(),
    val lastUpdated: Long = 0L
)
