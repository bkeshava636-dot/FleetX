package com.college.bustracker

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.ui.admin.MapStopLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStopCreationTest {

    // =========================================================================
    // 1. ADDING STOPS & SEQUENCE GENERATION
    // =========================================================================

    @Test
    fun testAddingStopsProducesSequentialOrder() {
        var stops = emptyList<BusStop>()

        stops = MapStopLogic.addStop(stops, "Main Gate", 12.9716, 77.5946)
        stops = MapStopLogic.addStop(stops, "Library Circle", 12.9720, 77.5950)
        stops = MapStopLogic.addStop(stops, "Hostel Junction", 12.9730, 77.5960)

        assertEquals(3, stops.size)
        assertEquals(1, stops[0].order)
        assertEquals(2, stops[1].order)
        assertEquals(3, stops[2].order)
        assertEquals("Main Gate", stops[0].name)
        assertEquals("Library Circle", stops[1].name)
        assertEquals("Hostel Junction", stops[2].name)
    }

    // =========================================================================
    // 2. TAPPING MAP CREATES STOP WITH EXACT COORDINATES
    // =========================================================================

    @Test
    fun testTappingMapCreatesStopWithCorrectLatLng() {
        val tapLat = 13.0827
        val tapLng = 80.2707
        val initial = emptyList<BusStop>()

        val updated = MapStopLogic.addStop(initial, "Admin Block", tapLat, tapLng)

        assertEquals(1, updated.size)
        val stop = updated.first()
        assertEquals(tapLat, stop.latitude, 0.000001)
        assertEquals(tapLng, stop.longitude, 0.000001)
        assertEquals("Admin Block", stop.name)
        assertTrue(stop.stopId.startsWith("STOP-"))
        assertTrue(stop.active)
    }

    // =========================================================================
    // 3. EDIT STOP NAME PRESERVES COORDINATES, ID, AND SEQUENCE
    // =========================================================================

    @Test
    fun testEditStopNamePreservesCoordinatesAndSequence() {
        val s1 = BusStop(stopId = "STOP-001", name = "Old Name", latitude = 12.123, longitude = 77.456, order = 1, active = true)
        val s2 = BusStop(stopId = "STOP-002", name = "Second Stop", latitude = 12.789, longitude = 77.987, order = 2, active = true)
        val initial = listOf(s1, s2)

        val updated = MapStopLogic.updateStop(initial, stopId = "STOP-001", newName = "Renamed Station")

        assertEquals(2, updated.size)
        val modified = updated.first { it.stopId == "STOP-001" }
        assertEquals("Renamed Station", modified.name)
        assertEquals(12.123, modified.latitude, 0.000001)
        assertEquals(77.456, modified.longitude, 0.000001)
        assertEquals(1, modified.order)
        assertEquals("STOP-001", modified.stopId)

        // Untouched stop remains identical
        val untouched = updated.first { it.stopId == "STOP-002" }
        assertEquals(s2, untouched)
    }

    // =========================================================================
    // 4. DRAG MARKER UPDATES LAT/LNG, KEEPS NAME AND SEQUENCE
    // =========================================================================

    @Test
    fun testDragMarkerUpdatesLatLngKeepsNameAndSequence() {
        val s1 = BusStop(stopId = "STOP-101", name = "Science Block", latitude = 12.500, longitude = 77.500, order = 1, active = true)
        val s2 = BusStop(stopId = "STOP-102", name = "Sports Complex", latitude = 12.600, longitude = 77.600, order = 2, active = true)
        val initial = listOf(s1, s2)

        val newLat = 12.505
        val newLng = 77.510
        val updated = MapStopLogic.updateCoordinates(initial, stopId = "STOP-101", latitude = newLat, longitude = newLng)

        val modified = updated.first { it.stopId == "STOP-101" }
        assertEquals(newLat, modified.latitude, 0.000001)
        assertEquals(newLng, modified.longitude, 0.000001)
        assertEquals("Science Block", modified.name)
        assertEquals(1, modified.order)
        assertEquals("STOP-101", modified.stopId)
    }

    // =========================================================================
    // 5. DELETING STOP RENUMBERS REMAINING STOPS SEQUENTIALLY
    // =========================================================================

    @Test
    fun testDeletingStopRenumbersRemainingStops() {
        val s1 = BusStop(stopId = "S1", name = "Stop 1", latitude = 12.1, longitude = 77.1, order = 1)
        val s2 = BusStop(stopId = "S2", name = "Stop 2", latitude = 12.2, longitude = 77.2, order = 2)
        val s3 = BusStop(stopId = "S3", name = "Stop 3", latitude = 12.3, longitude = 77.3, order = 3)
        val s4 = BusStop(stopId = "S4", name = "Stop 4", latitude = 12.4, longitude = 77.4, order = 4)
        val initial = listOf(s1, s2, s3, s4)

        val afterDelete = MapStopLogic.deleteStop(initial, "S2")

        assertEquals(3, afterDelete.size)
        assertEquals(listOf("S1", "S3", "S4"), afterDelete.map { it.stopId })
        assertEquals(listOf(1, 2, 3), afterDelete.map { it.order })
        assertEquals("Stop 1", afterDelete[0].name)
        assertEquals("Stop 3", afterDelete[1].name)
        assertEquals("Stop 4", afterDelete[2].name)
    }

    // =========================================================================
    // 6. REORDERING STOPS UPDATES SEQUENCE CORRECTLY
    // =========================================================================

    @Test
    fun testReorderingStopsUpdatesSequenceCorrectly() {
        val s1 = BusStop(stopId = "A", name = "Alpha", latitude = 12.0, longitude = 77.0, order = 1)
        val s2 = BusStop(stopId = "B", name = "Beta", latitude = 12.1, longitude = 77.1, order = 2)
        val s3 = BusStop(stopId = "C", name = "Gamma", latitude = 12.2, longitude = 77.2, order = 3)
        val initial = listOf(s1, s2, s3)

        val reordered = MapStopLogic.moveStop(initial, fromIndex = 2, toIndex = 0)

        assertEquals(3, reordered.size)
        assertEquals(listOf("C", "A", "B"), reordered.map { it.stopId })
        assertEquals(listOf(1, 2, 3), reordered.map { it.order })
    }

    // =========================================================================
    // 7. EMPTY STOP LIST VALIDATION (CANNOT SAVE EMPTY)
    // =========================================================================

    @Test
    fun testEmptyStopListValidation() {
        val emptyStops = emptyList<BusStop>()
        val result = MapStopLogic.validateForSave(emptyStops)

        assertTrue("Empty stops list must fail validation", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty", ignoreCase = true) == true)
    }

    // =========================================================================
    // 8. SINGLE STOP VALIDATION (MINIMUM 2 STOPS REQUIRED)
    // =========================================================================

    @Test
    fun testSingleStopValidation() {
        val singleStop = listOf(
            BusStop(stopId = "S1", name = "Campus Gate", latitude = 12.9, longitude = 77.5, order = 1)
        )
        val result = MapStopLogic.validateForSave(singleStop)

        assertTrue("Single stop list must fail validation (minimum 2 stops)", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("at least 2 stops", ignoreCase = true) == true)
    }

    // =========================================================================
    // 9. ROUTE WITH STOPS SAVED PRODUCES VALID BUSSTOP OBJECTS
    // =========================================================================

    @Test
    fun testRouteWithValidStopsPassesValidation() {
        val validStops = listOf(
            BusStop(stopId = "S1", name = "Stop 1", latitude = 12.91, longitude = 77.51, order = 1, active = true),
            BusStop(stopId = "S2", name = "Stop 2", latitude = 12.92, longitude = 77.52, order = 2, active = true),
            BusStop(stopId = "S3", name = "Stop 3", latitude = 12.93, longitude = 77.53, order = 3, active = true)
        )
        val result = MapStopLogic.validateForSave(validStops)

        assertTrue("Valid stops must pass validation", result.isSuccess)
    }

    @Test
    fun testInvalidStopNameOrCoordinatesFailsValidation() {
        val blankNameStops = listOf(
            BusStop(stopId = "S1", name = "Stop 1", latitude = 12.91, longitude = 77.51, order = 1),
            BusStop(stopId = "S2", name = "   ", latitude = 12.92, longitude = 77.52, order = 2)
        )
        assertTrue(MapStopLogic.validateForSave(blankNameStops).isFailure)

        val invalidCoords = listOf(
            BusStop(stopId = "S1", name = "Stop 1", latitude = 12.91, longitude = 77.51, order = 1),
            BusStop(stopId = "S2", name = "Stop 2", latitude = 95.0, longitude = 77.52, order = 2)
        )
        assertTrue(MapStopLogic.validateForSave(invalidCoords).isFailure)
    }

    // =========================================================================
    // 10. EXISTING STOP PRESERVATION
    // =========================================================================

    @Test
    fun testExistingStopPreservationWhenAppendingNewStops() {
        val existingProductionStop = BusStop(
            stopId = "STOP-15843",
            name = "Hebbal Flyover",
            latitude = 13.0358,
            longitude = 77.5970,
            order = 1,
            active = true
        )
        val initialRouteStops = listOf(existingProductionStop)

        val updatedStops = MapStopLogic.addStop(
            existingStops = initialRouteStops,
            name = "Yelahanka Old Town",
            latitude = 13.1008,
            longitude = 77.5963
        )

        assertEquals(2, updatedStops.size)
        assertEquals("STOP-15843", updatedStops[0].stopId)
        assertEquals("Hebbal Flyover", updatedStops[0].name)
        assertEquals(13.0358, updatedStops[0].latitude, 0.000001)
        assertEquals(77.5970, updatedStops[0].longitude, 0.000001)
        assertEquals(1, updatedStops[0].order)

        assertEquals(2, updatedStops[1].order)
        assertEquals("Yelahanka Old Town", updatedStops[1].name)
        assertTrue(updatedStops[1].stopId.startsWith("STOP-"))
        assertNotEquals("STOP-15843", updatedStops[1].stopId)
    }
}
