package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.ui.student.DriverDisplayInfo
import com.college.bustracker.ui.student.DriverInfoLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentDriverInfoTest {

    // =========================================================================
    // 1. SELECTED BUS WITH ASSIGNED DRIVER (NAME + PHONE)
    // =========================================================================

    @Test
    fun testSelectedBusWithAssignedDriverDisplaysDriverNameAndPhone() {
        val bus = Bus(busId = "BUS-25", busNumber = "BUS 25", driverId = "DRV-1", driverName = "Bullappa", active = true)
        val driver = UserProfile(
            uid = "DRV-1",
            email = "driver1@college.edu",
            name = "Bullappa",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            phoneNumber = "+91 9876543210",
            active = true
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = bus,
            activeSwap = null,
            allBuses = listOf(bus),
            allDrivers = listOf(driver)
        )

        assertEquals("BUS 25", info.busTitle)
        assertEquals("Bullappa", info.driverName)
        assertEquals("+91 9876543210", info.phoneNumber)
        assertTrue("Driver should be marked as assigned", info.isAssigned)
        assertFalse("Should not be marked as replacement when no swap", info.isReplacement)
    }

    // =========================================================================
    // 2. SELECTED BUS WITHOUT ASSIGNED DRIVER
    // =========================================================================

    @Test
    fun testSelectedBusWithoutAssignedDriverShowsNotAssigned() {
        val bus = Bus(busId = "BUS-99", busNumber = "BUS 99", driverId = null, driverName = null, active = true)

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = bus,
            activeSwap = null,
            allBuses = listOf(bus),
            allDrivers = emptyList()
        )

        assertEquals("BUS 99", info.busTitle)
        assertEquals("Driver not assigned", info.driverName)
        assertNull("Phone should be null for unassigned bus", info.phoneNumber)
        assertFalse("isAssigned should be false", info.isAssigned)
        assertFalse(info.isReplacement)
    }

    // =========================================================================
    // 3. ACTIVE BUS SWAP SHOWS REPLACEMENT BUS'S DRIVER
    // =========================================================================

    @Test
    fun testActiveBusSwapShowsReplacementBusDriverNotOriginalDriver() {
        val busOrig = Bus(busId = "BUS-25", busNumber = "BUS 25", driverId = "DRV-ORIG", driverName = "Original Driver", active = true)
        val busRepl = Bus(busId = "BUS-14", busNumber = "BUS 14", driverId = "DRV-REPL", driverName = "Ramesh Kumar", active = true)

        val driverOrig = UserProfile(
            uid = "DRV-ORIG",
            email = "orig@college.edu",
            name = "Original Driver",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            phoneNumber = "+91 1111111111",
            active = true
        )
        val driverRepl = UserProfile(
            uid = "DRV-REPL",
            email = "repl@college.edu",
            name = "Ramesh Kumar",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-14",
            phoneNumber = "+91 9998887776",
            active = true
        )

        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-14",
            routeId = "ROUTE-25",
            status = "ACTIVE"
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = busOrig,
            activeSwap = swap,
            allBuses = listOf(busOrig, busRepl),
            allDrivers = listOf(driverOrig, driverRepl)
        )

        assertEquals("BUS 14 (Replacement)", info.busTitle)
        assertEquals("Ramesh Kumar", info.driverName)
        assertEquals("+91 9998887776", info.phoneNumber)
        assertTrue("Driver should be marked assigned", info.isAssigned)
        assertTrue("Must be marked as replacement bus", info.isReplacement)
    }

    // =========================================================================
    // 4. BUS SWAP WITH NO REPLACEMENT BUS HANDLED GRACEFULLY
    // =========================================================================

    @Test
    fun testBusSwapWithMissingReplacementBusHandledGracefully() {
        val busOrig = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        val swap = BusSwap(
            swapId = "SWAP-002",
            originalBusId = "BUS-25",
            replacementBusId = "NONEXISTENT-BUS",
            status = "ACTIVE"
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = busOrig,
            activeSwap = swap,
            allBuses = listOf(busOrig),
            allDrivers = emptyList()
        )

        assertEquals("NONEXISTENT-BUS (Replacement)", info.busTitle)
        assertEquals("Driver not assigned", info.driverName)
        assertNull(info.phoneNumber)
        assertFalse(info.isAssigned)
        assertTrue(info.isReplacement)
    }

    // =========================================================================
    // 5. DRIVER WITH NO PHONE NUMBER -> PHONE NOT AVAILABLE
    // =========================================================================

    @Test
    fun testDriverWithNoPhoneNumberHasNullPhone() {
        val bus = Bus(busId = "BUS-25", busNumber = "BUS 25", driverId = "DRV-1", active = true)
        val driverNoPhone = UserProfile(
            uid = "DRV-1",
            name = "Suresh",
            role = UserRole.DRIVER,
            assignedBusId = "BUS-25",
            phoneNumber = null,
            active = true
        )

        val info = DriverInfoLogic.resolveDriver(
            selectedBus = bus,
            activeSwap = null,
            allBuses = listOf(bus),
            allDrivers = listOf(driverNoPhone)
        )

        assertEquals("Suresh", info.driverName)
        assertNull("Phone should be null when driver has no phone", info.phoneNumber)
        assertTrue(info.isAssigned)
    }

    // =========================================================================
    // 6. CALL DRIVER ACTION PRODUCES CORRECT TEL URI
    // =========================================================================

    @Test
    fun testCallDriverProducesCorrectTelUri() {
        val rawPhone = "+91 9876543210"
        val dialUri = DriverInfoLogic.getDialUri(rawPhone)

        assertEquals("tel:+91 9876543210", dialUri)
    }

    // =========================================================================
    // 7. STUDENT VIEW NEVER RECEIVES EMAIL, PASSWORD, OR UID
    // =========================================================================

    @Test
    fun testDriverDisplayInfoNeverExposesSensitiveAuthFields() {
        val fields = DriverDisplayInfo::class.java.declaredFields.map { it.name }

        assertFalse("Must never expose email in DriverDisplayInfo", fields.contains("email"))
        assertFalse("Must never expose password in DriverDisplayInfo", fields.contains("password"))
        assertFalse("Must never expose uid in DriverDisplayInfo", fields.contains("uid"))
        assertFalse("Must never expose token in DriverDisplayInfo", fields.contains("fcmToken"))
    }

    // =========================================================================
    // 8. DRIVER INFO UPDATES REACTIVELY ON BUS SELECTION CHANGE
    // =========================================================================

    @Test
    fun testDriverInfoUpdatesWhenBusSelectionChanges() {
        val bus1 = Bus(busId = "BUS-01", busNumber = "BUS 1", driverId = "D1", active = true)
        val bus2 = Bus(busId = "BUS-02", busNumber = "BUS 2", driverId = "D2", active = true)

        val driver1 = UserProfile(uid = "D1", name = "Driver One", role = UserRole.DRIVER, assignedBusId = "BUS-01", phoneNumber = "111", active = true)
        val driver2 = UserProfile(uid = "D2", name = "Driver Two", role = UserRole.DRIVER, assignedBusId = "BUS-02", phoneNumber = "222", active = true)

        val fleet = listOf(bus1, bus2)
        val drivers = listOf(driver1, driver2)

        // Select Bus 1
        val info1 = DriverInfoLogic.resolveDriver(bus1, null, fleet, drivers)
        assertEquals("BUS 1", info1.busTitle)
        assertEquals("Driver One", info1.driverName)
        assertEquals("111", info1.phoneNumber)

        // Switch selection to Bus 2
        val info2 = DriverInfoLogic.resolveDriver(bus2, null, fleet, drivers)
        assertEquals("BUS 2", info2.busTitle)
        assertEquals("Driver Two", info2.driverName)
        assertEquals("222", info2.phoneNumber)
    }
}
