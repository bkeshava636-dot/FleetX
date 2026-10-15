package com.college.bustracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.ScheduleHelper

class AutoTrackingBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutoTrackingBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            Log.i(TAG, "[AUTO_TRACK] $nowIso Device boot/package update ($action); restoring driver and student auto-tracking triggers")
            com.college.bustracker.diagnostics.GpsDiagnosticManager.onBootReceived(context, action ?: "UNKNOWN_BOOT_ACTION")

            // 0. Restore Active Tracking Session if device rebooted while tracking was on
            val persistentPrefs = context.getSharedPreferences(LocationTrackingService.PREFS_PERSISTENT_TRACKING, Context.MODE_PRIVATE)
            val wasSessionActive = persistentPrefs.getBoolean(LocationTrackingService.KEY_IS_SESSION_ACTIVE, false)
            val savedBusId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_BUS_ID, "") ?: ""
            if (wasSessionActive && savedBusId.isNotBlank() && AutoTrackingScheduler.isDriverLoggedIn(context)) {
                val savedRouteId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_ROUTE_ID, "") ?: ""
                val savedDriverId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_DRIVER_ID, "") ?: ""
                val savedMode = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_MODE, "MANUAL") ?: "MANUAL"
                Log.i(TAG, "[AUTO_TRACK] Device rebooted during active tracking session. Restoring tracking for $savedBusId")
                TrackingServiceHelper.startTracking(
                    context = context,
                    busId = savedBusId,
                    routeId = savedRouteId,
                    driverId = savedDriverId,
                    mode = savedMode,
                    forceWindow = true
                )
            }

            // 1. Restore Driver Auto-Tracking (if enabled and driver is logged in)
            val isDriverAutoEnabled = AutoTrackingScheduler.isAutoTrackingEnabled(context)
            if (isDriverAutoEnabled && AutoTrackingScheduler.isDriverLoggedIn(context)) {
                val schedule = AutoTrackingScheduler.getSavedSchedule(context)
                AutoTrackingScheduler.scheduleAlarms(context, schedule)
                AutoTrackingScheduler.checkAndTriggerAutoTracking(context)
            }

            // 2. Restore Student Auto-Tracking (if enabled)
            val studentPrefs = context.getSharedPreferences(StudentAutoTrackingScheduler.PREFS_NAME, Context.MODE_PRIVATE)
            val isStudentAutoEnabled = studentPrefs.getBoolean("auto_sharing_enabled", false)
            val studentBusId = studentPrefs.getString("auto_selected_bus_id", null)

            if (isStudentAutoEnabled && !studentBusId.isNullOrBlank()) {
                val studentSchedule = StudentAutoTrackingScheduler.getSavedSchedule(context)
                StudentAutoTrackingScheduler.scheduleAlarms(context, studentSchedule)

                if (ScheduleHelper.isWithinOperatingWindow(studentSchedule) &&
                    CollegeCalendarService.isWorkingDay()
                ) {
                    com.college.bustracker.util.AutomaticRideManager.init(context)
                    com.college.bustracker.util.AutomaticRideManager.startMonitoring(context)
                }
            }
        }
    }
}
