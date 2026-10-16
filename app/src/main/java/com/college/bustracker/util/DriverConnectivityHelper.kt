package com.college.bustracker.util

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.college.bustracker.CollegeBusApp
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient

/**
 * Manages location (GPS) and mobile data connectivity verification, automatic resolution,
 * and driver alert prompts during scheduled operating windows.
 */
object DriverConnectivityHelper {

    private const val TAG = "DriverConnectivity"
    const val REQUEST_LOCATION_RESOLUTION = 1001

    private var lastLocationNotificationTime = 0L
    private var lastMobileDataNotificationTime = 0L
    private const val NOTIFICATION_THROTTLE_MS = 60_000L // Throttle alerts to once per minute

    /**
     * Checks if device location (GPS or Network provider) is enabled.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks whether mobile data or an active network connection is enabled.
     */
    fun isMobileDataEnabled(context: Context): Boolean {
        // 1. Check TelephonyManager data status (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                try {
                    return tm.isDataEnabled
                } catch (ignored: Exception) {}
            }
        }

        // 2. Check Settings.Global mobile_data key
        try {
            val cr = context.contentResolver
            val mobileDataVal = Settings.Global.getInt(cr, "mobile_data", -1)
            if (mobileDataVal != -1) {
                return mobileDataVal == 1
            }
        } catch (ignored: Exception) {}

        // 3. Check active network capabilities
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true
            }
        }

        return false
    }

    /**
     * Attempts programmatic toggle of mobile data if granted permissions or running in privileged mode.
     */
    fun tryEnableMobileData(context: Context): Boolean {
        // Attempt reflection on TelephonyManager
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                val method = tm.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(tm, true)
                Log.i(TAG, "Successfully invoked setDataEnabled(true) via reflection")
                return true
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Cannot invoke setDataEnabled via reflection: ${e.message}")
        }

        // Attempt Settings.Global.putInt
        try {
            val cr = context.contentResolver
            val ok = Settings.Global.putInt(cr, "mobile_data", 1)
            if (ok) {
                Log.i(TAG, "Settings.Global.mobile_data set to 1")
                return true
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Cannot write to Settings.Global: ${e.message}")
        }

        return false
    }

    /**
     * Attempts programmatic toggle of GPS location if granted WRITE_SECURE_SETTINGS.
     */
    fun tryEnableLocationProgrammatically(context: Context): Boolean {
        try {
            val ok = Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
            )
            if (ok) {
                Log.i(TAG, "Settings.Secure LOCATION_MODE set to HIGH_ACCURACY")
                return true
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Cannot write to Settings.Secure: ${e.message}")
        }
        return false
    }

    /**
     * Prompts the user to turn on GPS using Google Play Services SettingsClient resolution.
     * When resolvable, Android shows its native system 1-tap dialog: "To continue, turn on device location".
     */
    fun promptEnableLocation(
        activity: Activity,
        requestCode: Int = REQUEST_LOCATION_RESOLUTION,
        onResolvableError: ((ResolvableApiException) -> Unit)? = null
    ) {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
            val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)

            val client: SettingsClient = LocationServices.getSettingsClient(activity)
            client.checkLocationSettings(builder.build())
                .addOnSuccessListener {
                    Log.i(TAG, "Location settings satisfied")
                }
                .addOnFailureListener { exception ->
                    if (exception is ResolvableApiException) {
                        try {
                            if (onResolvableError != null) {
                                onResolvableError(exception)
                            } else {
                                exception.startResolutionForResult(activity, requestCode)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start location resolution: ${e.message}")
                            openLocationSettings(activity)
                        }
                    } else {
                        openLocationSettings(activity)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed checkLocationSettings: ${e.message}")
            openLocationSettings(activity)
        }
    }

    fun openLocationSettings(context: Context) {
        SettingsOpener.openLocationSettings(context)
    }

    fun openMobileDataSettings(context: Context) {
        SettingsOpener.openMobileDataSettings(context)
    }

    fun showLocationDisabledNotification(context: Context, busNumber: String) {
        val now = System.currentTimeMillis()
        if (now - lastLocationNotificationTime < NOTIFICATION_THROTTLE_MS) return
        lastLocationNotificationTime = now

        val isKn = LocaleHelper.isKannada(context)
        val title = if (isKn) "⚠️ ಜಿಪಿಎಸ್ ಆಫ್ ಆಗಿದೆ (ಬಸ್ $busNumber)" else "⚠️ GPS Location Disabled (Bus $busNumber)"
        val body = if (isKn) "ಕಾರ್ಯಾಚರಣೆಯ ವೇಳಾಪಟ್ಟಿಯ ಸಮಯದಲ್ಲಿ ಲೈವ್ ಟ್ರ್ಯಾಕಿಂಗ್‌ಗೆ ಜಿಪಿಎಸ್ ಅಗತ್ಯವಿದೆ. ಆನ್ ಮಾಡಲು ಟ್ಯಾಪ್ ಮಾಡಿ."
                   else "Shift operating window is active. Live tracking requires GPS. Tap to turn on."

        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CollegeBusApp.ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(901, notification)
    }

    fun showMobileDataDisabledNotification(context: Context, busNumber: String) {
        val now = System.currentTimeMillis()
        if (now - lastMobileDataNotificationTime < NOTIFICATION_THROTTLE_MS) return
        lastMobileDataNotificationTime = now

        val isKn = LocaleHelper.isKannada(context)
        val title = if (isKn) "⚠️ ಮೊಬೈಲ್ ಡೇಟಾ ಆಫ್ ಆಗಿದೆ (ಬಸ್ $busNumber)" else "⚠️ Mobile Data Disabled (Bus $busNumber)"
        val body = if (isKn) "ಕಾರ್ಯಾಚರಣೆಯ ವೇಳಾಪಟ್ಟಿಯ ಸಮಯದಲ್ಲಿ ಸ್ಥಳ ಕಳುಹಿಸಲು ಮೊಬೈಲ್ ಡೇಟಾ ಅಗತ್ಯವಿದೆ. ಆನ್ ಮಾಡಲು ಟ್ಯಾಪ್ ಮಾಡಿ."
                   else "Shift operating window is active. Location sync requires mobile data. Tap to turn on."

        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            902,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CollegeBusApp.ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(902, notification)
    }
}
