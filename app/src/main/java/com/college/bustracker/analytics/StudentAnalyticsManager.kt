package com.college.bustracker.analytics

import android.content.Context
import android.os.Build
import com.college.bustracker.util.KolkataTimeFormatter
import com.college.bustracker.util.LocaleHelper
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

object StudentAnalyticsManager {

    private const val PREFS_NAME = "fleetx_student_analytics_prefs"
    private const val KEY_INSTALLATION_ID = "key_installation_id"
    private const val KEY_INSTALL_DATE = "key_install_date"
    private const val KEY_LAST_ACTIVE_DATE = "key_last_active_date"

    // Feature Event Types
    const val EVENT_APP_OPENED = "app_opened"
    const val EVENT_LIVE_TRACKING_OPENED = "live_tracking_opened"
    const val EVENT_BUS_DETAILS_VIEWED = "bus_details_viewed"
    const val EVENT_STOP_SELECTED = "stop_selected"
    const val EVENT_ANNOUNCEMENT_VIEWED = "announcement_viewed"
    const val EVENT_BUS_SWAP_ALERT_VIEWED = "bus_swap_alert_viewed"
    const val EVENT_SETTINGS_OPENED = "settings_opened"
    const val EVENT_UPDATE_AVAILABLE = "update_available"
    const val EVENT_UPDATE_INSTALLED = "update_installed"

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Retrieves or generates a completely anonymous installation identifier.
     * ZERO PII: No IMEI, no Phone Number, no Advertising ID, no MAC address, no GPS coordinates.
     */
    fun getInstallationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit()
                .putString(KEY_INSTALLATION_ID, id)
                .putString(KEY_INSTALL_DATE, KolkataTimeFormatter.getTodayIso())
                .apply()
        }
        return id
    }

    fun getInstalledDate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_INSTALL_DATE, KolkataTimeFormatter.getTodayIso()) ?: KolkataTimeFormatter.getTodayIso()
    }

    /**
     * Records an anonymous student usage event.
     * Batched & throttled to protect Firebase costs:
     * Only updates device installation document once per calendar day.
     */
    fun logEvent(context: Context, eventName: String) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val installationId = getInstallationId(context)
                val today = KolkataTimeFormatter.getTodayIso()
                val lastActiveDate = prefs.getString(KEY_LAST_ACTIVE_DATE, null)
                val isFirstOpenToday = lastActiveDate != today

                val packageInfo = try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) { null }
                val versionName = packageInfo?.versionName ?: "1.0.35"
                val lang = if (LocaleHelper.isKannada(context)) "kn" else "en"

                val firestore = FirebaseFirestore.getInstance()

                // 1. Once-per-day installation heartbeat doc (student_installations/{installationId})
                if (isFirstOpenToday) {
                    prefs.edit().putString(KEY_LAST_ACTIVE_DATE, today).apply()

                    val installPayload = hashMapOf<String, Any>(
                        "installationId" to installationId,
                        "appVersion" to versionName,
                        "language" to lang,
                        "lastActiveDate" to today,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    firestore.collection("student_installations")
                        .document(installationId)
                        .set(installPayload, SetOptions.merge())
                }

                // 2. Daily aggregate doc atomic increment (student_analytics_daily/{today})
                val dailyRef = firestore.collection("student_analytics_daily").document(today)
                val updates = hashMapOf<String, Any>(
                    "date" to today,
                    "totalEvents" to FieldValue.increment(1),
                    "events.$eventName" to FieldValue.increment(1),
                    "languages.$lang" to FieldValue.increment(1),
                    "versions.$versionName" to FieldValue.increment(1),
                    "updatedAt" to System.currentTimeMillis()
                )
                if (isFirstOpenToday) {
                    updates["dauCount"] = FieldValue.increment(1)
                    if (prefs.getString(KEY_INSTALL_DATE, today) == today) {
                        updates["newInstallations"] = FieldValue.increment(1)
                    } else {
                        updates["returningUsers"] = FieldValue.increment(1)
                    }
                }

                dailyRef.set(updates, SetOptions.merge())
            } catch (ignored: Exception) {
                // Anonymous analytics must never crash the app or interrupt student flow
            }
        }
    }
}
