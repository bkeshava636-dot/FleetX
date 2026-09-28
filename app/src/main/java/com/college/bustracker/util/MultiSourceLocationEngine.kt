package com.college.bustracker.util

import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import kotlin.math.sqrt

enum class SelectedSourceType {
    DRIVER,
    STUDENT_ASSISTED,
    NONE
}

data class FusionResult(
    val liveLocation: LiveLocation,
    val selectedSourceType: SelectedSourceType,
    val usedSourcesCount: Int,
    val isDuplicate: Boolean = false,
    val reason: String = "",
    val confidenceScore: Int = 0
)

/**
 * Authoritative selection and fusion engine for bus location tracking.
 *
 * Source Priority:
 * 1. Healthy driver GPS (within 45s, accuracy <= 40m).
 * 2. Fresh, accurate student GPS (when driver is stale/missing).
 * 3. Multiple consistent student sources (spatially clustered and fused via weighted centroid).
 * 4. Stale / Waiting fallback when no reliable sources exist.
 *
 * Enforces kinematic jump rejection, duplicate write prevention, and seamless driver recovery.
 */
object MultiSourceLocationEngine {

    const val DRIVER_STALE_THRESHOLD_MS = 45_000L
    const val DRIVER_MAX_ACCURACY_METERS = 100.0f

    const val STUDENT_STALE_THRESHOLD_MS = 30_000L
    const val STUDENT_MAX_ACCURACY_METERS = 40.0f

    const val MAX_SPEED_MPS = 35.0 // ~126 km/h max plausible speed
    const val CLUSTER_RADIUS_METERS = 50.0 // 50m bus dimensions + GPS noise

    fun selectBestLocation(
        busId: String,
        routeId: String = "",
        driverId: String = "",
        mode: String = "MANUAL",
        previousLocation: LiveLocation? = null,
        driverSource: LocationSource?,
        studentSources: List<LocationSource>,
        now: Long = System.currentTimeMillis()
    ): FusionResult {
        // 1. Evaluate Driver Source
        val isDriverHealthy = driverSource != null &&
                driverSource.active &&
                driverSource.sourceType == "PRIMARY_DRIVER" &&
                driverSource.latitude != 0.0 &&
                driverSource.longitude != 0.0 &&
                driverSource.accuracy in 0.1f..DRIVER_MAX_ACCURACY_METERS &&
                (now - driverSource.timestamp) in 0L..DRIVER_STALE_THRESHOLD_MS

        if (driverSource != null && isDriverHealthy) {
            val isPlausibleJump = isKinematicallyPlausible(
                prevLat = previousLocation?.latitude ?: 0.0,
                prevLng = previousLocation?.longitude ?: 0.0,
                prevTimestamp = previousLocation?.lastUpdated ?: 0L,
                newLat = driverSource.latitude,
                newLng = driverSource.longitude,
                newTimestamp = driverSource.timestamp,
                maxSpeedMps = MAX_SPEED_MPS
            )

            if (isPlausibleJump) {
                val candidate = LiveLocation(
                    busId = busId,
                    latitude = driverSource.latitude,
                    longitude = driverSource.longitude,
                    speed = driverSource.speed,
                    bearing = driverSource.bearing,
                    trackingStatus = "ACTIVE",
                    mode = mode,
                    routeId = routeId,
                    driverId = driverId,
                    lastUpdated = driverSource.timestamp
                )

                val isDup = isDuplicateWrite(previousLocation, candidate)
                return FusionResult(
                    liveLocation = candidate,
                    selectedSourceType = SelectedSourceType.DRIVER,
                    usedSourcesCount = 1,
                    isDuplicate = isDup,
                    reason = "Healthy driver GPS primary",
                    confidenceScore = 100
                )
            }
        }

        // 2. Validate student candidates independently
        val validStudents = studentSources.filter { s ->
            s.active &&
                    s.sourceType == "SECONDARY_STUDENT" &&
                    s.busId == busId &&
                    s.sourceId.isNotBlank() &&
                    s.latitude != 0.0 &&
                    s.longitude != 0.0 &&
                    s.latitude in -90.0..90.0 &&
                    s.longitude in -180.0..180.0 &&
                    s.accuracy in 0.1f..STUDENT_MAX_ACCURACY_METERS &&
                    (now - s.timestamp) in 0L..STUDENT_STALE_THRESHOLD_MS &&
                    s.timestamp <= now + 5000L &&
                    s.speed in 0.0f..MAX_SPEED_MPS.toFloat() &&
                    isKinematicallyPlausible(
                        prevLat = previousLocation?.latitude ?: 0.0,
                        prevLng = previousLocation?.longitude ?: 0.0,
                        prevTimestamp = previousLocation?.lastUpdated ?: 0L,
                        newLat = s.latitude,
                        newLng = s.longitude,
                        newTimestamp = s.timestamp,
                        maxSpeedMps = MAX_SPEED_MPS
                    )
        }

        // Deduplicate cloned or multi-reporting contributors
        val deduplicatedStudents = deduplicateStudentSources(validStudents)

        // 3. Evaluate Student Contributors via Cluster Consensus
        if (deduplicatedStudents.isNotEmpty()) {
            val consensusCluster = if (deduplicatedStudents.size == 1) {
                deduplicatedStudents
            } else {
                findConsensusCluster(deduplicatedStudents, previousLocation, CLUSTER_RADIUS_METERS)
            }

            val fusedFix = if (consensusCluster.size == 1) {
                consensusCluster.first()
            } else {
                fuseCluster(consensusCluster)
            }

            val confidence = when {
                consensusCluster.size >= 5 -> 95
                consensusCluster.size in 2..4 -> 85
                else -> 65
            }

            val candidate = LiveLocation(
                busId = busId,
                latitude = fusedFix.latitude,
                longitude = fusedFix.longitude,
                speed = fusedFix.speed,
                bearing = fusedFix.bearing,
                trackingStatus = "ACTIVE",
                mode = mode,
                routeId = routeId,
                driverId = driverId,
                lastUpdated = fusedFix.timestamp
            )

            val isDup = isDuplicateWrite(previousLocation, candidate)
            return FusionResult(
                liveLocation = candidate,
                selectedSourceType = SelectedSourceType.STUDENT_ASSISTED,
                usedSourcesCount = consensusCluster.size,
                isDuplicate = isDup,
                reason = "Driver stale/unavailable. Used ${consensusCluster.size} student contributor(s).",
                confidenceScore = confidence
            )
        }

        // 4. No reliable sources available -> STALE or WAITING_FOR_GPS
        val fallbackStatus = if (previousLocation != null && previousLocation.latitude != 0.0) {
            "STALE"
        } else {
            "WAITING_FOR_GPS"
        }

        val fallbackLocation = previousLocation?.copy(
            trackingStatus = fallbackStatus
        ) ?: LiveLocation(
            busId = busId,
            routeId = routeId,
            driverId = driverId,
            trackingStatus = fallbackStatus,
            lastUpdated = now
        )

        return FusionResult(
            liveLocation = fallbackLocation,
            selectedSourceType = SelectedSourceType.NONE,
            usedSourcesCount = 0,
            isDuplicate = isDuplicateWrite(previousLocation, fallbackLocation),
            reason = "No healthy driver or student GPS sources available",
            confidenceScore = if (fallbackStatus == "STALE") 20 else 0
        )
    }

    fun deduplicateStudentSources(sources: List<LocationSource>): List<LocationSource> {
        if (sources.size <= 1) return sources

        // 1. Group by unique sourceId and keep the most recent fix
        val byId = sources.groupBy { it.sourceId }.mapValues { (_, fixes) ->
            fixes.maxByOrNull { it.timestamp }!!
        }.values.toList()

        if (byId.size <= 1) return byId

        // 2. Sybil / clone coordinate defense:
        // If different sourceIds submit identical coordinates (< 1m) with identical speed and timestamp,
        // treat them as 1 vote for consensus to prevent artificial cluster inflation
        val result = mutableListOf<LocationSource>()
        for (s in byId) {
            val isCloned = result.any { existing ->
                LocationThrottler.calculateDistanceMeters(
                    existing.latitude, existing.longitude,
                    s.latitude, s.longitude
                ) < 1.0 && existing.speed == s.speed && existing.timestamp == s.timestamp
            }
            if (!isCloned) {
                result.add(s)
            }
        }
        return result
    }

    fun isKinematicallyPlausible(
        prevLat: Double,
        prevLng: Double,
        prevTimestamp: Long,
        newLat: Double,
        newLng: Double,
        newTimestamp: Long,
        maxSpeedMps: Double
    ): Boolean {
        if (prevLat == 0.0 || prevLng == 0.0 || prevTimestamp <= 0L) return true
        val elapsedSec = (newTimestamp - prevTimestamp).coerceAtLeast(1000L) / 1000.0
        // If previous fix was > 2 minutes ago, reset jump check
        if (elapsedSec > 120.0) return true
        val distanceMeters = LocationThrottler.calculateDistanceMeters(prevLat, prevLng, newLat, newLng)
        val impliedSpeed = distanceMeters / elapsedSec
        return impliedSpeed <= maxSpeedMps
    }

    fun findConsensusCluster(
        sources: List<LocationSource>,
        previousLocation: LiveLocation? = null,
        radiusMeters: Double = CLUSTER_RADIUS_METERS
    ): List<LocationSource> {
        if (sources.size <= 1) return sources

        // Find candidate clusters centered around each valid source
        val candidateClusters = sources.map { candidate ->
            sources.filter { other ->
                LocationThrottler.calculateDistanceMeters(
                    candidate.latitude, candidate.longitude,
                    other.latitude, other.longitude
                ) <= radiusMeters
            }
        }

        var bestCluster = candidateClusters.first()
        var bestScore = -1.0

        for (cluster in candidateClusters) {
            val count = cluster.size
            var weightSum = 0.0
            for (s in cluster) {
                val acc = s.accuracy.coerceAtLeast(1.0f)
                weightSum += 1.0 / (acc * acc)
            }

            // Majority consensus is paramount
            var score = count * 1000.0 + weightSum

            // If previous known location exists and was recent (< 2 min), reward continuity
            if (previousLocation != null && previousLocation.latitude != 0.0 && previousLocation.lastUpdated > 0L) {
                val centerLat = cluster.map { it.latitude }.average()
                val centerLng = cluster.map { it.longitude }.average()
                val distToPrev = LocationThrottler.calculateDistanceMeters(
                    previousLocation.latitude, previousLocation.longitude,
                    centerLat, centerLng
                )
                score += (2000.0 / (distToPrev + 10.0))
            }

            if (score > bestScore) {
                bestScore = score
                bestCluster = cluster
            }
        }

        return if (bestCluster.isNotEmpty()) bestCluster else sources
    }

    fun findConsensusCluster(
        sources: List<LocationSource>,
        radiusMeters: Double
    ): List<LocationSource> = findConsensusCluster(sources, null, radiusMeters)

    fun fuseCluster(cluster: List<LocationSource>): LocationSource {
        if (cluster.size == 1) return cluster.first()
        var totalWeight = 0.0
        var weightedLat = 0.0
        var weightedLng = 0.0
        var weightedSpeed = 0.0
        var maxTimestamp = 0L

        for (s in cluster) {
            val acc = s.accuracy.coerceAtLeast(1.0f)
            val weight = 1.0 / (acc * acc)
            totalWeight += weight
            weightedLat += s.latitude * weight
            weightedLng += s.longitude * weight
            weightedSpeed += s.speed * weight
            if (s.timestamp > maxTimestamp) {
                maxTimestamp = s.timestamp
            }
        }

        val fusedLat = weightedLat / totalWeight
        val fusedLng = weightedLng / totalWeight
        val fusedSpeed = (weightedSpeed / totalWeight).toFloat()
        val fusedAccuracy = (sqrt(1.0 / totalWeight).toFloat()).coerceAtLeast(3.0f)
        val representative = cluster.maxByOrNull { it.timestamp } ?: cluster.first()

        return LocationSource(
            busId = representative.busId,
            sourceId = "fused_cluster_${cluster.size}",
            sourceType = "SECONDARY_STUDENT",
            latitude = fusedLat,
            longitude = fusedLng,
            accuracy = fusedAccuracy,
            speed = fusedSpeed,
            bearing = representative.bearing,
            timestamp = maxTimestamp,
            active = true
        )
    }

    fun isDuplicateWrite(prev: LiveLocation?, candidate: LiveLocation): Boolean {
        if (prev == null) return false
        return prev.latitude == candidate.latitude &&
                prev.longitude == candidate.longitude &&
                prev.trackingStatus == candidate.trackingStatus &&
                (candidate.lastUpdated - prev.lastUpdated) < 3_000L
    }
}
