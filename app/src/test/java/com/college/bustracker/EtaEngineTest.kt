package com.college.bustracker

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.util.EtaEngine
import com.college.bustracker.util.EtaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaEngineTest {

    // Standard 4-stop route along Koppal / Hospet highway
    private val testStops = listOf(
        BusStop(stopId = "s1", name = "Bus Stand", latitude = 15.3400, longitude = 76.1500, order = 1),
        BusStop(stopId = "s2", name = "Gavi Math Circle", latitude = 15.3500, longitude = 76.1550, order = 2),
        BusStop(stopId = "s3", name = "Railway Station", latitude = 15.3600, longitude = 76.1600, order = 3),
        BusStop(stopId = "s4", name = "College Campus", latitude = 15.3700, longitude = 76.1650, order = 4)
    )

    @Test
    fun testFindNextStopIndex_busAtStart() {
        // Bus is near Stop 1
        val nextIdx = EtaEngine.findNextStopIndex(
            busLat = 15.3401,
            busLon = 76.1501,
            busSpeedMs = 0f,
            busBearing = 0f,
            stops = testStops
        )
        assertEquals(0, nextIdx)
    }

    @Test
    fun testFindNextStopIndex_betweenStop1AndStop2() {
        // Bus has left Stop 1 and is halfway to Stop 2
        val nextIdx = EtaEngine.findNextStopIndex(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 8.0f,
            busBearing = 30f,
            stops = testStops
        )
        assertEquals(1, nextIdx) // Next is Stop 2 (Gavi Math Circle)
    }

    @Test
    fun testNextStopDetection_busPastStop2MovingTowardsStop3() {
        // Bus is 60m past Stop 2, moving toward Stop 3.
        // Even if geographically closer to Stop 2 than Stop 3, it should select Stop 3!
        val nextIdx = EtaEngine.findNextStopIndex(
            busLat = 15.3506,
            busLon = 76.1553,
            busSpeedMs = 6.0f,
            busBearing = 30f,
            stops = testStops,
            previousPassedIndex = 1
        )
        assertEquals(2, nextIdx) // Stop 3 (Railway Station)
    }

    @Test
    fun testComputeRouteEta_inactiveTracking() {
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 0f,
            busBearing = 0f,
            trackingState = TrackingDisplayState.INACTIVE,
            stops = testStops
        )
        assertEquals(EtaStatus.BUS_NOT_STARTED, result.status)
        assertEquals("Bus has not started", result.statusMessage)
        assertFalse(result.isReliable)
        assertNotNull(result.nextStopEta)
        assertEquals("Bus Stand", result.nextStopEta?.stop?.name)
        assertEquals("—", result.nextStopEta?.etaFormatted)
    }

    @Test
    fun testComputeRouteEta_waitingForGps() {
        val result = EtaEngine.computeRouteEta(
            busLat = 0.0,
            busLon = 0.0,
            busSpeedMs = 0f,
            busBearing = 0f,
            trackingState = TrackingDisplayState.WAITING_FOR_GPS,
            stops = testStops
        )
        assertEquals(EtaStatus.WAITING_FOR_GPS, result.status)
        assertEquals("Waiting for live location", result.statusMessage)
        assertFalse(result.isReliable)
    }

    @Test
    fun testComputeRouteEta_staleLocation() {
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 5.0f,
            busBearing = 30f,
            trackingState = TrackingDisplayState.STALE,
            stops = testStops
        )
        assertEquals(EtaStatus.STALE, result.status)
        assertEquals("Location is stale", result.statusMessage)
        assertFalse(result.isReliable)
        assertNotNull(result.nextStopEta)
    }

    @Test
    fun testComputeRouteEta_normalActiveRoute() {
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 8.0f, // ~29 km/h
            busBearing = 30f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = testStops
        )
        assertEquals(EtaStatus.NORMAL, result.status)
        assertTrue(result.isReliable)
        assertEquals("Active", result.statusMessage)

        val nextStop = result.nextStopEta
        assertNotNull(nextStop)
        assertEquals("Gavi Math Circle", nextStop?.stop?.name)
        assertTrue(nextStop?.etaFormatted?.startsWith("~") == true || nextStop?.etaFormatted?.contains("min") == true)
        assertTrue(nextStop?.distanceMeters ?: 0.0 > 0.0)

        // Verify upcoming stops sequence has increasing ETAs and dwell time
        val upcoming = result.upcomingStops
        assertEquals(4, upcoming.size)
        // Stop 1 is passed
        assertTrue(upcoming[0].isPassed)
        assertEquals("Passed", upcoming[0].etaFormatted)

        // Stop 2 is next
        assertTrue(upcoming[1].isNext)
        assertNotNull(upcoming[1].etaMinutes)

        // Stop 3 is after Stop 2, so distance & ETA should be greater
        assertTrue((upcoming[2].etaMinutes ?: 0) >= (upcoming[1].etaMinutes ?: 0))
        assertTrue(upcoming[2].distanceMeters > upcoming[1].distanceMeters)

        // Stop 4 is final stop
        assertTrue((upcoming[3].etaMinutes ?: 0) >= (upcoming[2].etaMinutes ?: 0))
        assertTrue(upcoming[3].distanceMeters > upcoming[2].distanceMeters)
    }

    @Test
    fun testComputeRouteEta_busArrivedAtFinalDestination() {
        val result = EtaEngine.computeRouteEta(
            busLat = 15.37005, // 5 meters from final stop
            busLon = 76.16505,
            busSpeedMs = 0.2f, // Stationary
            busBearing = 0f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = testStops,
            previousPassedIndex = 3
        )
        assertEquals(EtaStatus.ARRIVED_FINAL_STOP, result.status)
        assertEquals("At Final Destination", result.statusMessage)
        assertEquals("Arrived", result.nextStopEta?.etaFormatted)
    }

    @Test
    fun testComputeRouteEta_emptyStops() {
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 5.0f,
            busBearing = 0f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = emptyList()
        )
        assertEquals(EtaStatus.NO_ROUTE, result.status)
        assertEquals("ETA unavailable", result.statusMessage)
        assertNull(result.nextStopEta)
    }

    @Test
    fun testFormatDistance() {
        assertEquals("350 m away", EtaEngine.formatDistance(350.0))
        assertEquals("1.5 km away", EtaEngine.formatDistance(1500.0))
        assertEquals("—", EtaEngine.formatDistance(0.0))
    }

    @Test
    fun testMonotonicProgression_doesNotRegressBackward() {
        // Bus previously passed Stop 2 (idx 1).
        // Minor GPS scatter puts it near Stop 2 boundary, but previousPassedIndex = 1.
        val nextIdx = EtaEngine.findNextStopIndex(
            busLat = 15.3499,
            busLon = 76.1549,
            busSpeedMs = 2.0f,
            busBearing = 30f,
            stops = testStops,
            previousPassedIndex = 1
        )
        assertTrue(nextIdx >= 1)
    }

    @Test
    fun testSpeedSmoothing_momentarySpikeDampened() {
        // Normal speed was 8 m/s (~29 km/h). Momentary noisy reading is 20 m/s (~72 km/h).
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 20.0f,
            busBearing = 30f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = testStops,
            previousSpeedMs = 8.0f // Previous smooth speed
        )
        assertNotNull(result.nextStopEta)
        // ETA should be reasonable, not collapsed to < 1 min for a 600m segment
        assertTrue(result.nextStopEta?.etaMinutes ?: 0 >= 1)
    }

    @Test
    fun testStationaryInTraffic_usesNominalSpeed() {
        // Bus is between Stop 1 and Stop 2, stopped in traffic (0 m/s, ~500m from Stop 2)
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 0.0f,
            busBearing = 30f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = testStops
        )
        assertNotNull(result.nextStopEta)
        assertEquals("Gavi Math Circle", result.nextStopEta?.stop?.name)
        // Should calculate ETA using nominal speed (~20 km/h) rather than infinite/undefined
        assertTrue(result.nextStopEta?.etaMinutes ?: 0 in 1..5)
    }

    @Test
    fun testStationaryAtStop_detectsStopDwell() {
        // Bus is within 20m of Stop 2
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3501,
            busLon = 76.1551,
            busSpeedMs = 0.0f,
            busBearing = 0f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = testStops
        )
        assertNotNull(result.nextStopEta)
        assertEquals("Arriving now", result.nextStopEta?.etaFormatted)
    }

    @Test
    fun testHysteresisFilter_preventsEtaJitter() {
        // Raw seconds = 320s (~5 min). Previous was 5 min. Small raw change to 340s (~6 min) should stay at 5 min
        val (mins1, str1) = EtaEngine.formatEta(320.0, 1000.0, previousMinutes = 5)
        assertEquals(5, mins1)
        assertEquals("~5 min", str1)

        // Raw seconds jump to 10 min from 5 min -> dampened step
        val (mins2, _) = EtaEngine.formatEta(600.0, 3000.0, previousMinutes = 5)
        assertEquals(6, mins2) // Bounded step-up
    }

    @Test
    fun testBoundsChecking_noNegativeOrImpossibleEta() {
        // Negative seconds should be clamped to at least < 1 min or Arriving now
        val (minsNeg, _) = EtaEngine.formatEta(-10.0, 200.0)
        assertTrue(minsNeg >= 0)

        // Huge seconds should be clamped to MAX_REALISTIC_ETA_MINUTES (180 min)
        val (minsHuge, _) = EtaEngine.formatEta(999999.0, 500000.0)
        assertEquals(180, minsHuge)
    }

    @Test
    fun testPiecewiseRouteDistance_sumsStopToStopChords() {
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3450,
            busLon = 76.1525,
            busSpeedMs = 8.0f,
            busBearing = 30f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = testStops
        )
        val upcoming = result.upcomingStops
        assertEquals(4, upcoming.size)

        // Stop 2 distance + hop(Stop 2 -> Stop 3) <= Stop 3 distance
        val distStop2 = upcoming[1].distanceMeters
        val distStop3 = upcoming[2].distanceMeters
        val distStop4 = upcoming[3].distanceMeters

        assertTrue(distStop3 > distStop2)
        assertTrue(distStop4 > distStop3)

        val hop2to3 = EtaEngine.calculateDistanceMeters(
            testStops[1].latitude, testStops[1].longitude,
            testStops[2].latitude, testStops[2].longitude
        )
        assertEquals(distStop2 + hop2to3, distStop3, 1.0)
    }

    @Test
    fun testScheduleHelper_determineRouteDirection_morningVsEvening() {
        val schedule = com.college.bustracker.data.model.RouteSchedule(
            morning = com.college.bustracker.data.model.ScheduleWindow("07:00", "09:00", true),
            evening = com.college.bustracker.data.model.ScheduleWindow("16:00", "18:00", true)
        )

        // Morning active window (07:30) -> STOPS_TO_COLLEGE
        val morningDir = com.college.bustracker.util.ScheduleHelper.determineRouteDirection(
            schedule, java.time.LocalTime.of(7, 30)
        )
        assertEquals(com.college.bustracker.util.RouteDirection.STOPS_TO_COLLEGE, morningDir)

        // Evening active window (16:30) -> COLLEGE_TO_STOPS
        val eveningDir = com.college.bustracker.util.ScheduleHelper.determineRouteDirection(
            schedule, java.time.LocalTime.of(16, 30)
        )
        assertEquals(com.college.bustracker.util.RouteDirection.COLLEGE_TO_STOPS, eveningDir)

        // Midday (14:00) outside window -> COLLEGE_TO_STOPS (after 12:00 default)
        val middayDir = com.college.bustracker.util.ScheduleHelper.determineRouteDirection(
            schedule, java.time.LocalTime.of(14, 0)
        )
        assertEquals(com.college.bustracker.util.RouteDirection.COLLEGE_TO_STOPS, middayDir)

        // Early morning (06:00) outside window -> STOPS_TO_COLLEGE (before 12:00 default)
        val earlyDir = com.college.bustracker.util.ScheduleHelper.determineRouteDirection(
            schedule, java.time.LocalTime.of(6, 0)
        )
        assertEquals(com.college.bustracker.util.RouteDirection.STOPS_TO_COLLEGE, earlyDir)
    }

    @Test
    fun testEveningEta_busDepartsCollegeMovingToStop3() {
        // In evening, route is reversed: College (s4) -> Railway Station (s3) -> Gavi Math (s2) -> Bus Stand (s1)
        val eveningStops = testStops.reversed()
        assertEquals("College Campus", eveningStops.first().name)
        assertEquals("Bus Stand", eveningStops.last().name)

        // Bus is between College (15.3700, 76.1650) and Stop 3 (15.3600, 76.1600), moving southwest (210 deg)
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3650,
            busLon = 76.1625,
            busSpeedMs = 8.0f,
            busBearing = 210f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = eveningStops
        )

        assertEquals(EtaStatus.NORMAL, result.status)
        assertNotNull(result.nextStopEta)
        // Next stop MUST be Stop 3 (Railway Station) in the evening direction!
        assertEquals("Railway Station", result.nextStopEta?.stop?.name)
        assertEquals(eveningStops[1].stopId, result.nextStopEta?.stop?.stopId)

        // Stop 4 (College) is marked passed
        assertTrue(result.upcomingStops[0].isPassed)
        // Stop 3 is next
        assertTrue(result.upcomingStops[1].isNext)
        // Stop 2 and Stop 1 are upcoming
        assertFalse(result.upcomingStops[2].isPassed)
        assertFalse(result.upcomingStops[3].isPassed)
        assertTrue(result.upcomingStops[3].distanceMeters > result.upcomingStops[2].distanceMeters)
    }

    @Test
    fun testEveningEta_busBetweenStop3AndStop2_selectsStop2() {
        val eveningStops = testStops.reversed()

        // Bus is between Stop 3 (15.3600, 76.1600) and Stop 2 (15.3500, 76.1550)
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3550,
            busLon = 76.1575,
            busSpeedMs = 7.0f,
            busBearing = 210f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = eveningStops,
            previousPassedIndex = 1 // Already passed College (0) and Stop 3 (1)
        )

        assertNotNull(result.nextStopEta)
        assertEquals("Gavi Math Circle", result.nextStopEta?.stop?.name)
        assertEquals("s2", result.nextStopEta?.stop?.stopId)
    }

    @Test
    fun testEveningEta_busArrivedAtFinalDestinationStop1() {
        val eveningStops = testStops.reversed()

        // Bus is at Stop 1 (Bus Stand: 15.3400, 76.1500)
        val result = EtaEngine.computeRouteEta(
            busLat = 15.34002,
            busLon = 76.15003,
            busSpeedMs = 0.1f, // Stationary
            busBearing = 0f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = eveningStops,
            previousPassedIndex = 2
        )

        assertEquals(EtaStatus.ARRIVED_FINAL_STOP, result.status)
        assertEquals("At Final Destination", result.statusMessage)
        assertEquals("Arrived", result.nextStopEta?.etaFormatted)
        assertEquals("Bus Stand", result.nextStopEta?.stop?.name)
    }

    @Test
    fun testEtaInconsistency_distanceBoundedByEuclideanAndRealisticFormatting() {
        // Reproduce the reported issue: Bus is moving towards Stop 2 (~1200m away)
        // Ensure distance is never compressed or collapsed by segment math
        val directDist = EtaEngine.calculateDistanceMeters(15.3400, 76.1500, 15.3500, 76.1550)
        val result = EtaEngine.computeRouteEta(
            busLat = 15.3400,
            busLon = 76.1500,
            busSpeedMs = 3.61f, // 13 km/h
            busBearing = 30f,
            trackingState = TrackingDisplayState.ACTIVE,
            stops = testStops
        )
        assertNotNull(result.nextStopEta)
        val nextStop = result.nextStopEta!!
        assertTrue("Distance must be >= direct distance to stop", nextStop.distanceMeters >= directDist - 1.0)
        // At 13 km/h (~3.61 m/s), 1200m takes ~330 seconds (~5-6 min). It must NEVER be formatted as '< 1 min'
        assertTrue("ETA must be several minutes, not < 1 min", nextStop.etaMinutes ?: 0 >= 4)
        assertFalse("Formatted ETA must not be < 1 min", nextStop.etaFormatted.contains("< 1 min") || nextStop.etaFormatted.contains("<1 min"))
    }

    @Test
    fun testEtaFormatting_84SecondsIsApprox1MinNotLessThan1Min() {
        // 305m at 13 km/h (3.61 m/s) = 84.4 seconds
        val (mins, formatted) = EtaEngine.formatEta(84.4, 305.0)
        assertEquals(1, mins)
        assertEquals("~1 min", formatted)
        assertFalse("< 1 min should only be for under 60 seconds", formatted.contains("<"))
    }

    @Test
    fun testEtaFormatting_under60SecondsIsLessThan1Min() {
        val (mins, formatted) = EtaEngine.formatEta(45.0, 150.0)
        assertEquals(1, mins)
        assertEquals("< 1 min", formatted)
    }

    @Test
    fun testFindNextStopIndex_rejectsFarawaySegmentsAcrossRoute() {
        // 8-stop realistic route
        val multiStops = listOf(
            BusStop(stopId = "s1", name = "BITM Campus", latitude = 15.1394, longitude = 76.9214, order = 1),
            BusStop(stopId = "s2", name = "Cantonment", latitude = 15.1450, longitude = 76.9250, order = 2),
            BusStop(stopId = "s3", name = "Infantry Road", latitude = 15.1500, longitude = 76.9300, order = 3),
            BusStop(stopId = "s4", name = "Radio Park", latitude = 15.1550, longitude = 76.9350, order = 4),
            BusStop(stopId = "s5", name = "Sudha Cross", latitude = 15.1600, longitude = 76.9400, order = 5),
            BusStop(stopId = "s6", name = "Royal Circle", latitude = 15.1650, longitude = 76.9450, order = 6),
            BusStop(stopId = "s7", name = "Vasvi School", latitude = 15.1700, longitude = 76.9500, order = 7),
            BusStop(stopId = "s8", name = "Moka Road", latitude = 15.1750, longitude = 76.9550, order = 8)
        )

        // Bus is near BITM Campus (Stop 1)
        val nextIdx = EtaEngine.findNextStopIndex(
            busLat = 15.1400,
            busLon = 76.9218,
            busSpeedMs = 5.0f,
            busBearing = 45f,
            stops = multiStops,
            previousPassedIndex = -1
        )
        // Next index must be Stop 1 or Stop 2, NEVER jumping ahead to Stop 7 (Vasvi School)!
        assertTrue("Must select early stop near bus, got index $nextIdx", nextIdx in 0..1)
    }
}

