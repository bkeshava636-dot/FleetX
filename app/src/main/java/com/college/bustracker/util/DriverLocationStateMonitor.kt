package com.college.bustracker.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.util.Log
import com.college.bustracker.service.AutoTrackingScheduler
import com.college.bustracker.service.DriverScheduleReceiver

/**
 * Monitors system location provider changes (Location Services toggled ON/OFF).
 * Ensures driver automatic tracking seamlessly starts the instant location becomes available,
 * or alerts the driver if location is turned off during an active shift window.
 */
object DriverLocationStateMonitor {

    private const val TAG = "DriverLocationMonitor"
    private var isInitialized = false
    private var receiver: BroadcastReceiver? = null

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val ctx = c ?: appContext
                val locationOn = DriverScheduleReceiver.isLocationEnabled(ctx)
                val autoEnabled = AutoTrackingScheduler.isAutoTrackingEnabled(ctx)

                Log.i(TAG, "Location provider state changed: locationOn=$locationOn, autoEnabled=$autoEnabled")

                if (autoEnabled) {
                    AutoTrackingScheduler.checkAndTriggerAutoTracking(ctx)
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                @Suppress("DEPRECATION")
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            appContext.registerReceiver(receiver, filter)
            isInitialized = true
            Log.i(TAG, "DriverLocationStateMonitor initialized and receiver registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register DriverLocationStateMonitor receiver: ${e.message}")
        }
    }
}
