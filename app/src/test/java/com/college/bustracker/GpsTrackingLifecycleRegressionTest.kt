package com.college.bustracker

import com.college.bustracker.service.AutoTrackingScheduler
import com.college.bustracker.service.AutoTrackingScheduler.SchedulerAction
import com.college.bustracker.service.GmsLocationResurrectionReceiver
import com.college.bustracker.service.GmsLocationResurrectionReceiver.WatchdogAction
import com.college.bustracker.service.LocationTrackingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression test suite for FleetX GPS tracking lifecycle fixes:
 * - GmsLocationResurrectionReceiver mode-aware operating window enforcement
 * - AutoTrackingScheduler manual session preservation & logout handling
 * - LocationTrackingService CancellationTokenSource & session generation callback guards
 */
class GpsTrackingLifecycleRegressionTest {

    // =========================================================================
    // 1. MANUAL tracking outside operating hours with automatic tracking disabled
    // =========================================================================
    @Test
    fun test01_manualTrackingOutsideOperatingHoursWithAutoDisabled_isPreserved() {
        val watchdogAction = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = true,
            isAutoEnabled = false,
            savedMode = "MANUAL",
            isWithinWindow = false,
            isServiceRunning = true
        )
        assertEquals(WatchdogAction.HEALTHY, watchdogAction)

        val schedulerAction = AutoTrackingScheduler.evaluateSchedulerDecision(
            isDriverActive = true,
            isAutoEnabled = false,
            isTrackingRunning = true,
            isPersistentSessionActive = true,
            savedMode = "MANUAL",
            isOperatingNow = false,
            isWindowOverriddenOff = false
        )
        assertEquals(SchedulerAction.NO_OP, schedulerAction)
    }

    // =========================================================================
    // 2. MANUAL tracking inside operating hours
    // =========================================================================
    @Test
    fun test02_manualTrackingInsideOperatingHours_isPreserved() {
        val watchdogAction = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = true,
            isAutoEnabled = false,
            savedMode = "MANUAL",
            isWithinWindow = true,
            isServiceRunning = true
        )
        assertEquals(WatchdogAction.HEALTHY, watchdogAction)

        val schedulerAction = AutoTrackingScheduler.evaluateSchedulerDecision(
            isDriverActive = true,
            isAutoEnabled = true,
            isTrackingRunning = true,
            isPersistentSessionActive = true,
            savedMode = "MANUAL",
            isOperatingNow = true,
            isWindowOverriddenOff = false
        )
        assertEquals(SchedulerAction.NO_OP, schedulerAction)
    }

    // =========================================================================
    // 3. AUTO tracking inside operating hours
    // =========================================================================
    @Test
    fun test03_autoTrackingInsideOperatingHours_startsAndRemainsHealthy() {
        val schedulerStart = AutoTrackingScheduler.evaluateSchedulerDecision(
            isDriverActive = true,
            isAutoEnabled = true,
            isTrackingRunning = false,
            isPersistentSessionActive = false,
            savedMode = "AUTO",
            isOperatingNow = true,
            isWindowOverriddenOff = false
        )
        assertEquals(SchedulerAction.START_AUTO, schedulerStart)

        val watchdogHealthy = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = true,
            isAutoEnabled = true,
            savedMode = "AUTO",
            isWithinWindow = true,
            isServiceRunning = true
        )
        assertEquals(WatchdogAction.HEALTHY, watchdogHealthy)
    }

    // =========================================================================
    // 4. AUTO tracking outside operating hours
    // =========================================================================
    @Test
    fun test04_autoTrackingOutsideOperatingHours_isStopped() {
        val watchdogAction = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = true,
            isAutoEnabled = true,
            savedMode = "AUTO",
            isWithinWindow = false,
            isServiceRunning = true
        )
        assertEquals(WatchdogAction.STOP_OUTSIDE_WINDOW, watchdogAction)

        val schedulerAction = AutoTrackingScheduler.evaluateSchedulerDecision(
            isDriverActive = true,
            isAutoEnabled = true,
            isTrackingRunning = true,
            isPersistentSessionActive = true,
            savedMode = "AUTO",
            isOperatingNow = false,
            isWindowOverriddenOff = false
        )
        assertEquals(SchedulerAction.STOP_OUTSIDE_WINDOW, schedulerAction)
    }

    // =========================================================================
    // 5. AUTO tracking on holidays (isWithinWindow == false)
    // =========================================================================
    @Test
    fun test05_autoTrackingOnHoliday_isStopped() {
        // On a college holiday, isWithinWindow / isOperatingNow evaluate to false
        val watchdogAction = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = true,
            isAutoEnabled = true,
            savedMode = "AUTO",
            isWithinWindow = false,
            isServiceRunning = true
        )
        assertEquals(WatchdogAction.STOP_OUTSIDE_WINDOW, watchdogAction)

        val schedulerAction = AutoTrackingScheduler.evaluateSchedulerDecision(
            isDriverActive = true,
            isAutoEnabled = true,
            isTrackingRunning = true,
            isPersistentSessionActive = true,
            savedMode = "AUTO",
            isOperatingNow = false,
            isWindowOverriddenOff = false
        )
        assertEquals(SchedulerAction.STOP_OUTSIDE_WINDOW, schedulerAction)
    }

    // =========================================================================
    // 6. Actual driver logout during MANUAL tracking
    // =========================================================================
    @Test
    fun test06_driverLogoutDuringManualTracking_stopsTracking() {
        val schedulerAction = AutoTrackingScheduler.evaluateSchedulerDecision(
            isDriverActive = false,
            isAutoEnabled = false,
            isTrackingRunning = true,
            isPersistentSessionActive = true,
            savedMode = "MANUAL",
            isOperatingNow = false,
            isWindowOverriddenOff = false
        )
        assertEquals(SchedulerAction.STOP_DRIVER_LOGGED_OUT, schedulerAction)

        val watchdogAction = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = false,
            isPersistentSessionActive = true,
            isAutoEnabled = false,
            savedMode = "MANUAL",
            isWithinWindow = false,
            isServiceRunning = true
        )
        assertEquals(WatchdogAction.IGNORE, watchdogAction)
    }

    // =========================================================================
    // 7. Explicit manual stop
    // =========================================================================
    @Test
    fun test07_explicitManualStop_doesNotResurrect() {
        // After explicit stop, KEY_IS_SESSION_ACTIVE is false and service is stopped
        val watchdogAction = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = false,
            isAutoEnabled = false,
            savedMode = "MANUAL",
            isWithinWindow = false,
            isServiceRunning = false
        )
        assertEquals(WatchdogAction.STOP_OUTSIDE_WINDOW, watchdogAction)

        // Even inside window with auto disabled, explicit manual stop must not resurrect
        val watchdogInside = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = false,
            isAutoEnabled = false,
            savedMode = "MANUAL",
            isWithinWindow = true,
            isServiceRunning = false
        )
        assertEquals(WatchdogAction.IGNORE, watchdogInside)
    }

    // =========================================================================
    // 8. Delayed getCurrentLocation callback after stopping
    // =========================================================================
    @Test
    fun test08_delayedGetCurrentLocationAfterStop_isRejected() {
        // Session 1 started (active=true, gen=1), then stopped (active=false, gen=2)
        val callbackGen = 1L
        val valid = LocationTrackingService.isCallbackValidForSession(
            isSessionActive = false,
            callbackGeneration = callbackGen,
            currentGeneration = 2L
        )
        assertFalse("Delayed getCurrentLocation callback must be rejected after stop", valid)
    }

    // =========================================================================
    // 9. Delayed lastLocation callback after stopping
    // =========================================================================
    @Test
    fun test09_delayedLastLocationAfterStop_isRejected() {
        val callbackGen = 1L
        val valid = LocationTrackingService.isCallbackValidForSession(
            isSessionActive = false,
            callbackGeneration = callbackGen,
            currentGeneration = 2L
        )
        assertFalse("Delayed lastLocation callback must be rejected after stop", valid)
    }

    // =========================================================================
    // 10. Stop followed immediately by a new tracking session
    // =========================================================================
    @Test
    fun test10_stopFollowedImmediatelyByNewSession_rejectsOldAcceptsNew() {
        // Session 1 (gen=1) stopped -> gen=2, then Session 2 started -> active=true, gen=3
        val oldSessionCallbackValid = LocationTrackingService.isCallbackValidForSession(
            isSessionActive = true,
            callbackGeneration = 1L,
            currentGeneration = 3L
        )
        assertFalse("Callback from previous session must be rejected by new session", oldSessionCallbackValid)

        val newSessionCallbackValid = LocationTrackingService.isCallbackValidForSession(
            isSessionActive = true,
            callbackGeneration = 3L,
            currentGeneration = 3L
        )
        assertTrue("Callback from current active session must be accepted", newSessionCallbackValid)
    }

    // =========================================================================
    // 11. Background tracking and process resurrection
    // =========================================================================
    @Test
    fun test11_backgroundResurrection_worksForManualOutsideHoursAndAutoInsideHours() {
        // MANUAL session killed by OS outside operating window -> must resurrect
        val resurrectManual = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = true,
            isAutoEnabled = false,
            savedMode = "MANUAL",
            isWithinWindow = false,
            isServiceRunning = false
        )
        assertEquals(WatchdogAction.RESURRECT, resurrectManual)

        // AUTO session killed by OS inside operating window -> must resurrect
        val resurrectAuto = GmsLocationResurrectionReceiver.evaluateWatchdogDecision(
            isDriverLoggedIn = true,
            isPersistentSessionActive = true,
            isAutoEnabled = true,
            savedMode = "AUTO",
            isWithinWindow = true,
            isServiceRunning = false
        )
        assertEquals(WatchdogAction.RESURRECT, resurrectAuto)
    }

    // =========================================================================
    // 12. RTDB status consistency after every stop scenario
    // =========================================================================
    @Test
    fun test12_rtdbStatusConsistencyGuards_presentInLocationTrackingService() {
        val serviceFile = File("C:/Users/bhrao/college_bus_tracker/app/src/main/java/com/college/bustracker/service/LocationTrackingService.kt")
        assertTrue("LocationTrackingService.kt must exist", serviceFile.exists())
        val content = serviceFile.readText()

        // Verify CancellationTokenSource is used for getCurrentLocation and cancelled in stopTrackingSession
        assertTrue(
            "getCurrentLocation must use CancellationTokenSource token",
            content.contains("getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token)")
        )

        val stopBlock = content.substringAfter("private fun stopTrackingSession(")
            .substringBefore("private fun buildTrackingNotification")
        val invalidateIdx = stopBlock.indexOf("isTrackingSessionActive = false")
        val cancelIdx = stopBlock.indexOf("currentLocationCancellationSource?.cancel()")
        val rtdbStopIdx = stopBlock.indexOf("trackingRepository.stopTracking(")

        assertTrue("stopTrackingSession must invalidate isTrackingSessionActive", invalidateIdx >= 0)
        assertTrue("stopTrackingSession must cancel pending getCurrentLocation requests", cancelIdx > invalidateIdx)
        assertTrue("Session invalidation & cancellation must happen before RTDB stopTracking", rtdbStopIdx > cancelIdx)

        // Verify onNewLocation checks isCallbackValidForSession at entry
        val onNewLocBlock = content.substringAfter("private fun onNewLocation(")
            .substringBefore("val callbackGeneration = breadcrumbOwnerGeneration")
        assertTrue(
            "onNewLocation must guard against inactive/stale sessions before processing",
            onNewLocBlock.contains("isCallbackValidForSession(isTrackingSessionActive, expectedGeneration, activeSessionGeneration)")
        )
    }
}
