package com.college.bustracker.data.repository

import com.college.bustracker.data.model.StudentFeedback
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FeedbackRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun submitFeedback(feedback: StudentFeedback): Result<Unit> {
        return try {
            val id = if (feedback.feedbackId.isNotBlank()) feedback.feedbackId else UUID.randomUUID().toString()
            val finalFeedback = feedback.copy(feedbackId = id, timestamp = System.currentTimeMillis())
            firestore.collection("student_feedback")
                .document(id)
                .set(finalFeedback.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
