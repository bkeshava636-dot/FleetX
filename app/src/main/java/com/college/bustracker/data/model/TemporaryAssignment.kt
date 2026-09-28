package com.college.bustracker.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class TemporaryAssignment(
    val assignmentId: String = "",
    val busId: String = "",
    val permanentDriverId: String = "",
    val permanentDriverUid: String = "",
    val permanentDriverName: String = "",
    val permanentDriverPhone: String? = null,
    val replacementDriverId: String = "",
    val replacementDriverUid: String = "",
    val replacementDriverName: String = "",
    val replacementDriverPhone: String? = null,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val createdByUid: String = "",
    val createdByRole: String = "DRIVER", // DRIVER or ADMIN
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val cancelledAt: Long? = null,
    val cancelledByUid: String? = null,
    val cancelledByRole: String? = null, // DRIVER or ADMIN
    val status: String? = null, // ACTIVE, CANCELLED, etc.
    val operatorEpoch: Long = System.currentTimeMillis()
) {
    fun isCancelled(): Boolean = cancelledAt != null || status == "CANCELLED"

    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !isCancelled() && nowMillis in startAt until endAt

    fun isUpcoming(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !isCancelled() && nowMillis < startAt

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !isCancelled() && nowMillis >= endAt

    fun getStatus(nowMillis: Long = System.currentTimeMillis()): String = when {
        isCancelled() -> "CANCELLED"
        isActive(nowMillis) -> "ACTIVE"
        isUpcoming(nowMillis) -> "UPCOMING"
        else -> "EXPIRED"
    }
}
