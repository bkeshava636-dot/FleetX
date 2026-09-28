package com.college.bustracker.util

import com.college.bustracker.data.model.RouteSchedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DurationOption(val label: String, val dayCount: Int) {
    ONE_DAY("1 DAY", 1),
    TWO_DAYS("2 DAYS", 2),
    THREE_DAYS("3 DAYS", 3),
    CUSTOM("CUSTOM", 0)
}

data class ReplacementTimeWindow(
    val startMillis: Long,
    val endMillis: Long,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val durationLabel: String
)

object TemporaryDurationHelper {

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val isoTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /**
     * Resolves the closing operating time for the route schedule, or defaults to the authoritative schedule.
     */
    fun resolveOperatingEndTime(schedule: RouteSchedule? = null): LocalTime {
        val effective = schedule ?: OperatingScheduleService.getSchedule()
        if (effective.evening.enabled && effective.evening.endTime.isNotBlank()) {
            try {
                return LocalTime.parse(effective.evening.endTime, isoTimeFormatter)
            } catch (ignored: Exception) {}
        }
        return LocalTime.of(18, 0)
    }

    /**
     * Resolves the starting operating time for the route schedule, or defaults to the authoritative schedule.
     */
    fun resolveOperatingStartTime(schedule: RouteSchedule? = null): LocalTime {
        val effective = schedule ?: OperatingScheduleService.getSchedule()
        if (effective.morning.enabled && effective.morning.startTime.isNotBlank()) {
            try {
                return LocalTime.parse(effective.morning.startTime, isoTimeFormatter)
            } catch (ignored: Exception) {}
        }
        return LocalTime.of(7, 0)
    }

    /**
     * Calculates the target end date for N operating days starting from startDate,
     * taking into account Sundays and full-day calendar holidays.
     */
    fun calculateOperatingEndDate(startDate: LocalDate, operatingDays: Int): LocalDate {
        if (operatingDays <= 1) return startDate

        var daysRemaining = operatingDays - 1
        var curr = startDate
        var iterations = 0

        while (daysRemaining > 0 && iterations < 30) {
            curr = curr.plusDays(1)
            iterations++
            val evaluation = CollegeCalendarService.evaluateDay(curr)
            if (evaluation.isWorkingDay) {
                daysRemaining--
            }
        }
        return curr
    }

    /**
     * Computes the complete ReplacementTimeWindow.
     */
    fun computeTimeWindow(
        startDate: LocalDate,
        startTime: LocalTime,
        option: DurationOption,
        schedule: RouteSchedule? = null,
        customEndDate: LocalDate? = null,
        customEndTime: LocalTime? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReplacementTimeWindow {
        val operatingEndTime = resolveOperatingEndTime(schedule)

        val (targetEndDate, targetEndTime, label) = when (option) {
            DurationOption.ONE_DAY -> {
                val endT = operatingEndTime
                val endD = if (endT.isBefore(startTime)) startDate.plusDays(1) else startDate
                Triple(endD, endT, "1 day")
            }
            DurationOption.TWO_DAYS -> {
                val endD = calculateOperatingEndDate(startDate, 2)
                Triple(endD, operatingEndTime, "2 days")
            }
            DurationOption.THREE_DAYS -> {
                val endD = calculateOperatingEndDate(startDate, 3)
                Triple(endD, operatingEndTime, "3 days")
            }
            DurationOption.CUSTOM -> {
                val endD = customEndDate ?: startDate
                val endT = customEndTime ?: operatingEndTime
                Triple(endD, endT, "Custom")
            }
        }

        val startZdt = ZonedDateTime.of(startDate, startTime, zoneId)
        val endZdt = ZonedDateTime.of(targetEndDate, targetEndTime, zoneId)

        return ReplacementTimeWindow(
            startMillis = startZdt.toInstant().toEpochMilli(),
            endMillis = endZdt.toInstant().toEpochMilli(),
            startDate = startDate,
            startTime = startTime,
            endDate = targetEndDate,
            endTime = targetEndTime,
            durationLabel = label
        )
    }

    fun formatDate(date: LocalDate): String {
        return date.format(shortDateFormatter)
    }

    fun formatTime(time: LocalTime): String {
        return time.format(timeFormatter)
    }

    fun formatDateTimeWindow(window: ReplacementTimeWindow): String {
        return "${formatDateTime(window.startDate, window.startTime)} → ${formatDateTime(window.endDate, window.endTime)} (${window.durationLabel})"
    }

    fun formatDateTime(date: LocalDate, time: LocalTime): String {
        return "${date.format(shortDateFormatter)}, ${time.format(timeFormatter)}"
    }

    fun formatFullDateTime(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (millis <= 0L) return "—"
        val zdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zoneId)
        return "${zdt.format(dateFormatter)}, ${zdt.format(timeFormatter)}"
    }

    fun buildSummary(
        busNumber: String,
        replacementDriverName: String,
        window: ReplacementTimeWindow
    ): String {
        val startStr = formatDateTime(window.startDate, window.startTime)
        val endStr = formatDateTime(window.endDate, window.endTime)
        return """
            Bus: $busNumber
            Replacement Driver: $replacementDriverName
            $startStr → $endStr
            Duration: ${window.durationLabel}
        """.trimIndent()
    }

    fun formatCancelDateTime(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (millis <= 0L) return "—"
        val zdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zoneId)
        return "${zdt.format(shortDateFormatter)}, ${zdt.format(timeFormatter)}"
    }

    fun formatCancelWindow(startMillis: Long, endMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return "${formatCancelDateTime(startMillis, zoneId)} → ${formatCancelDateTime(endMillis, zoneId)}"
    }
}
