package com.college.bustracker.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class HistoryAuditLog(
    val auditId: String = "",
    val action: String = "", // CLEAR_BUS_SWAP_HISTORY, CLEAR_DRIVER_REPLACEMENT_HISTORY
    val adminUid: String = "",
    val adminName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val dateFormatted: String = "",
    val timeFormatted: String = "",
    val clearedCount: Int = 0
)
