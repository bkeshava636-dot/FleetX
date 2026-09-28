package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import com.college.bustracker.util.OperatingScheduleService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class OperatingScheduleRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "OperatingScheduleRepo"
        const val COLLECTION_OPERATING_SCHEDULE = "operating_schedule"
        const val DOC_GLOBAL_SCHEDULE = "global"
        const val COLLECTION_CALENDAR_EXCEPTIONS = "calendar_exceptions"
        const val DOC_OPERATING_SCHEDULE = "operating_schedule"
    }

    private val _scheduleFlow = MutableStateFlow(OperatingScheduleService.getSchedule())

    fun getOperatingScheduleFlow(): Flow<RouteSchedule> = callbackFlow {
        // Immediately emit current cached schedule
        trySend(_scheduleFlow.value)

        // Listen to calendar_exceptions/operating_schedule (guaranteed permitted by current deployed rules)
        val registration = firestore.collection(COLLECTION_CALENDAR_EXCEPTIONS)
            .document(DOC_OPERATING_SCHEDULE)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    if (error != null) {
                        Log.w(TAG, "Error listening to calendar_exceptions/operating_schedule: ${error.message}")
                    }
                    trySend(_scheduleFlow.value)
                    return@addSnapshotListener
                }

                try {
                    val morningMap = snapshot.get("morning") as? Map<*, *>
                    val eveningMap = snapshot.get("evening") as? Map<*, *>

                    val morning = ScheduleWindow(
                        startTime = (morningMap?.get("startTime") as? String) ?: "07:00",
                        endTime = (morningMap?.get("endTime") as? String) ?: "09:00",
                        enabled = (morningMap?.get("enabled") as? Boolean) ?: true
                    )

                    val evening = ScheduleWindow(
                        startTime = (eveningMap?.get("startTime") as? String) ?: "16:00",
                        endTime = (eveningMap?.get("endTime") as? String) ?: "18:00",
                        enabled = (eveningMap?.get("enabled") as? Boolean) ?: true
                    )

                    val schedule = RouteSchedule(morning = morning, evening = evening)
                    _scheduleFlow.value = schedule
                    OperatingScheduleService.updateCachedSchedule(schedule)
                    trySend(schedule)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing operating schedule snapshot: ${e.message}")
                    trySend(_scheduleFlow.value)
                }
            }

        awaitClose { registration.remove() }
    }

    suspend fun saveOperatingSchedule(
        schedule: RouteSchedule,
        updatedBy: String = ""
    ): Result<Unit> {
        return try {
            val data = mapOf(
                "morning" to mapOf(
                    "startTime" to schedule.morning.startTime,
                    "endTime" to schedule.morning.endTime,
                    "enabled" to schedule.morning.enabled
                ),
                "evening" to mapOf(
                    "startTime" to schedule.evening.startTime,
                    "endTime" to schedule.evening.endTime,
                    "enabled" to schedule.evening.enabled
                ),
                "updatedAt" to System.currentTimeMillis(),
                "updatedBy" to updatedBy
            )

            // Update in-memory and disk cache immediately
            _scheduleFlow.value = schedule
            OperatingScheduleService.updateCachedSchedule(schedule)

            // Persist to calendar_exceptions/operating_schedule (covered by deployed admin rules)
            firestore.collection(COLLECTION_CALENDAR_EXCEPTIONS)
                .document(DOC_OPERATING_SCHEDULE)
                .set(data, SetOptions.merge())
                .await()

            // Also try saving to operating_schedule/global if allowed
            try {
                firestore.collection(COLLECTION_OPERATING_SCHEDULE)
                    .document(DOC_GLOBAL_SCHEDULE)
                    .set(data, SetOptions.merge())
                    .await()
            } catch (_: Exception) {
                // Secondary collection may fail if rules not yet deployed
            }

            Log.i(TAG, "Saved operating schedule successfully: morning=${schedule.morning.startTime}-${schedule.morning.endTime}, evening=${schedule.evening.startTime}-${schedule.evening.endTime}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save operating schedule: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getCachedSchedule(): RouteSchedule = OperatingScheduleService.getSchedule()
}
