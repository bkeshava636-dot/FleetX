package com.college.bustracker.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Helper to optionally attach a 1x1 transparent pixel window overlay while driver tracking is active.
 *
 * Why this works:
 * On Android, the WindowManager attaches foreground presentation tokens to the process's Linux cgroup,
 * dropping oom_score_adj to 0 (FOREGROUND_APP). This discourages OEM aggressive background killers
 * (such as Vivo 'abe', Xiaomi 'PowerKeeper', and Samsung 'Freecess') from killing the process when
 * the driver switches away from FleetX to Google Maps or locks the screen.
 *
 * Gracefully no-ops if overlay permission has not been granted.
 */
class KeepAliveOverlayHelper(private val context: Context) {

    companion object {
        private const val TAG = "KeepAliveOverlay"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isAttached: Boolean = false

    fun attachOverlay() {
        if (isAttached) return

        // Overlay permission check (Settings.canDrawOverlays)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Overlay permission not granted; keep-alive overlay skipped")
            return
        }

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) return

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                1, // 1 pixel width
                1, // 1 pixel height
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSPARENT
            ).apply {
                gravity = Gravity.START or Gravity.TOP
                x = 0
                y = 0
                alpha = 0.01f // Invisible to human eye
            }

            overlayView = View(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            windowManager?.addView(overlayView, params)
            isAttached = true
            Log.i(TAG, "Keep-alive 1x1 transparent overlay attached (process protected)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach keep-alive overlay: ${e.message}")
            overlayView = null
            isAttached = false
        }
    }

    fun detachOverlay() {
        if (!isAttached || overlayView == null) return
        try {
            windowManager?.removeView(overlayView)
            Log.i(TAG, "Keep-alive overlay detached cleanly")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detach keep-alive overlay: ${e.message}")
        } finally {
            overlayView = null
            windowManager = null
            isAttached = false
        }
    }
}
