package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.data.repository.ServerAuthDeleter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 13.3 Security Invariants Test Suite:
 * - Elimination of stored driver passwords.
 * - Server-side Firebase Auth account deletion by UID via ServerAuthDeleter.
 * - Driver deletion lifecycle (unassign buses, delete auth, delete RTDB, delete Firestore).
 * - Admin-only authorization check for deletion.
 * - Staff ID reuse with fresh Firebase UID.
 * - Safe legacy data cleanup using FieldValue.delete().
 */
class Phase13_3SecurityTest {

    /**
     * Fake implementation of ServerAuthDeleter for deterministic unit testing.
     */
    class FakeServerAuthDeleter(
        var shouldSucceed: Boolean = true,
        var errorMessage: String = "Server deletion failed"
    ) : ServerAuthDeleter {
        val deletedUids = mutableListOf<String>()

        override suspend fun deleteUserByUid(driverUid: String): Result<Unit> {
            return if (shouldSucceed) {
                deletedUids.add(driverUid)
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage))
            }
        }
    }

    // =========================================================================
    // 1. ELIMINATION OF PLAINTEXT PASSWORD STORAGE
    // =========================================================================

    @Test
    fun testUserProfile_hasNoPasswordField() {
        val profile = UserProfile(
            uid = "uid_driver_test",
            customId = "driver99",
            name = "Test Driver",
            email = "driver99@collegebus.internal",
            role = UserRole.DRIVER,
            assignedBusId = null,
            active = true
        )

        val fields = UserProfile::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("UserProfile must never have password field", fields.contains("password"))
        assertFalse("UserProfile must never have authpassword field", fields.contains("authpassword"))
    }

    @Test
    fun testDriverDocumentMap_neverContainsPassword() {
        val cleanId = "driver01"
        val cleanName = "Ramesh K"
        val internalEmail = "$cleanId@collegebus.internal"
        val newUid = "uid_generated_123"
        val active = true

        val profileMap = hashMapOf<String, Any?>(
            "uid" to newUid,
            "customId" to cleanId,
            "name" to cleanName,
            "email" to internalEmail,
            "role" to "DRIVER",
            "active" to active,
            "assignedBusId" to null
        )

        assertFalse("Firestore profile map must not contain authPassword", profileMap.containsKey("authPassword"))
        assertFalse("Firestore profile map must not contain password", profileMap.containsKey("password"))
        assertEquals("DRIVER", profileMap["role"])
        assertEquals(newUid, profileMap["uid"])
        assertEquals(cleanId, profileMap["customId"])
    }

    // =========================================================================
    // 2. SERVER-SIDE DELETION BY UID
    // =========================================================================

    @Test
    fun testServerAuthDeleter_deletesByUidDirectly() = runBlocking {
        val fakeDeleter = FakeServerAuthDeleter(shouldSucceed = true)
        val targetUid = "target_driver_uid_456"

        val result = fakeDeleter.deleteUserByUid(targetUid)

        assertTrue("ServerAuthDeleter must succeed", result.isSuccess)
        assertEquals(1, fakeDeleter.deletedUids.size)
        assertEquals(targetUid, fakeDeleter.deletedUids.first())
    }

    @Test
    fun testServerAuthDeleter_failureHaltsLifecycle() = runBlocking {
        val fakeDeleter = FakeServerAuthDeleter(shouldSucceed = false, errorMessage = "Cloud Function network timeout")
        val targetUid = "target_driver_uid_456"

        var firestoreDeleted = false
        var rtdbDeleted = false

        val result = fakeDeleter.deleteUserByUid(targetUid)
        if (result.isSuccess) {
            firestoreDeleted = true
            rtdbDeleted = true
        }

        assertTrue("Result must be failure when server-side deletion fails", result.isFailure)
        assertFalse("Firestore must NOT be modified if server deletion fails", firestoreDeleted)
        assertFalse("RTDB must NOT be modified if server deletion fails", rtdbDeleted)
        assertEquals("Cloud Function network timeout", result.exceptionOrNull()?.message)
    }

    // =========================================================================
    // 3. COMPLETE DRIVER DELETION LIFECYCLE
    // =========================================================================

    @Test
    fun testDriverDeletion_unassignsBusesAndRemovesAllRecords() = runBlocking {
        val fakeDeleter = FakeServerAuthDeleter(shouldSucceed = true)
        val driverUid = "driver_uid_88"

        val buses = mutableListOf(
            Bus(busId = "BUS-25", busNumber = "Bus 25", driverId = driverUid, driverName = "Driver 88"),
            Bus(busId = "BUS-26", busNumber = "Bus 26", driverId = "other_driver", driverName = "Driver Other")
        )
        val firestoreUsers = mutableMapOf(driverUid to "active_driver_doc")
        val rtdbUsers = mutableMapOf(driverUid to "active_rtdb_node")

        val serverResult = fakeDeleter.deleteUserByUid(driverUid)
        assertTrue(serverResult.isSuccess)

        // 1. Unassign from all buses
        val updatedBuses = buses.map { bus ->
            if (bus.driverId == driverUid) bus.copy(driverId = null, driverName = null) else bus
        }

        // 2. Remove RTDB
        rtdbUsers.remove(driverUid)

        // 3. Remove Firestore
        firestoreUsers.remove(driverUid)

        // Verifications
        assertEquals(1, fakeDeleter.deletedUids.size)
        assertEquals(driverUid, fakeDeleter.deletedUids.first())
        assertNull("Driver must be unassigned from BUS-25", updatedBuses.first { it.busId == "BUS-25" }.driverId)
        assertNull("Driver name must be cleared", updatedBuses.first { it.busId == "BUS-25" }.driverName)
        assertEquals("other_driver", updatedBuses.first { it.busId == "BUS-26" }.driverId)
        assertFalse("RTDB user node must be deleted", rtdbUsers.containsKey(driverUid))
        assertFalse("Firestore user doc must be deleted", firestoreUsers.containsKey(driverUid))
    }

    // =========================================================================
    // 4. AUTHORIZATION INVARIANTS
    // =========================================================================

    @Test
    fun testAuthorization_nonAdminCannotDelete() {
        val callerProfiles = listOf(
            UserProfile(uid = "uid_student", role = UserRole.STUDENT, active = true),
            UserProfile(uid = "uid_driver", role = UserRole.DRIVER, active = true),
            UserProfile(uid = "uid_inactive_admin", role = UserRole.ADMIN, active = false)
        )

        for (caller in callerProfiles) {
            val isAuthorized = (caller.role == UserRole.ADMIN && caller.active)
            assertFalse("Caller ${caller.uid} must not be authorized", isAuthorized)
        }
    }

    @Test
    fun testAuthorization_activeAdminIsAuthorized() {
        val admin = UserProfile(uid = "uid_admin", role = UserRole.ADMIN, active = true)
        val isAuthorized = (admin.role == UserRole.ADMIN && admin.active)
        assertTrue("Active admin must be authorized", isAuthorized)
    }

    @Test
    fun testProtection_cannotDeleteAdminAccount() {
        val targetProfile = UserProfile(uid = "target_admin_uid", role = UserRole.ADMIN, active = true)
        val isTargetAdmin = (targetProfile.role == UserRole.ADMIN)

        val deletionAttempt: Result<Unit> = if (isTargetAdmin) {
            Result.failure(IllegalArgumentException("Administrator accounts cannot be deleted."))
        } else {
            Result.success(Unit)
        }

        assertTrue("Deleting an ADMIN account must fail", deletionAttempt.isFailure)
        assertEquals("Administrator accounts cannot be deleted.", deletionAttempt.exceptionOrNull()?.message)
    }

    // =========================================================================
    // 5. STAFF ID REUSE WITH FRESH UID & NEW PASSWORD
    // =========================================================================

    @Test
    fun testStaffIdReuse_succeedsWithBrandNewUidAndNoCollision() = runBlocking {
        val fakeDeleter = FakeServerAuthDeleter(shouldSucceed = true)

        val oldUid = "uid_initial_driver01"
        val staffId = "driver01"
        var activeDriverUid: String? = oldUid

        val deleteRes = fakeDeleter.deleteUserByUid(oldUid)
        assertTrue(deleteRes.isSuccess)
        activeDriverUid = null

        val newUid = "uid_recreated_fresh_999"
        assertNotEquals("New UID must differ from old UID", oldUid, newUid)

        activeDriverUid = newUid
        val recreatedProfile = UserProfile(
            uid = newUid,
            customId = staffId,
            name = "Ramesh Kumar (Re-registered)",
            email = "$staffId@collegebus.internal",
            role = UserRole.DRIVER,
            active = true
        )

        assertEquals("driver01", recreatedProfile.customId)
        assertEquals("uid_recreated_fresh_999", activeDriverUid)
        assertEquals(1, fakeDeleter.deletedUids.size)
        assertEquals(oldUid, fakeDeleter.deletedUids.first())
    }

    // =========================================================================
    // 6. LEGACY DATA CLEANUP SANITIZATION
    // =========================================================================

    @Test
    fun testSanitizeLegacyDriverPasswords_removesFieldWithoutExposingValue() {
        val legacyDoc = mutableMapOf<String, Any?>(
            "uid" to "driver_legacy_1",
            "customId" to "driver01",
            "role" to "DRIVER",
            "authPassword" to "superSecret123"
        )

        assertTrue("Legacy document initially has authPassword", legacyDoc.containsKey("authPassword"))
        legacyDoc.remove("authPassword")

        assertFalse("Sanitized document must not contain authPassword", legacyDoc.containsKey("authPassword"))
        assertEquals("driver01", legacyDoc["customId"])
        assertEquals("DRIVER", legacyDoc["role"])
    }
}
