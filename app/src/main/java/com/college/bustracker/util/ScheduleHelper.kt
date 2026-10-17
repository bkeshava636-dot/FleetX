package com.college.bustracker.util

import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RouteDirection {
    STOPS_TO_COLLEGE, // Morning Forward: Stop 1 -> Stop 2 -> ... -> College
    COLLEGE_TO_STOPS  // Evening Reverse: College -> Stop N -> ... -> Stop 1
}

object ScheduleHelper {

    val KOLKATA_ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Kolkata")

    fun nowKolkata(): LocalTime = LocalTime.now(KOLKATA_ZONE)

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /**
     * Determines whether the active trip is Morning (stops to college) or Evening (college to stops).
     */
    fun determineRouteDirection(
        schedule: RouteSchedule = OperatingScheduleService.getSchedule(),
        now: LocalTime = nowKolkata()
    ): RouteDirection {
        return when {
            isTimeInWindow(now, schedule.morning) -> RouteDirection.STOPS_TO_COLLEGE
            isTimeInWindow(now, schedule.evening) -> RouteDirection.COLLEGE_TO_STOPS
            now.isBefore(LocalTime.of(12, 0)) -> RouteDirection.STOPS_TO_COLLEGE
            else -> RouteDirection.COLLEGE_TO_STOPS
        }
    }

    /**
     * Checks if given local time falls within the window (inclusive).
     */
    fun isTimeInWindow(time: LocalTime, window: ScheduleWindow): Boolean {
        if (!window.enabled) return false
        return try {
            val start = LocalTime.parse(window.startTime, timeFormatter)
            val end = LocalTime.parse(window.endTime, timeFormatter)
            if (start.isBefore(end)) {
                !time.isBefore(start) && time.isBefore(end)
            } else {
                // Crosses midnight
                !time.isBefore(start) || time.isBefore(end)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if current time is within either the morning or evening window.
     */
    fun isWithinOperatingWindow(
        schedule: RouteSchedule = OperatingScheduleService.getSchedule(),
        now: LocalTime = nowKolkata()
    ): Boolean {
        return isTimeInWindow(now, schedule.morning) || isTimeInWindow(now, schedule.evening)
    }

    /**
     * Returns a unique ID for the current active operating window (e.g., "2026-10-17_MORNING"
     * or "2026-10-17_EVENING"), or null if outside scheduled operating windows.
     */
    fun getCurrentWindowId(
        schedule: RouteSchedule = OperatingScheduleService.getSchedule(),
        now: LocalTime = nowKolkata(),
        today: java.time.LocalDate = CollegeCalendarService.nowLocalDate()
    ): String? {
        val dateStr = today.toString()
        return when {
            isTimeInWindow(now, schedule.morning) -> "${dateStr}_MORNING"
            isTimeInWindow(now, schedule.evening) -> "${dateStr}_EVENING"
            else -> null
        }
    }

    /**
     * Returns a human readable string describing the next scheduled session.
     */
    fun getNextSessionDescription(
        schedule: RouteSchedule = OperatingScheduleService.getSchedule(),
        now: LocalTime = nowKolkata()
    ): String {
        return try {
            val morningStart = LocalTime.parse(schedule.morning.startTime, timeFormatter)
            val eveningStart = LocalTime.parse(schedule.evening.startTime, timeFormatter)

            val mFormatted = OperatingScheduleService.formatWindowDisplay(schedule.morning.startTime, schedule.morning.endTime)
            val eFormatted = OperatingScheduleService.formatWindowDisplay(schedule.evening.startTime, schedule.evening.endTime)

            when {
                now.isBefore(morningStart) -> "Morning session: $mFormatted"
                isTimeInWindow(now, schedule.morning) -> "Currently in Morning Window ($mFormatted)"
                now.isBefore(eveningStart) -> "Evening session: $eFormatted"
                isTimeInWindow(now, schedule.evening) -> "Currently in Evening Window ($eFormatted)"
                else -> "Tomorrow Morning: $mFormatted"
            }
        } catch (e: Exception) {
            "Schedule unavailable"
        }
    }

    /**
     * Formats elapsed time since a given timestamp into relative string.
     */
    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "Never"
        val diffSeconds = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            diffSeconds < 5 -> "Just now"
            diffSeconds < 60 -> "$diffSeconds seconds ago"
            diffSeconds < 3600 -> "${diffSeconds / 60} min ago"
            else -> "${diffSeconds / 3600} hours ago"
        }
    }
}
