package com.college.bustracker.ui.student

import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile

data class DriverDisplayInfo(
    val busTitle: String = "—",
    val driverName: String = "Driver not assigned",
    val phoneNumber: String? = null,
    val isAssigned: Boolean = false,
    val isReplacement: Boolean = false
)

object DriverInfoLogic {

    fun resolveDriver(
        selectedBus: Bus?,
        activeSwap: BusSwap?,
        allBuses: List<Bus>,
        allDrivers: List<UserProfile>,
        activeTemporaryAssignment: TemporaryAssignment? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): DriverDisplayInfo {
        if (selectedBus == null) {
            return DriverDisplayInfo(
                busTitle = "—",
                driverName = "Driver not assigned",
                phoneNumber = null,
                isAssigned = false,
                isReplacement = false
            )
        }

        val isSwapActive = activeSwap != null &&
                activeSwap.status == "ACTIVE" &&
                activeSwap.originalBusId.equals(selectedBus.busId, ignoreCase = true) &&
                activeSwap.replacementBusId.isNotBlank()

        val targetBusId = if (isSwapActive) activeSwap!!.replacementBusId else selectedBus.busId
        val targetBus = allBuses.find { it.busId.equals(targetBusId, ignoreCase = true) }

        val displayBusTitle = if (isSwapActive) {
            val replBusNumber = targetBus?.busNumber ?: targetBusId
            "$replBusNumber (Replacement)"
        } else {
            selectedBus.busNumber.ifBlank { selectedBus.busId }
        }

        // 1. Temporary Replacement Overlay has highest operator precedence for the target bus
        if (activeTemporaryAssignment != null &&
            activeTemporaryAssignment.isActive(nowMillis) &&
            activeTemporaryAssignment.busId.equals(targetBusId, ignoreCase = true)
        ) {
            return DriverDisplayInfo(
                busTitle = displayBusTitle,
                driverName = activeTemporaryAssignment.replacementDriverName.ifBlank { "Driver" },
                phoneNumber = activeTemporaryAssignment.replacementDriverPhone?.takeIf { it.isNotBlank() },
                isAssigned = true,
                isReplacement = true
            )
        }

        // 2. Search active drivers assigned to this target bus
        // Match either assignedBusId == targetBusId OR driver.uid == targetBus.driverId
        val matchingDriver = allDrivers.find { driver ->
            driver.active && (
                driver.assignedBusId.equals(targetBusId, ignoreCase = true) ||
                (!targetBus?.driverId.isNullOrBlank() && driver.uid == targetBus?.driverId)
            )
        }

        return when {
            matchingDriver != null -> {
                DriverDisplayInfo(
                    busTitle = displayBusTitle,
                    driverName = matchingDriver.name.ifBlank { "Driver" },
                    phoneNumber = matchingDriver.phoneNumber?.takeIf { it.isNotBlank() },
                    isAssigned = true,
                    isReplacement = isSwapActive
                )
            }
            !targetBus?.driverName.isNullOrBlank() -> {
                DriverDisplayInfo(
                    busTitle = displayBusTitle,
                    driverName = targetBus!!.driverName!!,
                    phoneNumber = null,
                    isAssigned = true,
                    isReplacement = isSwapActive
                )
            }
            else -> {
                DriverDisplayInfo(
                    busTitle = displayBusTitle,
                    driverName = "Driver not assigned",
                    phoneNumber = null,
                    isAssigned = false,
                    isReplacement = isSwapActive
                )
            }
        }
    }

    /**
     * Builds the RFC 3966 telephone URI string for ACTION_DIAL.
     */
    fun getDialUri(phoneNumber: String): String {
        return "tel:${phoneNumber.trim()}"
    }
}
