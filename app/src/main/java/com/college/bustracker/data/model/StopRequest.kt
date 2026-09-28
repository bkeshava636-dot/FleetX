package com.college.bustracker.data.model

/**
 * Represents a driver's proposed bus stop submission sent to Admin for review.
 * Stored in Firestore at /stop_requests/{requestId}.
 */
data class StopRequest(
    val requestId: String = "",
    val driverUid: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val busId: String = "",
    val routeId: String = "",
    val proposedStopName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = STATUS_PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val reviewedBy: String? = null,
    val adminNote: String? = null,
    val rejectionReason: String? = null,
    val finalStopName: String? = null,
    val finalLatitude: Double? = null,
    val finalLongitude: Double? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
    }

    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "requestId" to requestId,
            "driverUid" to driverUid,
            "driverId" to driverId,
            "driverName" to driverName,
            "busId" to busId,
            "routeId" to routeId,
            "proposedStopName" to proposedStopName,
            "latitude" to latitude,
            "longitude" to longitude,
            "status" to status,
            "createdAt" to createdAt
        )
        if (reviewedAt != null) map["reviewedAt"] = reviewedAt
        if (reviewedBy != null) map["reviewedBy"] = reviewedBy
        if (adminNote != null) map["adminNote"] = adminNote
        if (rejectionReason != null) map["rejectionReason"] = rejectionReason
        if (finalStopName != null) map["finalStopName"] = finalStopName
        if (finalLatitude != null) map["finalLatitude"] = finalLatitude
        if (finalLongitude != null) map["finalLongitude"] = finalLongitude
        return map
    }
}
