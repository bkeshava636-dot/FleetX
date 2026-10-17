package com.college.bustracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.ScheduleHelper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AutoTrackingScheduler {

    companion object {
        private const val TAG = "AutoTrackingScheduler"
        const val PREF_FILE = "auto_tracking_prefs"
        const val KEY_AUTO_ENABLED = "key_auto_enabled"
        const val KEY_ASSIGNED_BUS = "key_assigned_bus"
        const val KEY_ASSIGNED_ROUTE = "key_assigned_route"
        const val KEY_DRIVER_ID = "key_driver_id"

        const val KEY_MORNING_ENABLED = "key_morning_enabled"
        const val KEY_MORNING_START = "key_morning_start"
        const val KEY_MORNING_END = "key_morning_end"
        const val KEY_EVENING_ENABLED = "key_evening_enabled"
        const val KEY_EVENING_START = "key_evening_start"
        const val KEY_EVENING_END = "key_evening_end"

        const val ACTION_DRIVER_SCHEDULE_CHECK = "com.college.bustracker.ACTION_DRIVER_SCHEDULE_CHECK"
        const val ACTION_ALARM_CHECK = "com.college.bustracker.ACTION_SCHEDULE_CHECK" // Backward compatibility

        const val REQUEST_CODE_MORNING_START = 2001
        const val REQUEST_CODE_MORNING_END = 2002
        const val REQUEST_CODE_EVENING_START = 2003
        const val REQUEST_CODE_EVENING_END = 2004
        const val REQUEST_CODE_MORNING_PRE_START = 2005
        const val REQUEST_CODE_EVENING_PRE_START = 2006

        const val KEY_OVERRIDDEN_OFF_WINDOW_ID = "key_overridden_off_window_id"

        fun setWindowOverriddenOff(context: Context, windowId: String) {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_OVERRIDDEN_OFF_WINDOW_ID, windowId).apply()
            Log.i(TAG, "[OVERRIDE] Operating window $windowId marked as OVERRIDDEN_OFF by user/system")
        }

        fun clearWindowOverride(context: Context) {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_OVERRIDDEN_OFF_WINDOW_ID)) {
                prefs.edit().remove(KEY_OVERRIDDEN_OFF_WINDOW_ID).apply()
                Log.i(TAG, "[OVERRIDE] Cleared window override; tracking free to resume")
            }
        }

        fun isWindowOverriddenOff(context: Context): Boolean {
            val currentWindowId = com.college.bustracker.util.ScheduleHelper.getCurrentWindowId() ?: return false
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val overriddenId = prefs.getString(KEY_OVERRIDDEN_OFF_WINDOW_ID, null)
            return overriddenId == currentWindowId
        }

        fun calculatePreStartTime(startTimeStr: String, leadMinutes: Long = 2L): String {
            return try {
                val time = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"))
                time.minusMinutes(leadMinutes).format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) {
                startTimeStr
            }
        }

        fun isDriverLoggedIn(context: Context): Boolean {
            val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val staffPrefs = context.getSharedPreferences("staff_session_prefs", Context.MODE_PRIVATE)
            val sessionUid = staffPrefs.getString("session_uid", null)
            val sessionRole = staffPrefs.getString("session_role", null)

            // If Firebase Auth is signed out AND staff_session_prefs is empty/null, driver is NOT logged in
            if (authUser == null && sessionUid.isNullOrBlank()) {
                return false
            }
            // If a session role is present, verify it's a driver (or admin)
            if (!sessionRole.isNullOrBlank() && sessionRole != "DRIVER" && sessionRole != "ADMIN") {
                return false
            }
            return true
        }

        fun onDriverLogout(context: Context) {
            val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            Log.i(TAG, "[AUTO_TRACK] $nowIso onDriverLogout: driver logged out. Disabling auto-tracking, cancelling alarms, and stopping tracking service.")

            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_AUTO_ENABLED, false)
                .remove(KEY_ASSIGNED_BUS)
                .remove(KEY_ASSIGNED_ROUTE)
                .remove(KEY_DRIVER_ID)
                .apply()

            cancelAlarms(context)
            DriverScheduleReceiver.dismissLocationDisabledNotification(context)
            DriverScheduleReceiver.dismissLocationPermissionMissingNotification(context)
            DriverScheduleReceiver.dismissBackgroundLocationPermissionMissingNotification(context)

            if (TrackingServiceHelper.isTracking.value) {
                TrackingServiceHelper.stopTracking(context, "DRIVER_LOGGED_OUT", "AutoTrackingScheduler.onDriverLogout")
            }

            com.college.bustracker.diagnostics.GpsDiagnosticManager.onTrackingPreferenceChanged(context, false, "")
            com.college.bustracker.diagnostics.GpsDiagnosticManager.stopWindowRecording(context, "Driver logged out")
            com.college.bustracker.diagnostics.GpsDiagnosticManager.stopRecording(context)
        }

        fun isAutoTrackingEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_ENABLED, false)
        }

        fun getSavedSchedule(context: Context? = null): RouteSchedule {
            return com.college.bustracker.util.OperatingScheduleService.getSchedule()
        }

        fun saveSchedule(context: Context, schedule: RouteSchedule) {
            com.college.bustracker.util.OperatingScheduleService.updateCachedSchedule(schedule, context)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_MORNING_ENABLED, schedule.morning.enabled)
                .putString(KEY_MORNING_START, schedule.morning.startTime)
                .putString(KEY_MORNING_END, schedule.morning.endTime)
                .putBoolean(KEY_EVENING_ENABLED, schedule.evening.enabled)
                .putString(KEY_EVENING_START, schedule.evening.startTime)
                .putString(KEY_EVENING_END, schedule.evening.endTime)
                .apply()
        }

        fun setAutoTrackingEnabled(
            context: Context,
            enabled: Boolean,
            busId: String,
            routeId: String,
            driverId: String,
            schedule: RouteSchedule = com.college.bustracker.util.OperatingScheduleService.getSchedule()
        ) {
            val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_AUTO_ENABLED, enabled)
                .putString(KEY_ASSIGNED_BUS, busId)
                .putString(KEY_ASSIGNED_ROUTE, routeId)
                .putString(KEY_DRIVER_ID, driverId)
                .apply()

            saveSchedule(context, schedule)
            com.college.bustracker.diagnostics.GpsDiagnosticManager.onTrackingPreferenceChanged(context, enabled, busId)

            if (enabled) {
                Log.i(TAG, "[AUTO_TRACK] $nowIso schedule enabled: busId=$busId, routeId=$routeId, driverId=$driverId, morning=${schedule.morning.startTime}-${schedule.morning.endTime} (enabled=${schedule.morning.enabled}), evening=${schedule.evening.startTime}-${schedule.evening.endTime} (enabled=${schedule.evening.enabled})")
                scheduleAlarms(context, schedule)
                checkAndTriggerAutoTracking(context)
            } else {
                Log.i(TAG, "[AUTO_TRACK] $nowIso schedule disabled")
                cancelAlarms(context)
                DriverScheduleReceiver.dismissLocationDisabledNotification(context)
                if (TrackingServiceHelper.isTracking.value) {
                    TrackingServiceHelper.stopTracking(context)
                }
            }
        }

        /**
         * Checks the current schedule and operating window, starting or stopping tracking
         * as appropriate. Also detects if device Location Services are turned off and alerts driver.
         */
        fun checkAndTriggerAutoTracking(context: Context) {
            val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, false)
            val isDriverActive = isDriverLoggedIn(context)

            if (!isEnabled || !isDriverActive) {
                if (TrackingServiceHelper.isTracking.value) {
                    Log.i(TAG, "[AUTO-TRACK] $nowIso Stopping driver tracking: auto-tracking disabled ($isEnabled) or driver logged out ($isDriverActive)")
                    TrackingServiceHelper.stopTracking(context, "DRIVER_LOGGED_OUT", "AutoTrackingScheduler")
                }
                return
            }

            var busId = prefs.getString(KEY_ASSIGNED_BUS, "") ?: ""
            var routeId = prefs.getString(KEY_ASSIGNED_ROUTE, "") ?: ""
            var driverId = prefs.getString(KEY_DRIVER_ID, "") ?: ""

            val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (!authUid.isNullOrBlank()) {
                driverId = authUid
            } else if (driverId.isBlank()) {
                val staffPrefs = context.getSharedPreferences("staff_session_prefs", Context.MODE_PRIVATE)
                driverId = staffPrefs.getString("session_uid", "") ?: ""
            }

            if (busId.isBlank()) {
                val staffPrefs = context.getSharedPreferences("staff_session_prefs", Context.MODE_PRIVATE)
                busId = staffPrefs.getString("session_assigned_bus", "") ?: ""
            }
            if (routeId.isBlank()) {
                routeId = "ROUTE-01"
            }

            if (busId.isBlank()) return

            val schedule = getSavedSchedule(context)
            val isWorkingDay = CollegeCalendarService.isWorkingDay()
            val isOperatingNow = ScheduleHelper.isWithinOperatingWindow(schedule) && isWorkingDay

            Log.i(TAG, "[AUTO-TRACK] $nowIso schedule start check: isOperatingNow=$isOperatingNow, isWorkingDay=$isWorkingDay, busId=$busId, driverId=$driverId")

            if (isOperatingNow) {
                com.college.bustracker.diagnostics.GpsDiagnosticManager.startWindowRecording(
                    context = context,
                    reason = "Operating window active check",
                    busId = busId,
                    driverId = driverId
                )

                if (!DriverScheduleReceiver.hasLocationPermission(context)) {
                    Log.w(TAG, "[AUTO-TRACK] $nowIso Location permission missing for driver auto-tracking")
                    DriverScheduleReceiver.showLocationPermissionMissingNotification(context)
                    return
                }
                DriverScheduleReceiver.dismissLocationPermissionMissingNotification(context)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !DriverScheduleReceiver.hasBackgroundLocationPermission(context)) {
                    Log.w(TAG, "[AUTO-TRACK] $nowIso Background location permission ('Allow all the time') missing for driver auto-tracking")
                    DriverScheduleReceiver.showBackgroundLocationPermissionMissingNotification(context)
                    return
                }
                DriverScheduleReceiver.dismissBackgroundLocationPermissionMissingNotification(context)

                if (!DriverScheduleReceiver.isLocationEnabled(context)) {
                    Log.w(TAG, "[AUTO-TRACK] $nowIso Location Services disabled during operating window")
                    DriverScheduleReceiver.showLocationDisabledNotification(context)
                } else {
                    DriverScheduleReceiver.dismissLocationDisabledNotification(context)
                    if (!TrackingServiceHelper.isTracking.value) {
                        if (isWindowOverriddenOff(context)) {
                            Log.i(TAG, "[AUTO-TRACK] $nowIso Operating window active, but OVERRIDDEN_OFF by driver; skipping auto-start")
                        } else {
                            Log.i(TAG, "[AUTO-TRACK] $nowIso Starting driver tracking for bus $busId (mode: AUTO)")
                            TrackingServiceHelper.startTracking(context, busId, routeId, driverId, "AUTO")
                        }
                    }
                }
            } else {
                com.college.bustracker.diagnostics.GpsDiagnosticManager.stopWindowRecording(
                    context = context,
                    reason = "Outside operating window"
                )
                if (TrackingServiceHelper.isTracking.value) {
                    Log.i(TAG, "[AUTO-TRACK] $nowIso Stopping driver tracking: outside operating window")
                    TrackingServiceHelper.stopTracking(context)
                }
                DriverScheduleReceiver.dismissLocationDisabledNotification(context)
                DriverScheduleReceiver.dismissLocationPermissionMissingNotification(context)
            }
        }

        fun scheduleAlarms(
            context: Context,
            schedule: RouteSchedule = com.college.bustracker.util.OperatingScheduleService.getSchedule()
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            saveSchedule(context, schedule)
            cancelAlarms(context)

            val now = java.time.ZonedDateTime.now(com.college.bustracker.util.ScheduleHelper.KOLKATA_ZONE).toLocalDateTime()

            if (schedule.morning.enabled) {
                val preMorningStart = calculatePreStartTime(schedule.morning.startTime, 2L)
                scheduleSingleAlarm(context, alarmManager, preMorningStart, REQUEST_CODE_MORNING_PRE_START, now, isPreWindow = true)
                scheduleSingleAlarm(context, alarmManager, schedule.morning.startTime, REQUEST_CODE_MORNING_START, now)
                scheduleSingleAlarm(context, alarmManager, schedule.morning.endTime, REQUEST_CODE_MORNING_END, now)
            }
            if (schedule.evening.enabled) {
                val preEveningStart = calculatePreStartTime(schedule.evening.startTime, 2L)
                scheduleSingleAlarm(context, alarmManager, preEveningStart, REQUEST_CODE_EVENING_PRE_START, now, isPreWindow = true)
                scheduleSingleAlarm(context, alarmManager, schedule.evening.startTime, REQUEST_CODE_EVENING_START, now)
                scheduleSingleAlarm(context, alarmManager, schedule.evening.endTime, REQUEST_CODE_EVENING_END, now)
            }
            Log.i(TAG, "Driver auto-tracking exact alarms scheduled with pre-window diagnostics (2 min lead)")
        }

        private fun scheduleSingleAlarm(
            context: Context,
            alarmManager: AlarmManager,
            timeStr: String,
            requestCode: Int,
            now: LocalDateTime = java.time.ZonedDateTime.now(com.college.bustracker.util.ScheduleHelper.KOLKATA_ZONE).toLocalDateTime(),
            isPreWindow: Boolean = false
        ) {
            try {
                val time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
                var targetDateTime = LocalDateTime.of(now.toLocalDate(), time)
                if (!targetDateTime.isAfter(now)) {
                    targetDateTime = targetDateTime.plusDays(1)
                }

                val triggerMillis = targetDateTime.atZone(com.college.bustracker.util.ScheduleHelper.KOLKATA_ZONE).toInstant().toEpochMilli()

                val intent = Intent(context, DriverScheduleReceiver::class.java).apply {
                    action = ACTION_DRIVER_SCHEDULE_CHECK
                    putExtra("EXTRA_EXPECTED_TRIGGER_MILLIS", triggerMillis)
                    putExtra("EXTRA_REQUEST_CODE", requestCode)
                    putExtra("EXTRA_TIME_STR", timeStr)
                    putExtra("EXTRA_IS_PRE_WINDOW", isPreWindow)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val showIntent = Intent(context, com.college.bustracker.ui.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode + 10000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                var alarmMethod = "setAlarmClock"
                var isExact = true
                try {
                    val clockInfo = AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent)
                    alarmManager.setAlarmClock(clockInfo, pendingIntent)
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmMethod = "setExactAndAllowWhileIdle"
                            isExact = true
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerMillis,
                                pendingIntent
                            )
                        } else {
                            alarmMethod = "setAndAllowWhileIdle"
                            isExact = false
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerMillis,
                                pendingIntent
                            )
                        }
                    } else {
                        alarmMethod = "setExactAndAllowWhileIdle"
                        isExact = true
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    }
                }

                val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                Log.i(TAG, "[AUTO_TRACK] $nowIso alarm scheduled: target=$targetDateTime ($triggerMillis ms), requestCode=$requestCode, timeStr=$timeStr, preWindow=$isPreWindow, method=$alarmMethod, exact=$isExact")
                com.college.bustracker.diagnostics.GpsDiagnosticManager.onAlarmScheduled(
                    context = context,
                    timeStr = timeStr,
                    triggerMillis = triggerMillis,
                    requestCode = requestCode,
                    isExact = isExact,
                    alarmMethod = alarmMethod
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule driver alarm for $timeStr: ${e.message}", e)
            }
        }

        fun cancelAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val requestCodes = listOf(
                REQUEST_CODE_MORNING_START,
                REQUEST_CODE_MORNING_END,
                REQUEST_CODE_EVENING_START,
                REQUEST_CODE_EVENING_END,
                REQUEST_CODE_MORNING_PRE_START,
                REQUEST_CODE_EVENING_PRE_START
            )
            for (code in requestCodes) {
                val intent = Intent(context, DriverScheduleReceiver::class.java).apply {
                    action = ACTION_DRIVER_SCHEDULE_CHECK
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    code,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pi != null) {
                    alarmManager.cancel(pi)
                }
            }
            Log.i(TAG, "Driver auto-tracking alarms cancelled")
        }
    }

    /**
     * Backward-compatible receiver that forwards to DriverScheduleReceiver.
     */
    class ScheduleCheckReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            DriverScheduleReceiver().onReceive(context, intent)
        }
    }
}
