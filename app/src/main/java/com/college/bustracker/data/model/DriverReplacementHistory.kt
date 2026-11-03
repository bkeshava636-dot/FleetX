package com.college.bustracker.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class DriverReplacementHistory(
    val historyId: String = "",
    val assignmentId: String = "",
    val previousDriverUid: String = "",
    val previousDriverName: String = "",
    val replacementDriverUid: String = "",
    val replacementDriverName: String = "",
    val busId: String = "",
    val date: String = "",
    val timeFormatted: String = "",
    val reason: String = "",
    val changedByUid: String = "",
    val changedByName: String = "",
    val effectiveFrom: Long = 0L,
    val effectiveUntil: Long = 0L,
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, CANCELLED
    val createdAt: Long = System.currentTimeMillis(),
    val cleared: Boolean = false
)
