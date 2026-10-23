package com.college.bustracker.ui.admin

import com.college.bustracker.data.model.BusStop

/**
 * Pure helper functions for map-based stop creation, editing, reordering, and renumbering.
 * Completely testable without Android/UI dependencies.
 */
object MapStopLogic {

    /**
     * Adds a new stop to the list with sequence = current size + 1.
     */
    fun addStop(
        existingStops: List<BusStop>,
        name: String,
        latitude: Double,
        longitude: Double,
        customId: String? = null,
        visibleOnMap: Boolean = true
    ): List<BusStop> {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Stop name cannot be blank." }
        if (visibleOnMap && (latitude != 0.0 || longitude != 0.0)) {
            require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
            require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        }

        val nextOrder = existingStops.size + 1
        val newStopId = customId ?: "STOP-${System.currentTimeMillis() % 100000}-$nextOrder"
        val newStop = BusStop(
            stopId = newStopId,
            name = cleanName,
            latitude = if (visibleOnMap) latitude else 0.0,
            longitude = if (visibleOnMap) longitude else 0.0,
            order = nextOrder,
            active = true,
            visibleOnMap = visibleOnMap
        )
        return existingStops + newStop
    }

    /**
     * Updates an existing stop's name or coordinates, preserving its original stopId and active status.
     */
    fun updateStop(
        existingStops: List<BusStop>,
        stopId: String,
        newName: String,
        newLatitude: Double? = null,
        newLongitude: Double? = null,
        newVisibleOnMap: Boolean? = null
    ): List<BusStop> {
        val cleanName = newName.trim()
        require(cleanName.isNotBlank()) { "Stop name cannot be blank." }
        if (newLatitude != null && (newVisibleOnMap != false)) {
            require(newLatitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        }
        if (newLongitude != null && (newVisibleOnMap != false)) {
            require(newLongitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        }

        return existingStops.map { stop ->
            if (stop.stopId == stopId) {
                stop.copy(
                    name = cleanName,
                    latitude = newLatitude ?: stop.latitude,
                    longitude = newLongitude ?: stop.longitude,
                    visibleOnMap = newVisibleOnMap ?: stop.visibleOnMap
                )
            } else {
                stop
            }
        }
    }

    /**
     * Updates only the coordinates of a stop (e.g. after dragging a map marker).
     */
    fun updateCoordinates(
        existingStops: List<BusStop>,
        stopId: String,
        latitude: Double,
        longitude: Double
    ): List<BusStop> {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }

        return existingStops.map { stop ->
            if (stop.stopId == stopId) {
                stop.copy(latitude = latitude, longitude = longitude)
            } else {
                stop
            }
        }
    }

    /**
     * Deletes a stop by ID and renumbers all remaining stops so their sequence is strictly 1..N.
     */
    fun deleteStop(
        existingStops: List<BusStop>,
        stopId: String
    ): List<BusStop> {
        val remaining = existingStops.filter { it.stopId != stopId }
        return renumber(remaining)
    }

    /**
     * Moves a stop up or down in the sequence and renumbers all stops.
     */
    fun moveStop(
        existingStops: List<BusStop>,
        fromIndex: Int,
        toIndex: Int
    ): List<BusStop> {
        if (fromIndex !in existingStops.indices || toIndex !in existingStops.indices || fromIndex == toIndex) {
            return existingStops
        }
        val mutable = existingStops.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return renumber(mutable)
    }

    /**
     * Renumbers stops sequentially starting at 1.
     */
    fun renumber(stops: List<BusStop>): List<BusStop> {
        return stops.mapIndexed { index, stop ->
            stop.copy(order = index + 1)
        }
    }

    /**
     * Validates stops before saving to Firestore.
     */
    fun validateForSave(stops: List<BusStop>): Result<Unit> {
        if (stops.isEmpty()) {
            return Result.failure(IllegalArgumentException("Route cannot be empty. Must have at least 2 stops."))
        }
        if (stops.size < 2) {
            return Result.failure(IllegalArgumentException("Route must have at least 2 stops."))
        }
        stops.forEachIndexed { index, stop ->
            if (stop.name.trim().isBlank()) {
                return Result.failure(IllegalArgumentException("Stop #${index + 1} has an empty name."))
            }
            if (stop.visibleOnMap) {
                if (stop.latitude !in -90.0..90.0 || stop.longitude !in -180.0..180.0) {
                    return Result.failure(IllegalArgumentException("Stop #${index + 1} has invalid coordinates."))
                }
            }
            if (stop.stopId.isBlank()) {
                return Result.failure(IllegalArgumentException("Stop #${index + 1} is missing a stopId."))
            }
        }
        return Result.success(Unit)
    }
}
