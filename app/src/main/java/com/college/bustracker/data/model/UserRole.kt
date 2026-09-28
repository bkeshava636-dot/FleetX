package com.college.bustracker.data.model

enum class UserRole {
    STUDENT,
    DRIVER,
    ADMIN;

    companion object {
        fun fromString(value: String?): UserRole {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: STUDENT
        }
    }
}
