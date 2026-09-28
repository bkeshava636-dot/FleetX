package com.college.bustracker

import com.college.bustracker.util.LocationThrottler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationThrottlerTest {

    @Test
    fun testHaversineDistance_betweenKoppalStops() {
        // Koppal Bus Stand (15.3435, 76.1554) to Gavi Math (15.3521, 76.1610)
        val distance = LocationThrottler.calculateDistanceMeters(
            15.3435, 76.1554,
            15.3521, 76.1610
        )
        // Distance is roughly ~1.1 km to 1.2 km
        assertTrue("Distance should be ~1140 meters, was: $distance", distance in 1000.0..1300.0)
    }

    @Test
    fun testFirstUpdateAlwaysDispatches() {
        val shouldDispatch = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 0.0,
            lastSentLng = 0.0,
            lastSentTimestamp = 0L,
            newLat = 15.3435,
            newLng = 76.1554,
            newTimestamp = 10000L,
            speedMps = 0.0f
        )
        assertTrue("First coordinate must always dispatch", shouldDispatch)
    }

    @Test
    fun testRapidBurstDebounce() {
        val lastTime = 10000L
        val shouldDispatch = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 15.3435,
            lastSentLng = 76.1554,
            lastSentTimestamp = lastTime,
            newLat = 15.3436,
            newLng = 76.1555,
            newTimestamp = lastTime + 2000L, // 2 seconds later (< 3s minimum)
            speedMps = 10.0f
        )
        assertFalse("Updates faster than 3s must be rejected to protect battery & bandwidth", shouldDispatch)
    }

    @Test
    fun testMovingBusThrottling() {
        val lastTime = 10000L
        // At 5 seconds moving -> not yet (min 8s)
        val tooEarly = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 15.3435,
            lastSentLng = 76.1554,
            lastSentTimestamp = lastTime,
            newLat = 15.3445,
            newLng = 76.1564,
            newTimestamp = lastTime + 5000L,
            speedMps = 8.0f // moving
        )
        assertFalse("Moving update at 5s should wait for 8s threshold", tooEarly)

        // At 9 seconds moving -> YES
        val ready = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 15.3435,
            lastSentLng = 76.1554,
            lastSentTimestamp = lastTime,
            newLat = 15.3445,
            newLng = 76.1564,
            newTimestamp = lastTime + 9000L,
            speedMps = 8.0f
        )
        assertTrue("Moving update at 9s (>8s threshold) must dispatch", ready)
    }

    @Test
    fun testStationaryBusThrottling() {
        val lastTime = 10000L
        // Bus waiting at stop for 12 seconds
        val stationaryAt12s = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 15.3435,
            lastSentLng = 76.1554,
            lastSentTimestamp = lastTime,
            newLat = 15.34351, // < 2 meters difference
            newLng = 76.15541,
            newTimestamp = lastTime + 12000L,
            speedMps = 0.0f
        )
        assertFalse("Stationary bus at 12s should not dispatch (waiting for 25s)", stationaryAt12s)

        // Bus waiting at stop for 26 seconds -> YES
        val stationaryAt26s = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 15.3435,
            lastSentLng = 76.1554,
            lastSentTimestamp = lastTime,
            newLat = 15.34351,
            newLng = 76.15541,
            newTimestamp = lastTime + 26000L,
            speedMps = 0.0f
        )
        assertTrue("Stationary bus at 26s (>25s threshold) must dispatch", stationaryAt26s)
    }

    @Test
    fun testHeartbeatTrigger() {
        val lastTime = 10000L
        val heartbeat = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 15.3435,
            lastSentLng = 76.1554,
            lastSentTimestamp = lastTime,
            newLat = 15.3435,
            newLng = 76.1554,
            newTimestamp = lastTime + 31000L, // 31 seconds
            speedMps = 0.0f
        )
        assertTrue("Forced heartbeat must trigger after 30 seconds", heartbeat)
    }
}
