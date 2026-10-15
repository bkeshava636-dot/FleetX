package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class EffectiveOperatorInfo(
    val busId: String,
    val isTemporary: Boolean,
    val driverUid: String,
    val driverId: String,
    val driverName: String,
    val driverPhone: String?,
    val temporaryAssignmentId: String? = null,
    val operatorEpoch: Long = 0L
)

class TemporaryAssignmentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val functionsProvider: () -> FirebaseFunctions = { FirebaseFunctions.getInstance("asia-south1") }
) {

    companion object {
        private const val TAG = "TemporaryAssignRepo"
    }

    // Server time offset tracking from RTDB /.info/serverTimeOffset
    private var serverTimeOffsetMillis: Long = 0L

    init {
        try {
            database.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    serverTimeOffsetMillis = snapshot.getValue(Long::class.java) ?: 0L
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to listen to server time offset: ${e.message}")
        }
    }

    fun getEstimatedServerTime(): Long {
        return System.currentTimeMillis() + serverTimeOffsetMillis
    }

    /**
     * Real-time stream of all temporary assignments (for Admin dashboard).
     */
    fun getAllAssignmentsFlow(): Flow<List<TemporaryAssignment>> = callbackFlow {
        val registration = firestore.collection("temporary_assignments")
            .orderBy("startAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Error fetching all assignments: ${error?.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(TemporaryAssignment::class.java)?.copy(assignmentId = doc.id)
                }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Real-time stream of temporary assignments for a specific bus.
     */
    fun getAssignmentsForBusFlow(busId: String): Flow<List<TemporaryAssignment>> = callbackFlow {
        if (busId.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val registration = firestore.collection("temporary_assignments")
            .whereEqualTo("busId", busId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(TemporaryAssignment::class.java)?.copy(assignmentId = doc.id)
                }.sortedByDescending { it.startAt }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Creates a temporary replacement via Callable Cloud Function (with Firestore/RTDB direct fallback).
     */
    suspend fun createTemporaryAssignment(
        busId: String,
        replacementDriverUid: String,
        startAt: Long,
        endAt: Long,
        callerUid: String,
        callerRole: String = "DRIVER",
        permanentDriverUid: String,
        permanentDriverId: String,
        permanentDriverName: String,
        permanentDriverPhone: String? = null,
        replacementDriverProfile: UserProfile
    ): Result<TemporaryAssignment> {
        return try {
            // First attempt secure HTTPS Callable function
            val data = hashMapOf(
                "busId" to busId,
                "replacementDriverUid" to replacementDriverUid,
                "startAt" to startAt,
                "endAt" to endAt
            )

            try {
                val callResult = functionsProvider().getHttpsCallable("createTemporaryAssignment")
                    .call(data)
                    .await()
                val resMap = callResult.data as? Map<*, *>
                val assignmentMap = resMap?.get("assignment") as? Map<*, *>
                if (assignmentMap != null) {
                    val assignment = mapToTemporaryAssignment(assignmentMap)
                    return Result.success(assignment)
                }
            } catch (fnEx: Exception) {
                Log.w(TAG, "Cloud function createTemporaryAssignment unavailable, using direct transaction: ${fnEx.message}")
            }

            // Direct Firestore Transaction + RTDB synchronization fallback
            val conflictCheck = firestore.collection("temporary_assignments")
                .whereEqualTo("busId", busId)
                .whereEqualTo("cancelledAt", null)
                .get()
                .await()

            for (doc in conflictCheck.documents) {
                val eStart = doc.getLong("startAt") ?: 0L
                val eEnd = doc.getLong("endAt") ?: 0L
                if (startAt < eEnd && endAt > eStart) {
                    return Result.failure(IllegalStateException("A temporary replacement already exists for bus $busId during this period."))
                }
            }

            // Check replacement driver conflict
            val driverConflict = firestore.collection("temporary_assignments")
                .whereEqualTo("replacementDriverUid", replacementDriverUid)
                .whereEqualTo("cancelledAt", null)
                .get()
                .await()

            for (doc in driverConflict.documents) {
                val eStart = doc.getLong("startAt") ?: 0L
                val eEnd = doc.getLong("endAt") ?: 0L
                if (startAt < eEnd && endAt > eStart) {
                    return Result.failure(IllegalStateException("Replacement driver is already operating another bus during this period."))
                }
            }

            val newDocRef = firestore.collection("temporary_assignments").document()
            val now = System.currentTimeMillis()
            val created = TemporaryAssignment(
                assignmentId = newDocRef.id,
                busId = busId,
                permanentDriverId = permanentDriverId,
                permanentDriverUid = permanentDriverUid,
                permanentDriverName = permanentDriverName,
                permanentDriverPhone = permanentDriverPhone,
                replacementDriverId = replacementDriverProfile.customId.ifBlank { replacementDriverUid },
                replacementDriverUid = replacementDriverUid,
                replacementDriverName = replacementDriverProfile.name,
                replacementDriverPhone = replacementDriverProfile.phoneNumber,
                startAt = startAt,
                endAt = endAt,
                createdByUid = callerUid,
                createdByRole = callerRole,
                createdAt = now,
                updatedAt = now,
                cancelledAt = null,
                cancelledByUid = null,
                cancelledByRole = null,
                status = "ACTIVE",
                operatorEpoch = now
            )

            newDocRef.set(created).await()

            // Synchronize RTDB /bus_operators/{busId}
            try {
                val rtdbPayload = hashMapOf<String, Any?>(
                    "busId" to busId,
                    "permanentDriverUid" to permanentDriverUid,
                    "temporaryDriverUid" to replacementDriverUid,
                    "hasTemporary" to true,
                    "startAt" to startAt,
                    "endAt" to endAt,
                    "temporaryAssignmentId" to newDocRef.id,
                    "operatorEpoch" to now
                )
                database.getReference("bus_operators").child(busId).setValue(rtdbPayload).await()
            } catch (rtdbEx: Exception) {
                Log.w(TAG, "Failed direct write to RTDB bus_operators: ${rtdbEx.message}")
            }

            Result.success(created)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temporary assignment: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Cancels an active or upcoming temporary assignment.
     */
    suspend fun cancelTemporaryAssignment(
        assignmentId: String,
        busId: String = "",
        cancelledByUid: String = "",
        cancelledByRole: String = "DRIVER"
    ): Result<Unit> {
        return try {
            val data = hashMapOf(
                "assignmentId" to assignmentId,
                "cancelledByUid" to cancelledByUid,
                "cancelledByRole" to cancelledByRole
            )
            try {
                functionsProvider().getHttpsCallable("cancelTemporaryAssignment")
                    .call(data)
                    .await()
                return Result.success(Unit)
            } catch (fnEx: Exception) {
                Log.w(TAG, "Cloud function cancelTemporaryAssignment unavailable, using direct update: ${fnEx.message}")
            }

            val now = System.currentTimeMillis()
            val updatePayload = hashMapOf<String, Any?>(
                "cancelledAt" to now,
                "status" to "CANCELLED",
                "cancelledByUid" to cancelledByUid,
                "cancelledByRole" to cancelledByRole,
                "updatedAt" to now
            )
            firestore.collection("temporary_assignments").document(assignmentId)
                .update(updatePayload)
                .await()

            try {
                val updateMap = hashMapOf<String, Any?>(
                    "hasTemporary" to false,
                    "temporaryDriverUid" to null,
                    "cancelledAt" to now,
                    "operatorEpoch" to now
                )
                database.getReference("bus_operators").child(busId).updateChildren(updateMap).await()
            } catch (rtdbEx: Exception) {
                Log.w(TAG, "Failed updating RTDB bus_operators on cancellation: ${rtdbEx.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling temporary assignment: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Resolves the current effective operator for a bus.
     */
    suspend fun resolveEffectiveOperator(
        busId: String,
        permanentDriverUid: String,
        permanentDriverName: String,
        permanentDriverPhone: String?,
        currentTime: Long = getEstimatedServerTime()
    ): EffectiveOperatorInfo {
        if (busId.isBlank()) {
            return EffectiveOperatorInfo(
                busId = busId,
                isTemporary = false,
                driverUid = permanentDriverUid,
                driverId = permanentDriverUid,
                driverName = permanentDriverName,
                driverPhone = permanentDriverPhone
            )
        }

        try {
            val snapshot = firestore.collection("temporary_assignments")
                .whereEqualTo("busId", busId)
                .whereEqualTo("cancelledAt", null)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val assignment = doc.toObject(TemporaryAssignment::class.java)
                if (assignment != null && assignment.isActive(currentTime)) {
                    return EffectiveOperatorInfo(
                        busId = busId,
                        isTemporary = true,
                        driverUid = assignment.replacementDriverUid,
                        driverId = assignment.replacementDriverId,
                        driverName = assignment.replacementDriverName,
                        driverPhone = assignment.replacementDriverPhone,
                        temporaryAssignmentId = assignment.assignmentId,
                        operatorEpoch = assignment.operatorEpoch
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve effective operator from Firestore: ${e.message}")
        }

        return EffectiveOperatorInfo(
            busId = busId,
            isTemporary = false,
            driverUid = permanentDriverUid,
            driverId = permanentDriverUid,
            driverName = permanentDriverName,
            driverPhone = permanentDriverPhone
        )
    }

    private fun mapToTemporaryAssignment(map: Map<*, *>): TemporaryAssignment {
        return TemporaryAssignment(
            assignmentId = map["assignmentId"] as? String ?: "",
            busId = map["busId"] as? String ?: "",
            permanentDriverId = map["permanentDriverId"] as? String ?: "",
            permanentDriverUid = map["permanentDriverUid"] as? String ?: "",
            permanentDriverName = map["permanentDriverName"] as? String ?: "",
            replacementDriverId = map["replacementDriverId"] as? String ?: "",
            replacementDriverUid = map["replacementDriverUid"] as? String ?: "",
            replacementDriverName = map["replacementDriverName"] as? String ?: "",
            replacementDriverPhone = map["replacementDriverPhone"] as? String,
            startAt = (map["startAt"] as? Number)?.toLong() ?: 0L,
            endAt = (map["endAt"] as? Number)?.toLong() ?: 0L,
            createdByUid = map["createdByUid"] as? String ?: "",
            createdByRole = map["createdByRole"] as? String ?: "DRIVER",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
            cancelledAt = (map["cancelledAt"] as? Number)?.toLong(),
            cancelledByUid = map["cancelledByUid"] as? String,
            cancelledByRole = map["cancelledByRole"] as? String,
            status = map["status"] as? String,
            operatorEpoch = (map["operatorEpoch"] as? Number)?.toLong() ?: 0L
        )
    }
}
