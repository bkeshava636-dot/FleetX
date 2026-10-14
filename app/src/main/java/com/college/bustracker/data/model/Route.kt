package com.college.bustracker.data.model

data class Route(
    val routeId: String = "",
    val name: String = "",
    val description: String = "",
    val active: Boolean = true,
    val busId: String = "",
    val stops: List<BusStop> = emptyList(),
    val schedules: RouteSchedule = RouteSchedule(),
    val schedulePdfUrl: String? = null,
    val schedulePdfName: String? = null
)
