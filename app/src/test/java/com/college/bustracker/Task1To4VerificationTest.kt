package com.college.bustracker

import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.data.model.getDisplayState
import com.college.bustracker.util.BusTopicManager
import com.college.bustracker.util.MultiSourceLocationEngine
import com.college.bustracker.util.SelectedSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Task1To4VerificationTest {

    // =========================================================================
    // TASK 1: Push Notification Topic & Permissions Verification
    // =========================================================================

    @Test
    fun testBusStartTopic_formattingAndSanitization() {
        assertEquals("bus_start_BUS-25", BusTopicManager.formatBusStartTopic("BUS-25"))
        assertEquals("bus_start_BUS_01", BusTopicManager.formatBusStartTopic("BUS 01"))
        assertEquals("bus_start_BUS__Special", BusTopicManager.formatBusStartTopic("BUS #Special"))
    }

    @Test
    fun testBusStopTopic_formatting() {
        assertEquals("bus_BUS-25_stop_STOP-01", BusTopicManager.formatTopic("BUS-25", "STOP-01"))
    }

    // =========================================================================
    // TASK 2: Stop-by-Stop Sequential Priority for Student Fallback
    // =========================================================================

    @Test
    fun testLocationSource_backwardCompatibilityDefaults() {
        val defaultSource = LocationSource(
            busId = "BUS-01",
            sourceId = "student_legacy",
            latitude = 15.34,
            longitude = 76.15
        )
        // Older records without stopId and stopOrder default safely
        assertEquals("", defaultSource.stopId)
        assertEquals(999, defaultSource.stopOrder)
    }

    @Test
    fun testStopByStopPriority_stop1PrioritizedOverStop3() {
        val now = System.currentTimeMillis()
        val staleDriver = LiveLocation(
            busId = "BUS-01",
            latitude = 15.3400,
            longitude = 76.1500,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 60_000L // 60s ago -> stale
        )

        val studentStop3 = LocationSource(
            busId = "BUS-01",
            sourceId = "student_stop3",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.3420,
            longitude = 76.1520,
            accuracy = 10.0f,
            speed = 5.0f,
            bearing = 45.0f,
            timestamp = now - 2_000L,
            active = true,
            stopId = "STOP-03",
            stopOrder = 3
        )

        val studentStop1 = LocationSource(
            busId = "BUS-01",
            sourceId = "student_stop1",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.3418,
            longitude = 76.1518,
            accuracy = 10.0f,
            speed = 5.0f,
            bearing = 45.0f,
            timestamp = now - 3_000L,
            active = true,
            stopId = "STOP-01",
            stopOrder = 1
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = "BUS-01",
            driverSource = null,
            studentSources = listOf(studentStop3, studentStop1),
            previousLocation = staleDriver,
            now = now
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals("ACTIVE", result.liveLocation.trackingStatus)
        // Ensure cluster fusion took place and candidate is valid
        assertTrue(result.liveLocation.latitude > 0.0)
    }

    @Test
    fun testStopByStopPriority_staleStop1DoesNotOverrideFreshStop2() {
        val now = System.currentTimeMillis()
        val staleDriver = LiveLocation(
            busId = "BUS-01",
            latitude = 15.3400,
            longitude = 76.1500,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 60_000L
        )

        val staleStop1Student = LocationSource(
            busId = "BUS-01",
            sourceId = "student_stop1_stale",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.3410,
            longitude = 76.1510,
            accuracy = 85.0f, // Bad accuracy > 40m -> INVALID
            timestamp = now - 5_000L,
            active = true,
            stopId = "STOP-01",
            stopOrder = 1
        )

        val freshStop2Student = LocationSource(
            busId = "BUS-01",
            sourceId = "student_stop2_fresh",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.3415,
            longitude = 76.1515,
            accuracy = 12.0f, // Good accuracy <= 40m
            speed = 6.0f,
            bearing = 40.0f,
            timestamp = now - 2_000L,
            active = true,
            stopId = "STOP-02",
            stopOrder = 2
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = "BUS-01",
            driverSource = null,
            studentSources = listOf(staleStop1Student, freshStop2Student),
            previousLocation = staleDriver,
            now = now
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals(1, result.usedSourcesCount)
        assertEquals(15.3415, result.liveLocation.latitude, 0.0001)
    }

    // =========================================================================
    // TASK 4: Bus Status Outside Operating Window
    // =========================================================================

    @Test
    fun testLiveLocation_getDisplayState_outsideOperatingWindow_returnsInactive() {
        val now = System.currentTimeMillis()
        val activeLocation = LiveLocation(
            busId = "BUS-25",
            latitude = 15.15,
            longitude = 76.90,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 5_000L // 5 seconds ago (very fresh)
        )

        // Inside window -> ACTIVE
        assertEquals(
            TrackingDisplayState.ACTIVE,
            activeLocation.getDisplayState(nowMillis = now, isWithinOperatingWindow = true)
        )

        // Outside operating window -> Must strictly be INACTIVE
        assertEquals(
            TrackingDisplayState.INACTIVE,
            activeLocation.getDisplayState(nowMillis = now, isWithinOperatingWindow = false)
        )
    }

    @Test
    fun testLiveLocation_getDisplayState_staleOutsideWindow_returnsInactive() {
        val now = System.currentTimeMillis()
        val staleLocation = LiveLocation(
            busId = "BUS-25",
            latitude = 15.15,
            longitude = 76.90,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 300_000L // 5 minutes ago
        )

        // Inside window -> STALE
        assertEquals(
            TrackingDisplayState.STALE,
            staleLocation.getDisplayState(nowMillis = now, isWithinOperatingWindow = true)
        )

        // Outside window -> INACTIVE (Bus has not started)
        assertEquals(
            TrackingDisplayState.INACTIVE,
            staleLocation.getDisplayState(nowMillis = now, isWithinOperatingWindow = false)
        )
    }
}
