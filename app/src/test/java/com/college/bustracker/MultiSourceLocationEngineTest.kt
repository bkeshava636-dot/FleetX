package com.college.bustracker

import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.college.bustracker.util.MultiSourceLocationEngine
import com.college.bustracker.util.SelectedSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceLocationEngineTest {

    private val busId = "BUS-25"
    private val routeId = "ROUTE-A"
    private val driverId = "driver_456"

    // =========================================================================
    // 1. HEALTHY DRIVER WINS OVER STUDENT
    // =========================================================================
    @Test
    fun test1_healthyDriverWinsOverStudent() {
        val now = System.currentTimeMillis()
        val driverSource = LocationSource(
            busId = busId,
            sourceId = driverId,
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 8.0f,
            speed = 10.0f,
            timestamp = now - 2_000L,
            active = true
        )
        val studentSource = LocationSource(
            busId = busId,
            sourceId = "student_1",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34355,
            longitude = 76.15545,
            accuracy = 12.0f,
            speed = 9.8f,
            timestamp = now - 1_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = null,
            driverSource = driverSource,
            studentSources = listOf(studentSource),
            now = now
        )

        assertEquals("Healthy driver must take priority over student", SelectedSourceType.DRIVER, result.selectedSourceType)
        assertEquals(15.34350, result.liveLocation.latitude, 0.00001)
        assertEquals(76.15540, result.liveLocation.longitude, 0.00001)
        assertEquals("ACTIVE", result.liveLocation.trackingStatus)
    }

    // =========================================================================
    // 2. STALE DRIVER + GOOD STUDENT -> STUDENT USED
    // =========================================================================
    @Test
    fun test2_staleDriver_goodStudentUsed() {
        val now = System.currentTimeMillis()
        // Driver GPS is 50 seconds old (exceeds 45s threshold)
        val staleDriver = LocationSource(
            busId = busId,
            sourceId = driverId,
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34000,
            longitude = 76.15000,
            accuracy = 10.0f,
            timestamp = now - 50_000L,
            active = true
        )
        // Student GPS is fresh (4 seconds old) and accurate (12m)
        val freshStudent = LocationSource(
            busId = busId,
            sourceId = "student_1",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34400,
            longitude = 76.15600,
            accuracy = 12.0f,
            timestamp = now - 4_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = null,
            driverSource = staleDriver,
            studentSources = listOf(freshStudent),
            now = now
        )

        assertEquals("Stale driver must failover to reliable student", SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals(15.34400, result.liveLocation.latitude, 0.00001)
        assertEquals(76.15600, result.liveLocation.longitude, 0.00001)
        assertEquals(1, result.usedSourcesCount)
    }

    // =========================================================================
    // 3. HEALTHY DRIVER + BAD STUDENT -> DRIVER REMAINS
    // =========================================================================
    @Test
    fun test3_healthyDriver_badStudent_driverRemains() {
        val now = System.currentTimeMillis()
        val driverSource = LocationSource(
            busId = busId,
            sourceId = driverId,
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 6.0f,
            timestamp = now - 3_000L,
            active = true
        )
        // Student with poor accuracy (500m >> 40m limit)
        val badStudent = LocationSource(
            busId = busId,
            sourceId = "student_bad",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.39000,
            longitude = 76.19000,
            accuracy = 500.0f,
            timestamp = now - 2_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = null,
            driverSource = driverSource,
            studentSources = listOf(badStudent),
            now = now
        )

        assertEquals("Driver must remain active despite bad student", SelectedSourceType.DRIVER, result.selectedSourceType)
        assertEquals(15.34350, result.liveLocation.latitude, 0.00001)
    }

    // =========================================================================
    // 4. MULTIPLE GOOD STUDENTS -> RELIABLE CLUSTER FUSED
    // =========================================================================
    @Test
    fun test4_multipleGoodStudents_reliableClusterFused() {
        val now = System.currentTimeMillis()
        // Driver is offline/missing
        val studentA = LocationSource(
            busId = busId,
            sourceId = "student_A",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 10.0f,
            timestamp = now - 3_000L,
            active = true
        )
        val studentB = LocationSource(
            busId = busId,
            sourceId = "student_B",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34360,
            longitude = 76.15550,
            accuracy = 15.0f,
            timestamp = now - 4_000L,
            active = true
        )
        // Outlier student 5km away should not pull the cluster
        val studentOutlier = LocationSource(
            busId = busId,
            sourceId = "student_outlier",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.39000,
            longitude = 76.20000,
            accuracy = 15.0f,
            timestamp = now - 2_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = null,
            driverSource = null,
            studentSources = listOf(studentA, studentB, studentOutlier),
            now = now
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals("Consensus cluster of 2 students should be fused", 2, result.usedSourcesCount)
        // Check that fused latitude is between student A and B
        assertTrue("Fused latitude should be between 15.34350 and 15.34360",
            result.liveLocation.latitude in 15.34350..15.34360)
    }

    // =========================================================================
    // 5. BAD GPS JUMP REJECTED (> 35 m/s)
    // =========================================================================
    @Test
    fun test5_badGpsJumpRejected() {
        val now = System.currentTimeMillis()
        val prevLocation = LiveLocation(
            busId = busId,
            latitude = 15.34350,
            longitude = 76.15540,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 2_000L // 2 seconds ago
        )

        // Teleportation jump: ~10km away in 2 seconds (~5,000 m/s >> 35 m/s)
        val jumpingDriver = LocationSource(
            busId = busId,
            sourceId = driverId,
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.44350,
            longitude = 76.25540,
            accuracy = 8.0f,
            timestamp = now,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = prevLocation,
            driverSource = jumpingDriver,
            studentSources = emptyList(),
            now = now
        )

        assertNotEquals("Kinematic jump must not be accepted as valid driver update",
            SelectedSourceType.DRIVER, result.selectedSourceType)
        assertEquals("Status should fallback to STALE on invalid jump", "STALE", result.liveLocation.trackingStatus)
    }

    // =========================================================================
    // 6. STALE STUDENT REJECTED (> 30s)
    // =========================================================================
    @Test
    fun test6_staleStudentRejected() {
        val now = System.currentTimeMillis()
        // Student fix is 35s old (> 30s threshold)
        val staleStudent = LocationSource(
            busId = busId,
            sourceId = "student_1",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 10.0f,
            timestamp = now - 35_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = null,
            driverSource = null,
            studentSources = listOf(staleStudent),
            now = now
        )

        assertEquals("Stale student must be rejected", SelectedSourceType.NONE, result.selectedSourceType)
        assertEquals(0, result.usedSourcesCount)
    }

    // =========================================================================
    // 7. UNAUTHORIZED / INACTIVE STUDENT REJECTED
    // =========================================================================
    @Test
    fun test7_unauthorizedOrInactiveStudentRejected() {
        val now = System.currentTimeMillis()
        val inactiveStudent = LocationSource(
            busId = busId,
            sourceId = "student_inactive",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 10.0f,
            timestamp = now - 2_000L,
            active = false // opted out
        )
        val wrongBusStudent = LocationSource(
            busId = "OTHER-BUS",
            sourceId = "student_wrong_bus",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 10.0f,
            timestamp = now - 2_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = null,
            driverSource = null,
            studentSources = listOf(inactiveStudent, wrongBusStudent),
            now = now
        )

        assertEquals(SelectedSourceType.NONE, result.selectedSourceType)
        assertEquals(0, result.usedSourcesCount)
    }

    // =========================================================================
    // 8. DRIVER RECOVERY -> SWITCHES BACK TO DRIVER
    // =========================================================================
    @Test
    fun test8_driverRecovery_switchesBackToDriver() {
        val now = System.currentTimeMillis()
        val prevStudentLocation = LiveLocation(
            busId = busId,
            latitude = 15.34350,
            longitude = 76.15540,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 5_000L
        )

        val activeStudent = LocationSource(
            busId = busId,
            sourceId = "student_1",
            sourceType = "SECONDARY_STUDENT",
            latitude = 15.34352,
            longitude = 76.15542,
            accuracy = 12.0f,
            timestamp = now - 2_000L,
            active = true
        )

        // Driver recovers with fresh GPS fix
        val recoveredDriver = LocationSource(
            busId = busId,
            sourceId = driverId,
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34355,
            longitude = 76.15545,
            accuracy = 7.0f,
            timestamp = now - 1_000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = prevStudentLocation,
            driverSource = recoveredDriver,
            studentSources = listOf(activeStudent),
            now = now
        )

        assertEquals("Engine must immediately switch back to Driver once recovered",
            SelectedSourceType.DRIVER, result.selectedSourceType)
        assertEquals(15.34355, result.liveLocation.latitude, 0.00001)
    }

    // =========================================================================
    // 9. NO CONTRIBUTORS -> CORRECT STALE / WAITING STATE
    // =========================================================================
    @Test
    fun test9_noContributors_correctStaleOrWaitingState() {
        val now = System.currentTimeMillis()

        // 9a. No previous fix -> WAITING_FOR_GPS
        val resultNoPrev = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = null,
            driverSource = null,
            studentSources = emptyList(),
            now = now
        )
        assertEquals(SelectedSourceType.NONE, resultNoPrev.selectedSourceType)
        assertEquals("WAITING_FOR_GPS", resultNoPrev.liveLocation.trackingStatus)

        // 9b. Previous valid fix -> STALE
        val prevLocation = LiveLocation(
            busId = busId,
            latitude = 15.34350,
            longitude = 76.15540,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 60_000L
        )
        val resultWithPrev = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = prevLocation,
            driverSource = null,
            studentSources = emptyList(),
            now = now
        )
        assertEquals(SelectedSourceType.NONE, resultWithPrev.selectedSourceType)
        assertEquals("STALE", resultWithPrev.liveLocation.trackingStatus)
    }

    // =========================================================================
    // 10. NO DUPLICATE UPDATES WITHIN 3s
    // =========================================================================
    @Test
    fun test10_noDuplicateUpdatesWithin3s() {
        val now = System.currentTimeMillis()
        val prevLocation = LiveLocation(
            busId = busId,
            latitude = 15.34350,
            longitude = 76.15540,
            trackingStatus = "ACTIVE",
            lastUpdated = now - 1_500L // 1.5 seconds ago
        )

        val sameDriverFix = LocationSource(
            busId = busId,
            sourceId = driverId,
            sourceType = "PRIMARY_DRIVER",
            latitude = 15.34350,
            longitude = 76.15540,
            accuracy = 8.0f,
            timestamp = now,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = prevLocation,
            driverSource = sameDriverFix,
            studentSources = emptyList(),
            now = now
        )

        assertTrue("Identical coordinate update within 3s must be flagged as duplicate", result.isDuplicate)

        // When timestamp difference exceeds 3s, it's not a duplicate
        val laterPrevLocation = prevLocation.copy(lastUpdated = now - 4_000L)
        val laterResult = MultiSourceLocationEngine.selectBestLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            previousLocation = laterPrevLocation,
            driverSource = sameDriverFix,
            studentSources = emptyList(),
            now = now
        )
        assertFalse("Update after 3s should not be marked as duplicate", laterResult.isDuplicate)
    }

    // =========================================================================
    // PHASE 10A — MULTI-STUDENT LOCATION RELIABILITY TESTS
    // =========================================================================

    @Test
    fun testPhase10A_singleStudentContributor() {
        val now = System.currentTimeMillis()
        val s1 = LocationSource(
            busId = busId, sourceId = "student_single", sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350, longitude = 76.15540, accuracy = 10.0f, speed = 8.0f,
            timestamp = now - 2000L, active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = listOf(s1), now = now, previousLocation = null
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals(1, result.usedSourcesCount)
        assertEquals(65, result.confidenceScore)
        assertEquals(15.34350, result.liveLocation.latitude, 0.0001)
    }

    @Test
    fun testPhase10A_twoStudentConsensus() {
        val now = System.currentTimeMillis()
        val s1 = LocationSource(
            busId = busId, sourceId = "student_1", sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350, longitude = 76.15540, accuracy = 10.0f, speed = 8.0f,
            timestamp = now - 2000L, active = true
        )
        val s2 = LocationSource(
            busId = busId, sourceId = "student_2", sourceType = "SECONDARY_STUDENT",
            latitude = 15.34360, longitude = 76.15550, accuracy = 12.0f, speed = 8.2f,
            timestamp = now - 1500L, active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = listOf(s1, s2), now = now, previousLocation = null
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals(2, result.usedSourcesCount)
        assertEquals(85, result.confidenceScore)
        assertTrue("Fused fix should lie between s1 and s2", result.liveLocation.latitude in 15.34350..15.34360)
    }

    @Test
    fun testPhase10A_fiveStudentConsensus() {
        val now = System.currentTimeMillis()
        val students = (1..5).map { i ->
            LocationSource(
                busId = busId, sourceId = "student_$i", sourceType = "SECONDARY_STUDENT",
                latitude = 15.34350 + (i * 0.00005),
                longitude = 76.15540 + (i * 0.00005),
                accuracy = 8.0f + i,
                speed = 7.5f,
                timestamp = now - (i * 500L),
                active = true
            )
        }

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = students, now = now, previousLocation = null
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals(5, result.usedSourcesCount)
        assertEquals(95, result.confidenceScore)
    }

    @Test
    fun testPhase10A_tenPlusStudentConsensus() {
        val now = System.currentTimeMillis()
        val students = (1..12).map { i ->
            LocationSource(
                busId = busId, sourceId = "student_$i", sourceType = "SECONDARY_STUDENT",
                latitude = 15.34350 + ((i % 4) * 0.00008),
                longitude = 76.15540 + ((i % 3) * 0.00008),
                accuracy = 10.0f,
                speed = 8.0f,
                timestamp = now - 1000L,
                active = true
            )
        }

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = students, now = now, previousLocation = null
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals(12, result.usedSourcesCount)
        assertEquals(95, result.confidenceScore)
    }

    @Test
    fun testPhase10A_driverPlusStudents_driverAlwaysWins() {
        val now = System.currentTimeMillis()
        val driver = LocationSource(
            busId = busId, sourceId = driverId, sourceType = "PRIMARY_DRIVER",
            latitude = 15.34350, longitude = 76.15540, accuracy = 7.0f, speed = 9.0f,
            timestamp = now - 2000L, active = true
        )
        val students = (1..5).map { i ->
            LocationSource(
                busId = busId, sourceId = "student_$i", sourceType = "SECONDARY_STUDENT",
                latitude = 15.34352, longitude = 76.15542, accuracy = 10.0f, speed = 9.0f,
                timestamp = now - 1000L, active = true
            )
        }

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = driver, studentSources = students, now = now, previousLocation = null
        )

        assertEquals("Driver must be selected when healthy, regardless of student count", SelectedSourceType.DRIVER, result.selectedSourceType)
        assertEquals(1, result.usedSourcesCount)
        assertEquals(100, result.confidenceScore)
        assertEquals(15.34350, result.liveLocation.latitude, 0.00001)
    }

    @Test
    fun testPhase10A_studentOutlierRejected() {
        val now = System.currentTimeMillis()
        // 4 students clustered at the bus (Location A: 15.34350, 76.15540)
        val onBusStudents = (1..4).map { i ->
            LocationSource(
                busId = busId, sourceId = "student_bus_$i", sourceType = "SECONDARY_STUDENT",
                latitude = 15.34350 + (i * 0.00003),
                longitude = 76.15540 + (i * 0.00003),
                accuracy = 8.0f,
                speed = 8.0f,
                timestamp = now - 1000L,
                active = true
            )
        }

        // 1 student 2 km away (outlier)
        val outlier = LocationSource(
            busId = busId, sourceId = "student_outlier", sourceType = "SECONDARY_STUDENT",
            latitude = 15.36150, // ~2km north
            longitude = 76.15540,
            accuracy = 8.0f,
            speed = 0.0f,
            timestamp = now - 1000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = onBusStudents + outlier, now = now, previousLocation = null
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals("Outlier must be excluded from consensus cluster", 4, result.usedSourcesCount)
        assertEquals(15.3435, result.liveLocation.latitude, 0.001)
        assertNotEquals("Final bus location must NOT jump to the outlier", 15.36150, result.liveLocation.latitude, 0.01)
    }

    @Test
    fun testPhase10A_multipleOutliersRejected() {
        val now = System.currentTimeMillis()
        // 5 students on bus (clustered ~2m apart inside bus)
        val onBus = (1..5).map { i ->
            LocationSource(
                busId = busId, sourceId = "student_bus_$i", sourceType = "SECONDARY_STUDENT",
                latitude = 15.34350 + (i * 0.00002),
                longitude = 76.15540 + (i * 0.00002),
                accuracy = 9.0f, speed = 8.0f,
                timestamp = now - 1000L - (i * 50L), active = true
            )
        }

        // 2 students at library (3 km away)
        val dormStudents = listOf(
            LocationSource(busId = busId, sourceId = "dorm_1", sourceType = "SECONDARY_STUDENT", latitude = 15.3700, longitude = 76.1550, accuracy = 10f, timestamp = now - 1000L, active = true),
            LocationSource(busId = busId, sourceId = "dorm_2", sourceType = "SECONDARY_STUDENT", latitude = 15.3702, longitude = 76.1551, accuracy = 10f, timestamp = now - 1000L, active = true)
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = onBus + dormStudents, now = now, previousLocation = null
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals("Dominant cluster of 5 on bus must win", 5, result.usedSourcesCount)
        assertEquals(15.34350, result.liveLocation.latitude, 0.0005)
    }

    @Test
    fun testPhase10A_conflictingStudentClusters_continuityResolves() {
        val now = System.currentTimeMillis()
        val prevLocation = LiveLocation(
            busId = busId, latitude = 15.34350, longitude = 76.15540, trackingStatus = "ACTIVE", lastUpdated = now - 5000L
        )

        // Cluster 1: 2 students near previous bus position
        val clusterNearBus = listOf(
            LocationSource(busId = busId, sourceId = "c1_1", sourceType = "SECONDARY_STUDENT", latitude = 15.34360, longitude = 76.15550, accuracy = 10f, timestamp = now - 1000L, active = true),
            LocationSource(busId = busId, sourceId = "c1_2", sourceType = "SECONDARY_STUDENT", latitude = 15.34365, longitude = 76.15555, accuracy = 10f, timestamp = now - 1000L, active = true)
        )

        // Cluster 2: 2 students far away (e.g. 500m away, still plausible velocity jump but separated)
        val clusterFar = listOf(
            LocationSource(busId = busId, sourceId = "c2_1", sourceType = "SECONDARY_STUDENT", latitude = 15.34800, longitude = 76.15550, accuracy = 10f, timestamp = now - 1000L, active = true),
            LocationSource(busId = busId, sourceId = "c2_2", sourceType = "SECONDARY_STUDENT", latitude = 15.34805, longitude = 76.15555, accuracy = 10f, timestamp = now - 1000L, active = true)
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, previousLocation = prevLocation, driverSource = null, studentSources = clusterNearBus + clusterFar, now = now
        )

        assertEquals(SelectedSourceType.STUDENT_ASSISTED, result.selectedSourceType)
        assertEquals(2, result.usedSourcesCount)
        // Cluster near previous location must win
        assertEquals(15.3436, result.liveLocation.latitude, 0.001)
    }

    @Test
    fun testPhase10A_staleStudentRejected() {
        val now = System.currentTimeMillis()
        val staleStudent = LocationSource(
            busId = busId, sourceId = "stale_student", sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350, longitude = 76.15540, accuracy = 10.0f,
            timestamp = now - 35_000L, // 35s ago (> 30s stale threshold)
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = listOf(staleStudent), now = now, previousLocation = null
        )

        assertEquals("Stale student must not be accepted", SelectedSourceType.NONE, result.selectedSourceType)
        assertEquals(0, result.usedSourcesCount)
    }

    @Test
    fun testPhase10A_inaccurateStudentRejected() {
        val now = System.currentTimeMillis()
        val inaccurateStudent = LocationSource(
            busId = busId, sourceId = "inaccurate_student", sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350, longitude = 76.15540,
            accuracy = 45.0f, // > 40m max accuracy threshold
            timestamp = now - 2000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = listOf(inaccurateStudent), now = now, previousLocation = null
        )

        assertEquals("Inaccurate student must be rejected", SelectedSourceType.NONE, result.selectedSourceType)
    }

    @Test
    fun testPhase10A_impossibleSpeedStudentRejected() {
        val now = System.currentTimeMillis()
        val speedDemon = LocationSource(
            busId = busId, sourceId = "speed_demon", sourceType = "SECONDARY_STUDENT",
            latitude = 15.34350, longitude = 76.15540, accuracy = 10.0f,
            speed = 45.0f, // 45 m/s (~162 km/h) > 35 m/s max
            timestamp = now - 1000L,
            active = true
        )

        val result = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = listOf(speedDemon), now = now, previousLocation = null
        )

        assertEquals("Impossible speed student must be rejected", SelectedSourceType.NONE, result.selectedSourceType)
    }

    @Test
    fun testPhase10A_driverRecoverySeamless() {
        val now = System.currentTimeMillis()
        val students = listOf(
            LocationSource(busId = busId, sourceId = "s1", sourceType = "SECONDARY_STUDENT", latitude = 15.34350, longitude = 76.15540, accuracy = 10f, timestamp = now - 2000L, active = true),
            LocationSource(busId = busId, sourceId = "s2", sourceType = "SECONDARY_STUDENT", latitude = 15.34355, longitude = 76.15545, accuracy = 10f, timestamp = now - 2000L, active = true)
        )

        // 1. Initially driver is absent -> students provide location
        val studentResult = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, driverSource = null, studentSources = students, now = now, previousLocation = null
        )
        assertEquals(SelectedSourceType.STUDENT_ASSISTED, studentResult.selectedSourceType)
        assertEquals(2, studentResult.usedSourcesCount)

        // 2. Driver recovers with a fresh, valid GPS fix
        val recoveredDriver = LocationSource(
            busId = busId, sourceId = driverId, sourceType = "PRIMARY_DRIVER",
            latitude = 15.34360, longitude = 76.15550, accuracy = 8.0f, speed = 8.5f,
            timestamp = now, active = true
        )

        val recoveredResult = MultiSourceLocationEngine.selectBestLocation(
            busId = busId, previousLocation = studentResult.liveLocation, driverSource = recoveredDriver, studentSources = students, now = now
        )

        assertEquals("Driver must immediately recover priority over student contributors", SelectedSourceType.DRIVER, recoveredResult.selectedSourceType)
        assertEquals(1, recoveredResult.usedSourcesCount)
        assertEquals(15.34360, recoveredResult.liveLocation.latitude, 0.00001)
    }

    @Test
    fun testPhase10A_sybilDuplicateDefense() {
        val now = System.currentTimeMillis()
        // Three submissions with identical coordinates (< 1m) and timestamp (e.g. simulated/cloned bot)
        val clonedSources = listOf(
            LocationSource(busId = busId, sourceId = "bot_1", sourceType = "SECONDARY_STUDENT", latitude = 15.343500, longitude = 76.155400, accuracy = 10f, speed = 8.0f, timestamp = now - 1000L, active = true),
            LocationSource(busId = busId, sourceId = "bot_2", sourceType = "SECONDARY_STUDENT", latitude = 15.343500, longitude = 76.155400, accuracy = 10f, speed = 8.0f, timestamp = now - 1000L, active = true),
            LocationSource(busId = busId, sourceId = "bot_3", sourceType = "SECONDARY_STUDENT", latitude = 15.343500, longitude = 76.155400, accuracy = 10f, speed = 8.0f, timestamp = now - 1000L, active = true)
        )

        val deduplicated = MultiSourceLocationEngine.deduplicateStudentSources(clonedSources)
        assertEquals("Identical cloned coordinate submissions must be collapsed to 1", 1, deduplicated.size)
    }
}
