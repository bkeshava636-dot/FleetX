package com.college.bustracker

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.util.AutomaticRideManager
import com.college.bustracker.util.AutomaticRideState
import com.college.bustracker.util.ScheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AutomaticRideManagerTest {

    private val sampleStops = listOf(
        BusStop(stopId = "s1", name = "Station", latitude = 15.3400, longitude = 76.1500, order = 1),
        BusStop(stopId = "s2", name = "Midway", latitude = 15.3450, longitude = 76.1550, order = 2),
        BusStop(stopId = "s3", name = "Campus", latitude = 15.3500, longitude = 76.1600, order = 3)
    )

    private val sampleRoute = Route(
        routeId = "ROUTE-1",
        name = "Campus Express",
        stops = sampleStops,
        schedules = RouteSchedule(
            morning = ScheduleWindow(enabled = true, startTime = "07:00", endTime = "09:00"),
            evening = ScheduleWindow(enabled = true, startTime = "16:00", endTime = "18:00")
        )
    )

    // =========================================================================
    // 1. MULTI-SIGNAL CONFIDENCE SCORING TESTS
    // =========================================================================

    @Test
    fun testConfidence_ridingWithBus_yieldsHighConfidenceAndRidingConfirmed() {
        val now = System.currentTimeMillis()
        // Bus is at 15.3450, 76.1550 traveling at 8.33 m/s (~30 km/h)
        val busLoc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3450,
            longitude = 76.1550,
            speed = 8.33f,
            lastUpdated = now - 5_000L,
            trackingStatus = "ACTIVE"
        )

        // Student is 15 meters away, speed 8.0 m/s (~29 km/h), accurate GPS fix
        val eval = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.3451,
            studentLng = 76.1551,
            studentSpeedMps = 8.0f,
            studentAccuracy = 6.0f,
            busLocation = busLoc,
            route = sampleRoute,
            consecutiveHighCount = 1
        )

        assertTrue("Confidence score should exceed 70 for riding student (was ${eval.score})", eval.score >= 70)
        assertTrue("Student must be marked as riding", eval.isRiding)
        assertTrue("Proximity score must be high", eval.proximityScore >= 22)
        assertTrue("Speed match score must be positive", eval.speedScore >= 18)
    }

    @Test
    fun testConfidence_stationaryStudentWaitingAtStop_doesNotTriggerContribution() {
        val now = System.currentTimeMillis()
        // Bus is moving away fast (10 m/s = 36 km/h)
        val busLoc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3455,
            longitude = 76.1555,
            speed = 10.0f,
            lastUpdated = now - 5_000L,
            trackingStatus = "ACTIVE"
        )

        // Student is waiting at stop s2, stationary (speed = 0.5 m/s)
        val eval = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.3450,
            studentLng = 76.1550,
            studentSpeedMps = 0.5f,
            studentAccuracy = 5.0f,
            busLocation = busLoc,
            route = sampleRoute,
            consecutiveHighCount = 0
        )

        assertFalse("Stationary student must not be marked as riding", eval.isRiding)
        assertTrue("Confidence score must be low (< 50) for waiting student (was ${eval.score})", eval.score < 50)
        assertEquals("Stationary penalty should apply when bus speeds away", -30, eval.speedScore)
    }

    @Test
    fun testConfidence_divergedFromRoute_appliesHeavyPenalties() {
        val now = System.currentTimeMillis()
        val busLoc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3450,
            longitude = 76.1550,
            speed = 8.0f,
            lastUpdated = now - 5_000L
        )

        // Student is far away (15.3800, 76.2000 > 5km away), e.g. at home or walking off-route
        val eval = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.3800,
            studentLng = 76.2000,
            studentSpeedMps = 1.0f,
            studentAccuracy = 10.0f,
            busLocation = busLoc,
            route = sampleRoute,
            consecutiveHighCount = 0
        )

        assertFalse("Diverged student must never be riding", eval.isRiding)
        assertEquals("Distance penalty must clamp confidence to 0", 0, eval.score)
    }

    @Test
    fun testConfidence_missingOrStaleDriverGps_fallsBackToRouteStopsAndVelocity() {
        // Driver GPS is completely missing (null) or stale (> 3 minutes)
        val eval = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.3450,
            studentLng = 76.1550,
            studentSpeedMps = 8.5f, // ~30 km/h transit velocity
            studentAccuracy = 8.0f,
            busLocation = null, // No driver location
            route = sampleRoute,
            consecutiveHighCount = 2 // Proven consecutive motion along route
        )

        // Proximity (near route stop) = 15
        // Speed (transit speed > 12 km/h) = 20
        // Route alignment (on polyline) = 20
        // Temporal coherence (2 consecutive fixes) = 15
        // Total = 70 -> Eligible for backup contribution!
        assertTrue("Student moving along route during driver outage should qualify as backup (was ${eval.score})", eval.score >= 70)
        assertTrue("Must be eligible to contribute backup telemetry", eval.isRiding)
    }

    @Test
    fun testConfidence_temporalCoherenceBoostIncreasesConfidence() {
        val now = System.currentTimeMillis()
        val busLoc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3450,
            longitude = 76.1550,
            speed = 8.0f,
            lastUpdated = now - 2_000L
        )

        val evalFirst = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.34505,
            studentLng = 76.15505,
            studentSpeedMps = 7.5f,
            studentAccuracy = 8.0f,
            busLocation = busLoc,
            route = sampleRoute,
            consecutiveHighCount = 0
        )

        val evalRepeated = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.34505,
            studentLng = 76.15505,
            studentSpeedMps = 7.5f,
            studentAccuracy = 8.0f,
            busLocation = busLoc,
            route = sampleRoute,
            consecutiveHighCount = 2
        )

        assertTrue(
            "Repeated coherence must result in higher confidence (${evalRepeated.score} > ${evalFirst.score})",
            evalRepeated.score > evalFirst.score
        )
        assertEquals("Temporal boost should be 15 pts", 15, evalRepeated.temporalScore)
    }

    // =========================================================================
    // 2. OPERATING SCHEDULE WINDOW GATE TESTS
    // =========================================================================

    @Test
    fun testScheduleWindow_insideMorningWindow_returnsTrue() {
        val schedule = RouteSchedule(
            morning = ScheduleWindow(enabled = true, startTime = "06:00", endTime = "10:00"),
            evening = ScheduleWindow(enabled = true, startTime = "16:00", endTime = "19:00")
        )

        val testTime = LocalTime.of(7, 30)
        val isWindow = ScheduleHelper.isTimeInWindow(testTime, schedule.morning)
        assertTrue("07:30 must be inside 06:00-10:00 morning window", isWindow)
        assertTrue("isWithinOperatingWindow must return true for 07:30", ScheduleHelper.isWithinOperatingWindow(schedule, testTime))
    }

    @Test
    fun testScheduleWindow_outsideOperatingWindows_returnsFalse() {
        val schedule = RouteSchedule(
            morning = ScheduleWindow(enabled = true, startTime = "07:00", endTime = "09:00"),
            evening = ScheduleWindow(enabled = true, startTime = "16:00", endTime = "18:00")
        )

        // Midday 13:00 (Idle window -> 0% battery)
        val midday = LocalTime.of(13, 0)
        assertFalse("13:00 must be outside morning window", ScheduleHelper.isTimeInWindow(midday, schedule.morning))
        assertFalse("13:00 must be outside evening window", ScheduleHelper.isTimeInWindow(midday, schedule.evening))
        assertFalse("13:00 must be outside operating windows", ScheduleHelper.isWithinOperatingWindow(schedule, midday))

        // Night 23:00 (Night idle)
        val night = LocalTime.of(23, 0)
        assertFalse("23:00 must be outside morning window", ScheduleHelper.isTimeInWindow(night, schedule.morning))
        assertFalse("23:00 must be outside evening window", ScheduleHelper.isTimeInWindow(night, schedule.evening))
        assertFalse("23:00 must be outside operating windows", ScheduleHelper.isWithinOperatingWindow(schedule, night))
    }

    // =========================================================================
    // 3. FINITE STATE MACHINE TRANSITION TESTS
    // =========================================================================

    @Test
    fun testFSM_statesEnumPreserved() {
        val states = AutomaticRideState.values()
        assertEquals("AutomaticRideState must have 6 states", 6, states.size)
        assertTrue(states.contains(AutomaticRideState.DISABLED))
        assertTrue(states.contains(AutomaticRideState.WAITING_FOR_WINDOW))
        assertTrue(states.contains(AutomaticRideState.WAITING_FOR_BUS))
        assertTrue(states.contains(AutomaticRideState.POSSIBLY_RIDING))
        assertTrue(states.contains(AutomaticRideState.CONTRIBUTING))
        assertTrue(states.contains(AutomaticRideState.ENDING_RIDE))
    }

    // =========================================================================
    // 4. PHASE 10B: HYSTERESIS & DEBOUNCING HARDENING TESTS
    // =========================================================================

    @Test
    fun testPhase10B_startRequiresTwoConsecutiveHighFixes() {
        val now = System.currentTimeMillis()

        // Fix 1: Score 80 (>= 75%) from WAITING_FOR_BUS
        val step1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.WAITING_FOR_BUS,
            score = 80,
            now = now,
            consecutiveHighCount = 0,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals("1st high fix must advance to POSSIBLY_RIDING", AutomaticRideState.POSSIBLY_RIDING, step1.nextState)
        assertEquals("consecutiveHighCount must be 1", 1, step1.consecutiveHighCount)
        assertFalse("Must NOT start sharing on single fix", step1.shouldStartSharing)

        // Fix 2: Score 85 (>= 75%) from POSSIBLY_RIDING
        val step2 = AutomaticRideManager.evaluateStateTransition(
            currentState = step1.nextState,
            score = 85,
            now = now + 15_000L,
            consecutiveHighCount = step1.consecutiveHighCount,
            consecutiveDivergenceCount = step1.consecutiveDivergenceCount,
            firstDivergenceTimestamp = step1.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals("2nd consecutive high fix must transition to CONTRIBUTING", AutomaticRideState.CONTRIBUTING, step2.nextState)
        assertEquals(2, step2.consecutiveHighCount)
        assertTrue("Must start sharing when 2 consecutive high fixes confirmed", step2.shouldStartSharing)
    }

    @Test
    fun testPhase10B_singleHighThenDrop_abortsToWaitingForBus() {
        val now = System.currentTimeMillis()

        // Fix 1: High fix
        val step1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.WAITING_FOR_BUS,
            score = 80,
            now = now,
            consecutiveHighCount = 0,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.POSSIBLY_RIDING, step1.nextState)
        assertEquals(1, step1.consecutiveHighCount)

        // Fix 2: Score drops to 20 (< 35%) -> walking away or false trigger
        val step2 = AutomaticRideManager.evaluateStateTransition(
            currentState = step1.nextState,
            score = 20,
            now = now + 15_000L,
            consecutiveHighCount = step1.consecutiveHighCount,
            consecutiveDivergenceCount = step1.consecutiveDivergenceCount,
            firstDivergenceTimestamp = step1.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals("Dropped score must abort to WAITING_FOR_BUS", AutomaticRideState.WAITING_FOR_BUS, step2.nextState)
        assertEquals("consecutiveHighCount must reset to 0", 0, step2.consecutiveHighCount)
        assertFalse("Must NOT start sharing", step2.shouldStartSharing)
    }

    @Test
    fun testPhase10B_temporaryGpsDropout_doesNotOscillateContributing() {
        val now = System.currentTimeMillis()

        // Student is CONTRIBUTING. Fix 1 has temporary GPS degradation (score 25 < 35%)
        val step1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 25,
            now = now,
            consecutiveHighCount = 3,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals("Must remain CONTRIBUTING during 1st bad fix", AutomaticRideState.CONTRIBUTING, step1.nextState)
        assertEquals(1, step1.consecutiveDivergenceCount)
        assertEquals(now, step1.firstDivergenceTimestamp)
        assertFalse("Must NOT stop sharing on 1st bad fix", step1.shouldStopSharing)

        // Fix 2 is also degraded (score 30 < 35%)
        val step2 = AutomaticRideManager.evaluateStateTransition(
            currentState = step1.nextState,
            score = 30,
            now = now + 15_000L,
            consecutiveHighCount = step1.consecutiveHighCount,
            consecutiveDivergenceCount = step1.consecutiveDivergenceCount,
            firstDivergenceTimestamp = step1.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals("Must remain CONTRIBUTING during 2nd bad fix", AutomaticRideState.CONTRIBUTING, step2.nextState)
        assertEquals(2, step2.consecutiveDivergenceCount)
        assertFalse("Must NOT stop sharing on 2nd bad fix", step2.shouldStopSharing)

        // Fix 3 recovers (score 60 >= 35%) -> student clears tunnel/trees
        val step3 = AutomaticRideManager.evaluateStateTransition(
            currentState = step2.nextState,
            score = 60,
            now = now + 30_000L,
            consecutiveHighCount = step2.consecutiveHighCount,
            consecutiveDivergenceCount = step2.consecutiveDivergenceCount,
            firstDivergenceTimestamp = step2.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals("Must remain CONTRIBUTING upon GPS recovery", AutomaticRideState.CONTRIBUTING, step3.nextState)
        assertEquals("consecutiveDivergenceCount must reset to 0", 0, step3.consecutiveDivergenceCount)
        assertEquals("firstDivergenceTimestamp must reset to 0L", 0L, step3.firstDivergenceTimestamp)
        assertFalse(step3.shouldStopSharing)
    }

    @Test
    fun testPhase10B_sustainedThreeLowFixes_transitionsToEndingRide() {
        val now = System.currentTimeMillis()

        // Fix 1: Score 20 (< 35%)
        val step1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 20,
            now = now,
            consecutiveHighCount = 2,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, step1.nextState)

        // Fix 2: Score 15 (< 35%)
        val step2 = AutomaticRideManager.evaluateStateTransition(
            currentState = step1.nextState,
            score = 15,
            now = now + 15_000L,
            consecutiveHighCount = step1.consecutiveHighCount,
            consecutiveDivergenceCount = step1.consecutiveDivergenceCount,
            firstDivergenceTimestamp = step1.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, step2.nextState)

        // Fix 3: Score 10 (< 35%) -> 3 consecutive low fixes reached
        val step3 = AutomaticRideManager.evaluateStateTransition(
            currentState = step2.nextState,
            score = 10,
            now = now + 30_000L,
            consecutiveHighCount = step2.consecutiveHighCount,
            consecutiveDivergenceCount = step2.consecutiveDivergenceCount,
            firstDivergenceTimestamp = step2.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals("3 consecutive low fixes must transition to ENDING_RIDE", AutomaticRideState.ENDING_RIDE, step3.nextState)
        assertTrue("Must stop sharing on 3 consecutive low fixes", step3.shouldStopSharing)
    }

    @Test
    fun testPhase10B_sustainedTimeout45Seconds_triggersStop() {
        val startTime = 100_000L

        // Fix 1 at t=100s, score 30 (< 35%)
        val step1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 30,
            now = startTime,
            consecutiveHighCount = 2,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, step1.nextState)
        assertEquals(startTime, step1.firstDivergenceTimestamp)

        // Fix 2 arrives 46 seconds later (t=146s), score 30 (< 35%)
        val step2 = AutomaticRideManager.evaluateStateTransition(
            currentState = step1.nextState,
            score = 30,
            now = startTime + 46_000L, // > 45_000L timeout
            consecutiveHighCount = step1.consecutiveHighCount,
            consecutiveDivergenceCount = step1.consecutiveDivergenceCount,
            firstDivergenceTimestamp = step1.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals("Timeout > 45s must transition to ENDING_RIDE", AutomaticRideState.ENDING_RIDE, step2.nextState)
        assertTrue("Must stop sharing on timeout", step2.shouldStopSharing)
    }

    @Test
    fun testPhase10B_operatingWindowClose_stopsContributingImmediately() {
        val step = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 90,
            now = System.currentTimeMillis(),
            consecutiveHighCount = 5,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = false // Window closed!
        )
        assertEquals("Window close must set state to WAITING_FOR_WINDOW", AutomaticRideState.WAITING_FOR_WINDOW, step.nextState)
        assertTrue("Window close must trigger stopSharing", step.shouldStopSharing)
    }

    @Test
    fun testPhase10B_cooldownFromEndingRideToWaitingForBus() {
        val step = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.ENDING_RIDE,
            score = 0,
            now = System.currentTimeMillis(),
            consecutiveHighCount = 0,
            consecutiveDivergenceCount = 3,
            firstDivergenceTimestamp = 1000L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.WAITING_FOR_BUS, step.nextState)
        assertEquals(0, step.consecutiveHighCount)
        assertEquals(0, step.consecutiveDivergenceCount)
        assertEquals(0L, step.firstDivergenceTimestamp)
    }
}
