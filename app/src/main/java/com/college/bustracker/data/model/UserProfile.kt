package com.college.bustracker.data.model

data class UserProfile(
    val uid: String = "",
    val customId: String = "",            // Unique staff ID code (e.g. staff badge / employee code)
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.STUDENT,
    val assignedBusId: String? = null,    // For DRIVER
    val phoneNumber: String? = null,      // Contact phone number (e.g. for DRIVER)
    val preferredBusId: String? = null,   // For STUDENT
    val preferredStopId: String? = null,  // For STUDENT
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
