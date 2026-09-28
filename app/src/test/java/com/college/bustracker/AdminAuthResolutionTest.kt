package com.college.bustracker

import com.college.bustracker.data.repository.AdminAuthResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Admin Authentication Resolution, Driver preservation,
 * and Password Reset rules.
 */
class AdminAuthResolutionTest {

    // =========================================================================
    // ADMIN AUTH RESOLUTION TESTS
    // =========================================================================

    @Test
    fun test01_adminCustomIdResolvesToRealEmail() {
        val resolved = AdminAuthResolver.resolveAdminAuthEmail("admin26", "bkeshava636@gmail.com")
        assertEquals("bkeshava636@gmail.com", resolved)
    }

    @Test
    fun test02_adminEmailInputUsesDirectEmail() {
        val resolved = AdminAuthResolver.resolveAdminAuthEmail("bkeshava636@gmail.com", null)
        assertEquals("bkeshava636@gmail.com", resolved)
    }

    @Test
    fun test03_adminLegacyInternalIdFallback() {
        val resolved = AdminAuthResolver.resolveAdminAuthEmail("admin01", null)
        assertEquals("admin01@collegebus.internal", resolved)
    }

    @Test
    fun test04_adminTrimsAndLowercasesInput() {
        val resolved = AdminAuthResolver.resolveAdminAuthEmail("  ADMIN26  ", "BKeshava636@gmail.com")
        assertEquals("BKeshava636@gmail.com", resolved)
    }

    @Test
    fun test05_adminCustomIdWithBlankFirestoreEmailFallsBack() {
        val resolved = AdminAuthResolver.resolveAdminAuthEmail("admin99", "   ")
        assertEquals("admin99@collegebus.internal", resolved)
    }

    // =========================================================================
    // DRIVER PRESERVATION TESTS
    // =========================================================================

    @Test
    fun test06_driverLoginPreservesInternalDomain() {
        val resolved = AdminAuthResolver.resolveDriverEmail("driver25")
        assertEquals("driver25@collegebus.internal", resolved)
    }

    @Test
    fun test07_driverLoginWithCustomEmailAllowed() {
        val resolved = AdminAuthResolver.resolveDriverEmail("driver25@custom.com")
        assertEquals("driver25@custom.com", resolved)
    }

    @Test
    fun test08_driverLoginTrimsAndLowercases() {
        val resolved = AdminAuthResolver.resolveDriverEmail("  DRIVER25  ")
        assertEquals("driver25@collegebus.internal", resolved)
    }

    // =========================================================================
    // PROFILE VALIDATION TESTS
    // =========================================================================

    @Test
    fun test09_validateAdminRecord_activeAdminSucceeds() {
        val result = AdminAuthResolver.validateAdminRecord("ADMIN", true)
        assertTrue(result.isSuccess)
    }

    @Test
    fun test10_validateAdminRecord_deactivatedAdminFails() {
        val result = AdminAuthResolver.validateAdminRecord("ADMIN", false)
        assertTrue(result.isFailure)
        assertEquals("Account is deactivated. Contact Administrator.", result.exceptionOrNull()?.message)
    }

    @Test
    fun test11_validateAdminRecord_nonAdminRoleFails() {
        val result = AdminAuthResolver.validateAdminRecord("DRIVER", true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("ADMIN privileges") == true)
    }

    // =========================================================================
    // PASSWORD RESET ELIGIBILITY TESTS
    // =========================================================================

    @Test
    fun test12_passwordReset_eligibleForRealEmail() {
        assertTrue(AdminAuthResolver.isEligibleForPasswordReset("bkeshava636@gmail.com"))
        assertTrue(AdminAuthResolver.isEligibleForPasswordReset("admin@company.org"))
    }

    @Test
    fun test13_passwordReset_rejectedForInternalDomain() {
        assertFalse(AdminAuthResolver.isEligibleForPasswordReset("admin01@collegebus.internal"))
        assertFalse(AdminAuthResolver.isEligibleForPasswordReset("driver25@collegebus.internal"))
    }

    @Test
    fun test14_passwordReset_rejectedForBlankOrInvalidEmail() {
        assertFalse(AdminAuthResolver.isEligibleForPasswordReset(null))
        assertFalse(AdminAuthResolver.isEligibleForPasswordReset(""))
        assertFalse(AdminAuthResolver.isEligibleForPasswordReset("not-an-email"))
    }

    // =========================================================================
    // EMAIL MASKING TESTS
    // =========================================================================

    @Test
    fun test15_maskEmail_masksLocalPartSafely() {
        assertEquals("b***6@gmail.com", AdminAuthResolver.maskEmail("bkeshava636@gmail.com"))
        assertEquals("a*@gmail.com", AdminAuthResolver.maskEmail("ab@gmail.com"))
    }
}
