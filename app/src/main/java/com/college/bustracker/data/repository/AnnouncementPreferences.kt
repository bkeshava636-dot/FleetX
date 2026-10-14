package com.college.bustracker.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks read announcement IDs locally on the device.
 * Operates without requiring user login so students have independent read/unread status.
 */
object AnnouncementPreferences {

    private const val PREFS_NAME = "fleetx_announcement_prefs"
    private const val KEY_READ_IDS = "read_announcement_ids"

    private const val KEY_NOTIFIED_IDS = "notified_announcement_ids"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getReadAnnouncementIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    fun isAnnouncementRead(context: Context, announcementId: String): Boolean {
        if (announcementId.isBlank()) return true
        return getReadAnnouncementIds(context).contains(announcementId)
    }

    fun markAsRead(context: Context, announcementId: String) {
        if (announcementId.isBlank()) return
        val currentSet = getReadAnnouncementIds(context).toMutableSet()
        if (currentSet.add(announcementId)) {
            getPrefs(context).edit().putStringSet(KEY_READ_IDS, currentSet).apply()
        }
    }

    fun markAllAsRead(context: Context, announcementIds: Collection<String>) {
        if (announcementIds.isEmpty()) return
        val currentSet = getReadAnnouncementIds(context).toMutableSet()
        currentSet.addAll(announcementIds)
        getPrefs(context).edit().putStringSet(KEY_READ_IDS, currentSet).apply()
    }

    fun hasAnnouncementBeenNotified(context: Context, announcementId: String): Boolean {
        if (announcementId.isBlank()) return true
        val set = getPrefs(context).getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet()
        return set.contains(announcementId)
    }

    fun markAnnouncementNotified(context: Context, announcementId: String) {
        if (announcementId.isBlank()) return
        val set = (getPrefs(context).getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet()).toMutableSet()
        if (set.add(announcementId)) {
            getPrefs(context).edit().putStringSet(KEY_NOTIFIED_IDS, set).apply()
        }
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
