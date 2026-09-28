package com.college.bustracker.data.repository

import com.college.bustracker.data.model.BusSwap
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class BusSwapRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val _localSwaps = MutableStateFlow<List<BusSwap>>(emptyList())

    fun getSwapsFlow(): Flow<List<BusSwap>> = callbackFlow {
        trySend(_localSwaps.value)

        val registration = firestore.collection("bus_swaps")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(_localSwaps.value)
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { it.toObject(BusSwap::class.java) }
                _localSwaps.value = list
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Reports a new bus swap alert with single-active-alert-per-bus enforcement.
     * Throws IllegalStateException if an active swap already exists for the same original bus.
     */
    suspend fun reportSwap(swap: BusSwap): Result<Unit> {
        val existingActive = _localSwaps.value.firstOrNull {
            it.originalBusId.equals(swap.originalBusId, ignoreCase = true) &&
                    it.status == BusSwap.STATUS_ACTIVE &&
                    it.swapId != swap.swapId
        }
        if (existingActive != null) {
            return Result.failure(
                IllegalStateException("An active bus swap alert already exists for ${swap.originalBusId}. Please edit or cancel the current swap before creating a new one.")
            )
        }

        val current = _localSwaps.value.toMutableList()
        current.removeAll { it.swapId == swap.swapId }
        current.add(0, swap)
        _localSwaps.value = current

        return try {
            kotlinx.coroutines.withTimeoutOrNull(2500L) {
                firestore.collection("bus_swaps").document(swap.swapId).set(swap).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing bus swap document in place without creating a duplicate.
     */
    suspend fun updateSwap(swap: BusSwap): Result<Unit> {
        val updatedSwap = swap.copy(updatedAt = System.currentTimeMillis())
        val original = _localSwaps.value
        val updatedList = original.map {
            if (it.swapId == swap.swapId) updatedSwap else it
        }
        _localSwaps.value = updatedList

        return try {
            kotlinx.coroutines.withTimeoutOrNull(2500L) {
                firestore.collection("bus_swaps").document(swap.swapId).set(updatedSwap).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _localSwaps.value = original
            Result.failure(e)
        }
    }

    /**
     * Cancels an active bus swap alert and records cancellation metadata.
     */
    suspend fun cancelSwap(swapId: String, cancelledByUid: String = ""): Result<Unit> {
        val now = System.currentTimeMillis()
        val original = _localSwaps.value
        val updated = original.map {
            if (it.swapId == swapId) it.copy(
                status = BusSwap.STATUS_CANCELLED,
                cancelledAt = now,
                cancelledByUid = cancelledByUid
            ) else it
        }
        _localSwaps.value = updated

        return try {
            val updates = mapOf(
                "status" to BusSwap.STATUS_CANCELLED,
                "cancelledAt" to now,
                "cancelledByUid" to cancelledByUid
            )
            firestore.collection("bus_swaps").document(swapId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            _localSwaps.value = original
            Result.failure(e)
        }
    }

    suspend fun updateSwapStatus(swapId: String, status: String): Result<Unit> {
        if (status == BusSwap.STATUS_CANCELLED) {
            return cancelSwap(swapId)
        }
        val original = _localSwaps.value
        val updated = original.map {
            if (it.swapId == swapId) it.copy(status = status) else it
        }
        _localSwaps.value = updated
        return try {
            firestore.collection("bus_swaps").document(swapId).update("status", status).await()
            Result.success(Unit)
        } catch (e: Exception) {
            _localSwaps.value = original
            Result.failure(e)
        }
    }
}
