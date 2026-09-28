package com.college.bustracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.college.bustracker.R
import com.college.bustracker.ui.MainActivity
import com.college.bustracker.util.StudentLocationAssistant

/**
 * Lightweight foreground service that maintains active student location contribution
 * during confirmed rides, enabling screen-off GPS updates compliant with Android 12+.
 */
class StudentContributorService : Service {

    constructor() : super()

    companion object {
        const val ACTION_START = "com.college.bustracker.ACTION_START_STUDENT_CONTRIBUTION"
        const val ACTION_STOP = "com.college.bustracker.ACTION_STOP_STUDENT_CONTRIBUTION"
        const val EXTRA_BUS_ID = "extra_bus_id"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "student_contributor_channel"

        fun startService(context: Context, busId: String) {
            val intent = Intent(context, StudentContributorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BUS_ID, busId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, StudentContributorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var activeBusId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                activeBusId = intent.getStringExtra(EXTRA_BUS_ID) ?: "Bus"
                val notification = buildNotification(activeBusId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_STOP -> {
                StudentLocationAssistant.stopSharing()
                try {
                    com.college.bustracker.util.AutomaticRideManager.disableAutoSharing(this)
                } catch (ignored: Exception) {}
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(busId: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StudentContributorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FleetX Transit")
            .setContentText("Contributing backup location for $busId")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Stop Sharing", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup Location Contribution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when your phone is contributing backup GPS for your FleetX bus"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
