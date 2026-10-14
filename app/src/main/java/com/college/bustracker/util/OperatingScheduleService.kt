package com.college.bustracker.util

import android.content.Context
import android.util.Log
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Authoritative global in-memory and disk-cached operating schedule service.
 * Provides the single source of truth for normal daily operating windows across the app.
 */
object OperatingScheduleService {

    private const val TAG = "OperatingScheduleSvc"
    private const val PREFS_NAME = "operating_schedule_cache"
    private const val KEY_SCHEDULE_JSON = "schedule_json"

    private val time24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val time12Formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    val DEFAULT_SCHEDULE = RouteSchedule(
        morning = ScheduleWindow("07:00", "09:00", true),
        evening = ScheduleWindow("16:00", "18:00", true)
    )

    private val _operatingSchedule = MutableStateFlow(DEFAULT_SCHEDULE)
    val operatingSchedule: StateFlow<RouteSchedule> = _operatingSchedule.asStateFlow()

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_SCHEDULE_JSON, null)
            if (!jsonStr.isNullOrBlank()) {
                val parsed = parseScheduleJson(jsonStr)
                if (parsed != null) {
                    _operatingSchedule.value = parsed
                    Log.i(TAG, "Loaded cached operating schedule: morning=${parsed.morning.startTime}-${parsed.morning.endTime}, evening=${parsed.evening.startTime}-${parsed.evening.endTime}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached operating schedule from disk: ${e.message}")
        }
    }

    fun getSchedule(): RouteSchedule {
        val current = _operatingSchedule.value
        if (current == DEFAULT_SCHEDULE) {
            // Check if context can rehydrate
            try {
                CollegeBusApp.instance?.let { ctx ->
                    init(ctx)
                }
            } catch (ignored: Throwable) {}
        }
        return _operatingSchedule.value
    }

    fun updateCachedSchedule(schedule: RouteSchedule, context: Context? = null) {
        _operatingSchedule.value = schedule
        try {
            val ctx = context ?: try { CollegeBusApp.instance } catch (e: Throwable) { null }
            if (ctx != null) {
                val json = serializeScheduleJson(schedule)
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SCHEDULE_JSON, json)
                    .apply()
            }
        } catch (ignored: Throwable) {
            // Unit test or headless environment
        }
    }

    fun formatTime12h(time24: String): String {
        return try {
            val localTime = LocalTime.parse(time24.trim(), time24Formatter)
            localTime.format(time12Formatter).uppercase(Locale.US)
        } catch (e: Exception) {
            time24
        }
    }

    fun formatWindowDisplay(startTime24: String, endTime24: String): String {
        val startFormatted = formatTime12h(startTime24)
        val endFormatted = formatTime12h(endTime24)
        return "$startFormatted — $endFormatted"
    }

    /**
     * Validates that startTime and endTime are non-empty, well-formed 24-hour HH:mm,
     * and that startTime is strictly before endTime.
     */
    fun validateWindow(startTime24: String, endTime24: String): Pair<Boolean, String?> {
        val s = startTime24.trim()
        val e = endTime24.trim()
        if (s.isBlank()) return Pair(false, "Start time cannot be empty")
        if (e.isBlank()) return Pair(false, "End time cannot be empty")

        val start = try {
            LocalTime.parse(s, time24Formatter)
        } catch (ex: Exception) {
            return Pair(false, "Invalid start time format (expected HH:mm)")
        }

        val end = try {
            LocalTime.parse(e, time24Formatter)
        } catch (ex: Exception) {
            return Pair(false, "Invalid end time format (expected HH:mm)")
        }

        if (!start.isBefore(end)) {
            return Pair(false, "Start time must be earlier than end time")
        }

        return Pair(true, null)
    }

    private fun serializeScheduleJson(schedule: RouteSchedule): String {
        val root = JSONObject()
        val morningObj = JSONObject().apply {
            put("startTime", schedule.morning.startTime)
            put("endTime", schedule.morning.endTime)
            put("enabled", schedule.morning.enabled)
        }
        val eveningObj = JSONObject().apply {
            put("startTime", schedule.evening.startTime)
            put("endTime", schedule.evening.endTime)
            put("enabled", schedule.evening.enabled)
        }
        root.put("morning", morningObj)
        root.put("evening", eveningObj)
        schedule.allRoutesPdfUrl?.let { root.put("allRoutesPdfUrl", it) }
        schedule.allRoutesPdfName?.let { root.put("allRoutesPdfName", it) }
        return root.toString()
    }

    private fun parseScheduleJson(jsonStr: String): RouteSchedule? {
        return try {
            val root = JSONObject(jsonStr)
            val morningObj = root.optJSONObject("morning")
            val eveningObj = root.optJSONObject("evening")

            val morning = if (morningObj != null) {
                ScheduleWindow(
                    startTime = morningObj.optString("startTime", "07:00"),
                    endTime = morningObj.optString("endTime", "09:00"),
                    enabled = morningObj.optBoolean("enabled", true)
                )
            } else ScheduleWindow("07:00", "09:00", true)

            val evening = if (eveningObj != null) {
                ScheduleWindow(
                    startTime = eveningObj.optString("startTime", "16:00"),
                    endTime = eveningObj.optString("endTime", "18:00"),
                    enabled = eveningObj.optBoolean("enabled", true)
                )
            } else ScheduleWindow("16:00", "18:00", true)

            val allRoutesPdfUrl = root.optString("allRoutesPdfUrl").takeIf { it.isNotBlank() }
            val allRoutesPdfName = root.optString("allRoutesPdfName").takeIf { it.isNotBlank() }

            RouteSchedule(
                morning = morning,
                evening = evening,
                allRoutesPdfUrl = allRoutesPdfUrl,
                allRoutesPdfName = allRoutesPdfName
            )
        } catch (e: Exception) {
            null
        }
    }
}
