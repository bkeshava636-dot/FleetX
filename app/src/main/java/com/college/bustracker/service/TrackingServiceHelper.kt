package com.college.bustracker.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TrackingServiceHelper {

    const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
    const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
    const val EXTRA_BUS_ID = "EXTRA_BUS_ID"
    const val EXTRA_ROUTE_ID = "EXTRA_ROUTE_ID"
    const val EXTRA_DRIVER_ID = "EXTRA_DRIVER_ID"
    const val EXTRA_TRACKING_MODE = "EXTRA_TRACKING_MODE" // "AUTO" or "MANUAL"
    const val EXTRA_STOP_REASON = "EXTRA_STOP_REASON"
    const val EXTRA_STOP_CALLER = "EXTRA_STOP_CALLER"

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _activeBusId = MutableStateFlow<String?>(null)
    val activeBusId: StateFlow<String?> = _activeBusId.asStateFlow()

    private val _gpsStatus = MutableStateFlow("Disconnected")
    val gpsStatus: StateFlow<String> = _gpsStatus.asStateFlow()

    private val _lastUpdateTimestamp = MutableStateFlow(0L)
    val lastUpdateTimestamp: StateFlow<Long> = _lastUpdateTimestamp.asStateFlow()

    fun updateServiceState(isTracking: Boolean, busId: String?, gps: String, timestamp: Long) {
        _isTracking.value = isTracking
        _activeBusId.value = busId
        _gpsStatus.value = gps
        if (timestamp > 0) {
            _lastUpdateTimestamp.value = timestamp
        }
    }

    const val EXTRA_START_TRIGGER = "EXTRA_START_TRIGGER"

    fun startTracking(
        context: Context,
        busId: String,
        routeId: String,
        driverId: String,
        mode: String = "MANUAL",
        forceWindow: Boolean = false,
        startTrigger: String = "USER_OPENED_APP_START"
    ) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = ACTION_START_TRACKING
            putExtra(EXTRA_BUS_ID, busId)
            putExtra(EXTRA_ROUTE_ID, routeId)
            putExtra(EXTRA_DRIVER_ID, driverId)
            putExtra(EXTRA_TRACKING_MODE, mode)
            putExtra("EXTRA_FORCE_WINDOW", forceWindow)
            putExtra(EXTRA_START_TRIGGER, startTrigger)
        }
        AutoTrackingScheduler.clearWindowOverride(context)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopTracking(
        context: Context,
        reason: String = "STOP_TRACKING",
        caller: String = "TrackingServiceHelper"
    ) {
        val currentWinId = com.college.bustracker.util.ScheduleHelper.getCurrentWindowId()
        if (currentWinId != null) {
            AutoTrackingScheduler.setWindowOverriddenOff(context, currentWinId)
        }
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
            putExtra(EXTRA_STOP_REASON, reason)
            putExtra(EXTRA_STOP_CALLER, caller)
        }
        context.startService(intent)
    }
}
