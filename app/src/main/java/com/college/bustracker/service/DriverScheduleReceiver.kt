package com.college.bustracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.ScheduleHelper

/**
 * Dedicated top-level BroadcastReceiver triggered by AlarmManager at driver schedule boundaries.
 * Operates under a partial WakeLock to reliably start or stop automatic driver tracking
 * even after app process death, swiping from recents, or device reboot.
 */
class DriverScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "DriverScheduleReceiver"
        const val NOTIFICATION_CHANNEL_ID = "driver_tracking_alerts_channel"
        const val NOTIFICATION_ID_LOCATION_OFF = 5001
        const val NOTIFICATION_ID_PERMISSION_MISSING = 5003

        fun isLocationEnabled(context: Context): Boolean {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            return LocationManagerCompat.isLocationEnabled(lm)
        }

        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun hasCoarseLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun dismissBackgroundLocationPermissionMissingNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID_PERMISSION_MISSING)
        }

        fun showLocationPermissionMissingNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Driver Schedule Tracking Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts driver when tracking is blocked due to missing location permission"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, com.college.bustracker.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_PERMISSION_MISSING,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Location Permission Required")
                .setContentText("Automatic bus tracking cannot start without location permission.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("FleetX automatic tracking scheduled window is active, but location permission has not been granted. Tap to open FleetX and grant location permissions.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(NOTIFICATION_ID_PERMISSION_MISSING, notification)
        }

        fun showBackgroundLocationPermissionMissingNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Driver Schedule Tracking Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts driver when background tracking is blocked due to missing background location permission"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_PERMISSION_MISSING,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Set Location to 'Allow all the time'")
                .setContentText("Automatic bus tracking requires 'Allow all the time' location permission.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("FleetX automatic tracking scheduled window is active, but location access in background is restricted. Tap here to open App Permissions -> Location and select 'Allow all the time'.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(NOTIFICATION_ID_PERMISSION_MISSING, notification)
        }

        fun dismissLocationPermissionMissingNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID_PERMISSION_MISSING)
        }

        fun showLocationDisabledNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Driver Schedule Tracking Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts driver when tracking is paused due to disabled device location services"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_LOCATION_OFF,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Location Services Disabled")
                .setContentText("Bus GPS tracking is paused. Tap here to turn on Location Services.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Bus GPS tracking is paused because device Location Services are turned off. Tap to open Settings and turn on Location so students can see your bus.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(NOTIFICATION_ID_LOCATION_OFF, notification)
        }

        fun dismissLocationDisabledNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID_LOCATION_OFF)
        }

        fun showStartTrackingPromptNotification(
            context: Context,
            busId: String,
            routeId: String,
            driverId: String
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Driver Schedule Tracking Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts driver when shift window starts"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, com.college.bustracker.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                5002,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Shift Operating Window Active")
                .setContentText("Tap to ensure bus tracking is broadcasting live GPS for $busId.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Your scheduled shift operating window is active. Tap here to open FleetX and start live tracking for bus $busId.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(5002, notification)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val pid = android.os.Process.myPid()
        Log.i(TAG, "[AUTO-TRACE] Receiver entered: action=${intent?.action}, pid=$pid")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CollegeBus:DriverScheduleWakeLock")
        wakeLock?.acquire(15_000L) // 15-second safety timeout
        Log.i(TAG, "[AUTO-TRACK] $nowIso wakelock acquired")

        try {
            if (intent?.action == "com.college.bustracker.ACTION_CONFIGURE_SCHEDULE") {
                if (!com.college.bustracker.BuildConfig.DEBUG) {
                    Log.w(TAG, "[AUTO-TRACK] ACTION_CONFIGURE_SCHEDULE ignored: prohibited in release builds")
                    return
                }
                val morningStart = intent.getStringExtra("EXTRA_MORNING_START") ?: "07:00"
                val morningEnd = intent.getStringExtra("EXTRA_MORNING_END") ?: "09:00"
                val eveningStart = intent.getStringExtra("EXTRA_EVENING_START") ?: "16:00"
                val eveningEnd = intent.getStringExtra("EXTRA_EVENING_END") ?: "18:00"
                val newSchedule = com.college.bustracker.data.model.RouteSchedule(
                    morning = com.college.bustracker.data.model.ScheduleWindow(morningStart, morningEnd, true),
                    evening = com.college.bustracker.data.model.ScheduleWindow(eveningStart, eveningEnd, true)
                )
                AutoTrackingScheduler.saveSchedule(context, newSchedule)
                AutoTrackingScheduler.scheduleAlarms(context, newSchedule)
                Log.i(TAG, "[AUTO-TRACK] $nowIso dynamic schedule configured: morning=$morningStart-$morningEnd, evening=$eveningStart-$eveningEnd")
                return
            }

            val prefs = context.getSharedPreferences(AutoTrackingScheduler.PREF_FILE, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(AutoTrackingScheduler.KEY_AUTO_ENABLED, false)
            Log.i(TAG, "[AUTO-TRACE] Auto tracking preference=$isEnabled")
            if (!isEnabled) {
                Log.d(TAG, "[AUTO-TRACK] Driver auto-tracking disabled in prefs; skipping trigger")
                return
            }

            var busId = prefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_BUS, "") ?: ""
            var routeId = prefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_ROUTE, "") ?: ""
            var driverId = prefs.getString(AutoTrackingScheduler.KEY_DRIVER_ID, "") ?: ""

            // Session recovery fallback if identity keys were not set in auto_tracking_prefs
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
            Log.i(TAG, "[AUTO-TRACE] Driver UID resolved: $driverId")
            Log.i(TAG, "[AUTO-TRACE] Effective bus resolved: $busId")

            if (busId.isBlank()) {
                Log.w(TAG, "[AUTO-TRACK] No assigned bus configured for driver; skipping trigger")
                return
            }

            val schedule = AutoTrackingScheduler.getSavedSchedule(context)
            val isWorkingDay = CollegeCalendarService.isWorkingDay()
            val isHoliday = CollegeCalendarService.isHoliday()
            val holidayName = CollegeCalendarService.getHolidayName() ?: "None"
            val forceWindow = com.college.bustracker.BuildConfig.DEBUG &&
                (intent?.getBooleanExtra("EXTRA_FORCE_WINDOW", false) == true)
            val isOperatingNow = forceWindow || (ScheduleHelper.isWithinOperatingWindow(schedule) && isWorkingDay)

            Log.i(TAG, "[AUTO-TRACK] Holiday check = $isHoliday (holidayName=$holidayName)")
            Log.i(TAG, "[AUTO-TRACK] Operating window = $isOperatingNow (isWorkingDay=$isWorkingDay, forceWindow=$forceWindow)")
            Log.i(TAG, "[AUTO-TRACK] Driver authorization = valid (driverId=$driverId, busId=$busId)")

            if (isOperatingNow) {
                // Pre-flight 1: Location Permissions
                val hasFinePerm = hasLocationPermission(context)
                val hasCoarsePerm = hasCoarseLocationPermission(context)
                val hasBgPerm = hasBackgroundLocationPermission(context)
                Log.i(TAG, "[AUTO-TRACE] fineLocation=$hasFinePerm")
                Log.i(TAG, "[AUTO-TRACE] coarseLocation=$hasCoarsePerm")
                Log.i(TAG, "[AUTO-TRACE] backgroundLocation=$hasBgPerm")

                if (!hasFinePerm) {
                    Log.w(TAG, "[AUTO-TRACK] Location permission not granted for driver automatic tracking; showing prompt")
                    showLocationPermissionMissingNotification(context)
                    return
                }
                dismissLocationPermissionMissingNotification(context)

                if (!hasBgPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Log.w(TAG, "[AUTO-TRACE] backgroundLocation=false (missing 'Allow all the time'); blocking service start and showing prompt")
                    showBackgroundLocationPermissionMissingNotification(context)
                    return
                }
                dismissBackgroundLocationPermissionMissingNotification(context)

                // Pre-flight 2: Device Location Services (GPS)
                val locationOn = isLocationEnabled(context)
                Log.i(TAG, "[AUTO-TRACK] Device location services check = $locationOn")
                if (!locationOn) {
                    Log.w(TAG, "[AUTO-TRACK] Device Location Services are OFF during driver operating window; posting warning notification")
                    showLocationDisabledNotification(context)
                } else {
                    dismissLocationDisabledNotification(context)
                    Log.i(TAG, "[AUTO-TRACE] Starting foreground service for bus $busId")
                    try {
                        TrackingServiceHelper.startTracking(context, busId, routeId, driverId, "AUTO", forceWindow)
                    } catch (fse: Exception) {
                        Log.w(TAG, "[AUTO-TRACK] Direct startForegroundService restricted; displaying launch notification: ${fse.message}")
                        showStartTrackingPromptNotification(context, busId, routeId, driverId)
                    }
                }
            } else {
                // Outside operating window or on a holiday
                Log.i(TAG, "[AUTO-TRACK] Window ended or holiday: stopping tracking while preserving auto preference")
                if (TrackingServiceHelper.isTracking.value) {
                    Log.i(TAG, "[AUTO-TRACK] Stopping LocationTrackingService: window closed or holiday")
                    TrackingServiceHelper.stopTracking(context)
                }
                dismissLocationDisabledNotification(context)
                dismissLocationPermissionMissingNotification(context)
                dismissBackgroundLocationPermissionMissingNotification(context)
            }

        } catch (e: Exception) {
            Log.e(TAG, "[AUTO-TRACK] Error handling driver schedule trigger: ${e.message}", e)
        } finally {
            try {
                if (
                    AutoTrackingScheduler.isAutoTrackingEnabled(context) &&
                    intent?.action != "com.college.bustracker.ACTION_CONFIGURE_SCHEDULE"
                ) {
                    AutoTrackingScheduler.scheduleAlarms(
                        context,
                        AutoTrackingScheduler.getSavedSchedule(context)
                    )

                    Log.i(TAG, "[AUTO-TRACE] Next alarms re-armed")
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "[AUTO-TRACE] Failed to re-arm alarms: ${e.message}",
                    e
                )
            }

            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (ignored: Exception) {
            }
        }
    }
}
