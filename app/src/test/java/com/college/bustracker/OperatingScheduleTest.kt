package com.college.bustracker

import com.college.bustracker.data.model.CalendarException
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.OperatingScheduleService
import com.college.bustracker.util.RouteDirection
import com.college.bustracker.util.ScheduleHelper
import com.college.bustracker.util.TemporaryDurationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OperatingScheduleTest {

    @Before
    fun setUp() {
        // Reset to default schedule before each test
        OperatingScheduleService.updateCachedSchedule(OperatingScheduleService.DEFAULT_SCHEDULE)
    }

    @Test
    fun testDefaultSchedule() {
        val schedule = OperatingScheduleService.getSchedule()
        assertEquals("07:00", schedule.morning.startTime)
        assertEquals("09:00", schedule.morning.endTime)
        assertTrue(schedule.morning.enabled)

        assertEquals("16:00", schedule.evening.startTime)
        assertEquals("18:00", schedule.evening.endTime)
        assertTrue(schedule.evening.enabled)
    }

    @Test
    fun testFormatTime12h() {
        assertEquals("07:00 AM", OperatingScheduleService.formatTime12h("07:00"))
        assertEquals("09:00 AM", OperatingScheduleService.formatTime12h("09:00"))
        assertEquals("04:00 PM", OperatingScheduleService.formatTime12h("16:00"))
        assertEquals("06:00 PM", OperatingScheduleService.formatTime12h("18:00"))
        assertEquals("04:30 PM", OperatingScheduleService.formatTime12h("16:30"))
        assertEquals("06:30 PM", OperatingScheduleService.formatTime12h("18:30"))
    }

    @Test
    fun testFormatWindowDisplay() {
        assertEquals("07:00 AM — 09:00 AM", OperatingScheduleService.formatWindowDisplay("07:00", "09:00"))
        assertEquals("04:00 PM — 06:00 PM", OperatingScheduleService.formatWindowDisplay("16:00", "18:00"))
        assertEquals("04:30 PM — 06:30 PM", OperatingScheduleService.formatWindowDisplay("16:30", "18:30"))
    }

    @Test
    fun testValidateWindow_validTimes() {
        val (v1, e1) = OperatingScheduleService.validateWindow("07:00", "09:00")
        assertTrue(v1)
        assertEquals(null, e1)

        val (v2, e2) = OperatingScheduleService.validateWindow("16:30", "18:30")
        assertTrue(v2)
        assertEquals(null, e2)
    }

    @Test
    fun testValidateWindow_invalidTimes() {
        // Start after end
        val (v1, e1) = OperatingScheduleService.validateWindow("09:00", "07:00")
        assertFalse(v1)
        assertTrue(e1?.contains("earlier than end time") == true)

        // Same start and end
        val (v2, e2) = OperatingScheduleService.validateWindow("08:00", "08:00")
        assertFalse(v2)
        assertTrue(e2?.contains("earlier than end time") == true)

        // Empty / Blank
        val (v3, e3) = OperatingScheduleService.validateWindow("", "09:00")
        assertFalse(v3)
        assertTrue(e3?.contains("empty") == true)

        // Invalid format
        val (v4, e4) = OperatingScheduleService.validateWindow("25:99", "09:00")
        assertFalse(v4)
        assertTrue(e4?.contains("Invalid start time") == true)
    }

    @Test
    fun testDynamicScheduleUpdate_propagatesToOperatingWindow() {
        // Default: 16:00 - 18:00
        val testTime1815 = LocalTime.of(18, 15)
        assertFalse(ScheduleHelper.isWithinOperatingWindow(now = testTime1815))

        // Admin updates schedule to: Morning 07:30-09:30, Evening 16:30-18:30
        val updatedSchedule = RouteSchedule(
            morning = ScheduleWindow("07:30", "09:30", true),
            evening = ScheduleWindow("16:30", "18:30", true)
        )
        OperatingScheduleService.updateCachedSchedule(updatedSchedule)

        // Now 18:15 should be WITHIN the evening window!
        assertTrue(ScheduleHelper.isWithinOperatingWindow(now = testTime1815))

        // And 16:15 should now be OUTSIDE the evening window
        val testTime1615 = LocalTime.of(16, 15)
        assertFalse(ScheduleHelper.isWithinOperatingWindow(now = testTime1615))
    }

    @Test
    fun testDynamicScheduleUpdate_propagatesToRouteDirection() {
        val updatedSchedule = RouteSchedule(
            morning = ScheduleWindow("07:30", "09:30", true),
            evening = ScheduleWindow("16:30", "18:30", true)
        )
        OperatingScheduleService.updateCachedSchedule(updatedSchedule)

        // At 08:00, within morning window -> STOPS_TO_COLLEGE
        val morningDir = ScheduleHelper.determineRouteDirection(now = LocalTime.of(8, 0))
        assertEquals(RouteDirection.STOPS_TO_COLLEGE, morningDir)

        // At 17:00, within evening window -> COLLEGE_TO_STOPS
        val eveningDir = ScheduleHelper.determineRouteDirection(now = LocalTime.of(17, 0))
        assertEquals(RouteDirection.COLLEGE_TO_STOPS, eveningDir)
    }

    @Test
    fun testDynamicScheduleUpdate_propagatesToTemporaryDurationHelper() {
        // Default: evening end = 18:00
        assertEquals(LocalTime.of(18, 0), TemporaryDurationHelper.resolveOperatingEndTime())

        // Admin updates schedule: evening end = 19:15
        val updatedSchedule = RouteSchedule(
            morning = ScheduleWindow("06:45", "08:45", true),
            evening = ScheduleWindow("17:00", "19:15", true)
        )
        OperatingScheduleService.updateCachedSchedule(updatedSchedule)

        // Duration helper now resolves to 19:15 automatically!
        assertEquals(LocalTime.of(19, 15), TemporaryDurationHelper.resolveOperatingEndTime())
        assertEquals(LocalTime.of(6, 45), TemporaryDurationHelper.resolveOperatingStartTime())
    }

    @Test
    fun testHolidaySuppressesOperatingWindow() {
        val updatedSchedule = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", true),
            evening = ScheduleWindow("16:00", "18:00", true)
        )
        OperatingScheduleService.updateCachedSchedule(updatedSchedule)

        // Time is 08:00 (inside operating window)
        val nowTime = LocalTime.of(8, 0)
        assertTrue(ScheduleHelper.isWithinOperatingWindow(now = nowTime))

        // On a holiday date (e.g. Sunday or Admin holiday exception)
        val holidayDate = LocalDate.of(2026, 9, 27) // Sunday
        val eval = CollegeCalendarService.evaluateDay(holidayDate)
        assertTrue(eval.isHoliday)
        assertFalse(eval.isWorkingDay)

        // Effective tracking should be disabled on holidays even during scheduled hours
        val isEffectiveTrackingAllowed = eval.isWorkingDay && ScheduleHelper.isWithinOperatingWindow(now = nowTime)
        assertFalse(isEffectiveTrackingAllowed)
    }
}
