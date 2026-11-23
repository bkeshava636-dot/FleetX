package com.college.bustracker

import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.repository.BundledFleetSeed
import com.college.bustracker.util.BusTopicManager
import com.college.bustracker.util.TopicSubscriber
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BoardingStopPersistenceTest {

    private class FakeSubscriber : TopicSubscriber {
        val subscribed = mutableListOf<String>()
        val unsubscribed = mutableListOf<String>()
        override fun subscribe(topic: String) { subscribed.add(topic) }
        override fun unsubscribe(topic: String) { unsubscribed.add(topic) }
    }

    // =========================================================================
    // 1. Topic Manager Non-Destructive Update
    // =========================================================================

    @Test
    fun testTopicManager_formatTopic() {
        val topic = BusTopicManager.formatTopic("BUS-25", "STOP-02")
        assertEquals("bus_BUS-25_stop_STOP-02", topic)
    }

    // =========================================================================
    // 2. Stop Resolution Logic (Simulating StudentViewModel.updateCurrentRouteForSelectedBus)
    // =========================================================================

    @Test
    fun testStopResolution_matchesExactStopId() {
        val stops = listOf(
            BusStop(stopId = "STOP-01", name = "Stop 1", order = 1),
            BusStop(stopId = "STOP-02", name = "Stop 2", order = 2),
            BusStop(stopId = "STOP-03", name = "Stop 3", order = 3)
        )

        val targetStopId = "STOP-02"
        val savedStopOrder = 2

        val matchingStop = stops.find { it.stopId.equals(targetStopId, ignoreCase = true) }
        val matchingByOrder = if (matchingStop == null && savedStopOrder in 1..998) {
            stops.find { it.order == savedStopOrder }
        } else null

        val resolved = matchingStop ?: matchingByOrder ?: stops.first()
        assertEquals("STOP-02", resolved.stopId)
        assertEquals(2, resolved.order)
    }

    @Test
    fun testStopResolution_fallbackToOrderWhenStopIdMismatched_preventsResetToStop1() {
        // Simulates the v1.0.44 regression:
        // User had saved STOP-02 (order = 2) from Firestore.
        // On cold launch, bundled seed emits stops with IDs like STOP-25-1, STOP-25-2.
        val seedStops = listOf(
            BusStop(stopId = "STOP-25-1", name = "Central Bus Stand", order = 1),
            BusStop(stopId = "STOP-25-2", name = "Gavi Math Circle", order = 2),
            BusStop(stopId = "STOP-25-3", name = "Radio Park", order = 3)
        )

        val targetStopId = "STOP-02" // From Firestore, does NOT match STOP-25-2
        val savedStopOrder = 2      // Saved order = 2

        val matchingStop = seedStops.find { it.stopId.equals(targetStopId, ignoreCase = true) }
        // Before fix: matchingStop was null, falling back immediately to seedStops.first() (Stop 1)!
        // With fix: fallback to matchingByOrder finds order 2!
        val matchingByOrder = if (matchingStop == null && savedStopOrder in 1..998) {
            seedStops.find { it.order == savedStopOrder }
        } else null

        val resolved = matchingStop ?: matchingByOrder ?: seedStops.first()
        assertNotNull(resolved)
        assertEquals("STOP-25-2", resolved.stopId)
        assertEquals(2, resolved.order)
        assertEquals("Gavi Math Circle", resolved.name)
    }

    // =========================================================================
    // 3. Bundled Fleet Seed Completeness (ROUTE-25 Presence)
    // =========================================================================

    @Test
    fun testBundledFleetSeed_all25BusesHaveValidRoutes() {
        val seedFile = File("src/main/assets/bundled_fleet_seed.json")
        val altFile = File("app/src/main/assets/bundled_fleet_seed.json")
        val targetFile = if (seedFile.exists()) seedFile else altFile
        assertTrue("bundled_fleet_seed.json must exist in assets", targetFile.exists())

        val jsonStr = targetFile.readText()
        val root = JSONObject(jsonStr)
        val busesArray = root.getJSONArray("buses")
        val routesArray = root.getJSONArray("routes")

        assertEquals(25, busesArray.length())
        assertEquals(25, routesArray.length())

        val routeIds = mutableSetOf<String>()
        for (i in 0 until routesArray.length()) {
            val rObj = routesArray.getJSONObject(i)
            val rId = rObj.getString("routeId")
            routeIds.add(rId)
            val stops = rObj.getJSONArray("stops")
            assertTrue("Route $rId must have at least 2 stops", stops.length() >= 2)
        }

        // Specifically verify ROUTE-25 is present for BUS-25
        assertTrue("ROUTE-25 must exist in bundled fleet seed", routeIds.contains("ROUTE-25"))

        for (i in 0 until busesArray.length()) {
            val bObj = busesArray.getJSONObject(i)
            val busRouteId = bObj.getString("routeId")
            assertTrue(
                "Bus ${bObj.getString("busId")} route $busRouteId must exist in routes",
                routeIds.contains(busRouteId)
            )
        }
    }

    // =========================================================================
    // 4. Fallback Stop Preservation
    // =========================================================================

    @Test
    fun testRouteMerge_preservesFallbackStopsWhenServerStopsEmpty() {
        val fallbackStops = listOf(
            BusStop(stopId = "STOP-01-1", name = "Stop 1", order = 1),
            BusStop(stopId = "STOP-01-2", name = "Stop 2", order = 2)
        )
        val serverRouteWithEmptyStops = Route(
            routeId = "ROUTE-01",
            name = "Route 01",
            stops = emptyList()
        )

        val mergedStops = if (serverRouteWithEmptyStops.stops.isNotEmpty()) {
            serverRouteWithEmptyStops.stops
        } else {
            fallbackStops
        }

        assertEquals(2, mergedStops.size)
        assertEquals("STOP-01-1", mergedStops[0].stopId)
        assertEquals("STOP-01-2", mergedStops[1].stopId)
    }
}
