package com.college.bustracker.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists anonymous student transit selections and active FCM topic subscriptions.
 * Preserves student's bus and stop selections across app restarts and process recreation.
 */
object StudentPreferences {

    private const val PREFS_NAME = "student_bus_preferences"
    private const val KEY_SELECTED_BUS_ID = "pref_selected_bus_id"
    private const val KEY_SELECTED_STOP_ID = "pref_selected_stop_id"
    private const val KEY_SUBSCRIBED_TOPIC = "pref_subscribed_topic"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedBusId(context: Context): String? {
        return getPrefs(context).getString(KEY_SELECTED_BUS_ID, null)
    }

    fun getSavedStopId(context: Context): String? {
        return getPrefs(context).getString(KEY_SELECTED_STOP_ID, null)
    }

    fun getSubscribedTopic(context: Context): String? {
        return getPrefs(context).getString(KEY_SUBSCRIBED_TOPIC, null)
    }

    fun saveSelection(context: Context, busId: String?, stopId: String?) {
        getPrefs(context).edit().apply {
            if (busId != null) {
                putString(KEY_SELECTED_BUS_ID, busId)
            } else {
                remove(KEY_SELECTED_BUS_ID)
            }
            if (stopId != null) {
                putString(KEY_SELECTED_STOP_ID, stopId)
            } else {
                remove(KEY_SELECTED_STOP_ID)
            }
            apply()
        }
    }

    fun saveSubscribedTopic(context: Context, topic: String?) {
        getPrefs(context).edit().apply {
            if (topic != null) {
                putString(KEY_SUBSCRIBED_TOPIC, topic)
            } else {
                remove(KEY_SUBSCRIBED_TOPIC)
            }
            apply()
        }
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
