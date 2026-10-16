package com.college.bustracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.college.bustracker.diagnostics.DiagnosticStage
import com.college.bustracker.diagnostics.GpsDiagnosticManager
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.ScheduleHelper
import com.google.android.gms.location.LocationResult

/**
 * Privileged GMS Hardware Piggyback Receiver (Layer 2).
 *
 * When LocationTrackingService is killed by OEM task killers (e.g., Vivo OriginOS/Funtouch OS,
 * Xiaomi HyperOS, Samsung One UI) or swiped from Recents, the PendingIntent registered with
 * Google Play Services (com.google.android.gms.persistent) remains active in system memory.
 *
 * As the device moves or hardware GPS produces a fix, Google Play Services broadcasts this
 * PendingIntent to this receiver. Because the broadcast originates from a privileged system
 * daemon, Android grants a temporary execution window and wake lock, allowing FleetX to
 * seamlessly resurrect the foreground tracking service without any user interaction!
 */
class GmsLocationResurrectionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "GmsResurrection"
        const val ACTION_GMS_LOCATION_TICK = "com.college.bustracker.ACTION_GMS_LOCATION_TICK"
        const val REQUEST_CODE_GMS_PIGGYBACK = 8888
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        Log.i(TAG, "[GMS_WATCHDOG] $nowIso onReceive triggered by Google Play Services")

        val result = LocationResult.extractResult(intent ?: return)
        val location = result?.lastLocation

        // Pre-check 1: Is driver logged in?
        if (!AutoTrackingScheduler.isDriverLoggedIn(context)) {
            Log.d(TAG, "[GMS_WATCHDOG] Driver not logged in; ignoring GMS location tick")
            return
        }

        // Pre-check 2: Is tracking preference enabled?
        val prefs = context.getSharedPreferences(AutoTrackingScheduler.PREF_FILE, Context.MODE_PRIVATE)
        val isAutoEnabled = prefs.getBoolean(AutoTrackingScheduler.KEY_AUTO_ENABLED, false)

        val persistentPrefs = context.getSharedPreferences(LocationTrackingService.PREFS_PERSISTENT_TRACKING, Context.MODE_PRIVATE)
        val isPersistentSessionActive = persistentPrefs.getBoolean(LocationTrackingService.KEY_IS_SESSION_ACTIVE, false)

        val schedule = AutoTrackingScheduler.getSavedSchedule(context)
        val isWorkingDay = CollegeCalendarService.isWorkingDay()
        val isWithinWindow = ScheduleHelper.isWithinOperatingWindow(schedule) && isWorkingDay

        val shouldBeTracking = isPersistentSessionActive || (isAutoEnabled && isWithinWindow)

        if (!shouldBeTracking) {
            Log.d(TAG, "[GMS_WATCHDOG] Outside operating window and no active session; ignoring tick")
            return
        }

        // Pre-check 3: Is LocationTrackingService already running?
        if (TrackingServiceHelper.isTracking.value) {
            Log.d(TAG, "[GMS_WATCHDOG] Tracking service is healthy and running. Location fix: lat=${location?.latitude}, lon=${location?.longitude}")
            return
        }

        // Service died while it should be tracking! Perform hardware resurrection!
        var busId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_BUS_ID, "") ?: ""
        var routeId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_ROUTE_ID, "") ?: ""
        var driverId = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_DRIVER_ID, "") ?: ""
        var mode = persistentPrefs.getString(LocationTrackingService.KEY_SAVED_MODE, "AUTO") ?: "AUTO"

        if (busId.isBlank()) {
            busId = prefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_BUS, "") ?: ""
        }
        if (routeId.isBlank()) {
            routeId = prefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_ROUTE, "ROUTE-01") ?: "ROUTE-01"
        }
        if (driverId.isBlank()) {
            driverId = prefs.getString(AutoTrackingScheduler.KEY_DRIVER_ID, "") ?: ""
        }

        if (busId.isBlank()) {
            Log.w(TAG, "[GMS_WATCHDOG] Cannot resurrect: no assigned bus found")
            return
        }

        Log.w(TAG, "[GMS_WATCHDOG] RESURRECTION TRIGGERED: Service was killed! Resurrecting for bus $busId (mode=$mode)")

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CollegeBus:GmsResurrectionWakeLock")
        wakeLock?.acquire(15_000L) // 15s safety lock

        try {
            GpsDiagnosticManager.logEvent(
                context = context,
                stage = DiagnosticStage.SYSTEM_HEALTH,
                eventName = "GMS_HARDWARE_RESURRECTION",
                details = mapOf(
                    "busId" to busId,
                    "mode" to mode,
                    "hasLocation" to (location != null).toString(),
                    "lat" to (location?.latitude?.toString() ?: "0.0"),
                    "lon" to (location?.longitude?.toString() ?: "0.0")
                )
            )

            TrackingServiceHelper.startTracking(
                context = context,
                busId = busId,
                routeId = routeId,
                driverId = driverId,
                mode = mode
            )
        } catch (e: Exception) {
            Log.e(TAG, "[GMS_WATCHDOG] Failed to resurrect TrackingService: ${e.message}", e)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (ignored: Exception) {}
        }
    }
}
