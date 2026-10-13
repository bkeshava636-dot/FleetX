package com.college.bustracker.data.repository

import android.util.Log
import com.college.bustracker.data.model.Announcement
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Handles Firestore synchronization and administrative operations for announcements.
 */
class AnnouncementRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "AnnouncementRepo"
        const val COLLECTION_ANNOUNCEMENTS = "announcements"
    }

    private val announcementsRef = firestore.collection(COLLECTION_ANNOUNCEMENTS)

    /**
     * Real-time flow of all announcements ordered by createdAt descending.
     */
    fun getAnnouncementsFlow(): Flow<List<Announcement>> = callbackFlow {
        val listener = announcementsRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to announcements: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Announcement::class.java)?.copy(id = doc.id) ?: fromDocMap(doc)
                        } catch (e: Exception) {
                            Log.w(TAG, "Fallback parsing announcement doc ${doc.id}: ${e.message}")
                            fromDocMap(doc)
                        }
                    }
                    trySend(list)
                }
            }

        awaitClose {
            listener.remove()
        }
    }

    private fun fromDocMap(doc: DocumentSnapshot): Announcement {
        val data = doc.data ?: return Announcement(id = doc.id)
        return Announcement(
            id = doc.id,
            titleEn = data["titleEn"] as? String ?: "",
            messageEn = data["messageEn"] as? String ?: "",
            titleKn = data["titleKn"] as? String ?: "",
            messageKn = data["messageKn"] as? String ?: "",
            category = data["category"] as? String ?: Announcement.CATEGORY_GENERAL,
            priority = data["priority"] as? String ?: Announcement.PRIORITY_NORMAL,
            targetAudience = data["targetAudience"] as? String ?: Announcement.AUDIENCE_ALL,
            targetId = data["targetId"] as? String ?: "",
            targetName = data["targetName"] as? String ?: "",
            status = data["status"] as? String ?: Announcement.STATUS_PUBLISHED,
            createdBy = data["createdBy"] as? String ?: "",
            createdByName = data["createdByName"] as? String ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            scheduledAt = (data["scheduledAt"] as? Number)?.toLong(),
            expiresAt = (data["expiresAt"] as? Number)?.toLong(),
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            timingBusId = data["timingBusId"] as? String,
            timingChangeType = data["timingChangeType"] as? String,
            previousTime = data["previousTime"] as? String,
            newTime = data["newTime"] as? String,
            effectiveDate = data["effectiveDate"] as? String,
            endDate = data["endDate"] as? String,
            changeReason = data["changeReason"] as? String
        )
    }

    /**
     * Creates a new announcement document in Firestore.
     */
    suspend fun createAnnouncement(announcement: Announcement): Result<String> {
        return try {
            val docRef = announcementsRef.document()
            val toSave = announcement.copy(
                id = docRef.id,
                createdAt = if (announcement.createdAt <= 0) System.currentTimeMillis() else announcement.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            docRef.set(toSave.toMap()).await()
            Log.i(TAG, "Created announcement: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create announcement", e)
            Result.failure(e)
        }
    }

    /**
     * Updates an existing announcement document.
     */
    suspend fun updateAnnouncement(announcement: Announcement): Result<Unit> {
        return try {
            require(announcement.id.isNotBlank()) { "Announcement ID must not be blank" }
            val toSave = announcement.copy(updatedAt = System.currentTimeMillis())
            announcementsRef.document(announcement.id).set(toSave.toMap()).await()
            Log.i(TAG, "Updated announcement: ${announcement.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update announcement ${announcement.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Publishes a draft announcement.
     */
    suspend fun publishAnnouncement(id: String): Result<Unit> {
        return try {
            require(id.isNotBlank()) { "Announcement ID must not be blank" }
            announcementsRef.document(id).update(
                mapOf(
                    "status" to Announcement.STATUS_PUBLISHED,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            Log.i(TAG, "Published announcement: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish announcement $id", e)
            Result.failure(e)
        }
    }

    /**
     * Archives an announcement (soft delete).
     */
    suspend fun archiveAnnouncement(id: String): Result<Unit> {
        return try {
            require(id.isNotBlank()) { "Announcement ID must not be blank" }
            announcementsRef.document(id).update(
                mapOf(
                    "status" to Announcement.STATUS_ARCHIVED,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            Log.i(TAG, "Archived announcement: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to archive announcement $id", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes an announcement document permanently.
     */
    suspend fun deleteAnnouncement(id: String): Result<Unit> {
        return try {
            require(id.isNotBlank()) { "Announcement ID must not be blank" }
            announcementsRef.document(id).delete().await()
            Log.i(TAG, "Deleted announcement: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete announcement $id", e)
            Result.failure(e)
        }
    }
}
