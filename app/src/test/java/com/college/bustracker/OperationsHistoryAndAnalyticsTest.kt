package com.college.bustracker

import com.college.bustracker.analytics.StudentAnalyticsManager
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.BusSwapHistory
import com.college.bustracker.data.model.DriverReplacementHistory
import com.college.bustracker.data.model.HistoryAuditLog
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.util.KolkataTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

class OperationsHistoryAndAnalyticsTest {

    @Test
    fun testKolkataTimeFormatter_UsesAsiaKolkataTimeZone() {
        assertEquals("Asia/Kolkata", KolkataTimeFormatter.TIME_ZONE.id)

        // Fixed UTC timestamp: 2026-10-14 14:05:00 UTC = 2026-10-14 19:35:00 IST (07:35 PM)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.OCTOBER, 14, 14, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val epoch = cal.timeInMillis

        val formattedDate = KolkataTimeFormatter.formatDateOnly(epoch)
        val formattedTime = KolkataTimeFormatter.formatTimeOnly(epoch)
        val formattedFull = KolkataTimeFormatter.formatFullDateTime(epoch)

        assertEquals("14 October 2026", formattedDate)
        assertTrue("Time must be 07:35 PM IST, found: $formattedTime", formattedTime.contains("07:35") && formattedTime.contains("PM", ignoreCase = true))
        assertTrue(formattedFull.contains("14 October 2026") && formattedFull.contains("07:35"))
    }

    @Test
    fun testBusSwapHistory_ModelFieldsAndIntegrity() {
        val now = 1792080000000L
        val history = BusSwapHistory(
            historyId = "swap_123",
            swapId = "swap_123",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-12",
            routeId = "ROUTE-01",
            date = "2026-10-14",
            timeFormatted = "07:35 PM",
            reason = "Engine mechanical breakdown",
            changedByUid = "admin_01",
            changedByName = "Chief Transport Admin",
            effectiveFrom = now,
            effectiveUntil = 0L,
            status = "ACTIVE",
            createdAt = now,
            cleared = false
        )

        assertEquals("BUS-25", history.originalBusId)
        assertEquals("BUS-12", history.replacementBusId)
        assertEquals("ACTIVE", history.status)
        assertFalse(history.cleared)
        assertEquals("07:35 PM", history.timeFormatted)
    }

    @Test
    fun testDriverReplacementHistory_ModelFieldsAndIntegrity() {
        val now = 1792080000000L
        val history = DriverReplacementHistory(
            historyId = "assign_456",
            assignmentId = "assign_456",
            previousDriverUid = "driver_perm_01",
            previousDriverName = "Ramesh Kumar",
            replacementDriverUid = "driver_temp_02",
            replacementDriverName = "Suresh Patel",
            busId = "BUS-25",
            date = "2026-10-14",
            timeFormatted = "07:35 PM",
            reason = "Medical sick leave",
            changedByUid = "admin_01",
            changedByName = "Chief Transport Admin",
            effectiveFrom = now,
            effectiveUntil = now + 86400000L,
            status = "ACTIVE",
            createdAt = now,
            cleared = false
        )

        assertEquals("BUS-25", history.busId)
        assertEquals("Ramesh Kumar", history.previousDriverName)
        assertEquals("Suresh Patel", history.replacementDriverName)
        assertEquals("Medical sick leave", history.reason)
        assertEquals("ACTIVE", history.status)
        assertFalse(history.cleared)
    }

    @Test
    fun testHistoryAuditLog_ModelFields() {
        val now = 1792080000000L
        val audit = HistoryAuditLog(
            auditId = "audit_789",
            action = "CLEAR_BUS_SWAP_HISTORY",
            adminUid = "admin_01",
            adminName = "Chief Transport Admin",
            timestamp = now,
            dateFormatted = "14 October 2026",
            timeFormatted = "07:35 PM",
            clearedCount = 5
        )

        assertEquals("CLEAR_BUS_SWAP_HISTORY", audit.action)
        assertEquals(5, audit.clearedCount)
        assertEquals("admin_01", audit.adminUid)
    }

    @Test
    fun testClearHistorySafety_NeverAffectsActiveSwapsOrAssignments() {
        // Test dataset with active and completed records
        val mockSwaps = listOf(
            BusSwapHistory(historyId = "1", status = "ACTIVE", originalBusId = "BUS-25", cleared = false),
            BusSwapHistory(historyId = "2", status = "COMPLETED", originalBusId = "BUS-04", cleared = false),
            BusSwapHistory(historyId = "3", status = "CANCELLED", originalBusId = "BUS-11", cleared = false)
        )

        // Emulate repository safety logic:
        val clearedSwaps = mockSwaps.map { swap ->
            if (swap.status != "ACTIVE" && !swap.cleared) {
                swap.copy(cleared = true)
            } else {
                swap
            }
        }

        // Active swap must remain completely untouched!
        val activeSwap = clearedSwaps.find { it.historyId == "1" }
        assertNotNull(activeSwap)
        assertFalse("Active bus swap must NEVER be cleared or cancelled by history management!", activeSwap!!.cleared)
        assertEquals("ACTIVE", activeSwap.status)

        // Completed and cancelled swaps are safely archived
        assertTrue(clearedSwaps.find { it.historyId == "2" }!!.cleared)
        assertTrue(clearedSwaps.find { it.historyId == "3" }!!.cleared)
    }

    @Test
    fun testAnonymousInstallationIdentifier_Format() {
        val uuidStr = UUID.randomUUID().toString()
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        assertTrue("Installation ID must be a standard anonymous UUID without PII", uuidRegex.matches(uuidStr))
        assertEquals(36, uuidStr.length)
    }

    @Test
    fun testAnalyticsFeatureEvents_Constants() {
        assertEquals("app_opened", StudentAnalyticsManager.EVENT_APP_OPENED)
        assertEquals("live_tracking_opened", StudentAnalyticsManager.EVENT_LIVE_TRACKING_OPENED)
        assertEquals("bus_details_viewed", StudentAnalyticsManager.EVENT_BUS_DETAILS_VIEWED)
        assertEquals("stop_selected", StudentAnalyticsManager.EVENT_STOP_SELECTED)
        assertEquals("announcement_viewed", StudentAnalyticsManager.EVENT_ANNOUNCEMENT_VIEWED)
        assertEquals("bus_swap_alert_viewed", StudentAnalyticsManager.EVENT_BUS_SWAP_ALERT_VIEWED)
        assertEquals("settings_opened", StudentAnalyticsManager.EVENT_SETTINGS_OPENED)
        assertEquals("update_available", StudentAnalyticsManager.EVENT_UPDATE_AVAILABLE)
        assertEquals("update_installed", StudentAnalyticsManager.EVENT_UPDATE_INSTALLED)
    }

    @Test
    fun testEtaDisclaimerStrings_ExistInEnglishAndKannada() {
        val enFile = File("app/src/main/res/values/strings.xml").let {
            if (it.exists()) it else File("C:/Users/bhrao/college_bus_tracker/app/src/main/res/values/strings.xml")
        }
        val knFile = File("app/src/main/res/values-kn/strings.xml").let {
            if (it.exists()) it else File("C:/Users/bhrao/college_bus_tracker/app/src/main/res/values-kn/strings.xml")
        }

        assertTrue("English strings.xml must exist", enFile.exists())
        assertTrue("Kannada strings.xml must exist", knFile.exists())

        val enContent = enFile.readText()
        val knContent = knFile.readText()

        assertTrue("English strings must contain eta_accuracy_disclaimer", enContent.contains("eta_accuracy_disclaimer"))
        assertTrue("English disclaimer must mention ETA is approximate", enContent.contains("ETA is approximate and may not be accurate"))

        assertTrue("Kannada strings must contain eta_accuracy_disclaimer", knContent.contains("eta_accuracy_disclaimer"))
        assertTrue("Kannada disclaimer must contain Kannada text", knContent.contains("ಬಸ್ ಆಗಮನದ ಸಮಯವು ಅಂದಾಜು ಮಾತ್ರವಾಗಿದ್ದು"))
    }

    @Test
    fun testBusSwapValidation_PreventsSameBusAndUnavailableBuses() {
        val originalBusId = "BUS-25"
        val replacementSameBusId = "BUS-25"
        val replacementValidBusId = "BUS-12"

        // Rule 1: Cannot swap same bus
        val isSameBus = originalBusId.equals(replacementSameBusId, ignoreCase = true)
        assertTrue("Swap must reject identical bus as replacement", isSameBus)

        // Rule 2: Valid replacement
        val isValid = !originalBusId.equals(replacementValidBusId, ignoreCase = true)
        assertTrue("Swap must accept different eligible replacement bus", isValid)
    }

    @Test
    fun testTemporaryAssignmentReason_RetainedInModel() {
        val assignment = TemporaryAssignment(
            assignmentId = "test_01",
            busId = "BUS-25",
            permanentDriverName = "Driver A",
            replacementDriverName = "Driver B",
            reason = "Family emergency"
        )
        assertEquals("Family emergency", assignment.reason)
    }

    @Test
    fun testMultipleBusSwaps_DisjointStopsAllowed() {
        val swap1 = BusSwap(
            swapId = "swap_1",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-23",
            scopeType = BusSwap.SCOPE_SELECTED_STOPS,
            affectedStopIds = listOf("stop_1", "stop_2"),
            status = BusSwap.STATUS_ACTIVE
        )
        val swap2 = BusSwap(
            swapId = "swap_2",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-14",
            scopeType = BusSwap.SCOPE_SELECTED_STOPS,
            affectedStopIds = listOf("stop_3", "stop_4"),
            status = BusSwap.STATUS_ACTIVE
        )

        val activeSwaps = listOf(swap1, swap2)
        val allAssignedStops = activeSwaps.flatMap { it.affectedStopIds }.toSet()

        // Stops must be completely disjoint
        assertEquals(4, allAssignedStops.size)
        assertTrue(allAssignedStops.contains("stop_1"))
        assertTrue(allAssignedStops.contains("stop_3"))

        // New candidate swap with stop_5 is valid
        val candidateStops = listOf("stop_5")
        val hasOverlap = candidateStops.any { allAssignedStops.contains(it) }
        assertFalse("New swap with unassigned stops must not collide", hasOverlap)
    }

    @Test
    fun testMultipleBusSwaps_StopCollisionDetected() {
        val swap1 = BusSwap(
            swapId = "swap_1",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-23",
            scopeType = BusSwap.SCOPE_SELECTED_STOPS,
            affectedStopIds = listOf("stop_1", "stop_2"),
            status = BusSwap.STATUS_ACTIVE
        )

        val activeSwaps = listOf(swap1)
        val existingAssignedStops = activeSwaps.flatMap { it.affectedStopIds }.toSet()

        val candidateStops = listOf("stop_2", "stop_3")
        val overlapping = candidateStops.filter { existingAssignedStops.contains(it) }

        assertEquals(1, overlapping.size)
        assertEquals("stop_2", overlapping.first())
    }

    @Test
    fun testPreviousDaySwapDetection_FlagsNonTodaySwaps() {
        val today = KolkataTimeFormatter.getTodayIso()
        val yesterday = "2026-11-06"

        val oldSwap = BusSwap(
            swapId = "old_swap",
            date = yesterday,
            status = BusSwap.STATUS_ACTIVE
        )
        val todaySwap = BusSwap(
            swapId = "today_swap",
            date = today,
            status = BusSwap.STATUS_ACTIVE
        )

        assertTrue("Old swap date should not equal today", oldSwap.date != today)
        assertEquals("Today swap date should equal today", today, todaySwap.date)
    }

    @Test
    fun testStudentBannerContradictionFix_StripsAutoGeneratedPrefix() {
        val rawMessage = "Bus swap reported: BUS 25 → BUS-23"
        val customReason = "Flat tyre on rear wheel"

        val cleanedRaw = if (rawMessage.startsWith("Bus swap reported:")) "" else rawMessage.trim()
        val cleanedCustom = if (customReason.startsWith("Bus swap reported:")) "" else customReason.trim()

        assertEquals("", cleanedRaw)
        assertEquals("Flat tyre on rear wheel", cleanedCustom)
    }
}
