package com.college.bustracker.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.college.bustracker.diagnostics.DeviceRomDetector

/**
 * Universal Settings Opener for FleetX.
 *
 * Resolves ContextWrappers to find the hosting Activity, guarantees FLAG_ACTIVITY_NEW_TASK,
 * and executes graceful multi-tiered fallback pipelines for:
 * - Battery Optimization exemption (Unrestricted background GPS)
 * - App Info / Application Details (Background location, permissions, overlay, autostart)
 * - Location Services (GPS)
 * - Mobile Data / Wireless
 * - Unknown App Sources (In-app updates)
 * - Overlay permission (KeepAlive pixel)
 */
object SettingsOpener {

    private const val TAG = "SettingsOpener"

    /**
     * Unwraps any Compose ContextWrapper chain to extract the underlying Activity.
     */
    fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Safely launches an Intent with FLAG_ACTIVITY_NEW_TASK and error protection.
     */
    fun safeStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activity = findActivity(context)
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            Log.i(TAG, "Launched settings intent: ${intent.action ?: intent.component}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch intent: ${intent.action ?: intent.component}: ${e.message}")
            false
        }
    }

    /**
     * Opens the Unrestricted Battery Optimization settings for FleetX.
     * Execution pipeline:
     * 1. Try ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (direct system whitelist prompt)
     * 2. Try OEM Autostart / Battery manager (Vivo, Xiaomi, Samsung, Oppo, OnePlus)
     * 3. Fallback to App Info screen (where driver can tap Battery -> Unrestricted)
     * 4. Fallback to ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
     * 5. Fallback to general Settings
     */
    fun openBatteryOptimization(context: Context): Boolean {
        val pkg = context.packageName
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        // 1. Direct whitelist prompt if not already exempted
        if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
            val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            }
            if (safeStartActivity(context, directIntent)) {
                return true
            }
        }

        // 2. Try OEM-specific battery & autostart manager
        try {
            if (DeviceRomDetector.openOemBatteryOptimizationSettings(context)) {
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "OEM battery settings check failed: ${e.message}")
        }

        // 3. App Details screen (App Info -> Battery -> Unrestricted)
        if (openAppDetails(context)) {
            return true
        }

        // 4. Global battery optimization list
        val globalBattery = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (safeStartActivity(context, globalBattery)) {
            return true
        }

        // 5. Ultimate fallback
        return safeStartActivity(context, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Opens FleetX's App Info / Application Details screen where drivers can configure:
     * - Location permission ("Allow all the time")
     * - Battery usage ("Unrestricted")
     * - Autostart / Background run
     * - Draw over other apps (Overlay)
     */
    fun openAppDetails(context: Context): Boolean {
        val pkg = context.packageName
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
        }
        if (safeStartActivity(context, intent)) {
            return true
        }

        val manageApps = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        if (safeStartActivity(context, manageApps)) {
            return true
        }

        return safeStartActivity(context, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Opens device location settings (GPS toggle).
     */
    fun openLocationSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        if (safeStartActivity(context, intent)) {
            return true
        }
        return safeStartActivity(context, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Opens device mobile data / wireless settings.
     */
    fun openMobileDataSettings(context: Context): Boolean {
        val roamingIntent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        if (safeStartActivity(context, roamingIntent)) {
            return true
        }
        val wirelessIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        if (safeStartActivity(context, wirelessIntent)) {
            return true
        }
        return safeStartActivity(context, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Opens "Install Unknown Apps" permission screen for FleetX updates.
     */
    fun openInstallUnknownApps(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (safeStartActivity(context, intent)) {
                return true
            }
        }
        return openAppDetails(context)
    }

    /**
     * Opens "Display over other apps" (Overlay) permission screen.
     */
    fun openOverlaySettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (safeStartActivity(context, intent)) {
                return true
            }
        }
        return openAppDetails(context)
    }
}
