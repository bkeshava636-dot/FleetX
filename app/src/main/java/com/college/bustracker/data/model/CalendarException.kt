package com.college.bustracker.data.model

data class CalendarException(
    val date: String = "", // Format: "YYYY-MM-DD"
    val type: String = TYPE_FULL_DAY_HOLIDAY, // "FULL_DAY_HOLIDAY" or "WORKING_DAY"
    val name: String = "",
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
) {
    companion object {
        const val TYPE_FULL_DAY_HOLIDAY = "FULL_DAY_HOLIDAY"
        const val TYPE_WORKING_DAY = "WORKING_DAY"
    }

    val isWorkingDayOverride: Boolean
        get() = type == TYPE_WORKING_DAY

    val isHolidayOverride: Boolean
        get() = type == TYPE_FULL_DAY_HOLIDAY
}
