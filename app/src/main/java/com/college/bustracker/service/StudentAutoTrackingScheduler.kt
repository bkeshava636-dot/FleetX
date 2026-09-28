package com.college.bustracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.ScheduleWindow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Handles exact background AlarmManager scheduling for student automatic transit tracking.
 * Ensures schedule triggers wake up the device from idle/doze even if the app was killed or never opened.
 */
object StudentAutoTrackingScheduler {

    private const val TAG = "StudentAutoScheduler"
    const val PREFS_NAME = "auto_ride_prefs"
    const val ACTION_STUDENT_SCHEDULE_CHECK = "com.college.bustracker.ACTION_STUDENT_SCHEDULE_CHECK"

    private const val KEY_MORNING_ENABLED = "student_morning_enabled"
    private const val KEY_MORNING_START = "student_morning_start"
    private const val KEY_MORNING_END = "student_morning_end"
    private const val KEY_EVENING_ENABLED = "student_evening_enabled"
    private const val KEY_EVENING_START = "student_evening_start"
    private const val KEY_EVENING_END = "student_evening_end"

    private const val REQUEST_CODE_STUDENT_M_START = 3001
    private const val REQUEST_CODE_STUDENT_M_END = 3002
    private const val REQUEST_CODE_STUDENT_E_START = 3003
    private const val REQUEST_CODE_STUDENT_E_END = 3004

    fun saveSchedule(context: Context, schedule: RouteSchedule) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_MORNING_ENABLED, schedule.morning.enabled)
            .putString(KEY_MORNING_START, schedule.morning.startTime)
            .putString(KEY_MORNING_END, schedule.morning.endTime)
            .putBoolean(KEY_EVENING_ENABLED, schedule.evening.enabled)
            .putString(KEY_EVENING_START, schedule.evening.startTime)
            .putString(KEY_EVENING_END, schedule.evening.endTime)
            .apply()
    }

    fun getSavedSchedule(context: Context): RouteSchedule {
        val globalSchedule = com.college.bustracker.util.OperatingScheduleService.getSchedule()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_MORNING_START)) {
            return globalSchedule
        }
        return RouteSchedule(
            morning = ScheduleWindow(
                startTime = prefs.getString(KEY_MORNING_START, globalSchedule.morning.startTime) ?: globalSchedule.morning.startTime,
                endTime = prefs.getString(KEY_MORNING_END, globalSchedule.morning.endTime) ?: globalSchedule.morning.endTime,
                enabled = prefs.getBoolean(KEY_MORNING_ENABLED, globalSchedule.morning.enabled)
            ),
            evening = ScheduleWindow(
                startTime = prefs.getString(KEY_EVENING_START, globalSchedule.evening.startTime) ?: globalSchedule.evening.startTime,
                endTime = prefs.getString(KEY_EVENING_END, globalSchedule.evening.endTime) ?: globalSchedule.evening.endTime,
                enabled = prefs.getBoolean(KEY_EVENING_ENABLED, globalSchedule.evening.enabled)
            )
        )
    }

    fun scheduleAlarms(context: Context, schedule: RouteSchedule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        saveSchedule(context, schedule)
        cancelAlarms(context)

        if (schedule.morning.enabled) {
            scheduleSingleAlarm(context, alarmManager, schedule.morning.startTime, REQUEST_CODE_STUDENT_M_START)
            scheduleSingleAlarm(context, alarmManager, schedule.morning.endTime, REQUEST_CODE_STUDENT_M_END)
        }
        if (schedule.evening.enabled) {
            scheduleSingleAlarm(context, alarmManager, schedule.evening.startTime, REQUEST_CODE_STUDENT_E_START)
            scheduleSingleAlarm(context, alarmManager, schedule.evening.endTime, REQUEST_CODE_STUDENT_E_END)
        }
        Log.i(TAG, "Student auto-tracking alarms scheduled for morning: ${schedule.morning.startTime}-${schedule.morning.endTime}, evening: ${schedule.evening.startTime}-${schedule.evening.endTime}")
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        timeStr: String,
        requestCode: Int
    ) {
        try {
            val time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
            var targetDateTime = LocalDateTime.of(LocalDate.now(), time)
            if (targetDateTime.isBefore(LocalDateTime.now())) {
                targetDateTime = targetDateTime.plusDays(1)
            }

            val triggerMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val intent = Intent(context, StudentScheduleReceiver::class.java).apply {
                action = ACTION_STUDENT_SCHEDULE_CHECK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule student alarm for $timeStr: ${e.message}")
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCodes = listOf(
            REQUEST_CODE_STUDENT_M_START,
            REQUEST_CODE_STUDENT_M_END,
            REQUEST_CODE_STUDENT_E_START,
            REQUEST_CODE_STUDENT_E_END
        )
        for (code in requestCodes) {
            val intent = Intent(context, StudentScheduleReceiver::class.java).apply {
                action = ACTION_STUDENT_SCHEDULE_CHECK
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
            }
        }
        Log.i(TAG, "Student auto-tracking alarms cancelled")
    }
}
