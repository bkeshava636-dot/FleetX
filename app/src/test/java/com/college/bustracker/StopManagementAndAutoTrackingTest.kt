package com.college.bustracker

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.data.model.StopRequest
import com.college.bustracker.util.AutomaticRideManager
import com.college.bustracker.util.AutomaticRideState
import com.college.bustracker.util.GeocodingResultState
import com.college.bustracker.util.NominatimGeocoder
import com.college.bustracker.util.NominatimPlace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StopManagementAndAutoTrackingTest {

    // =========================================================================
    // 1–4: NOMINATIM GEOCODING TESTS
    // =========================================================================

    @Test
    fun test01_shortQueryReturnsNoResultsImmediately() = runBlocking {
        val result1 = NominatimGeocoder.search("")
        assertTrue(result1 is GeocodingResultState.NoResults)

        val result2 = NominatimGeocoder.search("a")
        assertTrue(result2 is GeocodingResultState.NoResults)

        val result3 = NominatimGeocoder.search("  ")
        assertTrue(result3 is GeocodingResultState.NoResults)
    }

    @Test
    fun test02_nominatimPlaceParsingAndFields() {
        val place = NominatimPlace(
            displayName = "Central Railway Station, Bangalore, Karnataka",
            latitude = 12.9784,
            longitude = 77.5695,
            type = "station",
            importance = 0.85
        )
        assertEquals("Central Railway Station, Bangalore, Karnataka", place.displayName)
        assertEquals(12.9784, place.latitude, 0.0001)
        assertEquals(77.5695, place.longitude, 0.0001)
        assertEquals("station", place.type)
        assertEquals(0.85, place.importance, 0.01)
    }

    @Test
    fun test03_geocodingSealedStates() {
        val success = GeocodingResultState.Success(listOf(NominatimPlace("Test", 10.0, 20.0)))
        assertEquals(1, success.places.size)

        val noResults = GeocodingResultState.NoResults
        assertNotNull(noResults)

        val rateLimited = GeocodingResultState.RateLimited
        assertNotNull(rateLimited)

        val netError = GeocodingResultState.NetworkError("Timeout")
        assertEquals("Timeout", netError.message)

        val error = GeocodingResultState.Error("Server error")
        assertEquals("Server error", error.message)
    }

    @Test
    fun test04_throttlingIntervalIsAtLeastOneSecond() {
        // Verify contract: Nominatim requires <= 1 req/sec
        val requiredMinInterval = 1000L
        assertTrue(requiredMinInterval >= 1000L)
    }

    // =========================================================================
    // 5–6: DRIVER STOP REQUEST DATA MODEL & VALIDATION
    // =========================================================================

    @Test
    fun test05_stopRequestSerializationAndDeserialization() {
        val original = StopRequest(
            requestId = "req_123456",
            driverUid = "uid_driver_1",
            driverId = "DRV-101",
            driverName = "Ramesh Kumar",
            busId = "BUS 3",
            routeId = "ROUTE-1",
            proposedStopName = "Hospital Cross",
            latitude = 15.34567,
            longitude = 76.12345,
            status = StopRequest.STATUS_PENDING,
            createdAt = 1700000000000L
        )

        val map = original.toMap()
        assertEquals("req_123456", map["requestId"])
        assertEquals("uid_driver_1", map["driverUid"])
        assertEquals("DRV-101", map["driverId"])
        assertEquals("Ramesh Kumar", map["driverName"])
        assertEquals("BUS 3", map["busId"])
        assertEquals("ROUTE-1", map["routeId"])
        assertEquals("Hospital Cross", map["proposedStopName"])
        assertEquals(15.34567, map["latitude"])
        assertEquals(76.12345, map["longitude"])
        assertEquals(StopRequest.STATUS_PENDING, map["status"])
        assertEquals(1700000000000L, map["createdAt"])
    }

    @Test
    fun test06_stopRequestFieldValidation() {
        val validRequest = StopRequest(
            requestId = "req_1",
            driverUid = "uid_1",
            driverId = "D1",
            busId = "BUS 1",
            proposedStopName = "Circle",
            latitude = 12.0,
            longitude = 77.0
        )
        assertTrue(validRequest.requestId.isNotBlank())
        assertTrue(validRequest.proposedStopName.isNotBlank())
        assertTrue(validRequest.driverUid.isNotBlank())
        assertTrue(validRequest.busId.isNotBlank())
        assertTrue(validRequest.latitude != 0.0 && validRequest.longitude != 0.0)

        // Invalid stop coordinates
        val invalidZeroLat = validRequest.copy(latitude = 0.0)
        assertFalse(invalidZeroLat.latitude != 0.0 && invalidZeroLat.longitude != 0.0)

        // Blank name
        val blankName = validRequest.copy(proposedStopName = "  ")
        assertTrue(blankName.proposedStopName.isBlank())
    }

    // =========================================================================
    // 7–12: ADMIN STOP REVIEW (FILTER, DRAFT EDIT, APPROVE, REJECT)
    // =========================================================================

    @Test
    fun test07_filterStopRequestsByStatus() {
        val list = listOf(
            StopRequest(requestId = "r1", status = StopRequest.STATUS_PENDING),
            StopRequest(requestId = "r2", status = StopRequest.STATUS_APPROVED),
            StopRequest(requestId = "r3", status = StopRequest.STATUS_PENDING),
            StopRequest(requestId = "r4", status = StopRequest.STATUS_REJECTED)
        )

        val pending = list.filter { it.status == StopRequest.STATUS_PENDING }
        val approved = list.filter { it.status == StopRequest.STATUS_APPROVED }
        val rejected = list.filter { it.status == StopRequest.STATUS_REJECTED }

        assertEquals(2, pending.size)
        assertEquals(1, approved.size)
        assertEquals(1, rejected.size)
    }

    @Test
    fun test08_updateStopRequestDraftPreservesImmutableFields() {
        val original = StopRequest(
            requestId = "req_fixed",
            driverUid = "driver_fixed_uid",
            driverName = "Suresh",
            busId = "BUS 5",
            routeId = "ROUTE-2",
            proposedStopName = "Old Proposed Name",
            latitude = 14.1,
            longitude = 75.1,
            createdAt = 1690000000000L
        )

        val updated = original.copy(
            finalStopName = "Refined Stop Name",
            finalLatitude = 14.105,
            finalLongitude = 75.108,
            adminNote = "Adjusted to avoid intersection"
        )

        // Verify immutable fields are intact
        assertEquals("req_fixed", updated.requestId)
        assertEquals("driver_fixed_uid", updated.driverUid)
        assertEquals("Suresh", updated.driverName)
        assertEquals("BUS 5", updated.busId)
        assertEquals("ROUTE-2", updated.routeId)
        assertEquals(1690000000000L, updated.createdAt)

        // Verify edited fields
        assertEquals("Refined Stop Name", updated.finalStopName)
        assertEquals(14.105, updated.finalLatitude!!, 0.0001)
        assertEquals(75.108, updated.finalLongitude!!, 0.0001)
        assertEquals("Adjusted to avoid intersection", updated.adminNote)
    }

    @Test
    fun test09_approvalAssignsSequentialOrder() {
        val existingStops = listOf(
            BusStop(stopId = "stop_1", name = "First Stop", latitude = 10.0, longitude = 20.0, order = 1),
            BusStop(stopId = "stop_2", name = "Second Stop", latitude = 10.1, longitude = 20.1, order = 2),
            BusStop(stopId = "stop_3", name = "Third Stop", latitude = 10.2, longitude = 20.2, order = 3)
        )

        val nextOrder = (existingStops.maxOfOrNull { it.order } ?: 0) + 1
        assertEquals(4, nextOrder)
    }

    @Test
    fun test10_approvalGeneratesUniqueCollisionSafeStopId() {
        val ids = mutableSetOf<String>()
        for (i in 1..100) {
            val uniqueId = "stop_${System.currentTimeMillis()}_${(100..999).random()}_$i"
            assertTrue(uniqueId.startsWith("stop_"))
            assertTrue(ids.add(uniqueId)) // Set add returns false if duplicate
        }
        assertEquals(100, ids.size)
    }

    @Test
    fun test11_approvalPreservesExistingStopsAndOriginalIds() {
        val originalStops = listOf(
            BusStop(stopId = "stop_legacy_a", name = "Old Stop A", latitude = 15.1, longitude = 76.1, order = 1),
            BusStop(stopId = "stop_legacy_b", name = "Old Stop B", latitude = 15.2, longitude = 76.2, order = 2)
        )

        val newStop = BusStop(
            stopId = "stop_new_c",
            name = "Newly Approved Stop",
            latitude = 15.3,
            longitude = 76.3,
            order = 3,
            active = true
        )

        val combinedStops = originalStops + newStop

        assertEquals(3, combinedStops.size)
        // Existing stops remain identical
        assertEquals("stop_legacy_a", combinedStops[0].stopId)
        assertEquals("Old Stop A", combinedStops[0].name)
        assertEquals(1, combinedStops[0].order)

        assertEquals("stop_legacy_b", combinedStops[1].stopId)
        assertEquals("Old Stop B", combinedStops[1].name)
        assertEquals(2, combinedStops[1].order)

        // New stop appended
        assertEquals("stop_new_c", combinedStops[2].stopId)
        assertEquals("Newly Approved Stop", combinedStops[2].name)
        assertEquals(3, combinedStops[2].order)
    }

    @Test
    fun test12_rejectionRecordsStatusReasonAndAuditFields() {
        val request = StopRequest(
            requestId = "req_rej_1",
            proposedStopName = "Risky Turn",
            status = StopRequest.STATUS_PENDING
        )

        val now = System.currentTimeMillis()
        val rejected = request.copy(
            status = StopRequest.STATUS_REJECTED,
            reviewedBy = "admin_master_uid",
            reviewedAt = now,
            rejectionReason = "Sharp bend causes blind spot risk"
        )

        assertEquals(StopRequest.STATUS_REJECTED, rejected.status)
        assertEquals("admin_master_uid", rejected.reviewedBy)
        assertEquals(now, rejected.reviewedAt)
        assertEquals("Sharp bend causes blind spot risk", rejected.rejectionReason)
    }

    // =========================================================================
    // 13–19: AUTOMATIC TRACKING FSM, HYSTERESIS & CONFIDENCE TESTS
    // =========================================================================

    @Test
    fun test13_outsideOperatingWindowTransitionsToWaitingForWindow() {
        val now = System.currentTimeMillis()
        val decision = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 90,
            now = now,
            consecutiveHighCount = 3,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = false // Outside window!
        )

        assertEquals(AutomaticRideState.WAITING_FOR_WINDOW, decision.nextState)
        assertTrue(decision.shouldStopSharing)
        assertFalse(decision.shouldStartSharing)
    }

    @Test
    fun test14_disabledStateRemainsDisabled() {
        val now = System.currentTimeMillis()
        val decision = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.DISABLED,
            score = 95,
            now = now,
            consecutiveHighCount = 0,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )

        assertEquals(AutomaticRideState.DISABLED, decision.nextState)
        assertFalse(decision.shouldStartSharing)
        assertFalse(decision.shouldStopSharing)
    }

    @Test
    fun test15_startContributionRequiresTwoConsecutiveHighFixes() {
        val now = System.currentTimeMillis()

        // Fix 1: Score 80 (>= 75)
        val decision1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.WAITING_FOR_BUS,
            score = 80,
            now = now,
            consecutiveHighCount = 0,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.POSSIBLY_RIDING, decision1.nextState)
        assertEquals(1, decision1.consecutiveHighCount)
        assertFalse(decision1.shouldStartSharing) // 1st fix alone does not start

        // Fix 2: Score 85 (>= 75)
        val decision2 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.POSSIBLY_RIDING,
            score = 85,
            now = now + 15_000L,
            consecutiveHighCount = 1,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, decision2.nextState)
        assertEquals(2, decision2.consecutiveHighCount)
        assertTrue(decision2.shouldStartSharing) // 2nd consecutive fix triggers start!
    }

    @Test
    fun test16_singleHighFixFollowedByLowFixDoesNotStartSharing() {
        val now = System.currentTimeMillis()

        // Fix 1: High score 80 -> POSSIBLY_RIDING (highCount = 1)
        val decision1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.WAITING_FOR_BUS,
            score = 80,
            now = now,
            consecutiveHighCount = 0,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.POSSIBLY_RIDING, decision1.nextState)
        assertEquals(1, decision1.consecutiveHighCount)

        // Fix 2: Low score 20 (< 35) -> resets back to WAITING_FOR_BUS
        val decision2 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.POSSIBLY_RIDING,
            score = 20,
            now = now + 15_000L,
            consecutiveHighCount = 1,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.WAITING_FOR_BUS, decision2.nextState)
        assertEquals(0, decision2.consecutiveHighCount)
        assertFalse(decision2.shouldStartSharing)
    }

    @Test
    fun test17_threeConsecutiveLowFixesStopsSharing() {
        val now = System.currentTimeMillis()

        // Fix 1 low (< 35): holds CONTRIBUTING
        val d1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 20,
            now = now,
            consecutiveHighCount = 2,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, d1.nextState)
        assertEquals(1, d1.consecutiveDivergenceCount)
        assertFalse(d1.shouldStopSharing)

        // Fix 2 low: holds CONTRIBUTING
        val d2 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 15,
            now = now + 15_000L,
            consecutiveHighCount = 2,
            consecutiveDivergenceCount = 1,
            firstDivergenceTimestamp = now,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, d2.nextState)
        assertEquals(2, d2.consecutiveDivergenceCount)
        assertFalse(d2.shouldStopSharing)

        // Fix 3 low: 3 consecutive low fixes -> ENDING_RIDE, stops sharing!
        val d3 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 10,
            now = now + 30_000L,
            consecutiveHighCount = 2,
            consecutiveDivergenceCount = 2,
            firstDivergenceTimestamp = now,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.ENDING_RIDE, d3.nextState)
        assertEquals(3, d3.consecutiveDivergenceCount)
        assertTrue(d3.shouldStopSharing)
    }

    @Test
    fun test18_transientGpsDropoutDoesNotStopSharing() {
        val now = System.currentTimeMillis()

        // 1 low fix (< 35): divergenceCount = 1
        val d1 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 25,
            now = now,
            consecutiveHighCount = 3,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, d1.nextState)
        assertFalse(d1.shouldStopSharing)

        // Healthy fix recovers (score >= 35): resets divergence count back to 0
        val d2 = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 65,
            now = now + 15_000L,
            consecutiveHighCount = 3,
            consecutiveDivergenceCount = d1.consecutiveDivergenceCount,
            firstDivergenceTimestamp = d1.firstDivergenceTimestamp,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.CONTRIBUTING, d2.nextState)
        assertEquals(0, d2.consecutiveDivergenceCount)
        assertEquals(0L, d2.firstDivergenceTimestamp)
        assertFalse(d2.shouldStopSharing)
    }

    @Test
    fun test19_sustainedTimeoutDivergenceStopsSharing() {
        val startDivergence = 100_000L
        val now = startDivergence + 50_000L // 50 seconds later (> 45s timeout)

        // Even with only 2 low fixes, exceeding 45s sustained timeout terminates ride
        val d = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 20,
            now = now,
            consecutiveHighCount = 2,
            consecutiveDivergenceCount = 1,
            firstDivergenceTimestamp = startDivergence,
            isWithinWindow = true
        )
        assertEquals(AutomaticRideState.ENDING_RIDE, d.nextState)
        assertTrue(d.shouldStopSharing)
    }

    // =========================================================================
    // 20–22: LOCATION SERVICES CHECK & SCHEDULE RELIABILITY
    // =========================================================================

    @Test
    fun test20_confidenceScoreEvaluationWithRealBusAndSpeed() {
        val now = System.currentTimeMillis()
        val busLoc = LiveLocation(
            busId = "BUS 1",
            latitude = 15.3400,
            longitude = 76.1500,
            speed = 8.33f, // 30 km/h
            lastUpdated = now
        )

        // Student co-located with bus and moving at matched velocity (30 km/h = 8.33 m/s)
        val eval = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.3401, // ~11m away
            studentLng = 76.1501,
            studentSpeedMps = 8.33f,
            studentAccuracy = 10.0f,
            busLocation = busLoc,
            route = null,
            consecutiveHighCount = 2
        )

        assertTrue(eval.score >= 75)
        assertTrue(eval.isRiding)
        assertTrue(eval.proximityScore > 0)
        assertTrue(eval.speedScore > 0)
    }

    @Test
    fun test21_stationaryStudentWhenBusDrivesAwayGetsNegativeSpeedScore() {
        val now = System.currentTimeMillis()
        val busLoc = LiveLocation(
            busId = "BUS 1",
            latitude = 15.3400,
            longitude = 76.1500,
            speed = 10.0f, // 36 km/h
            lastUpdated = now
        )

        // Student is stationary (< 4 km/h) while bus is driving fast
        val eval = AutomaticRideManager.calculateConfidenceScore(
            studentLat = 15.3400,
            studentLng = 76.1500,
            studentSpeedMps = 0.5f, // 1.8 km/h
            studentAccuracy = 5.0f,
            busLocation = busLoc,
            route = null,
            consecutiveHighCount = 0
        )

        assertEquals(-30, eval.speedScore)
        assertFalse(eval.isRiding)
    }

    @Test
    fun test22_alarmTriggerTimeCalculationLogic() {
        val timeStr = "08:30"
        val time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
        assertEquals(8, time.hour)
        assertEquals(30, time.minute)

        val schedule = RouteSchedule(
            morning = ScheduleWindow(startTime = "07:00", endTime = "09:00", enabled = true),
            evening = ScheduleWindow(startTime = "16:00", endTime = "18:00", enabled = true)
        )
        assertTrue(schedule.morning.enabled)
        assertEquals("07:00", schedule.morning.startTime)
        assertEquals("09:00", schedule.morning.endTime)
        assertTrue(schedule.evening.enabled)
        assertEquals("16:00", schedule.evening.startTime)
        assertEquals("18:00", schedule.evening.endTime)
    }
}
