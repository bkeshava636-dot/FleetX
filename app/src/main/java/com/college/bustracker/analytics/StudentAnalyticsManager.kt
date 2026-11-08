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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
    private val analyticsMutex = Mutex()

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

                // Atomically determine and set first-open status without holding lock over network IO
                val isFirstOpenToday = analyticsMutex.withLock {
                    val lastActiveDate = prefs.getString(KEY_LAST_ACTIVE_DATE, null)
                    if (lastActiveDate != today) {
                        prefs.edit().putString(KEY_LAST_ACTIVE_DATE, today).apply()
                        true
                    } else {
                        false
                    }
                }

                val packageInfo = try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) { null }
                val versionName = packageInfo?.versionName ?: "1.0.40"
                val lang = if (LocaleHelper.isKannada(context)) "kn" else "en"

                val firestore = FirebaseFirestore.getInstance()

                try {
                    withTimeout(10_000L) {
                        // 1. Issue installation heartbeat write if first open today
                        val heartbeatTask = if (isFirstOpenToday) {
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
                        } else null

                        // 2. Issue daily aggregate atomic increment write
                        val dailyRef = firestore.collection("student_analytics_daily").document(today)
                        val updates = hashMapOf<String, Any>(
                            "date" to today,
                            "totalEvents" to FieldValue.increment(1),
                            "events" to mapOf(eventName to FieldValue.increment(1)),
                            "languages" to mapOf(lang to FieldValue.increment(1)),
                            "versions" to mapOf(versionName to FieldValue.increment(1)),
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

                        val dailyTask = dailyRef.set(updates, SetOptions.merge())

                        // 3. Await completion of both queued writes
                        heartbeatTask?.await()
                        dailyTask.await()
                    }
                } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                    // The write may still be queued offline and will sync later. Keep the date to avoid duplicate DAU increment.
                } catch (ignored: Exception) {
                    if (isFirstOpenToday) {
                        // Reset last active date on failure so a subsequent event can retry the daily heartbeat
                        analyticsMutex.withLock {
                            prefs.edit().remove(KEY_LAST_ACTIVE_DATE).apply()
                        }
                    }
                }
            } catch (ignored: Exception) {
                // Anonymous analytics must never crash the app or interrupt student flow
            }
        }
    }
}
