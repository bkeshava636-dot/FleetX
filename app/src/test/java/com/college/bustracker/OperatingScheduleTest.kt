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

    // ==========================================
    // 15 MANDATORY REGRESSION TEST SCENARIOS
    // ==========================================

    @Test
    fun test1_adminScheduleEqualsStudentSchedule() {
        val adminSchedule = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", true),
            evening = ScheduleWindow("16:30", "17:50", true)
        )
        OperatingScheduleService.updateCachedSchedule(adminSchedule)
        val studentSchedule = OperatingScheduleService.operatingSchedule.value
        assertEquals("Morning start must match", adminSchedule.morning.startTime, studentSchedule.morning.startTime)
        assertEquals("Morning end must match", adminSchedule.morning.endTime, studentSchedule.morning.endTime)
        assertEquals("Evening start must match", adminSchedule.evening.startTime, studentSchedule.evening.startTime)
        assertEquals("Evening end must match", adminSchedule.evening.endTime, studentSchedule.evening.endTime)
        assertEquals("Admin and student observe identical schedule", adminSchedule, studentSchedule)
    }

    @Test
    fun test2_defaultSchedule() {
        val def = OperatingScheduleService.DEFAULT_SCHEDULE
        assertEquals("07:00", def.morning.startTime)
        assertEquals("09:00", def.morning.endTime)
        assertTrue(def.morning.enabled)
        assertEquals("16:00", def.evening.startTime)
        assertEquals("18:00", def.evening.endTime)
        assertTrue(def.evening.enabled)
    }

    @Test
    fun test3_modifiedMorningSchedulePropagates() {
        val modified = RouteSchedule(
            morning = ScheduleWindow("07:30", "09:30", true),
            evening = ScheduleWindow("16:00", "18:00", true)
        )
        OperatingScheduleService.updateCachedSchedule(modified)
        assertFalse("07:15 should now be outside the morning window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(7, 15)))
        assertTrue("07:30 should be inside the morning window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(7, 30)))
        assertTrue("09:15 should now be inside the morning window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(9, 15)))
        assertFalse("09:45 should be outside the morning window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(9, 45)))
    }

    @Test
    fun test4_modifiedEveningSchedulePropagates() {
        // Matches production Firestore values: 16:30 - 17:50
        val modified = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", true),
            evening = ScheduleWindow("16:30", "17:50", true)
        )
        OperatingScheduleService.updateCachedSchedule(modified)
        assertFalse("16:15 should be outside updated evening window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(16, 15)))
        assertTrue("16:45 should be inside updated evening window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(16, 45)))
        assertTrue("17:45 should be inside updated evening window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(17, 45)))
        assertFalse("17:55 should be outside updated evening window", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(17, 55)))
    }

    @Test
    fun test5_disabledMorningWindow() {
        val modified = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", false),
            evening = ScheduleWindow("16:00", "18:00", true)
        )
        OperatingScheduleService.updateCachedSchedule(modified)
        assertFalse("Disabled morning window must evaluate to false even during morning hours", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(8, 0)))
        assertTrue("Enabled evening window must remain active", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(17, 0)))
    }

    @Test
    fun test6_disabledEveningWindow() {
        val modified = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", true),
            evening = ScheduleWindow("16:00", "18:00", false)
        )
        OperatingScheduleService.updateCachedSchedule(modified)
        assertTrue("Enabled morning window must remain active", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(8, 0)))
        assertFalse("Disabled evening window must evaluate to false even during evening hours", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(17, 0)))
    }

    @Test
    fun test7_bothWindowsDisabled() {
        val modified = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", false),
            evening = ScheduleWindow("16:00", "18:00", false)
        )
        OperatingScheduleService.updateCachedSchedule(modified)
        assertFalse("Morning time must be inactive", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(8, 0)))
        assertFalse("Midday time must be inactive", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(12, 0)))
        assertFalse("Evening time must be inactive", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(17, 0)))
        assertFalse("Night time must be inactive", ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(22, 0)))
    }

    @Test
    fun test8_holidayOverridesOperatingWindow() {
        OperatingScheduleService.updateCachedSchedule(OperatingScheduleService.DEFAULT_SCHEDULE)
        val holidayDate = LocalDate.of(2026, 10, 4) // Sunday
        val eval = CollegeCalendarService.evaluateDay(holidayDate, emptyMap())
        assertFalse("Sunday is a holiday", eval.isWorkingDay)
        assertTrue("eval.isHoliday must be true", eval.isHoliday)

        val isWithinWindow = ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(8, 0))
        assertTrue("Time falls within morning window", isWithinWindow)

        val isServiceOperating = eval.isWorkingDay && isWithinWindow
        assertFalse("Holiday strictly overrides operating window", isServiceOperating)
    }

    @Test
    fun test9_workingDayOverrideRestoresOperation() {
        OperatingScheduleService.updateCachedSchedule(OperatingScheduleService.DEFAULT_SCHEDULE)
        val sundayDate = LocalDate.of(2026, 10, 4)
        val workingDayOverride = mapOf(
            "2026-10-04" to CalendarException(
                date = "2026-10-04",
                type = CalendarException.TYPE_WORKING_DAY,
                name = "Special Working Sunday"
            )
        )
        val eval = CollegeCalendarService.evaluateDay(sundayDate, workingDayOverride)
        assertTrue("Override converts Sunday to working day", eval.isWorkingDay)
        assertFalse("Override clears holiday status", eval.isHoliday)

        val isWithinWindow = ScheduleHelper.isWithinOperatingWindow(now = LocalTime.of(8, 0))
        val isServiceOperating = eval.isWorkingDay && isWithinWindow
        assertTrue("Working day override restores operating window", isServiceOperating)
    }

    @Test
    fun test10_studentDashboardUpdatesWithoutRestart() {
        val initialSchedule = OperatingScheduleService.operatingSchedule.value
        assertEquals("07:00", initialSchedule.morning.startTime)

        val newAdminSchedule = RouteSchedule(
            morning = ScheduleWindow("07:45", "09:45", true),
            evening = ScheduleWindow("16:45", "18:45", true)
        )
        // Admin updates schedule in real time
        OperatingScheduleService.updateCachedSchedule(newAdminSchedule)

        // Student Dashboard observing StateFlow receives it immediately without restart
        val observedSchedule = OperatingScheduleService.operatingSchedule.value
        assertEquals("07:45", observedSchedule.morning.startTime)
        assertEquals("09:45", observedSchedule.morning.endTime)
        assertEquals("16:45", observedSchedule.evening.startTime)
        assertEquals("18:45", observedSchedule.evening.endTime)
    }

    @Test
    fun test11_driverAutomaticTrackingUsesSameSchedule() {
        val customSchedule = RouteSchedule(
            morning = ScheduleWindow("06:30", "08:30", true),
            evening = ScheduleWindow("15:30", "17:30", true)
        )
        OperatingScheduleService.updateCachedSchedule(customSchedule)
        val driverSchedule = com.college.bustracker.service.AutoTrackingScheduler.getSavedSchedule()
        val studentSchedule = com.college.bustracker.service.StudentAutoTrackingScheduler.getSavedSchedule()
        val authoritativeSchedule = OperatingScheduleService.getSchedule()

        assertEquals("Driver auto-tracking must match authoritative", authoritativeSchedule, driverSchedule)
        assertEquals("Student auto-tracking must match authoritative", authoritativeSchedule, studentSchedule)
        assertEquals("Driver and student auto-tracking must be identical", driverSchedule, studentSchedule)
    }

    @Test
    fun test12_etaDirectionUsesSameSchedule() {
        val schedule = RouteSchedule(
            morning = ScheduleWindow("07:15", "09:15", true),
            evening = ScheduleWindow("16:15", "17:45", true)
        )
        OperatingScheduleService.updateCachedSchedule(schedule)

        val morningDirection = ScheduleHelper.determineRouteDirection(schedule, now = LocalTime.of(8, 0))
        assertEquals(RouteDirection.STOPS_TO_COLLEGE, morningDirection)

        val eveningDirection = ScheduleHelper.determineRouteDirection(schedule, now = LocalTime.of(17, 0))
        assertEquals(RouteDirection.COLLEGE_TO_STOPS, eveningDirection)
    }

    @Test
    fun test13_asiaKolkataTimezone() {
        assertEquals("Asia/Kolkata", ScheduleHelper.KOLKATA_ZONE.id)
        val kolkataTime = ScheduleHelper.nowKolkata()
        val expectedTime = LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"))
        val diff = Math.abs(kolkataTime.toSecondOfDay() - expectedTime.toSecondOfDay())
        assertTrue("Kolkata time evaluates to Asia/Kolkata", diff <= 2)
    }

    @Test
    fun test14_midnightDateBoundary() {
        val overnightWindow = ScheduleWindow(startTime = "23:00", endTime = "01:00", enabled = true)
        assertTrue("23:30 should be inside overnight window", ScheduleHelper.isTimeInWindow(LocalTime.of(23, 30), overnightWindow))
        assertTrue("00:30 should be inside overnight window", ScheduleHelper.isTimeInWindow(LocalTime.of(0, 30), overnightWindow))
        assertFalse("02:00 should be outside overnight window", ScheduleHelper.isTimeInWindow(LocalTime.of(2, 0), overnightWindow))
        assertFalse("22:30 should be outside overnight window", ScheduleHelper.isTimeInWindow(LocalTime.of(22, 30), overnightWindow))
    }

    @Test
    fun test15_offlineCachedScheduleDoesNotBecomeIndependentConfiguration() {
        val schedule = RouteSchedule(
            morning = ScheduleWindow("07:20", "09:20", true),
            evening = ScheduleWindow("16:20", "17:40", true)
        )
        OperatingScheduleService.updateCachedSchedule(schedule, context = null)
        val readBack = OperatingScheduleService.getSchedule()
        assertEquals(schedule.morning.startTime, readBack.morning.startTime)
        assertEquals(schedule.morning.endTime, readBack.morning.endTime)
        assertEquals(schedule.evening.startTime, readBack.evening.startTime)
        assertEquals(schedule.evening.endTime, readBack.evening.endTime)
        assertEquals(schedule, OperatingScheduleService.operatingSchedule.value)
    }
}
