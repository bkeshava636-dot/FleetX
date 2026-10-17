package com.college.bustracker.data.model

/**
 * Represents a feedback submission or issue report submitted by a student.
 * Supports both FleetX App Feedback (bugs, suggestions) and College Bus Transit Issues (GPS, delays, stops).
 * Stored in Firestore at /student_feedback/{feedbackId}.
 */
data class StudentFeedback(
    val feedbackId: String = "",
    val feedbackType: String = TYPE_BUS, // "APP" or "BUS"
    val busId: String = "",
    val routeId: String = "",
    val category: String = CATEGORY_GENERAL,
    val subject: String = "",
    val description: String = "",
    val studentName: String = "",
    val studentContact: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_NEW
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_BUS = "BUS"

        // App-specific feedback categories
        const val CATEGORY_APP_BUG = "App Bug / Technical Glitch"
        const val CATEGORY_APP_SUGGESTION = "App Feature Request / Idea"
        const val CATEGORY_APP_UI = "UI / Usability Feedback"

        // Bus / Transit-specific categories
        const val CATEGORY_GPS_ISSUE = "Bus GPS / Tracking Not Updating"
        const val CATEGORY_BUS_DELAY = "Bus Delayed / Off Schedule"
        const val CATEGORY_ROUTE_STOP = "Route / Skipped Bus Stop"
        const val CATEGORY_OVERCROWDING_DRIVING = "Driving / Overcrowding"
        const val CATEGORY_LOST_ITEM = "Lost Item on Bus"

        // Shared
        const val CATEGORY_GENERAL = "General Feedback"

        const val STATUS_NEW = "NEW"
        const val STATUS_REVIEWED = "REVIEWED"
        const val STATUS_RESOLVED = "RESOLVED"

        val APP_CATEGORIES = listOf(
            CATEGORY_APP_BUG,
            CATEGORY_APP_SUGGESTION,
            CATEGORY_APP_UI,
            CATEGORY_GENERAL
        )

        val BUS_CATEGORIES = listOf(
            CATEGORY_GPS_ISSUE,
            CATEGORY_BUS_DELAY,
            CATEGORY_ROUTE_STOP,
            CATEGORY_OVERCROWDING_DRIVING,
            CATEGORY_LOST_ITEM,
            CATEGORY_GENERAL
        )

        val ALL_CATEGORIES = listOf(
            CATEGORY_APP_BUG,
            CATEGORY_APP_SUGGESTION,
            CATEGORY_APP_UI,
            CATEGORY_GPS_ISSUE,
            CATEGORY_BUS_DELAY,
            CATEGORY_ROUTE_STOP,
            CATEGORY_OVERCROWDING_DRIVING,
            CATEGORY_LOST_ITEM,
            CATEGORY_GENERAL
        )
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "feedbackId" to feedbackId,
        "feedbackType" to feedbackType,
        "busId" to busId,
        "routeId" to routeId,
        "category" to category,
        "subject" to subject,
        "description" to description,
        "studentName" to studentName,
        "studentContact" to studentContact,
        "timestamp" to timestamp,
        "status" to status
    )
}
