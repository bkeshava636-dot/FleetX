package com.college.bustracker.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BusSwapHistory(
    val historyId: String = "",
    val swapId: String = "",
    val originalBusId: String = "",
    val replacementBusId: String = "",
    val routeId: String = "",
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
