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

        private const val REQUEST_CODE_MORNING_START = 2001
        private const val REQUEST_CODE_MORNING_END = 2002
        private const val REQUEST_CODE_EVENING_START = 2003
        private const val REQUEST_CODE_EVENING_END = 2004

        fun isAutoTrackingEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_ENABLED, false)
        }

        fun getSavedSchedule(context: Context): RouteSchedule {
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
            if (!isEnabled) return

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
                        Log.i(TAG, "[AUTO-TRACK] $nowIso Starting driver tracking for bus $busId (mode: AUTO)")
                        TrackingServiceHelper.startTracking(context, busId, routeId, driverId, "AUTO")
                    }
                }
            } else {
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

            val now = LocalDateTime.now()

            if (schedule.morning.enabled) {
                scheduleSingleAlarm(context, alarmManager, schedule.morning.startTime, REQUEST_CODE_MORNING_START, now)
                scheduleSingleAlarm(context, alarmManager, schedule.morning.endTime, REQUEST_CODE_MORNING_END, now)
            }
            if (schedule.evening.enabled) {
                scheduleSingleAlarm(context, alarmManager, schedule.evening.startTime, REQUEST_CODE_EVENING_START, now)
                scheduleSingleAlarm(context, alarmManager, schedule.evening.endTime, REQUEST_CODE_EVENING_END, now)
            }
            Log.i(TAG, "Driver auto-tracking exact alarms scheduled for morning: ${schedule.morning.startTime}-${schedule.morning.endTime}, evening: ${schedule.evening.startTime}-${schedule.evening.endTime}")
        }

        private fun scheduleSingleAlarm(
            context: Context,
            alarmManager: AlarmManager,
            timeStr: String,
            requestCode: Int,
            now: LocalDateTime = LocalDateTime.now()
        ) {
            try {
                val time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
                var targetDateTime = LocalDateTime.of(now.toLocalDate(), time)
                if (!targetDateTime.isAfter(now)) {
                    targetDateTime = targetDateTime.plusDays(1)
                }

                val triggerMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val intent = Intent(context, DriverScheduleReceiver::class.java).apply {
                    action = ACTION_DRIVER_SCHEDULE_CHECK
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                Log.i(TAG, "[AUTO_TRACK] $nowIso alarm scheduled: target=$targetDateTime ($triggerMillis ms), requestCode=$requestCode, timeStr=$timeStr")

                val showIntent = Intent(context, com.college.bustracker.ui.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode + 10000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    val clockInfo = AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent)
                    alarmManager.setAlarmClock(clockInfo, pendingIntent)
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerMillis,
                                pendingIntent
                            )
                        } else {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerMillis,
                                pendingIntent
                            )
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    }
                }
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
                REQUEST_CODE_EVENING_END
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
