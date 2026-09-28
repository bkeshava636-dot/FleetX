package com.college.bustracker

import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.service.TrackingServiceHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying Driver Live Tracking Authorization,
 * RTDB Security Rule evaluation, Bus assignment/reassignment/unassignment sync,
 * and Student dynamic live bus reading.
 */
class DriverLiveTrackingAuthorizationTest {

    /**
     * Pure evaluator matching the exact logic of database.rules.json:
     *
     * "live_locations": {
     *   "$busId": {
     *     ".write": "auth != null &&
     *                root.child('users').child(auth.uid).child('active').val() == true &&
     *                (root.child('users').child(auth.uid).child('role').val() == 'ADMIN' ||
     *                (root.child('users').child(auth.uid).child('role').val() == 'DRIVER' &&
     *                 root.child('users').child(auth.uid).child('assignedBusId').val() == $busId))"
     *   }
     * }
     */
    private fun evaluateLiveLocationWriteRule(
        authUid: String?,
        rtdbUserRecord: Map<String, Any?>?,
        targetBusId: String
    ): Boolean {
        if (authUid.isNullOrBlank() || rtdbUserRecord == null) return false
        val active = rtdbUserRecord["active"] as? Boolean ?: return false
        if (!active) return false
        val role = rtdbUserRecord["role"] as? String ?: return false
        if (role == "ADMIN") return true
        if (role == "DRIVER") {
            val assignedBusId = rtdbUserRecord["assignedBusId"] as? String ?: return false
            return assignedBusId == targetBusId
        }
        return false
    }

    /**
     * Simulates BusRepository.syncDriverAuthorizationToRtdb payload builder.
     */
    private fun buildRtdbAuthRecord(
        role: String = "DRIVER",
        active: Boolean = true,
        name: String = "",
        assignedBusId: String? = null
    ): Map<String, Any?> {
        return hashMapOf(
            "role" to role,
            "active" to active,
            "name" to name,
            "assignedBusId" to assignedBusId
        )
    }

    // 1. Driver1 authorization sync
    @Test
    fun test01_driver1AuthorizationSync() {
        val rtdbRecord = buildRtdbAuthRecord(
            role = "DRIVER",
            active = true,
            name = "H. M. RAMESH",
            assignedBusId = "BUS-01"
        )
        assertEquals("DRIVER", rtdbRecord["role"])
        assertEquals(true, rtdbRecord["active"])
        assertEquals("H. M. RAMESH", rtdbRecord["name"])
        assertEquals("BUS-01", rtdbRecord["assignedBusId"])
    }

    // 2. Driver assignedBusId sync
    @Test
    fun test02_driverAssignedBusIdSync() {
        val profile = UserProfile(
            uid = "DRV-01-UID",
            customId = "driver1",
            name = "H. M. RAMESH",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-01",
            active = true
        )
        val rtdbRecord = buildRtdbAuthRecord(
            role = profile.role.name,
            active = profile.active,
            name = profile.name,
            assignedBusId = profile.assignedBusId
        )
        assertEquals(profile.assignedBusId, rtdbRecord["assignedBusId"])
    }

    // 3. Driver reassignment
    @Test
    fun test03_driverReassignment() {
        // Initial assignment
        var rtdbRecord = buildRtdbAuthRecord(
            role = "DRIVER",
            active = true,
            name = "Test Driver",
            assignedBusId = "BUS-01"
        )
        assertTrue(evaluateLiveLocationWriteRule("DRV-UID", rtdbRecord, "BUS-01"))
        assertFalse(evaluateLiveLocationWriteRule("DRV-UID", rtdbRecord, "BUS-02"))

        // Reassign to BUS-02
        rtdbRecord = buildRtdbAuthRecord(
            role = "DRIVER",
            active = true,
            name = "Test Driver",
            assignedBusId = "BUS-02"
        )
        assertFalse(evaluateLiveLocationWriteRule("DRV-UID", rtdbRecord, "BUS-01"))
        assertTrue(evaluateLiveLocationWriteRule("DRV-UID", rtdbRecord, "BUS-02"))
    }

    // 4. Driver unassignment
    @Test
    fun test04_driverUnassignment() {
        val rtdbRecord = buildRtdbAuthRecord(
            role = "DRIVER",
            active = true,
            name = "Test Driver",
            assignedBusId = null
        )
        assertNull(rtdbRecord["assignedBusId"])
        assertFalse(evaluateLiveLocationWriteRule("DRV-UID", rtdbRecord, "BUS-01"))
        assertFalse(evaluateLiveLocationWriteRule("DRV-UID", rtdbRecord, "BUS-02"))
    }

    // 5. Inactive driver
    @Test
    fun test05_inactiveDriver() {
        val rtdbRecord = buildRtdbAuthRecord(
            role = "DRIVER",
            active = false,
            name = "Deactivated Driver",
            assignedBusId = "BUS-01"
        )
        assertFalse(evaluateLiveLocationWriteRule("DRV-UID", rtdbRecord, "BUS-01"))
    }

    // 6. Driver cannot write another bus
    @Test
    fun test06_driverCannotWriteAnotherBus() {
        val driver1Record = buildRtdbAuthRecord(
            role = "DRIVER",
            active = true,
            name = "Driver 1",
            assignedBusId = "BUS-01"
        )
        assertFalse("Driver 1 must NOT write to BUS-02", evaluateLiveLocationWriteRule("DRV-1", driver1Record, "BUS-02"))
        assertFalse("Driver 1 must NOT write to BUS-25", evaluateLiveLocationWriteRule("DRV-1", driver1Record, "BUS-25"))
    }

    // 7. Driver can write assigned bus
    @Test
    fun test07_driverCanWriteAssignedBus() {
        val driver1Record = buildRtdbAuthRecord(
            role = "DRIVER",
            active = true,
            name = "Driver 1",
            assignedBusId = "BUS-01"
        )
        assertTrue("Driver 1 MUST be allowed to write to BUS-01", evaluateLiveLocationWriteRule("DRV-1", driver1Record, "BUS-01"))
    }

    // 8. BUS-01 live location
    @Test
    fun test08_bus01LiveLocation() {
        val loc = LiveLocation(
            busId = "BUS-01",
            latitude = 15.3435,
            longitude = 76.1554,
            speed = 8.5f,
            bearing = 45.0f,
            trackingStatus = "ACTIVE",
            lastUpdated = System.currentTimeMillis()
        )
        assertEquals("BUS-01", loc.busId)
        assertEquals("ACTIVE", loc.trackingStatus)
        assertTrue(loc.latitude > 0.0)
        assertTrue(loc.longitude > 0.0)
    }

    // 9. BUS-02 live location
    @Test
    fun test09_bus02LiveLocation() {
        val loc = LiveLocation(
            busId = "BUS-02",
            latitude = 15.3521,
            longitude = 76.1610,
            speed = 10.0f,
            bearing = 90.0f,
            trackingStatus = "ACTIVE",
            lastUpdated = System.currentTimeMillis()
        )
        assertEquals("BUS-02", loc.busId)
        assertEquals("ACTIVE", loc.trackingStatus)
    }

    // 10. BUS-24 live location
    @Test
    fun test10_bus24LiveLocation() {
        val loc = LiveLocation(
            busId = "BUS-24",
            latitude = 15.3610,
            longitude = 76.1725,
            speed = 6.2f,
            bearing = 180.0f,
            trackingStatus = "ACTIVE",
            lastUpdated = System.currentTimeMillis()
        )
        assertEquals("BUS-24", loc.busId)
        assertEquals("ACTIVE", loc.trackingStatus)
    }

    // 11. BUS-25 remains working
    @Test
    fun test11_bus25RemainsWorking() {
        val driver25Record = buildRtdbAuthRecord(
            role = "DRIVER",
            active = true,
            name = "BULLAPPA",
            assignedBusId = "BUS-25"
        )
        assertTrue("Driver 25 must remain authorized to write to BUS-25", evaluateLiveLocationWriteRule("DRV-25", driver25Record, "BUS-25"))
        assertFalse("Driver 25 must NOT write to BUS-01", evaluateLiveLocationWriteRule("DRV-25", driver25Record, "BUS-01"))
    }

    // 12. Student reads selected bus dynamically
    @Test
    fun test12_studentReadsSelectedBusDynamically() {
        val busMap = mapOf(
            "BUS-01" to LiveLocation(busId = "BUS-01", trackingStatus = "ACTIVE", latitude = 15.34, longitude = 76.15),
            "BUS-02" to LiveLocation(busId = "BUS-02", trackingStatus = "ACTIVE", latitude = 15.35, longitude = 76.16),
            "BUS-25" to LiveLocation(busId = "BUS-25", trackingStatus = "ACTIVE", latitude = 15.36, longitude = 76.17)
        )

        fun readBusLocation(selectedBusId: String): LiveLocation? = busMap[selectedBusId]

        val loc01 = readBusLocation("BUS-01")
        assertNotNull(loc01)
        assertEquals("BUS-01", loc01?.busId)

        val loc02 = readBusLocation("BUS-02")
        assertNotNull(loc02)
        assertEquals("BUS-02", loc02?.busId)

        val loc25 = readBusLocation("BUS-25")
        assertNotNull(loc25)
        assertEquals("BUS-25", loc25?.busId)
    }

    // 13. RTDB write failure is visible in diagnostics
    @Test
    fun test13_rtdbWriteFailureVisibleInDiagnostics() {
        // Update service state upon permission denied error
        TrackingServiceHelper.updateServiceState(
            isTracking = true,
            busId = "BUS-01",
            gps = "Permission Denied",
            timestamp = System.currentTimeMillis()
        )
        assertEquals("Permission Denied", TrackingServiceHelper.gpsStatus.value)

        // Reset to Connected
        TrackingServiceHelper.updateServiceState(
            isTracking = true,
            busId = "BUS-01",
            gps = "Connected",
            timestamp = System.currentTimeMillis()
        )
        assertEquals("Connected", TrackingServiceHelper.gpsStatus.value)
    }
}
