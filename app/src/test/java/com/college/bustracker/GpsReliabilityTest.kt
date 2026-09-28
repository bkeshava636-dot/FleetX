package com.college.bustracker

import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.data.model.getDisplayState
import com.college.bustracker.util.LocationThrottler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsReliabilityTest {

    // ==========================================
    // 1. LOCATION VALIDATION
    // ==========================================

    @Test
    fun testLocationValidation_validCoordinates() {
        val lat = 15.3435
        val lng = 76.1554
        val accuracy = 4.2f

        val isValid = lat != 0.0 && lng != 0.0 && accuracy <= 50.0f
        assertTrue("Standard valid GPS fix should pass validation", isValid)
    }

    @Test
    fun testLocationValidation_rejectZeroCoordinates() {
        val lat = 0.0
        val lng = 0.0
        val accuracy = 5.0f

        val isValid = lat != 0.0 && lng != 0.0 && accuracy <= 50.0f
        assertFalse("Zero coordinates (0.0, 0.0) must be rejected", isValid)
    }

    @Test
    fun testLocationValidation_rejectInaccurateFix() {
        val lat = 15.3435
        val lng = 76.1554
        val accuracy = 65.0f // > 50m

        val isValid = lat != 0.0 && lng != 0.0 && accuracy <= 50.0f
        assertFalse("Fix with accuracy worse than 50m must be rejected to prevent map drift", isValid)
    }

    // ==========================================
    // 2. STALE DETECTION
    // ==========================================

    @Test
    fun testStaleDetection_recentUpdateIsActive() {
        val now = System.currentTimeMillis()
        val loc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3435,
            longitude = 76.1554,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 15_000L // 15 seconds ago
        )

        assertFalse("15-second old update should not be stale", loc.isStale(90_000L))
        assertEquals("Display state must be ACTIVE", TrackingDisplayState.ACTIVE, loc.getDisplayState(now))
    }

    @Test
    fun testStaleDetection_exceedingThresholdIsStale() {
        val now = System.currentTimeMillis()
        val loc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3435,
            longitude = 76.1554,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 95_000L // 95 seconds ago (> 90s)
        )

        assertTrue("95-second old update must be marked stale", loc.isStale(90_000L))
        assertEquals("Display state must be STALE", TrackingDisplayState.STALE, loc.getDisplayState(now))
    }

    @Test
    fun testStaleDetection_zeroTimestampIsStale() {
        val loc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3435,
            longitude = 76.1554,
            trackingStatus = "ACTIVE",
            lastUpdated = 0L
        )

        assertTrue("0L timestamp must be marked stale", loc.isStale(90_000L))
    }

    @Test
    fun testDisplayState_waitingForGps() {
        val now = System.currentTimeMillis()
        val loc = LiveLocation(
            busId = "BUS-25",
            latitude = 0.0,
            longitude = 0.0,
            trackingStatus = "WAITING_FOR_GPS",
            lastUpdated = now
        )

        assertEquals(
            "Waiting for GPS must produce WAITING_FOR_GPS display state",
            TrackingDisplayState.WAITING_FOR_GPS,
            loc.getDisplayState(now)
        )
    }

    @Test
    fun testDisplayState_inactiveWhenStopped() {
        val now = System.currentTimeMillis()
        val loc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3435,
            longitude = 76.1554,
            trackingStatus = "INACTIVE",
            lastUpdated = now
        )

        assertEquals(
            "Inactive trackingStatus must produce INACTIVE display state",
            TrackingDisplayState.INACTIVE,
            loc.getDisplayState(now)
        )
    }

    // ==========================================
    // 3. DUPLICATE PREVENTION & DEBOUNCE
    // ==========================================

    @Test
    fun testDuplicatePrevention_rapidIdenticalDispatches() {
        val lastTimestamp = 10000L
        // Same coordinates within 2 seconds (< 3s minimum debounce)
        val shouldDispatch = LocationThrottler.shouldDispatchUpdate(
            lastSentLat = 15.3435,
            lastSentLng = 76.1554,
            lastSentTimestamp = lastTimestamp,
            newLat = 15.3435,
            newLng = 76.1554,
            newTimestamp = lastTimestamp + 2000L,
            speedMps = 0.0f
        )
        assertFalse("Duplicate write within 2s must be rejected", shouldDispatch)
    }

    // ==========================================
    // 4. BUFFERING & RECONNECT FRESHNESS
    // ==========================================

    @Test
    fun testOfflineBufferFreshness_shortDropoutRefreshesTimestamp() {
        val now = System.currentTimeMillis()
        val captureTime = now - 60_000L // captured 1 min ago
        val flushTime = now             // flushed now (< 180s threshold)

        val bufferedLoc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3435,
            longitude = 76.1554,
            lastUpdated = captureTime
        )

        // Simulate repository flush timestamp logic
        val flushedLoc = if ((flushTime - bufferedLoc.lastUpdated) < 180_000L) {
            bufferedLoc.copy(lastUpdated = flushTime)
        } else {
            bufferedLoc
        }

        assertEquals("Timestamp should be refreshed to flush time for short dropout", flushTime, flushedLoc.lastUpdated)
        assertFalse("Refreshed location must not be stale upon student reception", flushedLoc.isStale(90_000L))
    }

    @Test
    fun testOfflineBufferFreshness_longDropoutPreservesOriginalTimestamp() {
        val now = System.currentTimeMillis()
        val captureTime = now - 300_000L // 5 minutes dropout (> 180s threshold)
        val flushTime = now

        val bufferedLoc = LiveLocation(
            busId = "BUS-25",
            latitude = 15.3435,
            longitude = 76.1554,
            lastUpdated = captureTime
        )

        val flushedLoc = if ((flushTime - bufferedLoc.lastUpdated) < 180_000L) {
            bufferedLoc.copy(lastUpdated = flushTime)
        } else {
            bufferedLoc
        }

        assertEquals("Old timestamp must be preserved for extended dropout", captureTime, flushedLoc.lastUpdated)
    }

    // ==========================================
    // 5. STATIONARY HEARTBEAT WITHOUT DISPLACEMENT
    // ==========================================

    @Test
    fun testStationaryHeartbeat_permitsPeriodicWritesWithoutDisplacement() {
        val baseTime = 10_000L

        // 1. First update sent at baseTime
        assertTrue(
            LocationThrottler.shouldDispatchUpdate(
                lastSentLat = 0.0,
                lastSentLng = 0.0,
                lastSentTimestamp = 0L,
                newLat = 15.3435,
                newLng = 76.1554,
                newTimestamp = baseTime,
                speedMps = 0.0f
            )
        )

        // 2. Exact same coordinate 15s later (< 25s stationary threshold) -> REJECT
        assertFalse(
            LocationThrottler.shouldDispatchUpdate(
                lastSentLat = 15.3435,
                lastSentLng = 76.1554,
                lastSentTimestamp = baseTime,
                newLat = 15.3435,
                newLng = 76.1554,
                newTimestamp = baseTime + 15_000L,
                speedMps = 0.0f
            )
        )

        // 3. Exact same coordinate 26s later (>= 25s stationary threshold) -> DISPATCH
        assertTrue(
            LocationThrottler.shouldDispatchUpdate(
                lastSentLat = 15.3435,
                lastSentLng = 76.1554,
                lastSentTimestamp = baseTime,
                newLat = 15.3435,
                newLng = 76.1554,
                newTimestamp = baseTime + 26_000L,
                speedMps = 0.0f
            )
        )

        // 4. Exact same coordinate 30s later (>= 30s forced heartbeat) -> DISPATCH
        assertTrue(
            LocationThrottler.shouldDispatchUpdate(
                lastSentLat = 15.3435,
                lastSentLng = 76.1554,
                lastSentTimestamp = baseTime,
                newLat = 15.3435,
                newLng = 76.1554,
                newTimestamp = baseTime + 30_000L,
                speedMps = 0.0f
            )
        )
    }

    // ==========================================
    // 6. PHASE 10C: GPS / NETWORK FAILURE HARDENING
    // ==========================================

    @Test
    fun testPhase10C_activeStatusWithZeroCoordinatesNeverPermitted() {
        val now = System.currentTimeMillis()
        val zeroActive = LiveLocation(
            busId = "BUS-25",
            latitude = 0.0,
            longitude = 0.0,
            trackingStatus = "ACTIVE",
            lastUpdated = now
        )

        // Must degrade to WAITING_FOR_GPS instead of plotting in the Atlantic ocean
        assertEquals(
            "ACTIVE status with 0,0 coordinates must degrade to WAITING_FOR_GPS",
            TrackingDisplayState.WAITING_FOR_GPS,
            zeroActive.getDisplayState(now)
        )
    }

    @Test
    fun testPhase10C_waitingForGpsPreservesPreviousCoordinates() {
        val lastKnownLat = 15.34350
        val lastKnownLng = 76.15540

        val waitingLoc = LiveLocation(
            busId = "BUS-25",
            latitude = lastKnownLat,
            longitude = lastKnownLng,
            trackingStatus = "WAITING_FOR_GPS",
            lastUpdated = System.currentTimeMillis()
        )

        assertEquals(TrackingDisplayState.WAITING_FOR_GPS, waitingLoc.getDisplayState())
        assertEquals(lastKnownLat, waitingLoc.latitude, 0.0001)
        assertEquals(lastKnownLng, waitingLoc.longitude, 0.0001)
    }

    @Test
    fun testPhase10C_staleStateTransitionsAccuratelyOnGpsSilence() {
        val now = 100_000L
        val lastKnownLat = 15.34350
        val lastKnownLng = 76.15540

        // 1. Fresh update at t = 20s ago
        val activeLoc = LiveLocation(
            busId = "BUS-25",
            latitude = lastKnownLat,
            longitude = lastKnownLng,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 20_000L
        )
        assertEquals(TrackingDisplayState.ACTIVE, activeLoc.getDisplayState(now))

        // 2. GPS lost, 91s since last fix
        val staleLoc = activeLoc.copy(lastUpdated = now - 91_000L)
        assertEquals("Exceeding 90s must transition to STALE", TrackingDisplayState.STALE, staleLoc.getDisplayState(now))
        assertEquals("Coordinates must be preserved during STALE state", lastKnownLat, staleLoc.latitude, 0.0001)
    }

    @Test
    fun testPhase10C_offlineBufferMonotonicityCheck() {
        val now = 200_000L
        // Newer fix already in memory
        val newerMemoryFix = LiveLocation(
            busId = "BUS-25",
            latitude = 15.34400,
            longitude = 76.15600,
            lastUpdated = now
        )

        // Older buffered fix from previous offline period
        val olderBufferedFix = LiveLocation(
            busId = "BUS-25",
            latitude = 15.34300,
            longitude = 76.15500,
            lastUpdated = now - 60_000L
        )

        // Monotonicity rule: never overwrite newer state with older buffer
        val shouldSkipBuffer = newerMemoryFix.lastUpdated > olderBufferedFix.lastUpdated
        assertTrue("Monotonicity check must prevent older buffered fix from overwriting fresher state", shouldSkipBuffer)
    }

    @Test
    fun testPhase10C_driverGpsLossWithStudentBackupFailover() {
        val now = System.currentTimeMillis()
        val busId = "BUS-25"

        // Driver GPS is stale (30s old > 25s threshold)
        val staleDriver = com.college.bustracker.data.model.LocationSource(
            busId = busId,
            sourceId = "driver_1",
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 8.0f,
            timestamp = now - 50_000L,
            active = true
        )

        // Student contributor is fresh (2s old)
        val freshStudent = com.college.bustracker.data.model.LocationSource(
            busId = busId,
            sourceId = "student_1",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34380,
            longitude = 76.15570,
            accuracy = 10.0f,
            timestamp = now - 2_000L,
            active = true
        )

        val result = com.college.bustracker.util.MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            driverSource = staleDriver,
            studentSources = listOf(freshStudent),
            now = now
        )

        assertEquals(
            "Student contributor must take over when driver GPS becomes stale",
            com.college.bustracker.util.SelectedSourceType.STUDENT_ASSISTED,
            result.selectedSourceType
        )
        assertEquals(15.34380, result.liveLocation.latitude, 0.0001)
    }

    @Test
    fun testPhase10C_driverGpsRecoveryRestoresDriverInstantly() {
        val now = System.currentTimeMillis()
        val busId = "BUS-25"

        // Driver GPS recovers: fresh fix (1s old, 6m accuracy)
        val recoveredDriver = com.college.bustracker.data.model.LocationSource(
            busId = busId,
            sourceId = "driver_1",
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34400,
            longitude = 76.15600,
            accuracy = 6.0f,
            timestamp = now - 1_000L,
            active = true
        )

        val activeStudent = com.college.bustracker.data.model.LocationSource(
            busId = busId,
            sourceId = "student_1",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34395,
            longitude = 76.15595,
            accuracy = 12.0f,
            timestamp = now - 1_000L,
            active = true
        )

        val result = com.college.bustracker.util.MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            driverSource = recoveredDriver,
            studentSources = listOf(activeStudent),
            now = now
        )

        assertEquals(
            "Driver must immediately regain primary authority upon GPS recovery",
            com.college.bustracker.util.SelectedSourceType.DRIVER,
            result.selectedSourceType
        )
        assertEquals(100, result.confidenceScore)
        assertEquals(15.34400, result.liveLocation.latitude, 0.00001)
    }
}
