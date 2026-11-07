package com.college.bustracker.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BusStop(
    val stopId: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val order: Int = 0,
    val active: Boolean = true,
    val visibleOnMap: Boolean = true
) {
    val hasCoordinates: Boolean
        get() = visibleOnMap && (latitude != 0.0 || longitude != 0.0) && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
