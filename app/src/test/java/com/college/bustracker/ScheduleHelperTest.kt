package com.college.bustracker

import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.util.ScheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ScheduleHelperTest {

    @Test
    fun testIsTimeInWindow_withinNormalHours() {
        val window = ScheduleWindow(startTime = "07:00", endTime = "09:00", enabled = true)
        
        // Exact start time
        assertTrue(ScheduleHelper.isTimeInWindow(LocalTime.of(7, 0), window))
        // Middle of window
        assertTrue(ScheduleHelper.isTimeInWindow(LocalTime.of(8, 0), window))
        // Before end time
        assertTrue(ScheduleHelper.isTimeInWindow(LocalTime.of(8, 59), window))
        // Exact end time (half-open window [start, end) means end time triggers window stop)
        assertFalse(ScheduleHelper.isTimeInWindow(LocalTime.of(9, 0), window))
        // Before window
        assertFalse(ScheduleHelper.isTimeInWindow(LocalTime.of(6, 59), window))
        // After window
        assertFalse(ScheduleHelper.isTimeInWindow(LocalTime.of(9, 1), window))
    }

    @Test
    fun testIsTimeInWindow_disabledWindow() {
        val window = ScheduleWindow(startTime = "07:00", endTime = "09:00", enabled = false)
        assertFalse(ScheduleHelper.isTimeInWindow(LocalTime.of(8, 0), window))
    }

    @Test
    fun testIsWithinOperatingWindow_morningAndEvening() {
        val schedule = RouteSchedule(
            morning = ScheduleWindow("07:00", "09:00", true),
            evening = ScheduleWindow("16:00", "18:00", true)
        )

        // Inside morning window
        assertTrue(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(7, 30)))
        // Outside both
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(12, 0)))
        // Inside evening window
        assertTrue(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(17, 15)))
        // Late night
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(21, 0)))
    }

    @Test
    fun testFormatRelativeTime() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", ScheduleHelper.formatRelativeTime(now - 2_000L))
        assertEquals("15 seconds ago", ScheduleHelper.formatRelativeTime(now - 15_000L))
        assertEquals("5 min ago", ScheduleHelper.formatRelativeTime(now - 300_000L))
    }
}
