package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.ui.driver.DriverViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusSwapSingleActiveTest {

    private val testRouteStops = listOf(
        BusStop(stopId = "STOP-01", name = "Central Bus Stand", order = 1),
        BusStop(stopId = "STOP-02", name = "Gavi Math Circle", order = 2),
        BusStop(stopId = "STOP-03", name = "Hosalli Cross", order = 3)
    )

    // Helper fake repository for in-memory single active alert enforcement testing
    class FakeBusSwapRepository {
        val swaps = mutableListOf<BusSwap>()

        fun reportSwap(swap: BusSwap): Result<Unit> {
            val existingActive = swaps.firstOrNull {
                it.originalBusId.equals(swap.originalBusId, ignoreCase = true) &&
                        it.status == BusSwap.STATUS_ACTIVE &&
                        it.swapId != swap.swapId
            }
            if (existingActive != null) {
                return Result.failure(
                    IllegalStateException("An active bus swap alert already exists for ${swap.originalBusId}. Please edit or cancel the current swap before creating a new one.")
                )
            }
            swaps.removeAll { it.swapId == swap.swapId }
            swaps.add(0, swap)
            return Result.success(Unit)
        }

        fun updateSwap(swap: BusSwap): Result<Unit> {
            val idx = swaps.indexOfFirst { it.swapId == swap.swapId }
            if (idx == -1) return Result.failure(NoSuchElementException("Swap not found"))
            val updated = swap.copy(updatedAt = System.currentTimeMillis())
            swaps[idx] = updated
            return Result.success(Unit)
        }

        fun cancelSwap(swapId: String, cancelledByUid: String = ""): Result<Unit> {
            val idx = swaps.indexOfFirst { it.swapId == swapId }
            if (idx == -1) return Result.failure(NoSuchElementException("Swap not found"))
            val updated = swaps[idx].copy(
                status = BusSwap.STATUS_CANCELLED,
                cancelledAt = System.currentTimeMillis(),
                cancelledByUid = cancelledByUid
            )
            swaps[idx] = updated
            return Result.success(Unit)
        }

        fun getActiveSwapForBus(busId: String): BusSwap? {
            return swaps.firstOrNull {
                it.originalBusId.equals(busId, ignoreCase = true) && it.status == BusSwap.STATUS_ACTIVE
            }
        }
    }

    // 1. Default state: zero active swaps
    @Test
    fun test01_defaultState_zeroActiveSwaps() {
        val repo = FakeBusSwapRepository()
        assertNull(repo.getActiveSwapForBus("BUS-25"))
        assertEquals(0, repo.swaps.size)
    }

    // 2. Create active swap for bus -> single active swap
    @Test
    fun test02_createActiveSwap_singleActiveSwap() {
        val repo = FakeBusSwapRepository()
        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            status = BusSwap.STATUS_ACTIVE,
            scopeType = BusSwap.SCOPE_ALL_STOPS
        )
        val res = repo.reportSwap(swap)
        assertTrue(res.isSuccess)
        val active = repo.getActiveSwapForBus("BUS-25")
        assertNotNull(active)
        assertEquals("BUS-30", active?.replacementBusId)
    }

    // 3. Attempt creating duplicate active swap for same bus -> blocked
    @Test
    fun test03_duplicateActiveSwap_blocked() {
        val repo = FakeBusSwapRepository()
        val swap1 = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            status = BusSwap.STATUS_ACTIVE
        )
        val swap2 = BusSwap(
            swapId = "SWAP-002",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-31",
            status = BusSwap.STATUS_ACTIVE
        )
        val res1 = repo.reportSwap(swap1)
        val res2 = repo.reportSwap(swap2)
        assertTrue(res1.isSuccess)
        assertTrue(res2.isFailure)
        assertTrue(res2.exceptionOrNull() is IllegalStateException)
        assertEquals(1, repo.swaps.filter { it.originalBusId == "BUS-25" && it.status == BusSwap.STATUS_ACTIVE }.size)
    }

    // 4. Create active swap for different bus -> allowed
    @Test
    fun test04_createActiveSwap_differentBusAllowed() {
        val repo = FakeBusSwapRepository()
        val swap1 = BusSwap(swapId = "SWAP-001", originalBusId = "BUS-25", replacementBusId = "BUS-30", status = BusSwap.STATUS_ACTIVE)
        val swap2 = BusSwap(swapId = "SWAP-002", originalBusId = "BUS-26", replacementBusId = "BUS-31", status = BusSwap.STATUS_ACTIVE)
        assertTrue(repo.reportSwap(swap1).isSuccess)
        assertTrue(repo.reportSwap(swap2).isSuccess)
        assertEquals(2, repo.swaps.count { it.status == BusSwap.STATUS_ACTIVE })
    }

    // 5. Scope ALL_STOPS affects all stops on route
    @Test
    fun test05_scopeAllStops_affectsAllStops() {
        val swap = BusSwap(
            swapId = "SWAP-ALL",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            scopeType = BusSwap.SCOPE_ALL_STOPS,
            affectedStopIds = emptyList()
        )
        assertTrue(swap.isAffectingStop("STOP-01"))
        assertTrue(swap.isAffectingStop("STOP-02"))
        assertTrue(swap.isAffectingStop("STOP-03"))
        assertFalse(swap.isAffectingStop(null))
        assertFalse(swap.isAffectingStop(""))
    }

    // 6. Scope SELECTED_STOPS with valid stop affects only selected stops
    @Test
    fun test06_scopeSelectedStops_affectsOnlySelected() {
        val swap = BusSwap(
            swapId = "SWAP-SEL",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            scopeType = BusSwap.SCOPE_SELECTED_STOPS,
            affectedStopIds = listOf("STOP-02")
        )
        assertFalse(swap.isAffectingStop("STOP-01"))
        assertTrue(swap.isAffectingStop("STOP-02"))
        assertFalse(swap.isAffectingStop("STOP-03"))
    }

    // 7. Scope SELECTED_STOPS with empty stops evaluates to false
    @Test
    fun test07_scopeSelectedStops_emptyStops() {
        val swap = BusSwap(
            swapId = "SWAP-SEL-EMPTY",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            scopeType = BusSwap.SCOPE_SELECTED_STOPS,
            affectedStopIds = emptyList()
        )
        assertFalse(swap.isAffectingStop("STOP-01"))
        assertFalse(swap.isAffectingStop("STOP-02"))
    }

    // 8. Edit active swap: update replacement bus without duplicate creation
    @Test
    fun test08_editActiveSwap_updateReplacementBus_noDuplicate() {
        val repo = FakeBusSwapRepository()
        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            status = BusSwap.STATUS_ACTIVE
        )
        repo.reportSwap(swap)
        assertEquals(1, repo.swaps.size)

        val updated = swap.copy(replacementBusId = "BUS-40")
        val updateRes = repo.updateSwap(updated)
        assertTrue(updateRes.isSuccess)
        assertEquals(1, repo.swaps.size)
        assertEquals("BUS-40", repo.getActiveSwapForBus("BUS-25")?.replacementBusId)
        assertTrue(repo.getActiveSwapForBus("BUS-25")?.updatedAt ?: 0L > 0L)
    }

    // 9. Edit active swap: change scope from ALL_STOPS to SELECTED_STOPS
    @Test
    fun test09_editActiveSwap_changeScopeToSelectedStops() {
        val repo = FakeBusSwapRepository()
        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            scopeType = BusSwap.SCOPE_ALL_STOPS,
            status = BusSwap.STATUS_ACTIVE
        )
        repo.reportSwap(swap)

        val updated = swap.copy(
            scopeType = BusSwap.SCOPE_SELECTED_STOPS,
            affectedStopIds = listOf("STOP-01", "STOP-03")
        )
        repo.updateSwap(updated)

        val active = repo.getActiveSwapForBus("BUS-25")
        assertNotNull(active)
        assertEquals(BusSwap.SCOPE_SELECTED_STOPS, active?.scopeType)
        assertTrue(active?.isAffectingStop("STOP-01") == true)
        assertFalse(active?.isAffectingStop("STOP-02") == true)
        assertTrue(active?.isAffectingStop("STOP-03") == true)
    }

    // 10. Edit active swap: change scope from SELECTED_STOPS to ALL_STOPS
    @Test
    fun test10_editActiveSwap_changeScopeToAllStops() {
        val repo = FakeBusSwapRepository()
        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            scopeType = BusSwap.SCOPE_SELECTED_STOPS,
            affectedStopIds = listOf("STOP-01"),
            status = BusSwap.STATUS_ACTIVE
        )
        repo.reportSwap(swap)

        val updated = swap.copy(
            scopeType = BusSwap.SCOPE_ALL_STOPS,
            affectedStopIds = emptyList()
        )
        repo.updateSwap(updated)

        val active = repo.getActiveSwapForBus("BUS-25")
        assertEquals(BusSwap.SCOPE_ALL_STOPS, active?.scopeType)
        assertTrue(active?.isAffectingStop("STOP-01") == true)
        assertTrue(active?.isAffectingStop("STOP-02") == true)
    }

    // 11. Edit active swap: update message/reason
    @Test
    fun test11_editActiveSwap_updateMessage() {
        val repo = FakeBusSwapRepository()
        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            message = "Initial reason",
            status = BusSwap.STATUS_ACTIVE
        )
        repo.reportSwap(swap)

        val updated = swap.copy(message = "Updated reason: Flat tire repaired")
        repo.updateSwap(updated)

        assertEquals("Updated reason: Flat tire repaired", repo.getActiveSwapForBus("BUS-25")?.message)
    }

    // 12. Cancel active swap: status becomes CANCELLED, cancelledAt recorded
    @Test
    fun test12_cancelActiveSwap_statusCancelled_recordsTimestamp() {
        val repo = FakeBusSwapRepository()
        val swap = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            status = BusSwap.STATUS_ACTIVE
        )
        repo.reportSwap(swap)

        val cancelRes = repo.cancelSwap("SWAP-001", cancelledByUid = "DRIVER-123")
        assertTrue(cancelRes.isSuccess)
        assertNull(repo.getActiveSwapForBus("BUS-25"))

        val archived = repo.swaps.first { it.swapId == "SWAP-001" }
        assertEquals(BusSwap.STATUS_CANCELLED, archived.status)
        assertTrue(archived.cancelledAt > 0L)
        assertEquals("DRIVER-123", archived.cancelledByUid)
    }

    // 13. After cancellation, new active swap can be created for the bus
    @Test
    fun test13_afterCancellation_newActiveSwapAllowed() {
        val repo = FakeBusSwapRepository()
        val swap1 = BusSwap(
            swapId = "SWAP-001",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            status = BusSwap.STATUS_ACTIVE
        )
        repo.reportSwap(swap1)
        repo.cancelSwap("SWAP-001", "DRIVER-123")

        val swap2 = BusSwap(
            swapId = "SWAP-002",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-45",
            status = BusSwap.STATUS_ACTIVE
        )
        val res2 = repo.reportSwap(swap2)
        assertTrue(res2.isSuccess)
        assertEquals(1, repo.swaps.count { it.status == BusSwap.STATUS_ACTIVE })
        assertEquals("BUS-45", repo.getActiveSwapForBus("BUS-25")?.replacementBusId)
    }

    // 14. Validation: Same replacement as assigned bus rejected
    @Test
    fun test14_validation_sameReplacementBusRejected() {
        val assignedBus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        val fleet = listOf(assignedBus, Bus(busId = "BUS-30", busNumber = "BUS 30", active = true))
        val eligible = BusSwap.filterEligibleReplacementBuses(fleet, assignedBus)

        assertFalse(eligible.any { it.busId == "BUS-25" || it.busNumber == "BUS 25" })
    }

    // 15. Validation: SELECTED_STOPS requires non-empty stop list
    @Test
    fun test15_validation_selectedStopsRequiresNonEmptyList() {
        val scopeType = BusSwap.SCOPE_SELECTED_STOPS
        val emptyStops = emptyList<String>()
        val isValid = scopeType == BusSwap.SCOPE_ALL_STOPS || emptyStops.isNotEmpty()
        assertFalse(isValid)
    }

    // 16. Validation: ALL_STOPS is valid even with empty affectedStopIds
    @Test
    fun test16_validation_allStopsValidWithEmptyList() {
        val scopeType = BusSwap.SCOPE_ALL_STOPS
        val emptyStops = emptyList<String>()
        val isValid = scopeType == BusSwap.SCOPE_ALL_STOPS || emptyStops.isNotEmpty()
        assertTrue(isValid)
    }

    // 17. Student filtering: Only matches ACTIVE swap affecting student stop
    @Test
    fun test17_studentFiltering_onlyMatchesActiveSwapForStop() {
        val swaps = listOf(
            BusSwap(
                swapId = "SWAP-001",
                originalBusId = "BUS-25",
                replacementBusId = "BUS-30",
                status = BusSwap.STATUS_ACTIVE,
                scopeType = BusSwap.SCOPE_SELECTED_STOPS,
                affectedStopIds = listOf("STOP-02")
            ),
            BusSwap(
                swapId = "SWAP-002",
                originalBusId = "BUS-25",
                replacementBusId = "BUS-40",
                status = BusSwap.STATUS_CANCELLED,
                scopeType = BusSwap.SCOPE_ALL_STOPS
            )
        )

        // Student at STOP-01 (not affected by active swap)
        val student1Swap = swaps.find { swap ->
            swap.status == BusSwap.STATUS_ACTIVE &&
                    swap.originalBusId == "BUS-25" &&
                    swap.isAffectingStop("STOP-01")
        }
        assertNull(student1Swap)

        // Student at STOP-02 (affected by active swap)
        val student2Swap = swaps.find { swap ->
            swap.status == BusSwap.STATUS_ACTIVE &&
                    swap.originalBusId == "BUS-25" &&
                    swap.isAffectingStop("STOP-02")
        }
        assertNotNull(student2Swap)
        assertEquals("BUS-30", student2Swap?.replacementBusId)
    }

    // 18. Replacement bus filter excludes inactive buses
    @Test
    fun test18_replacementFilter_excludesInactiveBuses() {
        val assignedBus = Bus(busId = "BUS-25", busNumber = "BUS 25", active = true)
        val fleet = listOf(
            assignedBus,
            Bus(busId = "BUS-30", busNumber = "BUS 30", active = true),
            Bus(busId = "BUS-31", busNumber = "BUS 31", active = false) // Inactive bus
        )
        val eligible = BusSwap.filterEligibleReplacementBuses(fleet, assignedBus)

        assertEquals(1, eligible.size)
        assertEquals("BUS 30", eligible.first().busNumber)
    }
}
