package com.college.bustracker.data.model

import com.google.firebase.firestore.Exclude

data class Bus(
    val busId: String = "",
    val busNumber: String = "",
    val registrationNumber: String = "",
    val routeId: String = "",
    val driverId: String? = null,
    val driverName: String? = null,
    val capacity: Int = 50,
    val active: Boolean = true
) {
    @get:Exclude
    val isActive: Boolean
        get() = active

    @get:Exclude
    val numericBusNumber: Int
        get() {
            val regex = Regex("""\d+""")
            val numStr = regex.find(busNumber)?.value ?: regex.find(busId)?.value
            return numStr?.toIntOrNull() ?: Int.MAX_VALUE
        }
}

