package com.college.bustracker.data.model

data class ScheduleWindow(
    val startTime: String = "07:00", // "HH:mm" in 24-hour format
    val endTime: String = "09:00",   // "HH:mm" in 24-hour format
    val enabled: Boolean = true
)

data class RouteSchedule(
    val morning: ScheduleWindow = ScheduleWindow("07:00", "09:00", true),
    val evening: ScheduleWindow = ScheduleWindow("16:00", "18:00", true)
)
