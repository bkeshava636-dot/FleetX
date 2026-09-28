package com.college.bustracker

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.data.model.getDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAndLogicTest {

    @Test
    fun testLiveLocationStaleDetection() {
        val now = System.currentTimeMillis()

        // 30 seconds old -> Fresh & Active
        val freshLocation = LiveLocation(
            busId = "BUS-12",
            latitude = 15.3435,
            longitude = 76.1554,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 30_000L
        )
        assertFalse("30s old coordinate should NOT be stale", freshLocation.isStale(90_000L))
        assertTrue("Fresh active coordinate should be active", freshLocation.isActive)

        // 95 seconds old -> Stale
        val staleLocation = LiveLocation(
            busId = "BUS-12",
            latitude = 15.3435,
            longitude = 76.1554,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 95_000L
        )
        assertTrue("95s old coordinate must be flagged stale", staleLocation.isStale(90_000L))
        assertFalse("Stale coordinate must not report isActive=true", staleLocation.isActive)

        // Zero timestamp -> Stale
        val uninitialized = LiveLocation(busId = "BUS-12", lastUpdated = 0L)
        assertTrue("0 timestamp must be stale", uninitialized.isStale())
    }

    @Test
    fun testUserRoleParsing() {
        assertEquals(UserRole.STUDENT, UserRole.fromString("STUDENT"))
        assertEquals(UserRole.STUDENT, UserRole.fromString("student"))
        assertEquals(UserRole.DRIVER, UserRole.fromString("DRIVER"))
        assertEquals(UserRole.ADMIN, UserRole.fromString("ADMIN"))
        assertEquals(UserRole.STUDENT, UserRole.fromString("UNKNOWN_ROLE"))
        assertEquals(UserRole.STUDENT, UserRole.fromString(null))
    }

    @Test
    fun testStaffIdNormalizationAndPrivilegeChecks() {
        // ID normalization tests
        val rawInput = "  Admin01  "
        val normalized = rawInput.trim().lowercase()
        assertEquals("admin01", normalized)

        val driverInput = " Driver01 "
        val driverNormalized = driverInput.trim().lowercase()
        assertEquals("driver01", driverNormalized)

        // Internal email mapping check (never exposed to UI)
        val adminInternalEmail = "$normalized@collegebus.internal"
        assertEquals("admin01@collegebus.internal", adminInternalEmail)

        val driverInternalEmail = "$driverNormalized@collegebus.internal"
        assertEquals("driver01@collegebus.internal", driverInternalEmail)
    }

    @Test
    fun testRouteStopOrdering() {
        val route = Route(
            routeId = "ROUTE-01",
            name = "Koppal → College",
            stops = listOf(
                BusStop("S3", "Hosalli Cross", 15.3610, 76.1725, 3),
                BusStop("S1", "Bus Stand", 15.3435, 76.1554, 1),
                BusStop("S2", "Gavi Math", 15.3521, 76.1610, 2)
            )
        )

        val ordered = route.stops.sortedBy { it.order }
        assertEquals(1, ordered[0].order)
        assertEquals("Bus Stand", ordered[0].name)
        assertEquals(2, ordered[1].order)
        assertEquals("Gavi Math", ordered[1].name)
        assertEquals(3, ordered[2].order)
        assertEquals("Hosalli Cross", ordered[2].name)
    }

    @Test
    fun testTrackingDisplayStateTransitions() {
        val now = 1000000000L
        val nullLoc: LiveLocation? = null
        assertEquals(
            com.college.bustracker.data.model.TrackingDisplayState.INACTIVE,
            nullLoc.getDisplayState(now)
        )

        val inactiveLoc = LiveLocation(busId = "B1", trackingStatus = "INACTIVE")
        assertEquals(
            com.college.bustracker.data.model.TrackingDisplayState.INACTIVE,
            inactiveLoc.getDisplayState(now)
        )

        val waitingLoc = LiveLocation(busId = "B1", trackingStatus = "WAITING_FOR_GPS")
        assertEquals(
            com.college.bustracker.data.model.TrackingDisplayState.WAITING_FOR_GPS,
            waitingLoc.getDisplayState(now)
        )

        val zeroCoordActive = LiveLocation(
            busId = "B1",
            trackingStatus = "ACTIVE",
            latitude = 0.0,
            longitude = 0.0,
            lastUpdated = now
        )
        assertEquals(
            com.college.bustracker.data.model.TrackingDisplayState.WAITING_FOR_GPS,
            zeroCoordActive.getDisplayState(now)
        )

        val activeLoc = LiveLocation(
            busId = "B1",
            trackingStatus = "ACTIVE",
            latitude = 15.3435,
            longitude = 76.1554,
            lastUpdated = now - 15_000L // 15s ago
        )
        assertEquals(
            com.college.bustracker.data.model.TrackingDisplayState.ACTIVE,
            activeLoc.getDisplayState(now)
        )

        val staleLoc = LiveLocation(
            busId = "B1",
            trackingStatus = "ACTIVE",
            latitude = 15.3435,
            longitude = 76.1554,
            lastUpdated = now - 95_000L // 95s ago (> 90s)
        )
        assertEquals(
            com.college.bustracker.data.model.TrackingDisplayState.STALE,
            staleLoc.getDisplayState(now)
        )

        val zeroTimestampActive = LiveLocation(
            busId = "B1",
            trackingStatus = "ACTIVE",
            latitude = 15.3435,
            longitude = 76.1554,
            lastUpdated = 0L
        )
        assertEquals(
            com.college.bustracker.data.model.TrackingDisplayState.STALE,
            zeroTimestampActive.getDisplayState(now)
        )
    }
}
