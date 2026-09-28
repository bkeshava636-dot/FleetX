package com.college.bustracker.util

import android.content.Context
import android.util.Log
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.repository.StudentPreferences
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Interface abstracting FCM topic subscriptions for clean testability.
 */
interface TopicSubscriber {
    fun subscribe(topic: String)
    fun unsubscribe(topic: String)
}

/**
 * Default production implementation using FirebaseMessaging.
 */
class FirebaseTopicSubscriber : TopicSubscriber {
    override fun subscribe(topic: String) {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnSuccessListener {
                    Log.i("BusTopicManager", "Subscribed to FCM topic: $topic")
                }
                .addOnFailureListener { e ->
                    Log.e("BusTopicManager", "Failed to subscribe to FCM topic: $topic: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("BusTopicManager", "Error accessing FirebaseMessaging for topic: $topic", e)
        }
    }

    override fun unsubscribe(topic: String) {
        try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnSuccessListener {
                    Log.i("BusTopicManager", "Unsubscribed from FCM topic: $topic")
                }
                .addOnFailureListener { e ->
                    Log.e("BusTopicManager", "Failed to unsubscribe from FCM topic: $topic: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("BusTopicManager", "Error accessing FirebaseMessaging for topic: $topic", e)
        }
    }
}

/**
 * Manages FCM topic targeting and subscriptions for stop-specific bus swap alerts.
 * Topic pattern: bus_{originalBusId}_stop_{stopId}
 */
object BusTopicManager {

    private const val TAG = "BusTopicManager"

    /**
     * Sanitizes strings to conform to FCM topic format regex: [a-zA-Z0-9-_.~%]+
     */
    fun sanitizeForTopic(input: String): String {
        return input.trim().replace(Regex("[^a-zA-Z0-9-_.~%]"), "_")
    }

    /**
     * Formats an FCM topic target for a specific original bus and affected stop.
     * Example: bus_BUS-25_stop_STOP-01
     */
    fun formatTopic(originalBusId: String, stopId: String): String {
        val cleanBus = sanitizeForTopic(originalBusId)
        val cleanStop = sanitizeForTopic(stopId)
        return "bus_${cleanBus}_stop_${cleanStop}"
    }

    /**
     * Pure targeting evaluator:
     * Returns true ONLY if:
     * 1. The student's assigned bus matches the swap's original bus (NOT replacement bus).
     * 2. The student's stop is one of the affected stops for the swap.
     * 3. The swap status is ACTIVE.
     */
    fun isStudentTargeted(
        studentBusId: String?,
        studentStopId: String?,
        swap: BusSwap
    ): Boolean {
        if (studentBusId.isNullOrBlank() || studentStopId.isNullOrBlank()) return false
        if (swap.status.uppercase() != "ACTIVE") return false

        val busMatches = studentBusId.trim().equals(swap.originalBusId.trim(), ignoreCase = true)
        if (!busMatches) return false

        return swap.isAffectingStop(studentStopId.trim())
    }

    /**
     * Pure cancellation targeting evaluator:
     * Returns true ONLY if the student was previously affected by the original swap being cancelled.
     */
    fun isStudentTargetedForCancellation(
        studentBusId: String?,
        studentStopId: String?,
        swap: BusSwap
    ): Boolean {
        if (studentBusId.isNullOrBlank() || studentStopId.isNullOrBlank()) return false
        if (swap.status.uppercase() != "CANCELLED") return false

        val busMatches = studentBusId.trim().equals(swap.originalBusId.trim(), ignoreCase = true)
        if (!busMatches) return false

        return swap.isAffectingStop(studentStopId.trim())
    }

    /**
     * Updates student's FCM topic subscription:
     * - Unsubscribes from previous topic if bus or stop changed.
     * - Subscribes to new topic if both bus and stop are selected.
     * - If bus or stop is null, unsubscribes and does not subscribe.
     * - Preserves updated selection in StudentPreferences.
     */
    fun updateSubscription(
        context: Context,
        newBusId: String?,
        newStopId: String?,
        subscriber: TopicSubscriber = FirebaseTopicSubscriber()
    ) {
        val currentTopic = StudentPreferences.getSubscribedTopic(context)
        val newTopic = if (!newBusId.isNullOrBlank() && !newStopId.isNullOrBlank()) {
            formatTopic(newBusId, newStopId)
        } else {
            null
        }

        StudentPreferences.saveSelection(context, newBusId, newStopId)

        if (currentTopic == newTopic) {
            // Already in sync
            return
        }

        // Unsubscribe from old topic if one existed
        if (!currentTopic.isNullOrBlank()) {
            try {
                subscriber.unsubscribe(currentTopic)
                Log.d(TAG, "Unsubscribed from previous topic: $currentTopic")
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing from $currentTopic", e)
            }
        }

        // Subscribe to new topic if valid
        if (!newTopic.isNullOrBlank()) {
            try {
                subscriber.subscribe(newTopic)
                Log.d(TAG, "Subscribed to new topic: $newTopic")
            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing to $newTopic", e)
            }
        }

        StudentPreferences.saveSubscribedTopic(context, newTopic)
    }
}
