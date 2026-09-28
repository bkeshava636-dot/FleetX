package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.StopRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class StopRequestRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "StopRequestRepository"
        const val COLLECTION_STOP_REQUESTS = "stop_requests"
    }

    private val _requestsFlow = MutableStateFlow<List<StopRequest>>(emptyList())

    /**
     * Driver submits a proposed bus stop.
     */
    suspend fun submitStopRequest(request: StopRequest): Result<Unit> = runCatching {
        require(request.requestId.isNotBlank()) { "Request ID must not be blank" }
        require(request.proposedStopName.isNotBlank()) { "Proposed stop name must not be blank" }
        require(request.driverUid.isNotBlank()) { "Driver UID must not be blank" }
        require(request.busId.isNotBlank()) { "Bus ID must not be blank" }
        require(request.latitude != 0.0 && request.longitude != 0.0) { "Valid GPS coordinates required" }

        firestore.collection(COLLECTION_STOP_REQUESTS)
            .document(request.requestId)
            .set(request.toMap())
            .await()
        Log.i(TAG, "Stop request submitted: ${request.requestId} for bus ${request.busId}")
    }

    /**
     * Admin real-time flow of all stop requests (ordered by creation time descending).
     */
    fun getStopRequestsFlow(): Flow<List<StopRequest>> = callbackFlow {
        trySend(_requestsFlow.value)

        val registration = firestore.collection(COLLECTION_STOP_REQUESTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Error listening to stop requests: ${error?.message}")
                    trySend(_requestsFlow.value)
                    return@addSnapshotListener
                }

                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        StopRequest(
                            requestId = doc.getString("requestId") ?: doc.id,
                            driverUid = doc.getString("driverUid") ?: "",
                            driverId = doc.getString("driverId") ?: "",
                            driverName = doc.getString("driverName") ?: "",
                            busId = doc.getString("busId") ?: "",
                            routeId = doc.getString("routeId") ?: "",
                            proposedStopName = doc.getString("proposedStopName") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            status = doc.getString("status") ?: StopRequest.STATUS_PENDING,
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            reviewedAt = doc.getLong("reviewedAt"),
                            reviewedBy = doc.getString("reviewedBy"),
                            adminNote = doc.getString("adminNote"),
                            rejectionReason = doc.getString("rejectionReason"),
                            finalStopName = doc.getString("finalStopName"),
                            finalLatitude = doc.getDouble("finalLatitude"),
                            finalLongitude = doc.getDouble("finalLongitude")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse StopRequest ${doc.id}: ${e.message}")
                        null
                    }
                }

                _requestsFlow.value = list
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Driver real-time flow of their own requests.
     */
    fun getDriverStopRequestsFlow(driverUid: String): Flow<List<StopRequest>> = callbackFlow {
        val registration = firestore.collection(COLLECTION_STOP_REQUESTS)
            .whereEqualTo("driverUid", driverUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Error listening to driver stop requests: ${error?.message}")
                    return@addSnapshotListener
                }

                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        StopRequest(
                            requestId = doc.getString("requestId") ?: doc.id,
                            driverUid = doc.getString("driverUid") ?: "",
                            driverId = doc.getString("driverId") ?: "",
                            driverName = doc.getString("driverName") ?: "",
                            busId = doc.getString("busId") ?: "",
                            routeId = doc.getString("routeId") ?: "",
                            proposedStopName = doc.getString("proposedStopName") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            status = doc.getString("status") ?: StopRequest.STATUS_PENDING,
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            reviewedAt = doc.getLong("reviewedAt"),
                            reviewedBy = doc.getString("reviewedBy"),
                            adminNote = doc.getString("adminNote"),
                            rejectionReason = doc.getString("rejectionReason"),
                            finalStopName = doc.getString("finalStopName"),
                            finalLatitude = doc.getDouble("finalLatitude"),
                            finalLongitude = doc.getDouble("finalLongitude")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Admin approves a stop request.
     */
    suspend fun approveStopRequest(
        requestId: String,
        adminUid: String,
        finalName: String,
        finalLat: Double,
        finalLng: Double,
        adminNote: String? = null
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any?>(
            "status" to StopRequest.STATUS_APPROVED,
            "reviewedAt" to System.currentTimeMillis(),
            "reviewedBy" to adminUid,
            "finalStopName" to finalName,
            "finalLatitude" to finalLat,
            "finalLongitude" to finalLng
        )
        if (adminNote != null) updates["adminNote"] = adminNote

        firestore.collection(COLLECTION_STOP_REQUESTS)
            .document(requestId)
            .update(updates)
            .await()
        Log.i(TAG, "Stop request $requestId APPROVED by $adminUid")
    }

    /**
     * Admin rejects a stop request.
     */
    suspend fun rejectStopRequest(
        requestId: String,
        adminUid: String,
        rejectionReason: String? = null
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any?>(
            "status" to StopRequest.STATUS_REJECTED,
            "reviewedAt" to System.currentTimeMillis(),
            "reviewedBy" to adminUid
        )
        if (rejectionReason != null) updates["rejectionReason"] = rejectionReason

        firestore.collection(COLLECTION_STOP_REQUESTS)
            .document(requestId)
            .update(updates)
            .await()
        Log.i(TAG, "Stop request $requestId REJECTED by $adminUid (Reason: $rejectionReason)")
    }

    /**
     * Admin updates a pending stop request draft (name, location, or note).
     */
    suspend fun updateStopRequestDraft(
        requestId: String,
        name: String,
        lat: Double,
        lng: Double,
        adminNote: String? = null
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any?>(
            "finalStopName" to name,
            "finalLatitude" to lat,
            "finalLongitude" to lng
        )
        if (adminNote != null) updates["adminNote"] = adminNote

        firestore.collection(COLLECTION_STOP_REQUESTS)
            .document(requestId)
            .update(updates)
            .await()
    }
}
