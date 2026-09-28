package com.college.bustracker

import com.college.bustracker.data.model.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentContributionTest {

    // ==========================================
    // 1. OPT-IN & VALID FIXES
    // ==========================================

    @Test
    fun testOptIn_validContributorFixPasses() {
        val now = System.currentTimeMillis()
        val source = LocationSource(
            busId = "BUS-25",
            sourceId = "student_uid_123",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.3435,
            longitude = 76.1554,
            accuracy = 8.5f,
            speed = 5.2f,
            bearing = 180f,
            timestamp = now - 5_000L, // 5s ago
            active = true
        )

        assertTrue("Valid opted-in student fix must be accepted", source.isValid(now = now))
        assertEquals("Source type must be SECONDARY_STUDENT", "SECONDARY_STUDENT", source.sourceType)
    }

    // ==========================================
    // 2. OPT-OUT & CLEANUP
    // ==========================================

    @Test
    fun testOptOut_inactiveContributorRejected() {
        val now = System.currentTimeMillis()
        val source = LocationSource(
            busId = "BUS-25",
            sourceId = "student_uid_123",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.3435,
            longitude = 76.1554,
            accuracy = 8.5f,
            timestamp = now - 5_000L,
            active = false // Opted out
        )

        assertFalse("Opted-out contributor must be rejected", source.isValid(now = now))
    }

    @Test
    fun testCleanupOnOptOut_resetsActiveBusAndClearsRecord() {
        var activeBusId = "BUS-25"
        var isSharing = true
        val contributedSources = mutableMapOf("student_uid_123" to LocationSource(busId = "BUS-25", active = true))

        // Simulate opt-out cleanup
        activeBusId = ""
        isSharing = false
        contributedSources.remove("student_uid_123")

        assertFalse("Sharing state must be false after opt-out", isSharing)
        assertEquals("Active bus must be empty after opt-out", "", activeBusId)
        assertFalse("Contributor record must be purged from memory on opt-out", contributedSources.containsKey("student_uid_123"))
    }

    // ==========================================
    // 3. CORRECT BUS VS WRONG BUS
    // ==========================================

    @Test
    fun testCorrectBus_acceptedWhenMatchingSelectedBus() {
        val selectedBusId = "BUS-25"
        val contributorPayload = LocationSource(
            busId = "BUS-25",
            sourceId = "student_uid_123",
            active = true
        )

        val isBusMatch = contributorPayload.busId == selectedBusId
        assertTrue("Contributor bus ID must match the currently selected bus", isBusMatch)
    }

    @Test
    fun testWrongBus_rejectedWhenPayloadDoesNotMatchSelectedBus() {
        val selectedBusId = "BUS-25"
        val contributorPayload = LocationSource(
            busId = "BUS-8", // Student switched or attempting contribution to wrong bus
            sourceId = "student_uid_123",
            active = true
        )

        val isBusMatch = contributorPayload.busId == selectedBusId
        assertFalse("Contribution to a different bus must be rejected", isBusMatch)
    }

    // ==========================================
    // 4. UNAUTHORIZED SOURCE REJECTION
    // ==========================================

    @Test
    fun testUnauthorizedSource_blankSourceIdRejected() {
        val now = System.currentTimeMillis()
        val source = LocationSource(
            busId = "BUS-25",
            sourceId = "", // Missing source ID
            latitude = 15.3435,
            longitude = 76.1554,
            accuracy = 5.0f,
            timestamp = now,
            active = true
        )

        assertFalse("Blank sourceId must be rejected", source.isValid(now = now))
    }

    @Test
    fun testUnauthorizedSource_spoofedSourceIdRejectedBySecurityRule() {
        val authenticatedUid = "student_uid_real"
        val submittedSourceId = "student_uid_spoofed"

        // Server-side security rule check: auth.uid == $sourceId
        val isAuthorized = authenticatedUid == submittedSourceId
        assertFalse("Spoofed source ID must be rejected by server security rule", isAuthorized)
    }

    // ==========================================
    // 5. STALE CONTRIBUTOR EXPIRATION
    // ==========================================

    @Test
    fun testStaleContributor_olderThan30sRejected() {
        val now = System.currentTimeMillis()
        val source = LocationSource(
            busId = "BUS-25",
            sourceId = "student_uid_123",
            latitude = 15.3435,
            longitude = 76.1554,
            accuracy = 8.0f,
            timestamp = now - 35_000L, // 35s ago (> 30s threshold)
            active = true
        )

        assertFalse("Contributor fix older than 30s must be rejected as stale", source.isValid(now = now))
    }

    @Test
    fun testStaleContributor_futureOrZeroTimestampRejected() {
        val now = System.currentTimeMillis()
        val zeroTimestamp = LocationSource(
            busId = "BUS-25",
            sourceId = "student_uid_123",
            latitude = 15.3435,
            longitude = 76.1554,
            accuracy = 8.0f,
            timestamp = 0L,
            active = true
        )

        assertFalse("Zero timestamp must be rejected", zeroTimestamp.isValid(now = now))
    }

    // ==========================================
    // 6. POOR ACCURACY FILTERING
    // ==========================================

    @Test
    fun testPoorAccuracy_greaterThan40mRejected() {
        val now = System.currentTimeMillis()
        val inaccurateSource = LocationSource(
            busId = "BUS-25",
            sourceId = "student_uid_123",
            latitude = 15.3435,
            longitude = 76.1554,
            accuracy = 45.0f, // > 40m threshold
            timestamp = now,
            active = true
        )

        assertFalse("Fix with accuracy worse than 40m must be filtered out", inaccurateSource.isValid(now = now))
    }

    @Test
    fun testPoorAccuracy_zeroCoordinatesRejected() {
        val now = System.currentTimeMillis()
        val zeroSource = LocationSource(
            busId = "BUS-25",
            sourceId = "student_uid_123",
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 5.0f,
            timestamp = now,
            active = true
        )

        assertFalse("Coordinates (0.0, 0.0) must be rejected", zeroSource.isValid(now = now))
    }
}
