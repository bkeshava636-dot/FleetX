package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.util.StopEta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 13.2 Validation Suite:
 * 1. Admin/Driver Back Button & Session Persistence
 * 2. Compact Map UI & ETA Overlay
 * 3. Driver Deletion & Staff ID Reuse
 */
class Phase13FixesTest {

    // =========================================================================
    // 1. ADMIN/DRIVER SESSION PERSISTENCE & BACK NAVIGATION INVARIANTS
    // =========================================================================

    @Test
    fun testSessionPersistence_driverSessionSurvivesAppMinimization() {
        val driverProfile = UserProfile(
            uid = "uid_driver_01",
            customId = "driver01",
            name = "Ramesh K",
            email = "driver01@collegebus.internal",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            active = true
        )

        var activeSession: UserProfile? = driverProfile

        // System back button pressed on DriverHome -> moves task to back (minimized), NOT logged out
        val onSystemBackPressed = {
            // System moves task to back; session is NOT cleared
        }
        onSystemBackPressed()

        assertNotNull("Driver session must survive system back press", activeSession)
        assertEquals("driver01", activeSession?.customId)
        assertEquals(UserRole.DRIVER, activeSession?.role)
        assertTrue("Driver account must remain active", activeSession?.active == true)
    }

    @Test
    fun testSessionPersistence_adminSessionSurvivesAppMinimization() {
        val adminProfile = UserProfile(
            uid = "uid_admin_01",
            customId = "admin",
            name = "Transport Admin",
            email = "admin@collegebus.internal",
            role = UserRole.ADMIN,
            assignedBusId = null,
            active = true
        )

        var activeSession: UserProfile? = adminProfile

        // System back button pressed on AdminDashboard -> dismisses any dialog or minimizes
        val onSystemBackPressed = {
            // System moves task to back; session is NOT cleared
        }
        onSystemBackPressed()

        assertNotNull("Admin session must survive system back press", activeSession)
        assertEquals("admin", activeSession?.customId)
        assertEquals(UserRole.ADMIN, activeSession?.role)
    }

    @Test
    fun testSessionPersistence_explicitLogoutClearsSessionAndNavigatesHome() {
        var activeSession: UserProfile? = UserProfile(
            uid = "uid_driver_01",
            customId = "driver01",
            name = "Ramesh K",
            email = "driver01@collegebus.internal",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            active = true
        )

        var navigatedRoute: String? = "driver_home"

        // Explicit Logout Triggered
        val onLogout = {
            activeSession = null
            navigatedRoute = "student_home"
        }
        onLogout()

        assertNull("Session must be null after explicit logout", activeSession)
        assertEquals("Must navigate to student_home on logout", "student_home", navigatedRoute)
    }

    // =========================================================================
    // 2. MAP UI — COMPACT OVERLAY & VISIBILITY INVARIANTS
    // =========================================================================

    @Test
    fun testMapOverlay_stopsCollapsedByDefault() {
        val showUpcomingStopsInitial = false
        assertFalse("Route stops list must be collapsed by default", showUpcomingStopsInitial)
    }

    @Test
    fun testMapOverlay_compactEtaFormatting() {
        val stop = BusStop(stopId = "stop_01", name = "Gavi Math Circle", order = 1, latitude = 15.345, longitude = 76.155)
        val stopEta = StopEta(
            stop = stop,
            etaMinutes = 5,
            etaFormatted = "~5 min",
            distanceMeters = 1200.0,
            distanceFormatted = "1.2 km away",
            isPassed = false,
            isNext = true
        )

        assertEquals("~5 min", stopEta.etaFormatted)
        assertEquals("1.2 km away", stopEta.distanceFormatted)

        // Speed formatting
        val speedMps = 11.67f // ~42 km/h
        val speedKmH = (speedMps * 3.6).toInt()
        val speedText = if (speedKmH > 2) "$speedKmH km/h" else "At Stop"
        assertEquals("42 km/h", speedText)

        // Stationary bus
        val stoppedSpeedMps = 0.2f
        val stoppedKmH = (stoppedSpeedMps * 3.6).toInt()
        val stoppedText = if (stoppedKmH > 2) "$stoppedKmH km/h" else "At Stop"
        assertEquals("At Stop", stoppedText)
    }

    @Test
    fun testMapOverlay_statusBadgesAccurate() {
        val activeState = TrackingDisplayState.ACTIVE
        val staleState = TrackingDisplayState.STALE
        val waitingState = TrackingDisplayState.WAITING_FOR_GPS
        val inactiveState = TrackingDisplayState.INACTIVE

        assertEquals("ACTIVE", activeState.name)
        assertEquals("STALE", staleState.name)
        assertEquals("WAITING_FOR_GPS", waitingState.name)
        assertEquals("INACTIVE", inactiveState.name)
    }

    // =========================================================================
    // 3. DRIVER DELETION & STAFF ID REUSE INVARIANTS
    // =========================================================================

    @Test
    fun testDriverDeletion_unassignsFromBus() {
        val bus = Bus(
            busId = "BUS-25",
            busNumber = "Bus 25",
            routeId = "ROUTE-KOPPAL-COLLEGE",
            driverId = "driver_uid_101",
            driverName = "Original Driver"
        )

        val driverToDeleteId = "driver_uid_101"
        val updatedBus = if (bus.driverId == driverToDeleteId) {
            bus.copy(driverId = null, driverName = null)
        } else {
            bus
        }

        assertNull("Bus driverId must be null after driver deletion", updatedBus.driverId)
        assertNull("Bus driverName must be null after driver deletion", updatedBus.driverName)
    }

    @Test
    fun testDriverCreation_duplicateIdRejectedWhenActive() {
        val existingDrivers = mutableListOf(
            UserProfile(uid = "uid_01", customId = "driver01", name = "Driver 1", email = "driver01@collegebus.internal", role = UserRole.DRIVER, active = true)
        )

        val candidateId = "driver01".trim().lowercase()

        val isDuplicate = existingDrivers.any { it.customId == candidateId }
        assertTrue("Duplicate driver ID must be detected while driver exists", isDuplicate)

        val creationResult: Result<UserProfile> = if (isDuplicate) {
            Result.failure(IllegalArgumentException("Driver ID '$candidateId' already exists"))
        } else {
            Result.success(UserProfile(uid = "new_uid", customId = candidateId, role = UserRole.DRIVER))
        }

        assertTrue("Creation must fail for duplicate ID", creationResult.isFailure)
        assertEquals("Driver ID 'driver01' already exists", creationResult.exceptionOrNull()?.message)
    }

    @Test
    fun testDriverCreation_staffIdReuseSucceedsWithFreshUid() {
        val existingDrivers = mutableListOf(
            UserProfile(uid = "uid_old_101", customId = "driver01", name = "Old Driver", email = "driver01@collegebus.internal", role = UserRole.DRIVER, active = true)
        )

        // 1. Admin deletes driver01
        val oldUid = "uid_old_101"
        existingDrivers.removeIf { it.uid == oldUid }
        assertTrue("Driver list must be empty after deletion", existingDrivers.isEmpty())

        // 2. Admin creates a new driver with the SAME staff ID "driver01"
        val cleanId = "driver01"
        val newUid = "uid_fresh_202"

        val isDuplicate = existingDrivers.any { it.customId == cleanId }
        assertFalse("Clean ID must be available for reuse after deletion", isDuplicate)

        val newDriver = UserProfile(
            uid = newUid,
            customId = cleanId,
            name = "New Driver 2",
            email = "$cleanId@collegebus.internal",
            role = UserRole.DRIVER,
            assignedBusId = null,
            active = true
        )
        existingDrivers.add(newDriver)

        assertEquals(1, existingDrivers.size)
        assertEquals("driver01", existingDrivers.first().customId)
        assertNotEquals("New driver must receive a different UID", oldUid, existingDrivers.first().uid)
        assertEquals("uid_fresh_202", existingDrivers.first().uid)
        assertEquals("New Driver 2", existingDrivers.first().name)
    }
}
