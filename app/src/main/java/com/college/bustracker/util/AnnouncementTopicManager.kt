package com.college.bustracker.util

import android.content.Context
import android.util.Log
import com.college.bustracker.data.model.UserRole

/**
 * Manages FCM topic targeting and subscriptions for campus announcements.
 * Topics:
 * - announcements_all
 * - announcements_students
 * - announcements_drivers
 * - announcements_bus_{busId}
 * - announcements_route_{routeId}
 */
object AnnouncementTopicManager {
    private const val TAG = "AnnouncementTopicMgr"

    const val TOPIC_ALL = "announcements_all"
    const val TOPIC_STUDENTS = "announcements_students"
    const val TOPIC_DRIVERS = "announcements_drivers"

    fun formatBusTopic(busId: String): String {
        return "announcements_bus_${BusTopicManager.sanitizeForTopic(busId)}"
    }

    fun formatRouteTopic(routeId: String): String {
        return "announcements_route_${BusTopicManager.sanitizeForTopic(routeId)}"
    }

    fun subscribeToGeneralTopics(subscriber: TopicSubscriber = FirebaseTopicSubscriber()) {
        try {
            subscriber.subscribe(TOPIC_ALL)
            subscriber.subscribe(TOPIC_STUDENTS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to general announcement topics: ${e.message}")
        }
    }

    fun updateRoleSubscriptions(role: UserRole?, subscriber: TopicSubscriber = FirebaseTopicSubscriber()) {
        try {
            subscriber.subscribe(TOPIC_ALL)
            when (role) {
                UserRole.DRIVER -> {
                    subscriber.subscribe(TOPIC_DRIVERS)
                    subscriber.unsubscribe(TOPIC_STUDENTS)
                }
                UserRole.ADMIN -> {
                    subscriber.subscribe(TOPIC_DRIVERS)
                    subscriber.subscribe(TOPIC_STUDENTS)
                }
                else -> {
                    subscriber.subscribe(TOPIC_STUDENTS)
                    subscriber.unsubscribe(TOPIC_DRIVERS)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update role announcement subscriptions: ${e.message}")
        }
    }

    fun updateBusSubscription(
        oldBusId: String?,
        newBusId: String?,
        subscriber: TopicSubscriber = FirebaseTopicSubscriber()
    ) {
        if (!oldBusId.isNullOrBlank()) {
            try {
                subscriber.unsubscribe(formatBusTopic(oldBusId))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unsubscribe from old bus topic: ${e.message}")
            }
        }
        if (!newBusId.isNullOrBlank()) {
            try {
                subscriber.subscribe(formatBusTopic(newBusId))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to new bus topic: ${e.message}")
            }
        }
    }
}
