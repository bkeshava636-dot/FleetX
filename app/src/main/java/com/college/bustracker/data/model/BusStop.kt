package com.college.bustracker.data.model

data class BusStop(
    val stopId: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val order: Int = 0,
    val active: Boolean = true
)
