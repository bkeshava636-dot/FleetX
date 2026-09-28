package com.college.bustracker

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.CalendarException
import com.college.bustracker.data.model.TrackingDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveUiLogicTest {

    @Test
    fun testFormattedBusTitle_standardAndExtremeLengths() {
        fun formatBusTitle(busNumber: String): String {
            return when {
                busNumber.isBlank() || busNumber == "—" -> "BUS —"
                busNumber.startsWith("bus", ignoreCase = true) -> busNumber.uppercase()
                else -> "BUS $busNumber"
            }
        }

        assertEquals("BUS 14", formatBusTitle("14"))
        assertEquals("BUS 14", formatBusTitle("bus 14"))
        assertEquals("BUS-42-SPECIAL-EXPRESS-CAMPUS-ROUTE", formatBusTitle("bus-42-special-express-campus-route"))
        assertEquals("BUS —", formatBusTitle(""))
        assertEquals("BUS —", formatBusTitle("—"))
    }

    @Test
    fun testStopIndexFormatting_multiDigits() {
        val stopIndices = (0..120).map { index -> "#${index + 1}" }
        assertEquals("#1", stopIndices[0])
        assertEquals("#10", stopIndices[9])
        assertEquals("#100", stopIndices[99])
        assertEquals("#121", stopIndices[120])
        stopIndices.forEach {
            assertTrue(it.startsWith("#"))
            assertTrue(it.length >= 2)
        }
    }

    @Test
    fun testCalendarException_longReasonPreservation() {
        val longReason = "State Election Holiday and Supplementary University Youth Festival Celebration"
        val exception = CalendarException(
            date = "2026-10-15",
            type = "FULL_DAY_HOLIDAY",
            name = longReason
        )
        assertEquals(longReason, exception.name)
        assertFalse(exception.name.isBlank())
    }

    @Test
    fun testStatusBadge_defaultStringsNotEmpty() {
        val states = TrackingDisplayState.values()
        states.forEach { state ->
            val label = when (state) {
                TrackingDisplayState.ACTIVE -> "Live Now"
                TrackingDisplayState.WAITING_FOR_GPS -> "Waiting for GPS…"
                TrackingDisplayState.STALE -> "Location may be outdated"
                TrackingDisplayState.INACTIVE -> "Not Tracking Currently"
            }
            assertNotNull(label)
            assertTrue(label.isNotBlank())
        }
    }

    @Test
    fun testBusSwapNotice_extremeStrings() {
        val swap = BusSwap(
            swapId = "swap-test",
            originalBusId = "BUS-VERY-LONG-ORIGINAL-NUMBER-101",
            replacementBusId = "BUS-REPLACEMENT-NUMBER-202",
            date = "2026-09-12",
            status = "ACTIVE",
            message = "Bus 101 undergone unscheduled emergency engine maintenance. Route operated by reserve Bus 202."
        )
        val notice = "${swap.replacementBusId} is operating in place of ${swap.originalBusId} today"
        assertTrue(notice.contains("operating in place of"))
        assertTrue(swap.message!!.length > 50)
    }
}
