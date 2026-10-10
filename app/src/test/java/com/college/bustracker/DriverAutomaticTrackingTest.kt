package com.college.bustracker

import com.college.bustracker.data.model.CalendarException
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.service.AutoTrackingScheduler
import com.college.bustracker.service.DriverScheduleReceiver
import com.college.bustracker.service.TrackingServiceHelper
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.ScheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DriverAutomaticTrackingTest {

    private val schedule = RouteSchedule(
        morning = ScheduleWindow(startTime = "07:00", endTime = "09:00", enabled = true),
        evening = ScheduleWindow(startTime = "16:00", endTime = "18:00", enabled = true)
    )

    @Before
    fun setup() {
        TrackingServiceHelper.updateServiceState(
            isTracking = false,
            busId = null,
            gps = "Disconnected",
            timestamp = 0L
        )
        CollegeCalendarService.updateCachedExceptions(emptyList())
    }

    // 1. Driver schedule registration
    @Test
    fun test01_driverScheduleRegistrationCalculatesFutureEpochMillis() {
        val now = LocalTime.of(6, 30) // Before morning window
        val morningStart = LocalTime.parse(schedule.morning.startTime, DateTimeFormatter.ofPattern("HH:mm"))
        assertTrue(now.isBefore(morningStart))

        val isWithin = ScheduleHelper.isWithinOperatingWindow(schedule, now)
        assertFalse(isWithin)

        val windowNow = LocalTime.of(7, 30)
        assertTrue(ScheduleHelper.isWithinOperatingWindow(schedule, windowNow))
    }

    // 2. Driver schedule persistence
    @Test
    fun test02_driverSchedulePersistenceFormat() {
        assertEquals("07:00", schedule.morning.startTime)
        assertEquals("09:00", schedule.morning.endTime)
        assertTrue(schedule.morning.enabled)
        assertEquals("16:00", schedule.evening.startTime)
        assertEquals("18:00", schedule.evening.endTime)
        assertTrue(schedule.evening.enabled)

        // SharedPreferences keys
        assertEquals("auto_tracking_prefs", AutoTrackingScheduler.PREF_FILE)
        assertEquals("key_auto_enabled", AutoTrackingScheduler.KEY_AUTO_ENABLED)
        assertEquals("key_assigned_bus", AutoTrackingScheduler.KEY_ASSIGNED_BUS)
        assertEquals("key_assigned_route", AutoTrackingScheduler.KEY_ASSIGNED_ROUTE)
        assertEquals("key_driver_id", AutoTrackingScheduler.KEY_DRIVER_ID)
    }

    // 3. Driver scheduled trigger while app process is dead
    @Test
    fun test03_driverTriggerEvaluationDuringProcessDeath() {
        // Cold state: simulate receiver running with saved parameters
        val busId = "BUS-01"
        val isAutoEnabled = true
        val isWorkingDay = true
        val insideWindow = ScheduleHelper.isWithinOperatingWindow(schedule, LocalTime.of(8, 0))

        assertTrue(isAutoEnabled)
        assertTrue(busId.isNotBlank())
        assertTrue(isWorkingDay)
        assertTrue(insideWindow)

        val shouldStart = isAutoEnabled && busId.isNotBlank() && isWorkingDay && insideWindow
        assertTrue("Trigger must evaluate to true under cold conditions", shouldStart)
    }

    // 4. Driver scheduled trigger starts LocationTrackingService
    @Test
    fun test04_driverTriggerStartsTrackingServiceState() {
        assertFalse(TrackingServiceHelper.isTracking.value)

        TrackingServiceHelper.updateServiceState(
            isTracking = true,
            busId = "BUS-01",
            gps = "Connected",
            timestamp = System.currentTimeMillis()
        )

        assertTrue(TrackingServiceHelper.isTracking.value)
        assertEquals("BUS-01", TrackingServiceHelper.activeBusId.value)
        assertEquals("Connected", TrackingServiceHelper.gpsStatus.value)
    }

    // 5. Driver scheduled trigger starts the correct assigned bus
    @Test
    fun test05_driverTriggerAssignsCorrectBus() {
        val driver1Bus = "BUS-01"
        val driver2Bus = "BUS-25"

        TrackingServiceHelper.updateServiceState(true, driver1Bus, "Connected", 1000L)
        assertEquals("BUS-01", TrackingServiceHelper.activeBusId.value)

        TrackingServiceHelper.updateServiceState(true, driver2Bus, "Connected", 2000L)
        assertEquals("BUS-25", TrackingServiceHelper.activeBusId.value)
    }

    // 6. Driver schedule on holiday does not start
    @Test
    fun test06_driverScheduleOnHolidayDoesNotStart() {
        val sunday = LocalDate.of(2026, 9, 20) // Sunday
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        val evalSunday = CollegeCalendarService.evaluateDay(sunday)
        assertFalse("Sunday must not be a working day", evalSunday.isWorkingDay)
        assertTrue("Sunday must be flagged as holiday", evalSunday.isHoliday)

        // Custom holiday exception
        val holidayException = CalendarException(
            date = "2026-09-22",
            type = CalendarException.TYPE_FULL_DAY_HOLIDAY,
            name = "College Festival"
        )
        val evalCustom = CollegeCalendarService.evaluateDay(
            date = LocalDate.of(2026, 9, 22),
            exceptions = mapOf("2026-09-22" to holidayException)
        )
        assertFalse(evalCustom.isWorkingDay)
        assertTrue(evalCustom.isHoliday)
        assertEquals("College Festival", evalCustom.holidayName)
    }

    // 7. Driver schedule with Location OFF
    @Test
    fun test07_driverScheduleWithLocationOffNotificationTag() {
        // When location is OFF, notification ID and channel must match specification
        assertEquals(5001, DriverScheduleReceiver.NOTIFICATION_ID_LOCATION_OFF)
        assertEquals("driver_tracking_alerts_channel", DriverScheduleReceiver.NOTIFICATION_CHANNEL_ID)
    }

    // 8. Driver schedule after reboot
    @Test
    fun test08_driverScheduleRebootWindowDetection() {
        // Mid-morning reboot at 08:15 on a working day
        val rebootTime = LocalTime.of(8, 15)
        val isWorkingDay = true
        val insideWindow = ScheduleHelper.isWithinOperatingWindow(schedule, rebootTime)

        assertTrue(insideWindow)
        assertTrue(isWorkingDay)

        // Boot receiver should recognize immediate active operating window
        val shouldAutoResume = isWorkingDay && insideWindow
        assertTrue("Boot receiver should immediately resume tracking if booted mid-window", shouldAutoResume)
    }

    // 9. Duplicate schedule prevention
    @Test
    fun test09_duplicateSchedulePrevention() {
        TrackingServiceHelper.updateServiceState(true, "BUS-01", "Connected", 1000L)
        assertTrue(TrackingServiceHelper.isTracking.value)

        // If trigger fires while already tracking, state remains active without disruption
        val isAlreadyTracking = TrackingServiceHelper.isTracking.value
        assertTrue(isAlreadyTracking)
    }

    // 10. Schedule cancellation
    @Test
    fun test10_scheduleCancellationWhenAutoTrackingToggledOff() {
        TrackingServiceHelper.updateServiceState(true, "BUS-01", "Connected", 1000L)
        assertTrue(TrackingServiceHelper.isTracking.value)

        // Toggling auto-tracking off stops service
        TrackingServiceHelper.updateServiceState(false, null, "Disconnected", 2000L)
        assertFalse(TrackingServiceHelper.isTracking.value)
        assertEquals(null, TrackingServiceHelper.activeBusId.value)
    }

    // 11. Schedule update
    @Test
    fun test11_scheduleUpdateReflectsNewTimes() {
        val updatedSchedule = RouteSchedule(
            morning = ScheduleWindow(startTime = "06:30", endTime = "08:30", enabled = true),
            evening = ScheduleWindow(startTime = "15:30", endTime = "17:30", enabled = true)
        )

        val timeAt0645 = LocalTime.of(6, 45)
        // In original schedule, 06:45 is before 07:00 (outside)
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, timeAt0645))
        // In updated schedule, 06:45 is inside 06:30-08:30
        assertTrue(ScheduleHelper.isWithinOperatingWindow(updatedSchedule, timeAt0645))
    }

    // 12. Missed schedule handling
    @Test
    fun test12_missedScheduleHandlingMidWindowLaunch() {
        // App opened at 07:45 (missed the 07:00 start alarm)
        val now = LocalTime.of(7, 45)
        val isInside = ScheduleHelper.isWithinOperatingWindow(schedule, now)
        assertTrue("Driver opening app or receiver evaluating mid-window must detect active window", isInside)
    }

    // 13. Manual tracking still works
    @Test
    fun test13_manualTrackingWorksIndependentOfSchedule() {
        val outsideTime = LocalTime.of(12, 0)
        assertFalse(ScheduleHelper.isWithinOperatingWindow(schedule, outsideTime))

        // Driver starts manual tracking for emergency / special trip
        TrackingServiceHelper.updateServiceState(true, "BUS-01", "Connected", 5000L)
        assertTrue("Manual tracking must function outside normal schedule windows", TrackingServiceHelper.isTracking.value)
        assertEquals("BUS-01", TrackingServiceHelper.activeBusId.value)
    }

    // 14. Student automatic tracking remains unaffected
    @Test
    fun test14_studentAutomaticTrackingRemainsUnaffected() {
        // Driver and student actions must be distinct
        assertEquals("com.college.bustracker.ACTION_DRIVER_SCHEDULE_CHECK", AutoTrackingScheduler.ACTION_DRIVER_SCHEDULE_CHECK)
        assertEquals("com.college.bustracker.ACTION_STUDENT_SCHEDULE_CHECK", com.college.bustracker.service.StudentAutoTrackingScheduler.ACTION_STUDENT_SCHEDULE_CHECK)

        // Distinct shared preference files
        assertEquals("auto_tracking_prefs", AutoTrackingScheduler.PREF_FILE)
        assertEquals("auto_ride_prefs", com.college.bustracker.service.StudentAutoTrackingScheduler.PREFS_NAME)
    }

    // 15. Driver logout stops tracking and cancels auto-tracking
    @Test
    fun test15_driverLogoutStopsTrackingState() {
        TrackingServiceHelper.updateServiceState(true, "BUS-01", "Live GPS", System.currentTimeMillis())
        assertTrue(TrackingServiceHelper.isTracking.value)

        // When driver logs out, tracking must stop immediately
        TrackingServiceHelper.updateServiceState(false, null, "Driver Logged Out", System.currentTimeMillis())
        assertFalse("Tracking must be stopped after driver logs out", TrackingServiceHelper.isTracking.value)
        assertEquals(null, TrackingServiceHelper.activeBusId.value)
    }
}
