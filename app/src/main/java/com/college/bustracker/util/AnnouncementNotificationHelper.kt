package com.college.bustracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.Announcement
import com.college.bustracker.data.repository.AnnouncementPreferences
import com.college.bustracker.ui.MainActivity

/**
 * Handles posting immediate system status bar notifications whenever an announcement
 * or bus timing change is received on the device. Ensures students and drivers are
 * alerted immediately without depending solely on FCM delivery.
 */
object AnnouncementNotificationHelper {

    private const val TAG = "AnnouncementNotifier"

    fun notifyIfNew(context: Context, announcements: List<Announcement>) {
        if (announcements.isEmpty()) return
        val isKn = LocaleHelper.isKannada(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Ensure channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CollegeBusApp.ANNOUNCEMENTS_CHANNEL_ID,
                "Campus Announcements & Timing Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies students and drivers about bus timing changes, schedule updates, and campus alerts"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        announcements.forEach { announcement ->
            if (announcement.id.isBlank() || !announcement.isCurrentlyActive()) return@forEach
            if (!AnnouncementPreferences.hasAnnouncementBeenNotified(context, announcement.id)) {
                val title = announcement.getDisplayTitle(isKn).ifBlank {
                    if (isKn) "📢 ಕಾಲೇಜು ಪ್ರಕಟಣೆ" else "📢 Campus Announcement"
                }
                val body = announcement.getDisplayMessage(isKn).ifBlank {
                    if (isKn) "ಹೊಸ ಪ್ರಕಟಣೆ ಲಭ್ಯವಿದೆ" else "A new announcement has been published."
                }

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_ROUTE", "announcements")
                    putExtra("EXTRA_ANNOUNCEMENT_ID", announcement.id)
                }

                val requestCode = (announcement.id.hashCode() and 0x7FFFFFFF)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CollegeBusApp.ANNOUNCEMENTS_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()

                nm.notify(requestCode, notification)
                AnnouncementPreferences.markAnnouncementNotified(context, announcement.id)
                Log.i(TAG, "Notified announcement: ${announcement.id} -> $title")
            }
        }
    }
}
