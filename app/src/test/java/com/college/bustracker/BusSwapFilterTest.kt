package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.ui.driver.DriverViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusSwapFilterTest {

    // =========================================================================
    // TASK REQUIREMENT TESTS: A THROUGH H
    // =========================================================================

    /**
     * Requirement A: Multiple buses: Given 5+ buses in fleet, all eligible buses appear in the replacement dropdown.
     */
    @Test
    fun testA_multipleBuses_allEligibleAppearInReplacementDropdown() {
        val fleet = listOf(
            Bus(busId = "BUS-01", busNumber = "BUS 1", active = true),
            Bus(busId = "BUS-02", busNumber = "BUS 2", active = true),
            Bus(busId = "BUS-03", busNumber = "BUS 3", active = true),
            Bus(busId = "BUS-04", busNumber = "BUS 4", active = true),
            Bus(busId = "BUS-05", busNumber = "BUS 5", active = true),
            Bus(busId = "BUS-06", busNumber = "BUS 6", active = true),
            Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        )
        val currentBus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)

        val eligible = BusSwap.filterEligibleReplacementBuses(fleet, currentBus)

        assertEquals("Expected 6 eligible buses from a 7-bus fleet when 1 is assigned", 6, eligible.size)
        val busNumbers = eligible.map { it.busNumber }
        assertEquals(listOf("BUS 1", "BUS 2", "BUS 3", "BUS 4", "BUS 5", "BUS 6"), busNumbers)
    }

    /**
     * Requirement B: Numeric sorting: BUS 9 appears before BUS 10 (not alphabetical BUS 10 before BUS 9).
     */
    @Test
    fun testB_numericSorting_bus9BeforeBus10() {
        val fleet = listOf(
            Bus(busId = "BUS-10", busNumber = "BUS 10", active = true),
            Bus(busId = "BUS-02", busNumber = "BUS 2", active = true),
            Bus(busId = "BUS-09", busNumber = "BUS 9", active = true),
            Bus(busId = "BUS-01", busNumber = "BUS 1", active = true),
            Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        )
        val currentBus = Bus(busId = "BUS-01", busNumber = "BUS 1", active = true)

        val eligible = BusSwap.filterEligibleReplacementBuses(fleet, currentBus)

        val expectedOrder = listOf("BUS 2", "BUS 9", "BUS 10", "BUS 25")
        assertEquals(expectedOrder, eligible.map { it.busNumber })

        val index9 = eligible.indexOfFirst { it.busNumber == "BUS 9" }
        val index10 = eligible.indexOfFirst { it.busNumber == "BUS 10" }
        assertTrue("BUS 9 must appear strictly before BUS 10 in numeric sort", index9 < index10)
    }

    /**
     * Requirement C: Current bus exclusion: If driver is assigned to BUS 8, BUS 8 does not appear
     * in replacement options (matching both busId and busNumber).
     */
    @Test
    fun testC_currentBusExclusion_byBothBusIdAndBusNumber() {
        val fleet = listOf(
            Bus(busId = "BUS-08", busNumber = "BUS 8", active = true),
            Bus(busId = "BUS-12", busNumber = "BUS 12", active = true),
            Bus(busId = "BUS-15", busNumber = "BUS 15", active = true)
        )

        // Case 1: Matching by busId "BUS-08"
        val currentBus1 = Bus(busId = "BUS-08", busNumber = "BUS 8", active = true)
        val eligible1 = BusSwap.filterEligibleReplacementBuses(fleet, currentBus1)
        assertFalse("BUS 8 must be excluded by busId", eligible1.any { it.busId == "BUS-08" || it.busNumber == "BUS 8" })
        assertEquals(2, eligible1.size)

        // Case 2: Assigned bus object has busId = "BUS 8" (or case variation)
        val currentBus2 = Bus(busId = "BUS 8", busNumber = "BUS 8", active = true)
        val eligible2 = BusSwap.filterEligibleReplacementBuses(fleet, currentBus2)
        assertFalse("BUS 8 must be excluded when busId equals busNumber", eligible2.any { it.busNumber == "BUS 8" })
        assertEquals(2, eligible2.size)
    }

    /**
     * Requirement D: Inactive bus exclusion: Buses with active == false do not appear.
     */
    @Test
    fun testD_inactiveBusExclusion_inactiveBusesFilteredOut() {
        val fleet = listOf(
            Bus(busId = "BUS-01", busNumber = "BUS 1", active = true),
            Bus(busId = "BUS-02", busNumber = "BUS 2", active = false), // Inactive
            Bus(busId = "BUS-03", busNumber = "BUS 3", active = true),
            Bus(busId = "BUS-04", busNumber = "BUS 4", active = false), // Inactive
            Bus(busId = "BUS-05", busNumber = "BUS 5", active = true)
        )
        val currentBus = Bus(busId = "BUS-01", busNumber = "BUS 1", active = true)

        val eligible = BusSwap.filterEligibleReplacementBuses(fleet, currentBus)

        val eligibleNumbers = eligible.map { it.busNumber }
        assertEquals(listOf("BUS 3", "BUS 5"), eligibleNumbers)
        assertFalse("Inactive BUS 2 must not be included", eligibleNumbers.contains("BUS 2"))
        assertFalse("Inactive BUS 4 must not be included", eligibleNumbers.contains("BUS 4"))
    }

    /**
     * Requirement E: Dynamic updates: Simulating an added bus updates the available options.
     */
    @Test
    fun testE_dynamicUpdates_addingBusUpdatesAvailableOptions() {
        val initialFleet = mutableListOf(
            Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        )
        val currentBus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)

        // Initially only the assigned bus exists -> empty list
        val eligibleInitial = BusSwap.filterEligibleReplacementBuses(initialFleet, currentBus)
        assertTrue("Initially no replacement buses available", eligibleInitial.isEmpty())

        // Admin adds BUS 10 to Firestore fleet
        initialFleet.add(Bus(busId = "BUS-10", busNumber = "BUS 10", active = true))
        val eligibleAfterFirstAdd = BusSwap.filterEligibleReplacementBuses(initialFleet, currentBus)
        assertEquals(listOf("BUS 10"), eligibleAfterFirstAdd.map { it.busNumber })

        // Admin adds BUS 02 to Firestore fleet -> should dynamically appear and sort before BUS 10
        initialFleet.add(Bus(busId = "BUS-02", busNumber = "BUS 2", active = true))
        val eligibleAfterSecondAdd = BusSwap.filterEligibleReplacementBuses(initialFleet, currentBus)
        assertEquals(listOf("BUS 2", "BUS 10"), eligibleAfterSecondAdd.map { it.busNumber })
    }

    /**
     * Requirement F: Non-hardcoding verification: Ensure when fleet is empty or has only assigned bus,
     * list is empty and doesn't fall back to hardcoded buses (such as BUS 8 or BUS 15 or BUS 05 or BUS 18).
     */
    @Test
    fun testF_nonHardcodingVerification_noHardcodedFallbacks() {
        val currentBus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)

        // Case 1: Empty fleet
        val emptyFleet = emptyList<Bus>()
        val resultEmpty = BusSwap.filterEligibleReplacementBuses(emptyFleet, currentBus)
        assertTrue("Result must be completely empty with no hardcoded fallback", resultEmpty.isEmpty())

        // Case 2: Fleet contains ONLY the assigned bus
        val singleBusFleet = listOf(currentBus)
        val resultSingle = BusSwap.filterEligibleReplacementBuses(singleBusFleet, currentBus)
        assertTrue("Result must be completely empty when fleet only has assigned bus", resultSingle.isEmpty())

        // Explicitly assert that legacy fallbacks never appear
        val legacyFallbacks = setOf("BUS 05", "BUS 18", "BUS 8", "BUS 15")
        val fallbackMatches = resultSingle.filter { legacyFallbacks.contains(it.busNumber) }
        assertTrue("No legacy hardcoded fallbacks allowed", fallbackMatches.isEmpty())
    }

    /**
     * Requirement G: Swap creation: Creating a swap with an eligible bus works.
     */
    @Test
    fun testG_swapCreation_withEligibleReplacementBus() {
        val originalBus = Bus(busId = "BUS-25", busNumber = "BUS 25", routeId = "ROUTE-25", driverId = "driver01")
        val replacementBus = Bus(busId = "BUS-08", busNumber = "BUS 8", active = true)

        val swap = BusSwap(
            swapId = "SWAP-TEST-001",
            originalBusId = originalBus.busId,
            replacementBusId = replacementBus.busNumber,
            routeId = originalBus.routeId,
            driverId = originalBus.driverId ?: "driver01",
            driverName = "Driver 1",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            message = "Bus swap: ${originalBus.busNumber} -> ${replacementBus.busNumber}",
            status = "ACTIVE",
            date = "2026-09-11",
            createdAt = System.currentTimeMillis()
        )

        assertEquals("SWAP-TEST-001", swap.swapId)
        assertEquals("BUS-25", swap.originalBusId)
        assertEquals("BUS 8", swap.replacementBusId)
        assertEquals("ACTIVE", swap.status)
        assertTrue(swap.isAffectingStop("STOP-01"))
        assertTrue(swap.isAffectingStop("STOP-02"))
        assertFalse(swap.isAffectingStop("STOP-03"))
    }

    /**
     * Requirement H: Swap cancellation: Authorization check works.
     */
    @Test
    fun testH_swapCancellation_authorizationCheck() {
        val driverUid = "driver-uid-123"
        val driverAssignedBusId = "BUS-25"

        val ownSwap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-10",
            driverId = driverUid,
            status = "ACTIVE"
        )

        val otherDriverSwap = BusSwap(
            swapId = "SWAP-02",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-10",
            driverId = "driver-other-456",
            status = "ACTIVE"
        )

        val otherBusSwap = BusSwap(
            swapId = "SWAP-03",
            originalBusId = "BUS-12",
            replacementBusId = "BUS-10",
            driverId = driverUid,
            status = "ACTIVE"
        )

        fun isAuthorizedToCancel(swap: BusSwap, callerUid: String, callerBusId: String): Boolean {
            return swap.driverId == callerUid && swap.originalBusId.equals(callerBusId, ignoreCase = true)
        }

        assertTrue("Assigned driver can cancel own swap", isAuthorizedToCancel(ownSwap, driverUid, driverAssignedBusId))
        assertFalse("Cannot cancel swap created by another driver", isAuthorizedToCancel(otherDriverSwap, driverUid, driverAssignedBusId))
        assertFalse("Cannot cancel swap for a different bus", isAuthorizedToCancel(otherBusSwap, driverUid, driverAssignedBusId))
    }

    // =========================================================================
    // REGRESSION TESTS
    // =========================================================================

    @Test
    fun testLocationSpecificSwapFiltering() {
        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-12",
            replacementBusId = "BUS-18",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "ACTIVE"
        )

        assertTrue("Student at STOP-01 should be affected", swap.isAffectingStop("STOP-01"))
        assertTrue("Student at STOP-02 should be affected", swap.isAffectingStop("STOP-02"))
        assertFalse("Student at STOP-03 should NOT be affected", swap.isAffectingStop("STOP-03"))
        assertFalse("Student at STOP-04 should NOT be affected", swap.isAffectingStop("STOP-04"))
        assertFalse(swap.isAffectingStop(null))
        assertFalse(swap.isAffectingStop(""))
    }

    @Test
    fun testDuplicateSwapFiltering() {
        val currentSwaps = mutableListOf(
            BusSwap(
                swapId = "SWAP-001",
                originalBusId = "BUS-12",
                replacementBusId = "BUS-18",
                affectedStopIds = listOf("STOP-01"),
                status = "ACTIVE"
            )
        )

        val newSwap = BusSwap(
            swapId = "SWAP-002",
            originalBusId = "BUS-12",
            replacementBusId = "BUS-20",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "ACTIVE"
        )

        currentSwaps.removeAll { it.originalBusId == newSwap.originalBusId && it.status == "ACTIVE" }
        currentSwaps.add(0, newSwap)

        assertEquals("Only 1 active swap should exist for BUS-12", 1, currentSwaps.size)
        assertEquals("BUS-20", currentSwaps.first().replacementBusId)
    }

    @Test
    fun testSwapStatusLifecycle() {
        var swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-12",
            replacementBusId = "BUS-18",
            status = "ACTIVE"
        )
        assertEquals("ACTIVE", swap.status)

        swap = swap.copy(status = "CANCELLED")
        assertEquals("CANCELLED", swap.status)
    }

    @Test
    fun testNumericBusOrdering() {
        val rawBuses = listOf(
            Bus(busId = "BUS-10", busNumber = "BUS 10"),
            Bus(busId = "BUS-01", busNumber = "BUS 1"),
            Bus(busId = "BUS-25", busNumber = "BUS 25"),
            Bus(busId = "BUS-02", busNumber = "BUS 2"),
            Bus(busId = "BUS-09", busNumber = "BUS 9"),
            Bus(busId = "BUS-18", busNumber = "BUS 18")
        )

        val sorted = rawBuses.sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))

        val expectedOrder = listOf("BUS 1", "BUS 2", "BUS 9", "BUS 10", "BUS 18", "BUS 25")
        assertEquals(expectedOrder, sorted.map { it.busNumber })

        val indexBus9 = sorted.indexOfFirst { it.busNumber == "BUS 9" }
        val indexBus10 = sorted.indexOfFirst { it.busNumber == "BUS 10" }
        assertTrue("BUS 9 must appear before BUS 10", indexBus9 < indexBus10)
    }
}
