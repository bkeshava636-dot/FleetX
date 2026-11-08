package com.college.bustracker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object KolkataTimeFormatter {

    val TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    private val fullDateTimeFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.ENGLISH).apply {
        timeZone = TIME_ZONE
    }

    private val dateOnlyFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).apply {
        timeZone = TIME_ZONE
    }

    private val timeOnlyFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
        timeZone = TIME_ZONE
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
        timeZone = TIME_ZONE
    }

    @Synchronized
    fun formatFullDateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        return fullDateTimeFormat.format(Date(epochMillis))
    }

    @Synchronized
    fun formatDateOnly(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        return dateOnlyFormat.format(Date(epochMillis))
    }

    @Synchronized
    fun formatTimeOnly(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        return timeOnlyFormat.format(Date(epochMillis))
    }

    @Synchronized
    fun getTodayIso(): String {
        return isoDateFormat.format(Date())
    }

    @Synchronized
    fun formatIsoDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        return isoDateFormat.format(Date(epochMillis))
    }
}
