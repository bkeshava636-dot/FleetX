package com.college.bustracker.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.college.bustracker.diagnostics.GpsDiagnosticManager
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.ScheduleHelper
import com.google.firebase.auth.FirebaseAuth

/**
 * System-Bound Notification Listener Service.
 *
 * On aggressive OEM ROMs (Vivo Funtouch OS, Xiaomi HyperOS/MIUI, Oppo ColorOS),
 * NotificationListenerService is bound directly by system_server (NotificationManagerService).
 * Because system_server maintains a persistent IPC binding to this service, the process
 * is granted elevated oom_adj priority and is immune to Funtouch OS's background force-stop.
 *
 * Even if the user swipes FleetX from Recent Apps, system_server keeps this service bound
 * or immediately rebinds it via onListenerConnected(), bypassing Vivo's PemService / Autostart blocker.
 */
class ShiftKeepAliveNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "ShiftKeepAliveService"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "[SYSTEM_SERVER_BIND] NotificationListenerService connected by system_server. Evaluating tracking state...")
        checkAndResurrectTrackingIfNeeded("SYSTEM_NOTIFICATION_LISTENER_CONNECTED")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.id == LocationTrackingService.NOTIFICATION_ID) {
            Log.w(TAG, "[NOTIFICATION_REMOVED] FleetX tracking notification was removed. Evaluating resurrection...")
            checkAndResurrectTrackingIfNeeded("TRACKING_NOTIFICATION_REMOVED")
        }
    }

    private fun checkAndResurrectTrackingIfNeeded(callerReason: String) {
        val context = applicationContext
        if (!AutoTrackingScheduler.isDriverLoggedIn(context)) {
            Log.d(TAG, "Driver not logged in; ignoring resurrection check ($callerReason)")
            return
        }

        // Check if tracking is already active in memory
        if (TrackingServiceHelper.isTracking.value) {
            Log.d(TAG, "Tracking is already active; no resurrection needed ($callerReason)")
            return
        }

        // Check 1: Was an explicit tracking session active in persistent prefs?
        val persistentPrefs = context.getSharedPreferences(
            LocationTrackingService.PREFS_PERSISTENT_TRACKING,
            Context.MODE_PRIVATE
        )
        val isPersistentSessionActive = persistentPrefs.getBoolean(
            LocationTrackingService.KEY_IS_SESSION_ACTIVE,
            false
        )
        val savedBusId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_BUS_ID, "") ?: ""
        val savedRouteId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_ROUTE_ID, "") ?: ""
        val savedDriverId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_DRIVER_ID, "") ?: ""
        val savedMode = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_MODE, "MANUAL") ?: "MANUAL"

        if (isPersistentSessionActive && savedBusId.isNotBlank()) {
            Log.i(TAG, "[KEEP_ALIVE_RESURRECT] Resurrecting persistent tracking session for $savedBusId (caller: $callerReason)")
            GpsDiagnosticManager.logEvent(
                context = context,
                stage = com.college.bustracker.diagnostics.DiagnosticStage.SERVICE_START,
                eventName = "SYSTEM_BOUND_LISTENER_RESURRECTION",
                details = mapOf(
                    "busId" to savedBusId,
                    "caller" to callerReason,
                    "mode" to savedMode
                )
            )
            try {
                TrackingServiceHelper.startTracking(
                    context = context,
                    busId = savedBusId,
                    routeId = savedRouteId,
                    driverId = savedDriverId,
                    mode = savedMode,
                    forceWindow = true,
                    startTrigger = "SYSTEM_NOTIFICATION_LISTENER_REBIND"
                )
            } catch (e: Exception) {
                Log.e(TAG, "[KEEP_ALIVE_RESURRECT] Failed to start tracking: ${e.message}")
            }
            return
        }

        // Check 2: Are we currently inside an active scheduled operating window with auto-tracking enabled?
        val autoPrefs = context.getSharedPreferences(AutoTrackingScheduler.PREF_FILE, Context.MODE_PRIVATE)
        val isAutoEnabled = autoPrefs.getBoolean(AutoTrackingScheduler.KEY_AUTO_ENABLED, false)
        val assignedBus = autoPrefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_BUS, "") ?: ""
        val assignedRoute = autoPrefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_ROUTE, "ROUTE-01") ?: "ROUTE-01"

        if (isAutoEnabled && assignedBus.isNotBlank() && ScheduleHelper.isWithinOperatingWindow() && CollegeCalendarService.isWorkingDay()) {
            Log.i(TAG, "[KEEP_ALIVE_RESURRECT] Operating window is active and auto-tracking enabled for $assignedBus. Starting service...")
            GpsDiagnosticManager.logEvent(
                context = context,
                stage = com.college.bustracker.diagnostics.DiagnosticStage.SERVICE_START,
                eventName = "SYSTEM_BOUND_LISTENER_SCHEDULED_START",
                details = mapOf(
                    "busId" to assignedBus,
                    "caller" to callerReason,
                    "mode" to "AUTO"
                )
            )
            try {
                val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                TrackingServiceHelper.startTracking(
                    context = context,
                    busId = assignedBus,
                    routeId = assignedRoute,
                    driverId = authUid,
                    mode = "AUTO",
                    forceWindow = false,
                    startTrigger = "SYSTEM_NOTIFICATION_LISTENER_REBIND"
                )
            } catch (e: Exception) {
                Log.e(TAG, "[KEEP_ALIVE_RESURRECT] Failed to start auto-tracking: ${e.message}")
            }
        }
    }
}
