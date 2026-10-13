package com.college.bustracker.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Represents an announcement published by campus transport administrators.
 * Stored in Firestore at /announcements/{announcementId}.
 */
@IgnoreExtraProperties
data class Announcement(
    val id: String = "",
    val titleEn: String = "",
    val messageEn: String = "",
    val titleKn: String = "",
    val messageKn: String = "",
    val category: String = CATEGORY_GENERAL,
    val priority: String = PRIORITY_NORMAL,
    val targetAudience: String = AUDIENCE_ALL,
    val targetId: String = "",
    val targetName: String = "",
    val status: String = STATUS_PUBLISHED,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long? = null,
    val expiresAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    // Specific metadata for bus timing change workflow
    val timingBusId: String? = null,
    val timingChangeType: String? = null,
    val previousTime: String? = null,
    val newTime: String? = null,
    val effectiveDate: String? = null,
    val endDate: String? = null,
    val changeReason: String? = null
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_PUBLISHED = "PUBLISHED"
        const val STATUS_ARCHIVED = "ARCHIVED"

        const val CATEGORY_GENERAL = "GENERAL"
        const val CATEGORY_TIMING_CHANGE = "TIMING_CHANGE"
        const val CATEGORY_BUS_DELAY = "BUS_DELAY"
        const val CATEGORY_BUS_CANCELLATION = "BUS_CANCELLATION"
        const val CATEGORY_ROUTE_CHANGE = "ROUTE_CHANGE"
        const val CATEGORY_BUS_SWAP = "BUS_SWAP"
        const val CATEGORY_HOLIDAY = "HOLIDAY"
        const val CATEGORY_EMERGENCY = "EMERGENCY"

        const val PRIORITY_NORMAL = "NORMAL"
        const val PRIORITY_IMPORTANT = "IMPORTANT"
        const val PRIORITY_URGENT = "URGENT"

        const val AUDIENCE_ALL = "ALL"
        const val AUDIENCE_STUDENTS_ONLY = "STUDENTS_ONLY"
        const val AUDIENCE_DRIVERS_ONLY = "DRIVERS_ONLY"
        const val AUDIENCE_SELECTED_BUS = "SELECTED_BUS"
        const val AUDIENCE_SELECTED_ROUTE = "SELECTED_ROUTE"
        const val AUDIENCE_SELECTED_STOP = "SELECTED_STOP"
    }

    /**
     * Returns the localized title depending on the selected language.
     * Gracefully falls back to English if Kannada is not present, and vice versa.
     */
    fun getDisplayTitle(isKannada: Boolean): String {
        return if (isKannada) {
            titleKn.ifBlank { titleEn }
        } else {
            titleEn.ifBlank { titleKn }
        }
    }

    /**
     * Returns the localized message body depending on the selected language.
     * Gracefully falls back to English if Kannada is not present, and vice versa.
     */
    fun getDisplayMessage(isKannada: Boolean): String {
        return if (isKannada) {
            messageKn.ifBlank { messageEn }
        } else {
            messageEn.ifBlank { messageKn }
        }
    }

    /**
     * Checks if this announcement is currently active (published, scheduled time reached, not expired).
     */
    fun isCurrentlyActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (status != STATUS_PUBLISHED) return false
        if (scheduledAt != null && scheduledAt > nowMs) return false
        if (expiresAt != null && expiresAt > 0 && expiresAt <= nowMs) return false
        return true
    }

    /**
     * Evaluates whether this announcement targets the specified student.
     */
    fun isRelevantToStudent(
        studentBusId: String?,
        studentRouteId: String?,
        studentStopId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isCurrentlyActive(nowMs)) return false
        return when (targetAudience) {
            AUDIENCE_ALL, AUDIENCE_STUDENTS_ONLY -> true
            AUDIENCE_SELECTED_BUS -> !studentBusId.isNullOrBlank() && targetId.trim().equals(studentBusId.trim(), ignoreCase = true)
            AUDIENCE_SELECTED_ROUTE -> !studentRouteId.isNullOrBlank() && targetId.trim().equals(studentRouteId.trim(), ignoreCase = true)
            AUDIENCE_SELECTED_STOP -> !studentStopId.isNullOrBlank() && targetId.trim().equals(studentStopId.trim(), ignoreCase = true)
            else -> false
        }
    }

    /**
     * Evaluates whether this announcement targets the specified driver.
     */
    fun isRelevantToDriver(
        driverBusId: String?,
        driverRouteId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isCurrentlyActive(nowMs)) return false
        return when (targetAudience) {
            AUDIENCE_ALL, AUDIENCE_DRIVERS_ONLY -> true
            AUDIENCE_SELECTED_BUS -> !driverBusId.isNullOrBlank() && targetId.trim().equals(driverBusId.trim(), ignoreCase = true)
            AUDIENCE_SELECTED_ROUTE -> !driverRouteId.isNullOrBlank() && targetId.trim().equals(driverRouteId.trim(), ignoreCase = true)
            else -> false
        }
    }

    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "titleEn" to titleEn,
            "messageEn" to messageEn,
            "titleKn" to titleKn,
            "messageKn" to messageKn,
            "category" to category,
            "priority" to priority,
            "targetAudience" to targetAudience,
            "targetId" to targetId,
            "targetName" to targetName,
            "status" to status,
            "createdBy" to createdBy,
            "createdByName" to createdByName,
            "createdAt" to createdAt,
            "scheduledAt" to scheduledAt,
            "expiresAt" to expiresAt,
            "updatedAt" to updatedAt
        )
        if (timingBusId != null) map["timingBusId"] = timingBusId
        if (timingChangeType != null) map["timingChangeType"] = timingChangeType
        if (previousTime != null) map["previousTime"] = previousTime
        if (newTime != null) map["newTime"] = newTime
        if (effectiveDate != null) map["effectiveDate"] = effectiveDate
        if (endDate != null) map["endDate"] = endDate
        if (changeReason != null) map["changeReason"] = changeReason
        return map
    }
}
