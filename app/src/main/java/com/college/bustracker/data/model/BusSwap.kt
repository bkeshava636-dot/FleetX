package com.college.bustracker.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BusSwap(
    val swapId: String = "",
    val originalBusId: String = "",
    val replacementBusId: String = "",
    val routeId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val scopeType: String = SCOPE_ALL_STOPS, // "ALL_STOPS" or "SELECTED_STOPS"
    val affectedStopIds: List<String> = emptyList(),
    val message: String = "",
    val status: String = STATUS_ACTIVE, // ACTIVE, RESOLVED, CANCELLED
    val date: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0L,
    val cancelledAt: Long = 0L,
    val cancelledByUid: String = "",
    val createdByUid: String = "",
    val notificationSentAt: Any? = null,
    val notifiedStopIds: List<String> = emptyList(),
    val cancellationNotified: Boolean = false,
    val cancellationNotifiedAt: Any? = null
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_RESOLVED = "RESOLVED"
        const val STATUS_CANCELLED = "CANCELLED"

        const val SCOPE_ALL_STOPS = "ALL_STOPS"
        const val SCOPE_SELECTED_STOPS = "SELECTED_STOPS"

        fun filterEligibleReplacementBuses(
            allBuses: List<Bus>,
            assignedBus: Bus?
        ): List<Bus> {
            val assignedId = assignedBus?.busId?.trim()?.uppercase() ?: ""
            val assignedNum = assignedBus?.busNumber?.trim()?.uppercase() ?: ""
            return allBuses
                .filter { bus ->
                    bus.active &&
                    bus.busId.trim().uppercase() != assignedId &&
                    bus.busNumber.trim().uppercase() != assignedNum &&
                    (assignedId.isBlank() || !bus.busNumber.trim().equals(assignedId, ignoreCase = true)) &&
                    (assignedNum.isBlank() || !bus.busId.trim().equals(assignedNum, ignoreCase = true))
                }
                .sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))
        }
    }

    fun isAffectingStop(stopId: String?): Boolean {
        if (stopId.isNullOrBlank()) return false
        if (scopeType == SCOPE_SELECTED_STOPS) return affectedStopIds.contains(stopId)
        if (affectedStopIds.isNotEmpty()) return affectedStopIds.contains(stopId)
        return true
    }
}
