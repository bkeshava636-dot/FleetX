package com.college.bustracker.data.repository

import com.college.bustracker.data.model.BusSwapHistory
import com.college.bustracker.data.model.DriverReplacementHistory
import com.college.bustracker.data.model.HistoryAuditLog
import com.college.bustracker.util.KolkataTimeFormatter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class HistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Flow of bus swap history records, newest first.
     */
    fun getBusSwapHistoryFlow(): Flow<List<BusSwapHistory>> = callbackFlow {
        val registration = firestore.collection("bus_swap_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(BusSwapHistory::class.java)?.copy(historyId = doc.id)
                }.filter { !it.cleared }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Flow of driver replacement history records, newest first.
     */
    fun getDriverReplacementHistoryFlow(): Flow<List<DriverReplacementHistory>> = callbackFlow {
        val registration = firestore.collection("driver_replacement_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DriverReplacementHistory::class.java)?.copy(historyId = doc.id)
                }.filter { !it.cleared }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Flow of history audit logs, newest first.
     */
    fun getAuditLogsFlow(): Flow<List<HistoryAuditLog>> = callbackFlow {
        val registration = firestore.collection("history_audit_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(HistoryAuditLog::class.java)?.copy(auditId = doc.id)
                }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Safely clears historical bus swaps.
     * Safety guarantee:
     * - Only marks non-active / completed / cancelled swaps as cleared.
     * - NEVER cancels an active bus swap.
     * - NEVER restores old driver assignments, changes schedules, or alters GPS routing.
     * - Logs operation into history_audit_logs.
     */
    suspend fun clearBusSwapHistory(adminUid: String, adminName: String): Result<Int> {
        return try {
            val snapshot = firestore.collection("bus_swap_history").get().await()
            val now = System.currentTimeMillis()
            var count = 0

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                val status = doc.getString("status") ?: ""
                val isCleared = doc.getBoolean("cleared") ?: false
                // Strict Safety Check: NEVER clear active swaps
                if (status != "ACTIVE" && !isCleared) {
                    batch.update(doc.reference, mapOf("cleared" to true, "clearedAt" to now))
                    count++
                }
            }

            if (count > 0) {
                val auditRef = firestore.collection("history_audit_logs").document()
                val audit = HistoryAuditLog(
                    auditId = auditRef.id,
                    action = "CLEAR_BUS_SWAP_HISTORY",
                    adminUid = adminUid,
                    adminName = adminName,
                    timestamp = now,
                    dateFormatted = KolkataTimeFormatter.formatDateOnly(now),
                    timeFormatted = KolkataTimeFormatter.formatTimeOnly(now),
                    clearedCount = count
                )
                batch.set(auditRef, audit)
                batch.commit().await()
            }

            Result.success(count)
        } catch (e: Exception) {
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
            var count = 0

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                val status = doc.getString("status") ?: ""
                val isCleared = doc.getBoolean("cleared") ?: false
                // Strict Safety Check: NEVER clear active assignments
                if (status != "ACTIVE" && !isCleared) {
                    batch.update(doc.reference, mapOf("cleared" to true, "clearedAt" to now))
                    count++
                }
            }

            if (count > 0) {
                val auditRef = firestore.collection("history_audit_logs").document()
                val audit = HistoryAuditLog(
                    auditId = auditRef.id,
                    action = "CLEAR_DRIVER_REPLACEMENT_HISTORY",
                    adminUid = adminUid,
                    adminName = adminName,
                    timestamp = now,
                    dateFormatted = KolkataTimeFormatter.formatDateOnly(now),
                    timeFormatted = KolkataTimeFormatter.formatTimeOnly(now),
                    clearedCount = count
                )
                batch.set(auditRef, audit)
                batch.commit().await()
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
