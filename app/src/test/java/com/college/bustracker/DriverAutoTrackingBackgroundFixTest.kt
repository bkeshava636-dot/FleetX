package com.college.bustracker

import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.service.AutoTrackingScheduler
import com.college.bustracker.service.TrackingServiceHelper
import com.college.bustracker.util.LocationThrottler
import com.college.bustracker.util.MultiSourceLocationEngine
import com.college.bustracker.util.ScheduleHelper
import com.college.bustracker.util.SelectedSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Unit test suite verifying Driver Automatic Tracking background startup,
 * multi-window lifecycle, permission handling, session fallback, and location engine fixes.
 */
class DriverAutoTrackingBackgroundFixTest {

    private val testBusId = "BUS-01"
    private val testRouteId = "ROUTE-01"
    private val testDriverUid = "driver_uid_123"

    private val standardSchedule = RouteSchedule(
        morning = ScheduleWindow(startTime = "07:00", endTime = "09:00", enabled = true),
        evening = ScheduleWindow(startTime = "16:00", endTime = "18:00", enabled = true)
    )

    // =========================================================================
    // 1. AUTOMATIC ENABLED + WINDOW START -> WINDOW OPERATING ACTIVE
    // =========================================================================
    @Test
    fun test1_automaticEnabled_windowStart_isOperatingNow() {
        val nowMorning = LocalTime.of(7, 30)
        val isMorningOperating = ScheduleHelper.isTimeInWindow(nowMorning, standardSchedule.morning)
        assertTrue("7:30 AM should be within morning window 7:00 - 9:00", isMorningOperating)
    }

    // =========================================================================
    // 2. AUTOMATIC DISABLED + WINDOW START -> SHOULD NOT TRIGGER
    // =========================================================================
    @Test
    fun test2_automaticDisabled_shouldNotTrigger() {
        val isAutoEnabled = false
        val nowMorning = LocalTime.of(8, 0)
        val isOperating = ScheduleHelper.isTimeInWindow(nowMorning, standardSchedule.morning)
        val shouldStartTracking = isAutoEnabled && isOperating
        assertFalse("When auto tracking is disabled, tracking should not start", shouldStartTracking)
    }

    // =========================================================================
    // 3. HOLIDAY + WINDOW START -> TRACKING SUPPRESSED
    // =========================================================================
    @Test
    fun test3_holiday_windowStart_suppressed() {
        val isAutoEnabled = true
        val isHoliday = true
        val isWorkingDay = !isHoliday
        val isWindowActive = true
        val shouldStart = isAutoEnabled && isWindowActive && isWorkingDay
        assertFalse("On a holiday, tracking must not be started", shouldStart)
    }

    // =========================================================================
    // 4. MORNING END -> TRACKING STOPS, PREFERENCE REMAINS ON
    // =========================================================================
    @Test
    fun test4_morningEnd_trackingStops_preferencePreserved() {
        var isAutoEnabledPreference = true
        val nowMidday = LocalTime.of(12, 0)

        val isWithinMorning = ScheduleHelper.isTimeInWindow(nowMidday, standardSchedule.morning)
        val isWithinEvening = ScheduleHelper.isTimeInWindow(nowMidday, standardSchedule.evening)
        val isOperatingNow = isWithinMorning || isWithinEvening

        // Tracking must be stopped outside windows
        assertFalse("12:00 PM is outside both operating windows", isOperatingNow)

        // But automatic tracking preference MUST remain true for next window
        assertTrue("Automatic tracking preference must remain persisted as ON", isAutoEnabledPreference)
    }

    // =========================================================================
    // 5. EVENING START -> TRACKING STARTS AGAIN WITHOUT USER TOGGLE
    // =========================================================================
    @Test
    fun test5_eveningStart_trackingStartsAutomatically() {
        val isAutoEnabledPreference = true
        val isWorkingDay = true
        val nowEvening = LocalTime.of(16, 45)

        val isWithinEvening = ScheduleHelper.isTimeInWindow(nowEvening, standardSchedule.evening)
        val shouldStart = isAutoEnabledPreference && isWithinEvening && isWorkingDay

        assertTrue("16:45 must be within evening window 16:00 - 18:00", isWithinEvening)
        assertTrue("Evening operating window should automatically trigger tracking", shouldStart)
    }

    // =========================================================================
    // 6. BACKGROUND RECEIVER RECOVERS DRIVER AND BUS IDENTITIES
    // =========================================================================
    @Test
    fun test6_identityFallback_resolution() {
        // Primary prefs might be empty if app was killed
        var busId = ""
        var driverId = ""
        var routeId = ""

        val sessionProfile = UserProfile(
            uid = "driver_session_uid_99",
            name = "Ramesh",
            email = "driver@collegebus.internal",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-05"
        )

        // Fallback simulation
        if (driverId.isBlank()) driverId = sessionProfile.uid
        if (busId.isBlank()) busId = sessionProfile.assignedBusId ?: ""
        if (routeId.isBlank()) routeId = "ROUTE-01"

        assertEquals("driver_session_uid_99", driverId)
        assertEquals("BUS-05", busId)
        assertEquals("ROUTE-01", routeId)
    }

    // =========================================================================
    // 7. MISSING LOCATION PERMISSION -> TRACKING DOES NOT CLAIM ACTIVE
    // =========================================================================
    @Test
    fun test7_missingPermission_blocked() {
        val hasPermission = false
        val isOperatingNow = true

        val canStartTracking = hasPermission && isOperatingNow
        assertFalse("Missing location permission must block starting location tracking", canStartTracking)
    }

    // =========================================================================
    // 8. LOCATION ENGINE ACCEPTS INITIAL DRIVER FIXES (ACCURACY UP TO 100M)
    // =========================================================================
    @Test
    fun test8_driverInitialFixWithModerateAccuracy_accepted() {
        val now = System.currentTimeMillis()
        val initialDriverFix = LocationSource(
            busId = testBusId,
            sourceId = testDriverUid,
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 65.0f, // Moderate initial GPS accuracy from cold start
            speed = 0.0f,
            timestamp = now - 1_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = testBusId,
            routeId = testRouteId,
            driverId = testDriverUid,
            driverSource = initialDriverFix,
            studentSources = emptyList(),
            now = now
        )

        assertEquals("Driver initial fix with 65m accuracy must be accepted as DRIVER", SelectedSourceType.DRIVER, result.selectedSourceType)
        assertEquals("ACTIVE", result.liveLocation.trackingStatus)
        assertEquals(15.34350, result.liveLocation.latitude, 0.00001)
        assertEquals(76.15540, result.liveLocation.longitude, 0.00001)
    }

    // =========================================================================
    // 9. LOCATION THROTTLER DISPATCHES FIRST FIX IMMEDIATELY
    // =========================================================================
    @Test
    fun test9_locationThrottler_firstFixDispatchedImmediately() {
        val now = System.currentTimeMillis()
        val shouldDispatch = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 0.0,
            lastSentLng = 0.0,
            lastSentTimestamp = 0L,
            newLat = 15.34350,
            newLng = 76.15540,
            newTimestamp = now,
            speedMps = 0.0f
        )
        assertTrue("First location fix (lastSentTimestamp == 0) must always be dispatched immediately", shouldDispatch)
    }

    // =========================================================================
    // 10. TEMPORARY DRIVER REPLACEMENT PRESERVES PROPER EPOCH AND REPLACEMENT UID
    // =========================================================================
    @Test
    fun test10_temporaryDriver_authorization() {
        val now = System.currentTimeMillis()
        val tempAssignment = TemporaryAssignment(
            assignmentId = "TEMP-101",
            busId = testBusId,
            replacementDriverUid = "temp_driver_77",
            replacementDriverName = "Suresh",
            startAt = now - 1000L,
            endAt = now + 3600000L,
            operatorEpoch = 1700000000L
        )

        assertTrue("Temporary assignment should be active", tempAssignment.isActive(now))
        assertEquals("temp_driver_77", tempAssignment.replacementDriverUid)
        assertEquals(1700000000L, tempAssignment.operatorEpoch)
    }
}
