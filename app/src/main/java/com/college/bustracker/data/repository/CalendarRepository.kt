package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.CalendarException
import com.college.bustracker.util.CollegeCalendarService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CalendarRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "CalendarRepository"
        const val COLLECTION_EXCEPTIONS = "calendar_exceptions"
    }

    private val _exceptionsFlow = MutableStateFlow<List<CalendarException>>(emptyList())

    fun getCalendarExceptionsFlow(): Flow<List<CalendarException>> = callbackFlow {
        trySend(_exceptionsFlow.value)

        val registration = firestore.collection(COLLECTION_EXCEPTIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Error listening to calendar exceptions: ${error?.message}")
                    trySend(_exceptionsFlow.value)
                    return@addSnapshotListener
                }

                val list = snapshot.documents.mapNotNull { doc ->
                    if (doc.id == "operating_schedule" || doc.id.startsWith("operating_") || doc.id == "global_schedule") {
                        return@mapNotNull null
                    }
                    try {
                        val date = doc.getString("date") ?: doc.id
                        if (!date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                            return@mapNotNull null
                        }
                        val type = doc.getString("type") ?: CalendarException.TYPE_FULL_DAY_HOLIDAY
                        val name = doc.getString("name") ?: ""
                        val updatedAt = doc.getLong("updatedAt") ?: 0L
                        val updatedBy = doc.getString("updatedBy") ?: ""
                        CalendarException(
                            date = date,
                            type = type,
                            name = name,
                            updatedAt = updatedAt,
                            updatedBy = updatedBy
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                _exceptionsFlow.value = list
                CollegeCalendarService.updateCachedExceptions(list)
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    suspend fun saveException(exception: CalendarException): Result<Unit> {
        return try {
            val docRef = firestore.collection(COLLECTION_EXCEPTIONS).document(exception.date)
            docRef.set(exception).await()

            // Update local memory cache immediately
            val current = _exceptionsFlow.value.toMutableList()
            current.removeAll { it.date == exception.date }
            current.add(exception)
            _exceptionsFlow.value = current
            CollegeCalendarService.updateCachedExceptions(current)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save calendar exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteException(dateStr: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_EXCEPTIONS).document(dateStr).delete().await()

            val current = _exceptionsFlow.value.toMutableList()
            current.removeAll { it.date == dateStr }
            _exceptionsFlow.value = current
            CollegeCalendarService.updateCachedExceptions(current)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete calendar exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getCachedExceptions(): List<CalendarException> = _exceptionsFlow.value
}
