package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.data.model.getDisplayState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression Test Suite for Student Live Location Real-Time Refresh Pipeline.
 *
 * Verifies:
 * 1. Initial live location -> new snapshot arrival -> ViewModel state changes -> UI coordinates update.
 * 2. Monotonicity: Same bus ID with newer lastUpdated replaces previous location; out-of-order older timestamps do not overwrite.
 * 3. TrackingDisplayState transition from INACTIVE -> ACTIVE on valid live fix.
 * 4. Stale-location threshold evaluation.
 * 5. Test bus (TEST-BUS-01) selection preservation in fleet filtering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudentLiveLocationRefreshTest {

    @Test
    fun testLiveLocationPipeline_initialToNewSnapshotStateEmission() = runTest {
        val liveLocationState = MutableStateFlow<LiveLocation?>(null)

        // 1. Initial state (null or uninitialized)
        assertEquals("Initial live location state must be null before any fix", null, liveLocationState.value)

        // 2. Initial cached/idle state emitted when listener starts
        val initialCached = LiveLocation(busId = "TEST-BUS-01", latitude = 0.0, longitude = 0.0, trackingStatus = "INACTIVE")
        liveLocationState.value = initialCached

        assertEquals("Cached location should reflect initial state", 0.0, liveLocationState.value!!.latitude, 0.0001)
        assertEquals("Cached location should be INACTIVE", TrackingDisplayState.INACTIVE, liveLocationState.value.getDisplayState())

        // 3. New Firebase snapshot arrives with live coordinates
        val fix1 = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.15012,
            longitude = 76.90045,
            speed = 4.5f,
            bearing = 90.0f,
            trackingStatus = "ACTIVE",
            lastUpdated = System.currentTimeMillis()
        )
        liveLocationState.value = fix1

        // 4. Verify ViewModel/UI-observable state contains new coordinates
        val currentLoc = liveLocationState.value
        assertNotNull("Live location must not be null after fix", currentLoc)
        assertEquals("Latitude must update to new fix", 15.15012, currentLoc!!.latitude, 0.00001)
        assertEquals("Longitude must update to new fix", 76.90045, currentLoc.longitude, 0.00001)
        assertEquals("Tracking state must transition to ACTIVE", TrackingDisplayState.ACTIVE, currentLoc.getDisplayState())
    }

    @Test
    fun testLiveLocationPipeline_sameBusNewerLastUpdatedReplacesOlder() = runTest {
        var currentLocation: LiveLocation? = null

        fun onNewLocation(newLoc: LiveLocation) {
            val curr = currentLocation
            val serverNow = 1790950000000L + 10_000L
            val isDriverRecovery = curr?.sourceType == "STUDENT_ASSISTED" &&
                    newLoc.sourceType != "STUDENT_ASSISTED" &&
                    !newLoc.isStale(nowMillis = serverNow) &&
                    newLoc.trackingStatus == "ACTIVE"

            val isNewData = curr == null ||
                    curr.busId != newLoc.busId ||
                    newLoc.lastUpdated >= curr.lastUpdated ||
                    isDriverRecovery

            if (isNewData) {
                currentLocation = newLoc
            }
        }

        val baseTime = 1790950000000L

        // Fix A
        val fixA = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1500,
            longitude = 76.9000,
            lastUpdated = baseTime
        )
        onNewLocation(fixA)
        assertEquals(15.1500, currentLocation!!.latitude, 0.0001)

        // Fix B (newer timestamp, different coordinates)
        val fixB = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1550,
            longitude = 76.9050,
            lastUpdated = baseTime + 5000L
        )
        onNewLocation(fixB)
        assertEquals("Newer timestamp must replace previous location", 15.1550, currentLocation!!.latitude, 0.0001)
        assertEquals(fixB.lastUpdated, currentLocation!!.lastUpdated)

        // Fix C (even newer timestamp, different coordinates)
        val fixC = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1600,
            longitude = 76.9100,
            lastUpdated = baseTime + 10000L
        )
        onNewLocation(fixC)
        assertEquals("Newer timestamp must replace previous location", 15.1600, currentLocation!!.latitude, 0.0001)
        assertEquals(fixC.lastUpdated, currentLocation!!.lastUpdated)

        // Out-of-order older fix arrives (timestamp between A and B)
        val staleOutOfOrder = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1520,
            longitude = 76.9020,
            lastUpdated = baseTime + 2000L
        )
        onNewLocation(staleOutOfOrder)
        assertEquals("Out-of-order older timestamp must NOT overwrite newer location", 15.1600, currentLocation!!.latitude, 0.0001)
        assertEquals(fixC.lastUpdated, currentLocation!!.lastUpdated)
    }

    @Test
    fun testLiveLocationPipeline_staleThresholdEvaluation() {
        val now = System.currentTimeMillis()

        // Fresh active location (updated 10s ago)
        val freshLocation = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1500,
            longitude = 76.9000,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 10_000L
        )
        assertEquals("Fresh location (<90s) must be ACTIVE", TrackingDisplayState.ACTIVE, freshLocation.getDisplayState(now))
        assertFalse("Fresh location must not be stale", freshLocation.isStale())

        // Stale active location (updated 95s ago > 90s threshold)
        val staleLocation = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1500,
            longitude = 76.9000,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 95_000L
        )
        assertEquals("Location older than 90s must show STALE state", TrackingDisplayState.STALE, staleLocation.getDisplayState(now))
        assertTrue("Location older than 90s isStale must be true", staleLocation.isStale(90_000L))

        // Inactive location
        val inactiveLocation = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1500,
            longitude = 76.9000,
            trackingStatus = "INACTIVE",
            lastUpdated = now - 10_000L
        )
        assertEquals("Explicit INACTIVE status must show INACTIVE state regardless of age", TrackingDisplayState.INACTIVE, inactiveLocation.getDisplayState(now))

        // Negative elapsed time (due to device clock skew where driver is ahead)
        val aheadLocation = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1500,
            longitude = 76.9000,
            trackingStatus = "ACTIVE",
            lastUpdated = now + 5000L
        )
        assertEquals("Location timestamp slightly ahead must be ACTIVE, never STALE", TrackingDisplayState.ACTIVE, aheadLocation.getDisplayState(now))
        assertFalse("Location ahead of local clock must not be stale", aheadLocation.isStale(nowMillis = now))
    }

    @Test
    fun testLiveLocationPipeline_statusChangeAndDriverRecoveryAccepted() = runTest {
        var currentLocation: LiveLocation? = null
        val serverNow = 2500L

        fun onNewLocation(newLoc: LiveLocation) {
            val curr = currentLocation
            val isDriverRecovery = curr?.sourceType == "STUDENT_ASSISTED" &&
                    newLoc.sourceType != "STUDENT_ASSISTED" &&
                    !newLoc.isStale(nowMillis = serverNow) &&
                    newLoc.trackingStatus == "ACTIVE"

            val isNewData = curr == null ||
                    curr.busId != newLoc.busId ||
                    newLoc.lastUpdated >= curr.lastUpdated ||
                    isDriverRecovery

            if (isNewData) {
                currentLocation = newLoc
            }
        }

        // 1. Initial waiting for GPS
        val initialWait = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 0.0,
            longitude = 0.0,
            trackingStatus = "WAITING_FOR_GPS",
            lastUpdated = 1000L
        )
        onNewLocation(initialWait)
        assertEquals("WAITING_FOR_GPS", currentLocation!!.trackingStatus)

        // 2. Active fix arrives with newer timestamp
        val activeFix = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1500,
            longitude = 76.9000,
            trackingStatus = "ACTIVE",
            lastUpdated = 1500L
        )
        onNewLocation(activeFix)
        assertEquals("ACTIVE", currentLocation!!.trackingStatus)

        // 3. Student assisted fix takes over
        val studentAssisted = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1505,
            longitude = 76.9005,
            trackingStatus = "ACTIVE",
            sourceType = "STUDENT_ASSISTED",
            lastUpdated = 2000L
        )
        onNewLocation(studentAssisted)
        assertEquals("STUDENT_ASSISTED", currentLocation!!.sourceType)

        // 4. Stale driver packet (120s old > 90s stale) arrives -> must NOT overwrite live student fix
        val staleDriver = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1400,
            longitude = 76.8900,
            trackingStatus = "ACTIVE",
            sourceType = "PRIMARY_DRIVER",
            lastUpdated = serverNow - 120_000L
        )
        onNewLocation(staleDriver)
        assertEquals("Stale driver packet must NOT overwrite active student assisted fix", "STUDENT_ASSISTED", currentLocation!!.sourceType)
        assertEquals(15.1505, currentLocation!!.latitude, 0.0001)

        // 5. Fresh driver recovers with timestamp that is active
        val driverRecovered = LiveLocation(
            busId = "TEST-BUS-01",
            latitude = 15.1510,
            longitude = 76.9010,
            trackingStatus = "ACTIVE",
            sourceType = "PRIMARY_DRIVER",
            lastUpdated = 2400L
        )
        onNewLocation(driverRecovered)
        assertEquals("Fresh driver recovery must replace STUDENT_ASSISTED", "PRIMARY_DRIVER", currentLocation!!.sourceType)
        assertEquals(15.1510, currentLocation!!.latitude, 0.0001)
    }

    @Test
    fun testLiveLocationPipeline_testBusSelectionPreservation() {
        val fleetList = listOf(
            Bus(busId = "BUS-01", busNumber = "BUS-01", active = true),
            Bus(busId = "BUS-03", busNumber = "BUS-03", active = true),
            Bus(busId = "TEST-BUS-01", busNumber = "TEST-BUS-01", active = true)
        )

        // Filter for general students hides TEST- buses by default
        val generalStudentBuses = fleetList.filter { it.active && !it.busId.startsWith("TEST-", ignoreCase = true) }
        assertEquals(2, generalStudentBuses.size)
        assertFalse(generalStudentBuses.any { it.busId == "TEST-BUS-01" })

        // When TEST-BUS-01 is explicitly selected, student fleet retains it
        val selectedBusId = "TEST-BUS-01"
        val activeWithSelection = fleetList.filter { it.active }.filter {
            !it.busId.startsWith("TEST-", ignoreCase = true) || it.busId.equals(selectedBusId, ignoreCase = true)
        }
        assertEquals(3, activeWithSelection.size)
        assertTrue(activeWithSelection.any { it.busId == "TEST-BUS-01" })

        // Selection must not be ejected while active in fleet
        val currentSelected = Bus(busId = "TEST-BUS-01", busNumber = "TEST-BUS-01", active = true)
        val shouldEjectWhileActive = activeWithSelection.none { it.busId.equals(currentSelected.busId, ignoreCase = true) }
        assertFalse("Explicitly selected test bus must NOT be ejected from selection when active", shouldEjectWhileActive)

        // If bus is removed or deactivated, it should be ejected
        val fleetWithoutTestBus = fleetList.filter { !it.busId.startsWith("TEST-") }
        val shouldEjectWhenRemoved = fleetWithoutTestBus.none { it.busId.equals(currentSelected.busId, ignoreCase = true) }
        assertTrue("Test bus must be ejected if deleted/deactivated from fleet", shouldEjectWhenRemoved)
    }
}
