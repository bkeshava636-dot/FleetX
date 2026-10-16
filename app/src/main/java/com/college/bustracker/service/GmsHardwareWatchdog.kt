package com.college.bustracker.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Manages registration of the hardware GPS piggyback with Google Play Services.
 * Runs in com.google.android.gms.persistent and survives OEM process kills and task swiping.
 */
object GmsHardwareWatchdog {
    private const val TAG = "GmsHardwareWatchdog"

    private fun createLocationPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GmsLocationResurrectionReceiver::class.java).apply {
            action = GmsLocationResurrectionReceiver.ACTION_GMS_LOCATION_TICK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context,
            GmsLocationResurrectionReceiver.REQUEST_CODE_GMS_PIGGYBACK,
            intent,
            flags
        )
    }

    @SuppressLint("MissingPermission")
    fun register(context: Context) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val pendingIntent = createLocationPendingIntent(context)

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                25_000L // 25s keepalive cadence
            ).apply {
                setMinUpdateIntervalMillis(15_000L)
                setMaxUpdateDelayMillis(30_000L)
                setMinUpdateDistanceMeters(0f)
                setWaitForAccurateLocation(false)
            }.build()

            fusedClient.requestLocationUpdates(locationRequest, pendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "[GMS_WATCHDOG] GMS hardware piggyback registered successfully")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "[GMS_WATCHDOG] GMS hardware piggyback registration failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "[GMS_WATCHDOG] Failed to register GMS watchdog: ${e.message}")
        }
    }

    fun unregister(context: Context) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val pendingIntent = createLocationPendingIntent(context)
            fusedClient.removeLocationUpdates(pendingIntent)
                .addOnCompleteListener {
                    Log.i(TAG, "[GMS_WATCHDOG] GMS hardware piggyback unregistered")
                }
        } catch (e: Exception) {
            Log.w(TAG, "[GMS_WATCHDOG] Failed to unregister GMS watchdog: ${e.message}")
        }
    }
}
