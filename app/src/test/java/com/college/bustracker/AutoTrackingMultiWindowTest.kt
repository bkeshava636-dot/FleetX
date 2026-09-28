package com.college.bustracker

import com.college.bustracker.data.model.CalendarException
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.OperatingScheduleService
import com.college.bustracker.util.ScheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AutoTrackingMultiWindowTest {

    @Before
    fun setUp() {
        OperatingScheduleService.updateCachedSchedule(OperatingScheduleService.DEFAULT_SCHEDULE)
    }

    @Test
    fun testWindowEvaluation_acrossFullDay() {
        val schedule = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", true),
            evening = ScheduleWindow("16:00", "18:00", true)
        )

        // 1. Before Morning Window (06:30 AM) -> Inactive
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(6, 30)))

        // 2. Start of Morning Window (07:00:00) -> ACTIVE
        assertTrue(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(7, 0)))

        // 3. Middle of Morning Window (08:15 AM) -> ACTIVE
        assertTrue(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(8, 15)))

        // 4. End of Morning Window (09:00:00) -> INACTIVE (Boundary triggers stop)
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(9, 0)))

        // 5. Between Morning & Evening (12:30 PM) -> Inactive
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(12, 30)))

        // 6. Before Evening Window (15:59:00) -> Inactive
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(15, 59)))

        // 7. Start of Evening Window (16:00:00) -> ACTIVE
        assertTrue(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(16, 0)))

        // 8. Middle of Evening Window (17:30 PM) -> ACTIVE
        assertTrue(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(17, 30)))

        // 9. End of Evening Window (18:00:00) -> INACTIVE (Boundary triggers stop)
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(18, 0)))

        // 10. After Evening Window (20:00 PM) -> Inactive
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(20, 0)))
    }

    @Test
    fun testAlarmTargetCalculation_beforeMorning() {
        val now = LocalDateTime.of(2026, 9, 24, 6, 0) // 06:00 AM Today
        val morningStart = LocalTime.of(7, 0)
        val morningEnd = LocalTime.of(9, 0)
        val eveningStart = LocalTime.of(16, 0)
        val eveningEnd = LocalTime.of(18, 0)

        // At 06:00 AM, ALL 4 alarms should target TODAY
        assertEquals(LocalDateTime.of(2026, 9, 24, 7, 0), calculateTargetTime(morningStart, now))
        assertEquals(LocalDateTime.of(2026, 9, 24, 9, 0), calculateTargetTime(morningEnd, now))
        assertEquals(LocalDateTime.of(2026, 9, 24, 16, 0), calculateTargetTime(eveningStart, now))
        assertEquals(LocalDateTime.of(2026, 9, 24, 18, 0), calculateTargetTime(eveningEnd, now))
    }

    @Test
    fun testAlarmTargetCalculation_betweenMorningAndEvening() {
        val now = LocalDateTime.of(2026, 9, 24, 12, 0) // 12:00 PM Today
        val morningStart = LocalTime.of(7, 0)
        val morningEnd = LocalTime.of(9, 0)
        val eveningStart = LocalTime.of(16, 0)
        val eveningEnd = LocalTime.of(18, 0)

        // Morning alarms have passed -> roll over to TOMORROW
        assertEquals(LocalDateTime.of(2026, 9, 25, 7, 0), calculateTargetTime(morningStart, now))
        assertEquals(LocalDateTime.of(2026, 9, 25, 9, 0), calculateTargetTime(morningEnd, now))

        // Evening alarms are upcoming -> target TODAY
        assertEquals(LocalDateTime.of(2026, 9, 24, 16, 0), calculateTargetTime(eveningStart, now))
        assertEquals(LocalDateTime.of(2026, 9, 24, 18, 0), calculateTargetTime(eveningEnd, now))
    }

    @Test
    fun testAlarmTargetCalculation_afterEvening() {
        val now = LocalDateTime.of(2026, 9, 24, 20, 0) // 08:00 PM Today
        val morningStart = LocalTime.of(7, 0)
        val morningEnd = LocalTime.of(9, 0)
        val eveningStart = LocalTime.of(16, 0)
        val eveningEnd = LocalTime.of(18, 0)

        // ALL 4 alarms have passed today -> ALL roll over to TOMORROW
        assertEquals(LocalDateTime.of(2026, 9, 25, 7, 0), calculateTargetTime(morningStart, now))
        assertEquals(LocalDateTime.of(2026, 9, 25, 9, 0), calculateTargetTime(morningEnd, now))
        assertEquals(LocalDateTime.of(2026, 9, 25, 16, 0), calculateTargetTime(eveningStart, now))
        assertEquals(LocalDateTime.of(2026, 9, 25, 18, 0), calculateTargetTime(eveningEnd, now))
    }

    @Test
    fun testDynamicScheduleUpdate_recalculatesWindows() {
        // Admin modifies schedule to: Morning 06:30 - 08:30, Evening 16:30 - 18:30
        val customSchedule = RouteSchedule(
            morning = ScheduleWindow("06:30", "08:30", true),
            evening = ScheduleWindow("16:30", "18:30", true)
        )
        OperatingScheduleService.updateCachedSchedule(customSchedule)

        // 06:45 AM is ACTIVE under new schedule (would be inactive under default 07:00 start)
        assertTrue(ScheduleHelper.isWithinOperatingWindow(customSchedule, LocalTime.of(6, 45)))

        // 08:45 AM is INACTIVE under new schedule (would be active under default 09:00 end)
        assertFalse(ScheduleHelper.isWithinOperatingWindow(customSchedule, LocalTime.of(8, 45)))

        // 18:15 PM is ACTIVE under new schedule (would be inactive under default 18:00 end)
        assertTrue(ScheduleHelper.isWithinOperatingWindow(customSchedule, LocalTime.of(18, 15)))
    }

    @Test
    fun testHolidaySuppression() {
        val schedule = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", true),
            evening = ScheduleWindow("16:00", "18:00", true)
        )

        val holidayDate = LocalDate.of(2026, 10, 2) // Gandhi Jayanti
        val exceptions = listOf(
            CalendarException(
                date = "2026-10-02",
                type = CalendarException.TYPE_FULL_DAY_HOLIDAY,
                name = "National Holiday"
            )
        )
        CollegeCalendarService.updateCachedExceptions(exceptions)

        val eval = CollegeCalendarService.evaluateDay(holidayDate)
        assertTrue(eval.isHoliday)
        assertFalse(eval.isWorkingDay)

        // Even during active operating window (08:00 AM), tracking is suppressed on holiday
        val isWindowTime = ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(8, 0))
        assertTrue(isWindowTime)
        val shouldTrack = eval.isWorkingDay && isWindowTime
        assertFalse(shouldTrack)
    }

    private fun calculateTargetTime(time: LocalTime, now: LocalDateTime): LocalDateTime {
        var target = LocalDateTime.of(now.toLocalDate(), time)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target
    }
}
