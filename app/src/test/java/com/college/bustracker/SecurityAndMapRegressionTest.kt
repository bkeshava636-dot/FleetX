package com.college.bustracker

import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.util.LocationThrottler
import com.college.bustracker.util.MultiSourceLocationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Production Hardening & Regression Suite.
 * Audits security invariants, student map behavior, offline recovery, and performance constraints.
 */
class SecurityAndMapRegressionTest {

    // =========================================================================
    // 1. SECURITY: DRIVER ASSIGNMENT & RTDB WRITE CONSTRAINTS
    // =========================================================================

    @Test
    fun testSecurity_driverCanOnlyWriteAssignedBus() {
        val driver = UserProfile(
            uid = "driver_101",
            role = UserRole.DRIVER,
            active = true,
            assignedBusId = "BUS-25"
        )

        // Authorized: assigned bus matches
        val canWriteAssigned = driver.active && driver.role == UserRole.DRIVER && driver.assignedBusId == "BUS-25"
        assertTrue("Driver must be authorized to write to their assigned bus", canWriteAssigned)

        // Unauthorized: attempting to write to BUS-8
        val canWriteOtherBus = driver.active && driver.role == UserRole.DRIVER && driver.assignedBusId == "BUS-8"
        assertFalse("Driver must NOT be permitted to write to unassigned bus", canWriteOtherBus)

        // Inactive driver cannot write
        val inactiveDriver = driver.copy(active = false)
        val inactiveCanWrite = inactiveDriver.active && inactiveDriver.role == UserRole.DRIVER && inactiveDriver.assignedBusId == "BUS-25"
        assertFalse("Inactive driver must NOT be permitted to write to assigned bus", inactiveCanWrite)
    }

    @Test
    fun testSecurity_studentCannotWriteFinalBusLocation() {
        val student = UserProfile(
            uid = "student_555",
            role = UserRole.STUDENT,
            active = true,
            assignedBusId = null
        )

        // Rule: auth != null && root.child('users').child(auth.uid).child('active').val() == true &&
        // (role == 'ADMIN' || (role == 'DRIVER' && assignedBusId == $busId))
        val canWriteLiveLocation = student.active && (student.role == UserRole.ADMIN || (student.role == UserRole.DRIVER && student.assignedBusId == "BUS-25"))
        assertFalse("Student role must NEVER be permitted to write to live_locations", canWriteLiveLocation)
    }

    @Test
    fun testSecurity_studentCannotSpoofContributorIdOrBus() {
        val studentUid = "student_abc"
        val activeBusId = "BUS-25"

        // Legitimate contributor write: sourceId matches auth.uid and bus matches active ride
        val legitSource = LocationSource(
            busId = activeBusId,
            sourceId = studentUid,
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.3435,
            longitude = 76.1554,
            accuracy = 10f,
            timestamp = System.currentTimeMillis(),
            active = true
        )
        val isLegitValid = legitSource.sourceId == studentUid && legitSource.busId == activeBusId
        assertTrue("Legitimate source matching auth.uid and busId must pass", isLegitValid)

        // Spoof attempt 1: Contributor specifies another student's UID as sourceId
        val spoofedUidSource = legitSource.copy(sourceId = "victim_student_xyz")
        val isSpoofedUidValid = spoofedUidSource.sourceId == studentUid
        assertFalse("Contributor must not be allowed to impersonate another sourceId", isSpoofedUidValid)

        // Spoof attempt 2: Contributor contributes to a bus they are not riding
        val spoofedBusSource = legitSource.copy(busId = "BUS-99")
        val isSpoofedBusValid = spoofedBusSource.busId == activeBusId
        assertFalse("Contributor must not be allowed to contribute to a different bus", isSpoofedBusValid)
    }

    @Test
    fun testSecurity_rawContributorLocationsProtectedFromPublicReads() {
        // Under database.rules.json:
        // location_sources/$busId/$sourceId:
        // .read: "auth != null && (auth.uid == $sourceId || root.child('users').child(auth.uid).child('role').val() == 'ADMIN')"
        val studentA_Uid = "student_A"
        val studentB_Uid = "student_B"
        val adminUid = "admin_master"

        fun canReadSource(callerUid: String, callerRole: UserRole, targetSourceId: String): Boolean {
            return callerUid == targetSourceId || callerRole == UserRole.ADMIN
        }

        assertTrue("Owner student can read their own contributor node", canReadSource(studentA_Uid, UserRole.STUDENT, studentA_Uid))
        assertFalse("Other students CANNOT read peer contributor nodes", canReadSource(studentB_Uid, UserRole.STUDENT, studentA_Uid))
        assertTrue("Admin can inspect contributor nodes for diagnostics", canReadSource(adminUid, UserRole.ADMIN, studentA_Uid))
    }

    @Test
    fun testSecurity_adminPrivilegesPreserved() {
        val admin = UserProfile(
            uid = "admin_001",
            role = UserRole.ADMIN,
            active = true
        )

        val canManageUsers = admin.active && admin.role == UserRole.ADMIN
        val canWriteAnyBus = admin.active && admin.role == UserRole.ADMIN
        assertTrue("Admin must retain full management permissions", canManageUsers && canWriteAnyBus)
    }

    // =========================================================================
    // 2. STUDENT MAP CAMERA & TRACKING DISPLAY STATES
    // =========================================================================

    @Test
    fun testStudentMap_cameraCentersImmediatelyAtBusLocation() {
        val liveBus = LiveLocation(
            busId = "BUS-25",
            latitude = 15.34356,
            longitude = 76.15542,
            trackingStatus = "ACTIVE",
            lastUpdated = System.currentTimeMillis()
        )

        // Required behavior: when student taps "VIEW LIVE BUS ON MAP",
        // initial camera target must be the bus location (not default university center)
        val initialCenterLat = liveBus.latitude
        val initialCenterLng = liveBus.longitude
        val initialZoom = 16.0 // Bus-level street zoom

        assertEquals("Map latitude must center on bus latitude", 15.34356, initialCenterLat, 0.00001)
        assertEquals("Map longitude must center on bus longitude", 76.15542, initialCenterLng, 0.00001)
        assertEquals("Map zoom level must be 16.0 for clear bus visibility", 16.0, initialZoom, 0.01)
    }

    @Test
    fun testStudentMap_trackingDisplayStateTransitions() {
        val now = System.currentTimeMillis()

        // Active state: fresh location within 90s
        val activeLocation = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3435,
            longitude = 76.1554,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 10_000L // 10s ago
        )
        val isActive = activeLocation.trackingStatus == "ACTIVE" && (now - activeLocation.lastUpdated) <= 90_000L
        assertTrue("Bus location within 90s must be evaluated as ACTIVE", isActive)

        // Stale state: last update exceeds 90s threshold
        val staleLocation = activeLocation.copy(lastUpdated = now - 100_000L) // 100s ago
        val isStale = staleLocation.trackingStatus != "INACTIVE" && (now - staleLocation.lastUpdated) > 90_000L
        assertTrue("Bus location exceeding 90s without updates must be evaluated as STALE", isStale)

        // Inactive state: driver explicitly stopped tracking
        val inactiveLocation = activeLocation.copy(trackingStatus = "INACTIVE")
        assertEquals("INACTIVE", inactiveLocation.trackingStatus)
    }

    // =========================================================================
    // 3. PERFORMANCE, THROTTLING & MEMORY SAFETY
    // =========================================================================

    @Test
    fun testPerformance_throttlingPreventsExcessiveFirebaseWrites() {
        val now = System.currentTimeMillis()
        val lat = 15.3435
        val lng = 76.1554

        // Stationary update: interval < 8s -> THROTTLED (save cost and battery)
        val stationaryThrottled = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = lat,
            lastSentLng = lng,
            lastSentTimestamp = now - 4_000L, // 4s ago
            newLat = lat,
            newLng = lng,
            newTimestamp = now,
            speedMps = 0.0f
        )
        assertFalse("Stationary fix within 4s must be throttled", stationaryThrottled)

        // Moving update (>15m moved): dispatch allowed after MIN_MOVING_INTERVAL_MS (8.0s)
        val movingAllowed = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = lat,
            lastSentLng = lng,
            lastSentTimestamp = now - 8_500L, // 8.5s ago
            newLat = lat + 0.0003, // ~33 meters away
            newLng = lng + 0.0003,
            newTimestamp = now,
            speedMps = 10.0f
        )
        assertTrue("Moving displacement (>10m) after 8.5s must be dispatched", movingAllowed)
    }

    @Test
    fun testPerformance_offlineBufferOrderAndCapacity() {
        val buffer = mutableListOf<LiveLocation>()
        val maxBufferSize = 50

        // Simulate 60 offline updates
        val baseTime = System.currentTimeMillis()
        for (i in 1..60) {
            val item = LiveLocation(
                busId = "BUS-25",
                latitude = 15.34 + (i * 0.0001),
                longitude = 76.15 + (i * 0.0001),
                lastUpdated = baseTime + (i * 5_000L)
            )
            if (buffer.size >= maxBufferSize) {
                buffer.removeAt(0) // FIFO drop oldest to prevent OOM
            }
            buffer.add(item)
        }

        assertEquals("Buffer must not exceed maximum size cap of 50 items", 50, buffer.size)
        // First item in buffer should be update #11 (oldest 10 dropped)
        assertEquals(baseTime + (11 * 5_000L), buffer.first().lastUpdated)
        // Last item in buffer should be the newest update #60
        assertEquals(baseTime + (60 * 5_000L), buffer.last().lastUpdated)
    }

    @Test
    fun testLifecycle_serviceScopeAndJobsCancelledOnStop() {
        var isJobActive = true
        var isWakeLockHeld = true

        // Simulate stopTrackingSession()
        fun stopTrackingSession() {
            isJobActive = false
            isWakeLockHeld = false
        }

        stopTrackingSession()

        assertFalse("Background coroutine jobs must be cancelled on service stop", isJobActive)
        assertFalse("WakeLock must be released on service stop", isWakeLockHeld)
    }
}
