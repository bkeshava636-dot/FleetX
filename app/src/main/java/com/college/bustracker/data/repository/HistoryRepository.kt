package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.BusSwapHistory
import com.college.bustracker.data.model.DriverReplacementHistory
import com.college.bustracker.data.model.HistoryAuditLog
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.util.KolkataTimeFormatter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class HistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "HistoryRepository"
    }

    /**
     * Flow of bus swap history records, newest first.
     * Uses memory-side sorting to eliminate composite index requirements,
     * and seamlessly integrates fallback to bus_swaps for complete legacy visibility.
     */
    fun getBusSwapHistoryFlow(): Flow<List<BusSwapHistory>> = callbackFlow {
        var dedicatedHistory = emptyList<BusSwapHistory>()
        var fallbackSwaps = emptyList<BusSwap>()

        fun emitMerged() {
            val dedicatedMap = dedicatedHistory.associateBy { it.swapId }
            val merged = dedicatedHistory.toMutableList()

            // Merge from bus_swaps if not already present in dedicated history
            for (swap in fallbackSwaps) {
                if (swap.swapId.isNotBlank() && !dedicatedMap.containsKey(swap.swapId)) {
                    val createdAt = if (swap.createdAt > 0L) swap.createdAt else System.currentTimeMillis()
                    merged.add(
                        BusSwapHistory(
                            historyId = swap.swapId,
                            swapId = swap.swapId,
                            originalBusId = swap.originalBusId,
                            replacementBusId = swap.replacementBusId,
                            routeId = swap.routeId,
                            date = swap.date.ifBlank { KolkataTimeFormatter.getTodayIso() },
                            timeFormatted = KolkataTimeFormatter.formatTimeOnly(createdAt),
                            reason = swap.message.ifBlank { "Bus swap active" },
                            changedByUid = swap.createdByUid.ifBlank { swap.driverId },
                            changedByName = swap.driverName.ifBlank { "Staff" },
                            effectiveFrom = createdAt,
                            effectiveUntil = swap.cancelledAt,
                            status = swap.status,
                            createdAt = createdAt,
                            cleared = false
                        )
                    )
                }
            }

            val result = merged.filter { !it.cleared }.sortedByDescending { it.createdAt }
            trySend(result)
        }

        val regHistory = firestore.collection("bus_swap_history")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "bus_swap_history listener error: ${error.message}")
                }
                dedicatedHistory = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(BusSwapHistory::class.java)?.copy(historyId = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed parsing BusSwapHistory ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                emitMerged()
            }

        val regSwaps = firestore.collection("bus_swaps")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "bus_swaps fallback listener error: ${error.message}")
                }
                fallbackSwaps = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        if (doc.getBoolean("cleared") == true) return@mapNotNull null
                        doc.toObject(BusSwap::class.java)?.copy(swapId = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                emitMerged()
            }

        awaitClose {
            regHistory.remove()
            regSwaps.remove()
        }
    }

    /**
     * Flow of driver replacement history records, newest first.
     * Uses memory-side sorting and seamlessly integrates fallback to temporary_assignments.
     */
    fun getDriverReplacementHistoryFlow(): Flow<List<DriverReplacementHistory>> = callbackFlow {
        var dedicatedHistory = emptyList<DriverReplacementHistory>()
        var fallbackAssignments = emptyList<TemporaryAssignment>()

        fun emitMerged() {
            val dedicatedMap = dedicatedHistory.associateBy { it.assignmentId }
            val merged = dedicatedHistory.toMutableList()

            for (assign in fallbackAssignments) {
                if (assign.assignmentId.isNotBlank() && !dedicatedMap.containsKey(assign.assignmentId)) {
                    val createdAt = if (assign.createdAt > 0L) assign.createdAt else assign.startAt
                    merged.add(
                        DriverReplacementHistory(
                            historyId = assign.assignmentId,
                            assignmentId = assign.assignmentId,
                            previousDriverUid = assign.permanentDriverUid,
                            previousDriverName = assign.permanentDriverName,
                            replacementDriverUid = assign.replacementDriverUid,
                            replacementDriverName = assign.replacementDriverName,
                            busId = assign.busId,
                            date = KolkataTimeFormatter.formatDateOnly(createdAt),
                            timeFormatted = KolkataTimeFormatter.formatTimeOnly(createdAt),
                            reason = assign.reason.ifBlank { "Temporary driver coverage" },
                            changedByUid = assign.createdByUid,
                            changedByName = assign.createdByRole.ifBlank { "Staff" },
                            effectiveFrom = assign.startAt,
                            effectiveUntil = assign.endAt,
                            status = assign.status ?: "ACTIVE",
                            createdAt = createdAt,
                            cleared = false
                        )
                    )
                }
            }

            val result = merged.filter { !it.cleared }.sortedByDescending { it.createdAt }
            trySend(result)
        }

        val regHistory = firestore.collection("driver_replacement_history")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "driver_replacement_history listener error: ${error.message}")
                }
                dedicatedHistory = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(DriverReplacementHistory::class.java)?.copy(historyId = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed parsing DriverReplacementHistory ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                emitMerged()
            }

        val regAssignments = firestore.collection("temporary_assignments")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "temporary_assignments fallback listener error: ${error.message}")
                }
                fallbackAssignments = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        if (doc.getBoolean("cleared") == true) return@mapNotNull null
                        doc.toObject(TemporaryAssignment::class.java)?.copy(assignmentId = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                emitMerged()
            }

        awaitClose {
            regHistory.remove()
            regAssignments.remove()
        }
    }

    /**
     * Flow of history audit logs, newest first.
     */
    fun getAuditLogsFlow(): Flow<List<HistoryAuditLog>> = callbackFlow {
        val registration = firestore.collection("history_audit_logs")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "history_audit_logs listener error: ${error?.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(HistoryAuditLog::class.java)?.copy(auditId = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.timestamp }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Safely clears historical bus swaps.
     * Safety guarantee:
     * - Only marks non-active / completed / cancelled swaps as cleared.
     * - NEVER cancels an active bus swap.
     * - Logs operation into history_audit_logs.
     */
    suspend fun clearBusSwapHistory(adminUid: String, adminName: String): Result<Int> {
        return try {
            val snapshot = firestore.collection("bus_swap_history").get().await()
            val now = System.currentTimeMillis()
            val updates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, String>>()

            for (doc in snapshot.documents) {
                val status = doc.getString("status") ?: ""
                val isCleared = doc.getBoolean("cleared") ?: false
                val effectiveUntil = doc.getLong("effectiveUntil") ?: doc.getLong("cancelledAt") ?: 0L
                val isExpired = effectiveUntil in 1 until now
                if ((status != "ACTIVE" || isExpired) && !isCleared) {
                    val swapId = doc.getString("swapId") ?: doc.id
                    updates.add(doc.reference to swapId)
                }
            }

            // Also check bus_swaps collection for any non-active swaps to ensure fallback records are cleared
            try {
                val swapsSnap = firestore.collection("bus_swaps").get().await()
                for (doc in swapsSnap.documents) {
                    val status = doc.getString("status") ?: ""
                    val isCleared = doc.getBoolean("cleared") ?: false
                    if (status != BusSwap.STATUS_ACTIVE && !isCleared) {
                        val swapId = doc.getString("swapId") ?: doc.id
                        updates.add(doc.reference to swapId)
                    }
                }
            } catch (ignored: Exception) {}

            val committedIds = mutableSetOf<String>()
            var commitException: Exception? = null

            // Commit updates in batches of up to 450 writes (staying safely below Firestore 500-write limit)
            for (chunk in updates.chunked(450)) {
                val batch = firestore.batch()
                for ((ref, _) in chunk) {
                    batch.update(ref, mapOf("cleared" to true, "clearedAt" to now))
                }
                try {
                    batch.commit().await()
                    for ((_, id) in chunk) {
                        committedIds.add(id)
                    }
                } catch (e: Exception) {
                    commitException = e
                    break
                }
            }

            // Write audit entry if any chunks were committed
            if (committedIds.isNotEmpty()) {
                val auditRef = firestore.collection("history_audit_logs").document()
                val audit = HistoryAuditLog(
                    auditId = auditRef.id,
                    action = "CLEAR_BUS_SWAP_HISTORY",
                    adminUid = adminUid,
                    adminName = adminName,
                    timestamp = now,
                    dateFormatted = KolkataTimeFormatter.formatDateOnly(now),
                    timeFormatted = KolkataTimeFormatter.formatTimeOnly(now),
                    clearedCount = committedIds.size
                )
                try {
                    auditRef.set(audit).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write clear bus swap audit log: ${e.message}")
                }
            }

            if (commitException != null) {
                throw commitException
            }

            Result.success(committedIds.size)
        } catch (e: Exception) {
            Log.e(TAG, "clearBusSwapHistory failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Safely clears historical driver replacements.
     * Safety guarantee:
     * - Only marks non-active / completed / cancelled replacements as cleared.
     * - NEVER touches active assignments or accounts.
     * - Logs operation into history_audit_logs.
     */
    suspend fun clearDriverReplacementHistory(adminUid: String, adminName: String): Result<Int> {
        return try {
            val snapshot = firestore.collection("driver_replacement_history").get().await()
            val now = System.currentTimeMillis()
            val updates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, String>>()

            for (doc in snapshot.documents) {
                val status = doc.getString("status") ?: ""
                val isCleared = doc.getBoolean("cleared") ?: false
                val endAt = doc.getLong("effectiveUntil") ?: doc.getLong("endAt") ?: 0L
                val isExpired = endAt in 1 until now
                if ((status != "ACTIVE" || isExpired) && !isCleared) {
                    val assignId = doc.getString("assignmentId") ?: doc.id
                    updates.add(doc.reference to assignId)
                }
            }

            // Also check temporary_assignments collection
            try {
                val assignSnap = firestore.collection("temporary_assignments").get().await()
                for (doc in assignSnap.documents) {
                    val status = doc.getString("status") ?: ""
                    val isCleared = doc.getBoolean("cleared") ?: false
                    if (status != "ACTIVE" && !isCleared) {
                        val assignId = doc.getString("assignmentId") ?: doc.id
                        updates.add(doc.reference to assignId)
                    }
                }
            } catch (ignored: Exception) {}

            val committedIds = mutableSetOf<String>()
            var commitException: Exception? = null

            // Commit updates in batches of up to 450 writes (staying safely below Firestore 500-write limit)
            for (chunk in updates.chunked(450)) {
                val batch = firestore.batch()
                for ((ref, _) in chunk) {
                    batch.update(ref, mapOf("cleared" to true, "clearedAt" to now))
                }
                try {
                    batch.commit().await()
                    for ((_, id) in chunk) {
                        committedIds.add(id)
                    }
                } catch (e: Exception) {
                    commitException = e
                    break
                }
            }

            // Write audit entry if any chunks were committed
            if (committedIds.isNotEmpty()) {
                val auditRef = firestore.collection("history_audit_logs").document()
                val audit = HistoryAuditLog(
                    auditId = auditRef.id,
                    action = "CLEAR_DRIVER_REPLACEMENT_HISTORY",
                    adminUid = adminUid,
                    adminName = adminName,
                    timestamp = now,
                    dateFormatted = KolkataTimeFormatter.formatDateOnly(now),
                    timeFormatted = KolkataTimeFormatter.formatTimeOnly(now),
                    clearedCount = committedIds.size
                )
                try {
                    auditRef.set(audit).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write clear driver replacement audit log: ${e.message}")
                }
            }

            if (commitException != null) {
                throw commitException
            }

            Result.success(committedIds.size)
        } catch (e: Exception) {
            Log.e(TAG, "clearDriverReplacementHistory failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
