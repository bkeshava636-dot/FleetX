package com.college.bustracker.util

import android.content.Context
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.CalendarException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayEvaluation(
    val date: LocalDate,
    val isWorkingDay: Boolean,
    val isHoliday: Boolean,
    val holidayName: String?,
    val isOverride: Boolean,
    val summary: String
)

object CollegeCalendarService {

    private const val PREFS_NAME = "college_calendar_cache"
    private const val KEY_EXCEPTIONS_JSON = "exceptions_json"

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "YYYY-MM-DD"

    private val _cachedExceptions = MutableStateFlow<Map<String, CalendarException>>(emptyMap())
    val cachedExceptions: StateFlow<Map<String, CalendarException>> = _cachedExceptions.asStateFlow()

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_EXCEPTIONS_JSON, null)
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = org.json.JSONArray(jsonStr)
                val list = mutableListOf<CalendarException>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        CalendarException(
                            date = obj.optString("date"),
                            type = obj.optString("type", CalendarException.TYPE_FULL_DAY_HOLIDAY),
                            name = obj.optString("name", ""),
                            updatedAt = obj.optLong("updatedAt", 0L),
                            updatedBy = obj.optString("updatedBy", "")
                        )
                    )
                }
                _cachedExceptions.value = list.associateBy { it.date }
            }
        } catch (e: Exception) {
            android.util.Log.w("CollegeCalendarService", "Failed to load cached exceptions from disk: ${e.message}")
        }
    }

    fun updateCachedExceptions(exceptions: List<CalendarException>, context: Context? = null) {
        _cachedExceptions.value = exceptions.associateBy { it.date }
        try {
            val ctx = context ?: try { CollegeBusApp.instance } catch (e: Throwable) { null }
            if (ctx != null) {
                val jsonArray = org.json.JSONArray()
                for (ex in exceptions) {
                    val obj = org.json.JSONObject().apply {
                        put("date", ex.date)
                        put("type", ex.type)
                        put("name", ex.name)
                        put("updatedAt", ex.updatedAt)
                        put("updatedBy", ex.updatedBy)
                    }
                    jsonArray.put(obj)
                }
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_EXCEPTIONS_JSON, jsonArray.toString())
                    .apply()
            }
        } catch (ignored: Throwable) {
            // Unit test or headless environment
        }
    }

    fun nowLocalDate(): LocalDate = LocalDate.now(ScheduleHelper.KOLKATA_ZONE)

    /**
     * Pure evaluator for any date against given or cached exceptions.
     */
    fun evaluateDay(
        date: LocalDate = nowLocalDate(),
        exceptions: Map<String, CalendarException> = _cachedExceptions.value
    ): DayEvaluation {
        var currentExceptions = exceptions
        if (currentExceptions.isEmpty()) {
            try {
                CollegeBusApp.instance?.let { ctx ->
                    init(ctx)
                    currentExceptions = _cachedExceptions.value
                }
            } catch (ignored: Throwable) {}
        }
        val dateKey = date.format(dateFormatter)
        val override = currentExceptions[dateKey]

        if (override != null) {
            return if (override.type == CalendarException.TYPE_WORKING_DAY) {
                val label = if (override.name.isNotBlank()) override.name else "Working Day (Override)"
                DayEvaluation(
                    date = date,
                    isWorkingDay = true,
                    isHoliday = false,
                    holidayName = null,
                    isOverride = true,
                    summary = label
                )
            } else {
                val name = if (override.name.isNotBlank()) override.name else "College Holiday"
                DayEvaluation(
                    date = date,
                    isWorkingDay = false,
                    isHoliday = true,
                    holidayName = name,
                    isOverride = true,
                    summary = "Holiday — $name"
                )
            }
        }

        // Default recurring rules
        return when {
            date.dayOfWeek == DayOfWeek.SUNDAY -> {
                DayEvaluation(
                    date = date,
                    isWorkingDay = false,
                    isHoliday = true,
                    holidayName = "Sunday",
                    isOverride = false,
                    summary = "Holiday — Sunday"
                )
            }
            date.dayOfWeek == DayOfWeek.SATURDAY && date.dayOfMonth in 1..7 -> {
                DayEvaluation(
                    date = date,
                    isWorkingDay = false,
                    isHoliday = true,
                    holidayName = "1st Saturday",
                    isOverride = false,
                    summary = "Holiday — 1st Saturday"
                )
            }
            date.dayOfWeek == DayOfWeek.SATURDAY && date.dayOfMonth in 15..21 -> {
                DayEvaluation(
                    date = date,
                    isWorkingDay = false,
                    isHoliday = true,
                    holidayName = "3rd Saturday",
                    isOverride = false,
                    summary = "Holiday — 3rd Saturday"
                )
            }
            else -> {
                DayEvaluation(
                    date = date,
                    isWorkingDay = true,
                    isHoliday = false,
                    holidayName = null,
                    isOverride = false,
                    summary = "Working Day"
                )
            }
        }
    }

    fun isWorkingDay(
        date: LocalDate = nowLocalDate(),
        exceptions: Map<String, CalendarException> = _cachedExceptions.value
    ): Boolean {
        return evaluateDay(date, exceptions).isWorkingDay
    }

    fun isHoliday(
        date: LocalDate = nowLocalDate(),
        exceptions: Map<String, CalendarException> = _cachedExceptions.value
    ): Boolean {
        return evaluateDay(date, exceptions).isHoliday
    }

    fun getHolidayName(
        date: LocalDate = nowLocalDate(),
        exceptions: Map<String, CalendarException> = _cachedExceptions.value
    ): String? {
        return evaluateDay(date, exceptions).holidayName
    }

    /**
     * Formats LocalDate to standard YYYY-MM-DD string.
     */
    fun formatDate(date: LocalDate): String {
        return date.format(dateFormatter)
    }

    /**
     * Parses standard YYYY-MM-DD string to LocalDate, or null if malformed.
     */
    fun parseDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr, dateFormatter)
        } catch (e: Exception) {
            null
        }
    }
}
