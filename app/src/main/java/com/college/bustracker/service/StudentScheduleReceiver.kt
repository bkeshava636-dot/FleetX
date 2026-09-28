package com.college.bustracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.college.bustracker.util.AutomaticRideManager
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.ScheduleHelper

/**
 * BroadcastReceiver triggered by AlarmManager at student schedule boundaries.
 * Operates under a partial WakeLock to reliably start or stop automatic ride monitoring
 * even after app process death, swiping from recents, or device reboot.
 */
class StudentScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StudentScheduleReceiver"
        private const val NOTIFICATION_CHANNEL_ID = "bus_tracking_channel"
        private const val NOTIFICATION_ID_LOCATION_OFF = 4001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CollegeBus:StudentScheduleWakeLock")
        wakeLock?.acquire(10_000L) // 10-second safety timeout

        try {
            val prefs = context.getSharedPreferences(StudentAutoTrackingScheduler.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("auto_sharing_enabled", false)
            val busId = prefs.getString("auto_selected_bus_id", null)

            if (!isEnabled || busId.isNullOrBlank()) {
                Log.d(TAG, "Student auto-sharing disabled; skipping trigger")
                return
            }

            val schedule = StudentAutoTrackingScheduler.getSavedSchedule(context)
            val isWorkingDay = CollegeCalendarService.isWorkingDay()
            val isOperatingNow = ScheduleHelper.isWithinOperatingWindow(schedule) && isWorkingDay

            Log.i(TAG, "Student schedule alarm fired: isOperatingNow=$isOperatingNow (isWorkingDay=$isWorkingDay)")

            if (isOperatingNow) {
                val isLocationEnabled = AutomaticRideManager.isLocationEnabled(context)
                if (!isLocationEnabled) {
                    showLocationDisabledNotification(context)
                    Log.w(TAG, "Location is OFF during operating window; notified user")
                } else {
                    dismissLocationDisabledNotification(context)
                    AutomaticRideManager.init(context)
                    AutomaticRideManager.startMonitoring(context)
                }
            } else {
                AutomaticRideManager.stopMonitoring()
                dismissLocationDisabledNotification(context)
            }

            // Always chain/reschedule for the subsequent boundary
            StudentAutoTrackingScheduler.scheduleAlarms(context, schedule)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling student schedule trigger: ${e.message}", e)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun showLocationDisabledNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Bus Tracking Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts and status for bus transit tracking"
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
            .setContentTitle("Bus Tracking Paused")
            .setContentText("Please turn on device location to enable automatic bus tracking.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_LOCATION_OFF, notification)
    }

    private fun dismissLocationDisabledNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID_LOCATION_OFF)
    }
}
