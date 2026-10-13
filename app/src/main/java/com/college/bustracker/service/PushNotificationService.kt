package com.college.bustracker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.repository.StudentPreferences
import com.college.bustracker.ui.MainActivity
import com.college.bustracker.util.AnnouncementTopicManager
import com.college.bustracker.util.BusTopicManager
import com.college.bustracker.util.LocaleHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushNotificationService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PushNotificationService"
        const val EXTRA_ROUTE = "EXTRA_ROUTE"
        const val EXTRA_SWAP_ID = "EXTRA_SWAP_ID"
        const val EXTRA_BUS_ID = "EXTRA_BUS_ID"
        const val EXTRA_REPLACEMENT_BUS_ID = "EXTRA_REPLACEMENT_BUS_ID"
        const val EXTRA_STOP_ID = "EXTRA_STOP_ID"
        const val EXTRA_ANNOUNCEMENT_ID = "EXTRA_ANNOUNCEMENT_ID"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val notificationType = data["notificationType"] ?: "BUS_SWAP"

        if (notificationType.equals("ANNOUNCEMENT", ignoreCase = true)) {
            handleAnnouncementMessage(remoteMessage, data)
        } else {
            handleBusSwapMessage(remoteMessage, data)
        }
    }

    private fun handleAnnouncementMessage(remoteMessage: RemoteMessage, data: Map<String, String>) {
        val announcementId = data["announcementId"] ?: data["id"] ?: ""
        val isKn = LocaleHelper.isKannada(applicationContext)

        val titleEn = data["titleEn"] ?: ""
        val titleKn = data["titleKn"] ?: ""
        val messageEn = data["messageEn"] ?: ""
        val messageKn = data["messageKn"] ?: ""

        val rawTitle = if (isKn) {
            titleKn.ifBlank { titleEn }
        } else {
            titleEn.ifBlank { titleKn }
        }

        val rawMessage = if (isKn) {
            messageKn.ifBlank { messageEn }
        } else {
            messageEn.ifBlank { messageKn }
        }

        val title = remoteMessage.notification?.title ?: data["title"] ?: rawTitle.ifBlank {
            if (isKn) "📢 ಕಾಲೇಜು ಪ್ರಕಟಣೆ" else "📢 Campus Announcement"
        }
        val message = remoteMessage.notification?.body ?: data["body"] ?: rawMessage.ifBlank {
            if (isKn) "ಹೊಸ ಪ್ರಕಟಣೆ ಲಭ್ಯವಿದೆ" else "A new announcement has been published."
        }

        Log.i(TAG, "Announcement FCM received: id=$announcementId, title=$title")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, "announcements")
            putExtra(EXTRA_ANNOUNCEMENT_ID, announcementId)
        }

        val requestCode = if (announcementId.isNotBlank()) announcementId.hashCode() else 301
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CollegeBusApp.ANNOUNCEMENTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = if (announcementId.isNotBlank()) announcementId.hashCode() else System.currentTimeMillis().toInt()
        manager.notify(notificationId, notification)
    }

    private fun handleBusSwapMessage(remoteMessage: RemoteMessage, data: Map<String, String>) {
        val swapId = data["swapId"] ?: ""
        val originalBusId = data["originalBusId"] ?: ""
        val replacementBusId = data["replacementBusId"] ?: ""
        val stopId = data["stopId"] ?: ""
        val stopName = data["stopName"] ?: ""
        val status = (data["status"] ?: "ACTIVE").uppercase()

        val isKn = LocaleHelper.isKannada(applicationContext)

        val defaultTitle = if (status == "CANCELLED") {
            if (isKn) "🚌 ಬಸ್ ಬದಲಾವಣೆ ರದ್ದುಗೊಂಡಿದೆ" else "🚌 Bus Change Cancelled"
        } else {
            if (isKn) "🚌 ಬಸ್ ಬದಲಾವಣೆ ಸೂಚನೆ" else "🚌 Bus Change Alert"
        }

        val defaultBody = when {
            status == "CANCELLED" -> {
                val target = if (stopName.isNotBlank()) " for $stopName" else ""
                "$originalBusId is back in service$target today."
            }
            replacementBusId.isNotBlank() && originalBusId.isNotBlank() -> {
                val target = if (stopName.isNotBlank()) " for $stopName" else ""
                "$replacementBusId is replacing $originalBusId today$target."
            }
            else -> "Your bus assignment has changed."
        }

        val title = remoteMessage.notification?.title ?: data["title"] ?: defaultTitle
        val body = remoteMessage.notification?.body ?: data["body"] ?: defaultBody

        Log.i(TAG, "FCM message received: swapId=$swapId, originalBusId=$originalBusId, stopId=$stopId, status=$status")

        showNotification(
            title = title,
            message = body,
            swapId = swapId,
            originalBusId = originalBusId,
            replacementBusId = replacementBusId,
            stopId = stopId
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        // Resubscribe to saved bus and stop topic when token refreshes
        val savedBus = StudentPreferences.getSavedBusId(applicationContext)
        val savedStop = StudentPreferences.getSavedStopId(applicationContext)
        if (!savedBus.isNullOrBlank() && !savedStop.isNullOrBlank()) {
            BusTopicManager.updateSubscription(applicationContext, savedBus, savedStop)
        }
        AnnouncementTopicManager.subscribeToGeneralTopics()
    }

    private fun showNotification(
        title: String,
        message: String,
        swapId: String,
        originalBusId: String,
        replacementBusId: String,
        stopId: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, "student_home")
            putExtra(EXTRA_SWAP_ID, swapId)
            putExtra(EXTRA_BUS_ID, originalBusId)
            putExtra(EXTRA_REPLACEMENT_BUS_ID, replacementBusId)
            putExtra(EXTRA_STOP_ID, stopId)
        }

        val requestCode = if (swapId.isNotBlank()) swapId.hashCode() else 201
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CollegeBusApp.ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = if (swapId.isNotBlank()) swapId.hashCode() else System.currentTimeMillis().toInt()
        manager.notify(notificationId, notification)
        Log.i(TAG, "Notification displayed in system tray: id=$notificationId, title=$title, stopId=$stopId")
    }
}
