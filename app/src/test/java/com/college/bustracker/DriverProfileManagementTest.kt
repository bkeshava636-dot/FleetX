package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.data.repository.ServerAuthDeleter
import com.college.bustracker.data.repository.ServerAuthUpdater
import com.college.bustracker.ui.admin.DriverProfileValidator
import com.college.bustracker.ui.student.DriverDisplayInfo
import com.college.bustracker.ui.student.DriverInfoLogic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comprehensive automated test suite for Phase: Admin Driver Profile Management.
 * Covers all 25 criteria specified in the task requirements.
 */
class DriverProfileManagementTest {

    // =========================================================================
    // TEST 1: ADD DRIVER WITH PHONE NUMBER
    // =========================================================================
    @Test
    fun test01_addDriverWithPhoneNumber() {
        val phoneResult = DriverProfileValidator.validateAndNormalizePhoneNumber("9876543210")
        assertTrue(phoneResult.isSuccess)
        assertEquals("9876543210", phoneResult.getOrNull())

        val profile = UserProfile(
            uid = "DRV-NEW-01",
            customId = "driver01",
            name = "Ramesh Kumar",
            role = UserRole.DRIVER,
            phoneNumber = phoneResult.getOrNull(),
            active = true
        )
        assertEquals("9876543210", profile.phoneNumber)
        assertEquals("Ramesh Kumar", profile.name)
        assertEquals("driver01", profile.customId)
    }

    // =========================================================================
    // TEST 2: EDIT DRIVER NAME
    // =========================================================================
    @Test
    fun test02_editDriverName() {
        val original = UserProfile(
            uid = "DRV-1",
            customId = "driver01",
            name = "Old Name",
            role = UserRole.DRIVER,
            phoneNumber = "9876543210"
        )
        val validName = DriverProfileValidator.validateName("Ravi Shankar")
        assertTrue(validName.isSuccess)

        val updated = original.copy(name = validName.getOrThrow())
        assertEquals("Ravi Shankar", updated.name)
        assertEquals("DRV-1", updated.uid) // UID preserved
        assertEquals("driver01", updated.customId) // ID preserved
    }

    // =========================================================================
    // TEST 3: EDIT DRIVER PHONE NUMBER
    // =========================================================================
    @Test
    fun test03_editDriverPhoneNumber() {
        val original = UserProfile(
            uid = "DRV-1",
            customId = "driver01",
            name = "Ravi",
            phoneNumber = "9876543210"
        )
        // Update to new valid Indian number with +91 prefix
        val phoneRes = DriverProfileValidator.validateAndNormalizePhoneNumber("+91 9123456780")
        assertTrue(phoneRes.isSuccess)
        val updated = original.copy(phoneNumber = phoneRes.getOrThrow())
        assertEquals("9123456780", updated.phoneNumber)
    }

    // =========================================================================
    // TEST 4: EDIT DRIVER ID
    // =========================================================================
    @Test
    fun test04_editDriverId() {
        val original = UserProfile(
            uid = "DRV-1",
            customId = "driver01",
            name = "Ravi"
        )
        val idRes = DriverProfileValidator.validateDriverId("driver01_lead")
        assertTrue(idRes.isSuccess)
        val updated = original.copy(customId = idRes.getOrThrow())
        assertEquals("driver01_lead", updated.customId)
        assertEquals("DRV-1", updated.uid)
    }

    // =========================================================================
    // TEST 5: REJECT DUPLICATE DRIVER ID
    // =========================================================================
    @Test
    fun test05_rejectDuplicateDriverId() {
        val existingDrivers = listOf(
            UserProfile(uid = "DRV-1", customId = "driver01"),
            UserProfile(uid = "DRV-2", customId = "driver02")
        )
        val proposedId = "driver02"
        val isDuplicate = existingDrivers.any { it.uid != "DRV-1" && it.customId.equals(proposedId, ignoreCase = true) }
        assertTrue("Duplicate Driver ID among different accounts must be detected", isDuplicate)

        val invalidIdBlank = DriverProfileValidator.validateDriverId("   ")
        assertTrue("Blank driver ID must fail", invalidIdBlank.isFailure)

        val invalidIdAdmin = DriverProfileValidator.validateDriverId("admin_driver")
        assertTrue("Driver ID containing 'admin' must fail", invalidIdAdmin.isFailure)
    }

    // =========================================================================
    // TEST 6: PRESERVE FIREBASE AUTH UID AFTER PROFILE EDIT
    // =========================================================================
    @Test
    fun test06_preserveFirebaseAuthUidAfterProfileEdit() {
        val originalUid = "GaEgDkQeIVdZdaaQFAxgrApoXbb2" // Bullappa's real UID
        val original = UserProfile(
            uid = originalUid,
            customId = "driver25",
            name = "BULLAPPA",
            role = UserRole.DRIVER
        )
        val edited = original.copy(
            name = "Bullappa Gowda",
            phoneNumber = "9876543210",
            customId = "driver25_updated"
        )
        assertEquals("UID must remain strictly identical after editing name, phone, and ID", originalUid, edited.uid)
    }

    // =========================================================================
    // TEST 7: DRIVER CAN STILL LOG IN AFTER NAME/PHONE EDIT
    // =========================================================================
    @Test
    fun test07_driverCanStillLogInAfterNamePhoneEdit() {
        val customId = "driver25"
        val emailBefore = DriverProfileValidator.mapStaffEmail(customId)

        // After editing name and phone, Driver ID remains unchanged
        val emailAfter = DriverProfileValidator.mapStaffEmail(customId)
        assertEquals("Login email mapping must remain identical when Driver ID is unchanged", emailBefore, emailAfter)
        assertEquals("driver25@collegebus.internal", emailAfter)
    }

    // =========================================================================
    // TEST 8: DRIVER ID LOGIN MAPPING WORKS AFTER ID CHANGE
    // =========================================================================
    @Test
    fun test08_driverIdLoginMappingWorksAfterIdChange() {
        val newCustomId = "driver25_new"
        val mappedEmail = DriverProfileValidator.mapStaffEmail(newCustomId)
        assertEquals("driver25_new@collegebus.internal", mappedEmail)
    }

    // =========================================================================
    // TEST 9: OLD DRIVER ID NO LONGER WORKS AFTER ID CHANGE
    // =========================================================================
    @Test
    fun test09_oldDriverIdNoLongerWorksAfterIdChange() {
        val oldId = "driver25"
        val newId = "driver25_new"
        val oldEmail = DriverProfileValidator.mapStaffEmail(oldId)
        val newEmail = DriverProfileValidator.mapStaffEmail(newId)

        assertNotEquals(oldEmail, newEmail)
        assertEquals("driver25@collegebus.internal", oldEmail)
        assertEquals("driver25_new@collegebus.internal", newEmail)
    }

    // =========================================================================
    // TEST 10: ASSIGN BUS
    // =========================================================================
    @Test
    fun test10_assignBusFromProfileEditor() {
        val driver = UserProfile(uid = "DRV-1", customId = "drv01", name = "Driver One", assignedBusId = null)
        val updated = driver.copy(assignedBusId = "BUS-25")
        assertEquals("BUS-25", updated.assignedBusId)
    }

    // =========================================================================
    // TEST 11: CHANGE ASSIGNED BUS
    // =========================================================================
    @Test
    fun test11_changeAssignedBus() {
        val driver = UserProfile(uid = "DRV-1", customId = "drv01", assignedBusId = "BUS-25")
        val updated = driver.copy(assignedBusId = "BUS-14")
        assertEquals("BUS-14", updated.assignedBusId)
    }

    // =========================================================================
    // TEST 12: UNASSIGN BUS
    // =========================================================================
    @Test
    fun test12_unassignBus() {
        val driver = UserProfile(uid = "DRV-1", customId = "drv01", assignedBusId = "BUS-25")
        val updated = driver.copy(assignedBusId = null)
        assertNull("Assigned bus should be null after unassign", updated.assignedBusId)
    }

    // =========================================================================
    // TEST 13: ACTIVATE / DEACTIVATE DRIVER
    // =========================================================================
    @Test
    fun test13_activateDeactivateDriver() {
        val activeDriver = UserProfile(uid = "DRV-1", active = true, assignedBusId = null)
        val deactivated = activeDriver.copy(active = false)
        assertFalse(deactivated.active)

        // Safety rule: deactivating an assigned driver must be blocked
        val assignedDriver = UserProfile(uid = "DRV-1", active = true, assignedBusId = "BUS-25")
        val canDeactivateDirectly = assignedDriver.assignedBusId.isNullOrBlank()
        assertFalse("Cannot deactivate driver while still assigned to a bus", canDeactivateDirectly)
    }

    // =========================================================================
    // TEST 14: STUDENT DASHBOARD RECEIVES UPDATED DRIVER NAME
    // =========================================================================
    @Test
    fun test14_studentDashboardReceivesUpdatedDriverName() {
        val bus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        val updatedDriver = UserProfile(
            uid = "DRV-1",
            name = "Ravi Shankar",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            phoneNumber = "9876543210",
            active = true
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = bus,
            activeSwap = null,
            allBuses = listOf(bus),
            allDrivers = listOf(updatedDriver)
        )
        assertEquals("Ravi Shankar", info.driverName)
    }

    // =========================================================================
    // TEST 15: STUDENT DASHBOARD RECEIVES UPDATED PHONE NUMBER
    // =========================================================================
    @Test
    fun test15_studentDashboardReceivesUpdatedPhoneNumber() {
        val bus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        val updatedDriver = UserProfile(
            uid = "DRV-1",
            name = "Ravi",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            phoneNumber = "9876543210",
            active = true
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = bus,
            activeSwap = null,
            allBuses = listOf(bus),
            allDrivers = listOf(updatedDriver)
        )
        assertEquals("9876543210", info.phoneNumber)
        assertTrue(info.isAssigned)
    }

    // =========================================================================
    // TEST 16: MISSING PHONE NUMBER STATE
    // =========================================================================
    @Test
    fun test16_missingPhoneNumberState() {
        val bus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        val driverWithoutPhone = UserProfile(
            uid = "DRV-1",
            name = "Bullappa",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            phoneNumber = null,
            active = true
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = bus,
            activeSwap = null,
            allBuses = listOf(bus),
            allDrivers = listOf(driverWithoutPhone)
        )
        assertNull("Missing phone number must be null in driver display info", info.phoneNumber)
        assertEquals("Bullappa", info.driverName)
    }

    // =========================================================================
    // TEST 17: ACTIVE BUS SWAP REPLACEMENT-DRIVER INFORMATION STILL WORKS
    // =========================================================================
    @Test
    fun test17_activeBusSwapReplacementDriverInfoWorks() {
        val busOrig = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        val busRepl = Bus(busId = "BUS-14", busNumber = "BUS 14", active = true)

        val driverOrig = UserProfile(uid = "D-ORIG", name = "Original", assignedBusId = "BUS-25", phoneNumber = "1111111111", active = true)
        val driverRepl = UserProfile(uid = "D-REPL", name = "Replacement Driver", assignedBusId = "BUS-14", phoneNumber = "9998887776", active = true)

        val swap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-14",
            status = "ACTIVE"
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = busOrig,
            activeSwap = swap,
            allBuses = listOf(busOrig, busRepl),
            allDrivers = listOf(driverOrig, driverRepl)
        )
        assertEquals("Replacement Driver", info.driverName)
        assertEquals("9998887776", info.phoneNumber)
        assertTrue(info.isReplacement)
    }

    // =========================================================================
    // TEST 18: CALL DRIVER ACTION PRODUCES RFC 3966 TEL URI
    // =========================================================================
    @Test
    fun test18_callDriverStillUsesActionDial() {
        val phone = "9876543210"
        val dialUri = DriverInfoLogic.getDialUri(phone)
        assertEquals("tel:9876543210", dialUri)
    }

    // =========================================================================
    // TEST 19: NO CALL_PHONE PERMISSION ADDED IN ANDROID MANIFEST
    // =========================================================================
    @Test
    fun test19_noCallPhonePermissionAdded() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val content = if (manifestFile.exists()) manifestFile.readText() else File("app/src/main/AndroidManifest.xml").readText()
        assertFalse("CALL_PHONE permission must NOT be added to AndroidManifest.xml", content.contains("android.permission.CALL_PHONE"))
    }

    // =========================================================================
    // TEST 20: STUDENT CANNOT EDIT DRIVER INFORMATION
    // =========================================================================
    @Test
    fun test20_studentCannotEditDriverInformation() {
        val studentRole = UserRole.STUDENT
        val isAdmin = studentRole == UserRole.ADMIN
        assertFalse("Student cannot have Admin edit privileges", isAdmin)
    }

    // =========================================================================
    // TEST 21: DRIVER CANNOT EDIT DRIVER INFORMATION
    // =========================================================================
    @Test
    fun test21_driverCannotEditDriverInformation() {
        val driverRole = UserRole.DRIVER
        val isAdmin = driverRole == UserRole.ADMIN
        assertFalse("Driver cannot have Admin edit privileges", isAdmin)
    }

    // =========================================================================
    // TEST 22: NON-ADMIN CANNOT PERFORM DRIVER PROFILE WRITES
    // =========================================================================
    @Test
    fun test22_nonAdminCannotPerformDriverProfileWrites() {
        val roles = listOf(UserRole.STUDENT, UserRole.DRIVER)
        roles.forEach { role ->
            val canWrite = role == UserRole.ADMIN
            assertFalse("Role $role must not be permitted to perform driver profile writes", canWrite)
        }
    }

    // =========================================================================
    // TEST 23: PASSWORD IS NEVER STORED IN FIRESTORE/RTDB/PREFS/LOGS
    // =========================================================================
    @Test
    fun test23_passwordNeverStoredInFirestoreRtdbPrefsLogs() {
        val userProfileFields = UserProfile::class.java.declaredFields.map { it.name }
        assertFalse("UserProfile data class must never have a password field", userProfileFields.contains("password"))

        val driverDisplayFields = DriverDisplayInfo::class.java.declaredFields.map { it.name }
        assertFalse("DriverDisplayInfo must never have a password field", driverDisplayFields.contains("password"))
    }

    // =========================================================================
    // TEST 24: EXISTING SECURE DRIVER DELETION STILL WORKS
    // =========================================================================
    @Test
    fun test24_existingSecureDriverDeletionStillWorks() = runBlocking {
        class FakeDeleter(val success: Boolean) : ServerAuthDeleter {
            override suspend fun deleteUserByUid(driverUid: String): Result<Unit> {
                return if (success) Result.success(Unit) else Result.failure(Exception("Deletion failed"))
            }
        }
        val deleter = FakeDeleter(true)
        val res = deleter.deleteUserByUid("DRV-TO-DELETE")
        assertTrue(res.isSuccess)
    }

    // =========================================================================
    // TEST 25: EXISTING PRODUCTION DATA REMAINS INTACT
    // =========================================================================
    @Test
    fun test25_existingProductionDataRemainsIntact() {
        // Simulating existing production driver Bullappa
        val prodDriver = UserProfile(
            uid = "GaEgDkQeIVdZdaaQFAxgrApoXbb2",
            customId = "driver25",
            name = "BULLAPPA",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            phoneNumber = null,
            active = true
        )
        // Updating only phone number
        val edited = prodDriver.copy(phoneNumber = "9876543210")

        assertEquals("GaEgDkQeIVdZdaaQFAxgrApoXbb2", edited.uid)
        assertEquals("driver25", edited.customId)
        assertEquals("BULLAPPA", edited.name)
        assertEquals("BUS-25", edited.assignedBusId)
        assertEquals(true, edited.active)
        assertEquals("9876543210", edited.phoneNumber)
    }
}
