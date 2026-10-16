package com.college.bustracker.diagnostics

import android.content.Context
import android.util.Log
import com.college.bustracker.util.ScheduleHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportRangeType {
    TODAY,
    ALL,
    CURRENT_SESSION,
    DATE_RANGE
}

/**
 * Handles strictly local persistence of continuous GPS diagnostic records and sessions.
 * Never uploads any diagnostic data to Firebase RTDB, Firestore, or cloud storage.
 * All data is stored in app-private storage: filesDir/gps_diagnostics/
 */
class GpsDiagnosticStorage(private val context: Context? = null) {

    companion object {
        private const val TAG = "GpsDiagnosticStorage"
        private const val DIAGNOSTICS_DIR = "gps_diagnostics"
        private const val SESSIONS_SUBDIR = "sessions"
        private const val EXPORTS_SUBDIR = "exports"
        private const val STATE_FILE = "recording_state.json"
        private const val INDEX_FILE = "diagnostic_index.json"
        private const val MAX_RETAINED_SESSIONS = 50

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        private val ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }

    private val fileLock = Any()

    fun getStorageDir(): File {
        val ctx = context ?: throw IllegalStateException("Storage requires a valid Context")
        val dir = File(ctx.filesDir, DIAGNOSTICS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSessionsDir(): File {
        val dir = File(getStorageDir(), SESSIONS_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getExportsDir(): File {
        val dir = File(getStorageDir(), EXPORTS_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // ==========================================
    // CONTINUOUS RECORDING STATE
    // ==========================================

    @Synchronized
    fun saveRecordingState(state: ContinuousRecordingState) {
        try {
            val file = File(getStorageDir(), STATE_FILE)
            val json = JSONObject().apply {
                put("isRecording", state.isRecording)
                put("startedAtMs", state.startedAtMs)
                put("startedAtFormatted", state.startedAtFormatted)
                put("recordingSessionId", state.recordingSessionId)
                put("totalEventsRecorded", state.totalEventsRecorded)
                put("lastEventTimestampMs", state.lastEventTimestampMs)
                put("lastEventName", state.lastEventName)
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save recording state: ${e.message}", e)
        }
    }

    @Synchronized
    fun loadRecordingState(): ContinuousRecordingState {
        return try {
            val file = File(getStorageDir(), STATE_FILE)
            if (!file.exists()) return ContinuousRecordingState()
            val json = JSONObject(file.readText())
            ContinuousRecordingState(
                isRecording = json.optBoolean("isRecording", false),
                startedAtMs = json.optLong("startedAtMs", 0L),
                startedAtFormatted = json.optString("startedAtFormatted", ""),
                recordingSessionId = json.optString("recordingSessionId", ""),
                totalEventsRecorded = json.optLong("totalEventsRecorded", 0L),
                lastEventTimestampMs = json.optLong("lastEventTimestampMs", 0L),
                lastEventName = json.optString("lastEventName", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load recording state: ${e.message}", e)
            ContinuousRecordingState()
        }
    }

    // ==========================================
    // JSONL DAILY EVENT APPEND & STREAMING
    // ==========================================

    /**
     * Appends a diagnostic event to the appropriate daily JSONL file.
     * Safely rolls over at midnight based on Asia/Kolkata timezone.
     */
    fun appendDailyEvent(event: DiagnosticEvent) {
        synchronized(fileLock) {
            try {
                val eventDate = Instant.ofEpochMilli(event.timestampMs)
                    .atZone(ScheduleHelper.KOLKATA_ZONE)
                    .toLocalDate()
                val fileName = "${eventDate.format(DATE_FORMATTER)}.jsonl"
                val file = File(getStorageDir(), fileName)

                val jsonObj = serializeEventToJson(event)
                val line = jsonObj.toString() + "\n"

                FileWriter(file, true).use { writer ->
                    writer.write(line)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append diagnostic event: ${e.message}", e)
            }
        }
    }

    fun getTodayEvents(): List<DiagnosticEvent> {
        val todayStr = LocalDate.now(ScheduleHelper.KOLKATA_ZONE).format(DATE_FORMATTER)
        return getEventsForDate(todayStr)
    }

    fun getEventsForDate(dateStr: String): List<DiagnosticEvent> {
        synchronized(fileLock) {
            val file = File(getStorageDir(), "$dateStr.jsonl")
            if (!file.exists()) return emptyList()
            val list = mutableListOf<DiagnosticEvent>()
            try {
                file.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            val obj = JSONObject(trimmed)
                            list.add(deserializeEventFromJson(obj))
                        } catch (e: Exception) {
                            Log.w(TAG, "Corrupted line in $dateStr.jsonl: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading $dateStr.jsonl: ${e.message}", e)
            }
            return list
        }
    }

    fun getAllDailyEvents(): List<DiagnosticEvent> {
        synchronized(fileLock) {
            val allEvents = mutableListOf<DiagnosticEvent>()
            val dir = getStorageDir()
            val dailyFiles = dir.listFiles { f -> f.extension == "jsonl" }?.sortedBy { it.name } ?: emptyList()
            for (file in dailyFiles) {
                try {
                    file.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            try {
                                allEvents.add(deserializeEventFromJson(JSONObject(trimmed)))
                            } catch (ignored: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading ${file.name}: ${e.message}", e)
                }
            }
            return allEvents
        }
    }

    fun getEventsForDateRange(startDateStr: String, endDateStr: String): List<DiagnosticEvent> {
        val start = try { LocalDate.parse(startDateStr, DATE_FORMATTER) } catch (e: Exception) { null }
        val end = try { LocalDate.parse(endDateStr, DATE_FORMATTER) } catch (e: Exception) { null }
        val startVal = start ?: return getTodayEvents()
        val endVal = end ?: return getTodayEvents()

        val matchedEvents = mutableListOf<DiagnosticEvent>()
        var cur = startVal
        while (!cur.isAfter(endVal)) {
            matchedEvents.addAll(getEventsForDate(cur.format(DATE_FORMATTER)))
            cur = cur.plusDays(1)
        }
        return matchedEvents
    }

    // ==========================================
    // OPERATING WINDOW SESSIONS
    // ==========================================

    @Synchronized
    fun saveReport(report: GpsDiagnosticReport) {
        try {
            val sessionsDir = getSessionsDir()
            val fileName = "session_${report.sessionId}.json"
            val file = File(sessionsDir, fileName)
            val jsonString = serializeReport(report)
            file.writeText(jsonString)

            updateIndex(report)
            pruneOldSessions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save diagnostic report: ${e.message}", e)
        }
    }

    @Synchronized
    fun loadReport(sessionId: String): GpsDiagnosticReport? {
        return try {
            val file = File(getSessionsDir(), "session_${sessionId}.json")
            if (!file.exists()) {
                // Check legacy root location
                val legacy = File(getStorageDir(), "gps_session_${sessionId}.json")
                if (legacy.exists()) return deserializeReport(legacy.readText())
                return null
            }
            deserializeReport(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load report $sessionId: ${e.message}", e)
            null
        }
    }

    @Synchronized
    fun getLatestReport(): GpsDiagnosticReport? {
        val index = getIndex()
        if (index.isEmpty()) {
            val sessionsDir = getSessionsDir()
            val files = sessionsDir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }
            val first = files?.firstOrNull() ?: return null
            return deserializeReport(first.readText())
        }
        val latestSessionId = index.firstOrNull()?.optString("sessionId") ?: return null
        return loadReport(latestSessionId)
    }

    @Synchronized
    fun getAllReportSummaries(): List<GpsDiagnosticReport> {
        val dir = getSessionsDir()
        val reports = mutableListOf<GpsDiagnosticReport>()
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        for (file in files.sortedByDescending { it.lastModified() }) {
            try {
                val report = deserializeReport(file.readText())
                if (report != null) {
                    reports.add(report)
                }
            } catch (ignored: Exception) {}
        }
        return reports
    }

    private fun updateIndex(report: GpsDiagnosticReport) {
        val dir = getStorageDir()
        val indexFile = File(dir, INDEX_FILE)
        val list = mutableListOf<JSONObject>()

        val entry = JSONObject().apply {
            put("sessionId", report.sessionId)
            put("date", report.date)
            put("windowName", report.windowName)
            put("windowStart", report.windowStart)
            put("windowEnd", report.windowEnd)
            put("busId", report.busId)
            put("sessionStatus", report.sessionStatus.name)
            put("primaryFailure", report.primaryFailure.name)
            put("createdAtMs", report.createdAtMs)
            put("completedAtMs", report.completedAtMs ?: 0L)
        }
        list.add(entry)

        val existing = getIndex()
        for (item in existing) {
            if (item.optString("sessionId") != report.sessionId) {
                list.add(item)
            }
        }

        try {
            val arr = JSONArray()
            list.take(MAX_RETAINED_SESSIONS).forEach { arr.put(it) }
            indexFile.writeText(arr.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update diagnostic index: ${e.message}")
        }
    }

    fun getIndex(): List<JSONObject> {
        val dir = getStorageDir()
        val file = File(dir, INDEX_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun pruneOldSessions() {
        try {
            val dir = getSessionsDir()
            val files = dir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > MAX_RETAINED_SESSIONS) {
                files.drop(MAX_RETAINED_SESSIONS).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune old sessions: ${e.message}")
        }
    }

    // ==========================================
    // STORAGE USAGE & CLEANUP
    // ==========================================

    @Synchronized
    fun getStorageStats(): DiagnosticStorageStats {
        return try {
            val dir = getStorageDir()
            var totalBytes = 0L
            var dailyFilesCount = 0
            var totalEvents = 0L
            var todayEvents = 0L

            val todayStr = LocalDate.now(ScheduleHelper.KOLKATA_ZONE).format(DATE_FORMATTER)

            fun calculateSize(target: File) {
                if (target.isDirectory) {
                    target.listFiles()?.forEach { calculateSize(it) }
                } else {
                    totalBytes += target.length()
                }
            }
            calculateSize(dir)

            val jsonlFiles = dir.listFiles { f -> f.extension == "jsonl" } ?: emptyArray()
            dailyFilesCount = jsonlFiles.size
            jsonlFiles.forEach { file ->
                var lines = 0L
                try {
                    file.forEachLine { if (it.isNotBlank()) lines++ }
                } catch (ignored: Exception) {}
                totalEvents += lines
                if (file.name == "$todayStr.jsonl") {
                    todayEvents = lines
                }
            }

            val sessionsCount = getSessionsDir().listFiles { f -> f.extension == "json" }?.size ?: 0

            val formattedSize = when {
                totalBytes < 1024 -> "$totalBytes B"
                totalBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", totalBytes / 1024.0)
                else -> String.format(Locale.US, "%.2f MB", totalBytes / (1024.0 * 1024.0))
            }

            DiagnosticStorageStats(
                totalEventsRecorded = totalEvents,
                todayEventsRecorded = todayEvents,
                storageSizeBytes = totalBytes,
                storageSizeFormatted = formattedSize,
                activeDaysCount = dailyFilesCount,
                sessionsCount = sessionsCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate storage stats: ${e.message}", e)
            DiagnosticStorageStats(0, 0, 0, "0 B", 0, 0)
        }
    }

    @Synchronized
    fun deleteAllRecords() {
        synchronized(fileLock) {
            try {
                val dir = getStorageDir()
                val recordingState = loadRecordingState()

                // Delete all files and subdirectories
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }

                // Preserve active recording state with reset counts
                if (recordingState.isRecording) {
                    saveRecordingState(recordingState.copy(totalEventsRecorded = 0L))
                }
                Log.i(TAG, "All diagnostic records deleted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete diagnostic records: ${e.message}", e)
            }
        }
    }

    // ==========================================
    // EXPORT ENGINE (ZIP CREATION)
    // ==========================================

    /**
     * Builds a comprehensive ZIP export containing manifest, summary, daily jsonl, and sessions.
     * Can be invoked at any time without stopping active recording.
     */
    fun createExportZip(rangeType: ExportRangeType, selectedDate: String? = null): File {
        synchronized(fileLock) {
            val exportsDir = getExportsDir()
            val nowStr = LocalDate.now(ScheduleHelper.KOLKATA_ZONE).format(DATE_FORMATTER)
            val timestamp = System.currentTimeMillis()
            val zipFile = File(exportsDir, "FleetX_GPS_Diagnostics_${nowStr}_$timestamp.zip")

            val eventsToInclude = when (rangeType) {
                ExportRangeType.TODAY -> getTodayEvents()
                ExportRangeType.ALL -> getAllDailyEvents()
                ExportRangeType.CURRENT_SESSION -> getTodayEvents()
                ExportRangeType.DATE_RANGE -> {
                    if (selectedDate != null) getEventsForDate(selectedDate) else getTodayEvents()
                }
            }

            val summaryReport = GpsDiagnosticManager.generateRootCauseSummary()

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // 1. Write summary.txt
                val summaryText = buildSummaryText(summaryReport, rangeType, eventsToInclude.size)
                val summaryEntry = ZipEntry("summary.txt")
                zos.putNextEntry(summaryEntry)
                zos.write(summaryText.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Write manifest.json
                val manifestObj = JSONObject().apply {
                    put("exportTimestampMs", timestamp)
                    put("exportDate", nowStr)
                    put("rangeType", rangeType.name)
                    put("totalEventsExported", eventsToInclude.size)
                    put("isContinuousRecordingActive", loadRecordingState().isRecording)
                }
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestObj.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 3. Write Daily JSONL files
                val targetDates = when (rangeType) {
                    ExportRangeType.TODAY -> listOf(nowStr)
                    ExportRangeType.ALL -> {
                        getStorageDir().listFiles { f -> f.extension == "jsonl" }?.map { it.nameWithoutExtension } ?: listOf(nowStr)
                    }
                    ExportRangeType.CURRENT_SESSION -> listOf(nowStr)
                    ExportRangeType.DATE_RANGE -> if (selectedDate != null) listOf(selectedDate) else listOf(nowStr)
                }

                for (dateName in targetDates) {
                    val dailyFile = File(getStorageDir(), "$dateName.jsonl")
                    if (dailyFile.exists()) {
                        val dailyEntry = ZipEntry("daily_records/$dateName.jsonl")
                        zos.putNextEntry(dailyEntry)
                        FileInputStream(dailyFile).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }

                // 4. Write Sessions if any
                val sessionFiles = getSessionsDir().listFiles { f -> f.extension == "json" } ?: emptyArray()
                for (sess in sessionFiles) {
                    val sessionEntry = ZipEntry("sessions/${sess.name}")
                    zos.putNextEntry(sessionEntry)
                    FileInputStream(sess).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
            return zipFile
        }
    }

    fun buildSummaryText(summary: ContinuousRootCauseSummary, rangeType: ExportRangeType, eventCount: Int): String {
        val rom = DeviceRomDetector.getRomInfo()
        return buildString {
            appendLine("==========================================")
            appendLine("FleetX GPS Continuous Diagnostic Export")
            appendLine("==========================================")
            appendLine("Export Scope:               ${rangeType.name}")
            appendLine("Events Exported:            $eventCount")
            appendLine("Generation Time:            ${Instant.now().atZone(ScheduleHelper.KOLKATA_ZONE).format(ISO_FORMATTER)}")
            appendLine("Device:                     ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Detected System / ROM:      ${rom.displayName} (${rom.romVersion})")
            appendLine("Background Kill Risk:       ${rom.killRisk}")
            appendLine("Recommended OEM Fix:        ${rom.advice}")
            appendLine()
            appendLine("PRIMARY OBSERVATION:")
            appendLine(summary.primaryObservation)
            appendLine()
            appendLine("LAST CONFIRMED HEALTHY EVENT:")
            appendLine(summary.lastConfirmedHealthyEvent)
            appendLine()
            appendLine("LAST SUCCESSFUL RTDB WRITE:")
            appendLine(summary.lastSuccessfulRtdbWrite)
            appendLine()
            appendLine("FIRST OBSERVED FAILURE:")
            appendLine(summary.firstObservedFailure)
            appendLine()
            appendLine("SERVICE STATE:")
            appendLine(summary.serviceState)
            appendLine()
            appendLine("POSSIBLE CAUSES:")
            appendLine(summary.possibleCauses)
            appendLine()
            appendLine("NEXT EVIDENCE NEEDED:")
            appendLine(summary.nextEvidenceNeeded)
            appendLine("==========================================")
        }
    }

    /**
     * Saves both the comprehensive diagnostic ZIP and readable summary.txt directly into device's Downloads directory.
     */
    fun saveReportToDownloads(rangeType: ExportRangeType): File {
        val zipFile = createExportZip(rangeType)
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destZip = File(downloadsDir, zipFile.name)
            zipFile.copyTo(destZip, overwrite = true)

            val summaryReport = GpsDiagnosticManager.generateRootCauseSummary()
            val summaryText = buildSummaryText(summaryReport, rangeType, 0)
            val destTxt = File(downloadsDir, "${zipFile.nameWithoutExtension}_summary.txt")
            destTxt.writeText(summaryText)
            return destZip
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving to public Downloads: ${e.message}", e)
            return zipFile
        }
    }

    /**
     * Returns full plain text representation of diagnostic summary for clipboard copying and direct display.
     */
    fun getReportPlainText(rangeType: ExportRangeType): String {
        val events = when (rangeType) {
            ExportRangeType.TODAY -> getTodayEvents()
            ExportRangeType.ALL -> getAllDailyEvents()
            else -> getTodayEvents()
        }
        val summaryReport = GpsDiagnosticManager.generateRootCauseSummary()
        return buildSummaryText(summaryReport, rangeType, events.size)
    }

    // ==========================================
    // JSON SERIALIZATION / DESERIALIZATION
    // ==========================================

    fun serializeEventToJson(event: DiagnosticEvent): JSONObject {
        return JSONObject().apply {
            put("timestampMs", event.timestampMs)
            put("timeFormatted", event.timeFormatted)
            put("stage", event.stage.name)
            put("eventName", event.eventName)
            put("isFailure", event.isFailure)
            put("failureType", event.failureType.name)
            put("recordingSessionId", event.recordingSessionId)
            put("operatingWindow", event.operatingWindow)
            put("isWindowActive", event.isWindowActive)
            put("appVisibility", event.appVisibility)
            put("screenInteractive", event.screenInteractive)
            put("trackingPreference", event.trackingPreference)
            put("backgroundLocationPermission", event.backgroundLocationPermission)
            put("gpsEnabled", event.gpsEnabled)
            event.errorType?.let { put("errorType", it) }
            event.errorStage?.let { put("errorStage", it) }

            val detObj = JSONObject()
            event.details.forEach { (k, v) ->
                // Zero coordinate fields protection (do NOT strip latencyMs)
                val lower = k.lowercase()
                val isCoord = lower == "latitude" || lower == "longitude" || lower == "lat" || lower == "lng" ||
                        lower.startsWith("latitude_") || lower.startsWith("longitude_")
                if (!isCoord) {
                    detObj.put(k, v)
                }
            }
            put("details", detObj)
        }
    }

    fun deserializeEventFromJson(json: JSONObject): DiagnosticEvent {
        val detailsMap = mutableMapOf<String, String>()
        val detObj = json.optJSONObject("details")
        if (detObj != null) {
            val keys = detObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                detailsMap[key] = detObj.optString(key)
            }
        }

        val stageName = json.optString("stage", DiagnosticStage.SYSTEM_HEALTH.name)
        val stage = try { DiagnosticStage.valueOf(stageName) } catch (e: Exception) { DiagnosticStage.SYSTEM_HEALTH }

        val failName = json.optString("failureType", FailureType.NONE.name)
        val failType = try { FailureType.valueOf(failName) } catch (e: Exception) { FailureType.NONE }

        return DiagnosticEvent(
            timestampMs = json.optLong("timestampMs", 0L),
            timeFormatted = json.optString("timeFormatted", ""),
            stage = stage,
            eventName = json.optString("eventName", "UNKNOWN_EVENT"),
            details = detailsMap,
            isFailure = json.optBoolean("isFailure", false),
            failureType = failType,
            recordingSessionId = json.optString("recordingSessionId", ""),
            operatingWindow = json.optString("operatingWindow", ""),
            isWindowActive = json.optBoolean("isWindowActive", false),
            appVisibility = json.optString("appVisibility", "UNKNOWN"),
            screenInteractive = json.optBoolean("screenInteractive", false),
            trackingPreference = json.optBoolean("trackingPreference", false),
            backgroundLocationPermission = json.optBoolean("backgroundLocationPermission", false),
            gpsEnabled = json.optBoolean("gpsEnabled", false),
            errorType = if (json.has("errorType")) json.optString("errorType") else null,
            errorStage = if (json.has("errorStage")) json.optString("errorStage") else null
        )
    }

    fun serializeReport(report: GpsDiagnosticReport): String {
        val root = JSONObject().apply {
            put("sessionId", report.sessionId)
            put("date", report.date)
            put("windowName", report.windowName)
            put("windowStart", report.windowStart)
            put("windowEnd", report.windowEnd)
            put("busId", report.busId)
            put("driverIdMasked", report.driverIdMasked)
            put("sessionStatus", report.sessionStatus.name)
            put("primaryFailure", report.primaryFailure.name)

            val secFailArr = JSONArray()
            report.secondaryFailures.forEach { secFailArr.put(it.name) }
            put("secondaryFailures", secFailArr)

            val pipeObj = JSONObject().apply {
                put("alarm", report.pipeline.alarm.name)
                put("permissions", report.pipeline.permissions.name)
                put("gpsProvider", report.pipeline.gpsProvider.name)
                put("serviceStart", report.pipeline.serviceStart.name)
                put("locationCallback", report.pipeline.locationCallback.name)
                put("firebaseInit", report.pipeline.firebaseInit.name)
                put("rtdbWrite", report.pipeline.rtdbWrite.name)
            }
            put("pipeline", pipeObj)

            val eventsArr = JSONArray()
            report.events.forEach { ev -> eventsArr.put(serializeEventToJson(ev)) }
            put("events", eventsArr)

            val hbArr = JSONArray()
            report.heartbeats.forEach { hb ->
                val hbObj = JSONObject().apply {
                    put("timestampMs", hb.timestampMs)
                    put("timeFormatted", hb.timeFormatted)
                    put("serviceAlive", hb.serviceAlive)
                    put("locationCallbacksCount", hb.locationCallbacksCount)
                    put("lastLocationAgeSeconds", hb.lastLocationAgeSeconds)
                    put("rtdbWritesCount", hb.rtdbWritesCount)
                    put("lastRtdbLatencyMs", hb.lastRtdbLatencyMs)
                }
                hbArr.put(hbObj)
            }
            put("heartbeats", hbArr)

            if (report.deviceState != null) {
                val ds = report.deviceState
                val dsObj = JSONObject().apply {
                    put("isInteractive", ds.isInteractive)
                    put("isPowerSaveMode", ds.isPowerSaveMode)
                    put("isIgnoringBatteryOptimizations", ds.isIgnoringBatteryOptimizations)
                    put("isDeviceIdleMode", ds.isDeviceIdleMode)
                    put("isAppInForeground", ds.isAppInForeground)
                    put("fineLocationGranted", ds.fineLocationGranted)
                    put("coarseLocationGranted", ds.coarseLocationGranted)
                    put("backgroundLocationGranted", ds.backgroundLocationGranted)
                    put("notificationGranted", ds.notificationGranted)
                    put("locationServicesEnabled", ds.locationServicesEnabled)
                    put("networkConnected", ds.networkConnected)
                    put("androidVersion", ds.androidVersion)
                    put("deviceManufacturer", ds.deviceManufacturer)
                    put("deviceModel", ds.deviceModel)
                }
                put("deviceState", dsObj)
            }

            put("summaryMessage", report.summaryMessage)
            put("createdAtMs", report.createdAtMs)
            report.completedAtMs?.let { put("completedAtMs", it) }
        }
        return root.toString(2)
    }

    fun deserializeReport(jsonString: String): GpsDiagnosticReport? {
        return try {
            val root = JSONObject(jsonString)
            val sessionId = root.getString("sessionId")
            val date = root.optString("date", "")
            val windowName = root.optString("windowName", "")
            val windowStart = root.optString("windowStart", "")
            val windowEnd = root.optString("windowEnd", "")
            val busId = root.optString("busId", "")
            val driverIdMasked = root.optString("driverIdMasked", "")

            val statusStr = root.optString("sessionStatus", SessionStatus.RUNNING.name)
            val sessionStatus = try { SessionStatus.valueOf(statusStr) } catch (e: Exception) { SessionStatus.RUNNING }

            val primFailStr = root.optString("primaryFailure", FailureType.NONE.name)
            val primaryFailure = try { FailureType.valueOf(primFailStr) } catch (e: Exception) { FailureType.NONE }

            val secFailures = mutableListOf<FailureType>()
            val secFailArr = root.optJSONArray("secondaryFailures")
            if (secFailArr != null) {
                for (i in 0 until secFailArr.length()) {
                    try {
                        secFailures.add(FailureType.valueOf(secFailArr.getString(i)))
                    } catch (ignored: Exception) {}
                }
            }

            val pipeObj = root.optJSONObject("pipeline")
            fun step(key: String): PipelineStepStatus {
                val str = pipeObj?.optString(key, PipelineStepStatus.NOT_REACHED.name) ?: PipelineStepStatus.NOT_REACHED.name
                return try { PipelineStepStatus.valueOf(str) } catch (e: Exception) { PipelineStepStatus.NOT_REACHED }
            }
            val pipeline = PipelineStatus(
                alarm = step("alarm"),
                permissions = step("permissions"),
                gpsProvider = step("gpsProvider"),
                serviceStart = step("serviceStart"),
                locationCallback = step("locationCallback"),
                firebaseInit = step("firebaseInit"),
                rtdbWrite = step("rtdbWrite")
            )

            val events = mutableListOf<DiagnosticEvent>()
            val eventsArr = root.optJSONArray("events")
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length()) {
                    events.add(deserializeEventFromJson(eventsArr.getJSONObject(i)))
                }
            }

            val heartbeats = mutableListOf<DiagnosticHeartbeat>()
            val hbArr = root.optJSONArray("heartbeats")
            if (hbArr != null) {
                for (i in 0 until hbArr.length()) {
                    val hbObj = hbArr.getJSONObject(i)
                    heartbeats.add(
                        DiagnosticHeartbeat(
                            timestampMs = hbObj.optLong("timestampMs", 0L),
                            timeFormatted = hbObj.optString("timeFormatted", ""),
                            serviceAlive = hbObj.optBoolean("serviceAlive", false),
                            locationCallbacksCount = hbObj.optInt("locationCallbacksCount", 0),
                            lastLocationAgeSeconds = hbObj.optLong("lastLocationAgeSeconds", 0L),
                            rtdbWritesCount = hbObj.optInt("rtdbWritesCount", 0),
                            lastRtdbLatencyMs = hbObj.optLong("lastRtdbLatencyMs", 0L)
                        )
                    )
                }
            }

            var deviceState: DeviceStateSnapshot? = null
            val dsObj = root.optJSONObject("deviceState")
            if (dsObj != null) {
                deviceState = DeviceStateSnapshot(
                    isInteractive = dsObj.optBoolean("isInteractive", false),
                    isPowerSaveMode = dsObj.optBoolean("isPowerSaveMode", false),
                    isIgnoringBatteryOptimizations = dsObj.optBoolean("isIgnoringBatteryOptimizations", false),
                    isDeviceIdleMode = dsObj.optBoolean("isDeviceIdleMode", false),
                    isAppInForeground = dsObj.optBoolean("isAppInForeground", false),
                    fineLocationGranted = dsObj.optBoolean("fineLocationGranted", false),
                    coarseLocationGranted = dsObj.optBoolean("coarseLocationGranted", false),
                    backgroundLocationGranted = dsObj.optBoolean("backgroundLocationGranted", false),
                    notificationGranted = dsObj.optBoolean("notificationGranted", false),
                    locationServicesEnabled = dsObj.optBoolean("locationServicesEnabled", false),
                    networkConnected = dsObj.optBoolean("networkConnected", false),
                    androidVersion = dsObj.optInt("androidVersion", 0),
                    deviceManufacturer = dsObj.optString("deviceManufacturer", ""),
                    deviceModel = dsObj.optString("deviceModel", "")
                )
            }

            val summaryMessage = root.optString("summaryMessage", "")
            val createdAtMs = root.optLong("createdAtMs", 0L)
            val completedAtMs = if (root.has("completedAtMs")) root.optLong("completedAtMs") else null

            GpsDiagnosticReport(
                sessionId = sessionId,
                date = date,
                windowName = windowName,
                windowStart = windowStart,
                windowEnd = windowEnd,
                busId = busId,
                driverIdMasked = driverIdMasked,
                sessionStatus = sessionStatus,
                primaryFailure = primaryFailure,
                secondaryFailures = secFailures,
                pipeline = pipeline,
                events = events,
                heartbeats = heartbeats,
                deviceState = deviceState,
                summaryMessage = summaryMessage,
                createdAtMs = createdAtMs,
                completedAtMs = completedAtMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize report: ${e.message}", e)
            null
        }
    }

    fun exportAsText(report: GpsDiagnosticReport): String {
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("FleetX GPS Diagnostic Report")
        sb.appendLine("==========================================")
        sb.appendLine("Date:             ${report.date}")
        sb.appendLine("Operating Window: ${report.windowName} (${report.windowStart} - ${report.windowEnd})")
        sb.appendLine("Bus Assigned:     ${report.busId}")
        sb.appendLine("Driver:           ${report.driverIdMasked}")
        sb.appendLine("Session ID:       ${report.sessionId}")
        sb.appendLine("Overall Status:   ${report.sessionStatus}")
        sb.appendLine("Primary Failure:  ${report.primaryFailure} (${report.primaryFailure.userReason})")
        if (report.secondaryFailures.isNotEmpty()) {
            sb.appendLine("Secondary Issues: ${report.secondaryFailures.joinToString(", ")}")
        }
        sb.appendLine()
        sb.appendLine("PIPELINE STATUS:")
        fun symbol(status: PipelineStepStatus): String = when (status) {
            PipelineStepStatus.PASS -> "✓ PASS"
            PipelineStepStatus.FAIL -> "✕ FAIL"
            PipelineStepStatus.WARNING -> "⚠ WARN"
            PipelineStepStatus.NOT_REACHED -> "— NOT REACHED"
        }
        sb.appendLine("Alarm:             ${symbol(report.pipeline.alarm)}")
        sb.appendLine("Permissions:       ${symbol(report.pipeline.permissions)}")
        sb.appendLine("GPS Provider:      ${symbol(report.pipeline.gpsProvider)}")
        sb.appendLine("Service Start:     ${symbol(report.pipeline.serviceStart)}")
        sb.appendLine("Location Callback: ${symbol(report.pipeline.locationCallback)}")
        sb.appendLine("Firebase Init:     ${symbol(report.pipeline.firebaseInit)}")
        sb.appendLine("RTDB Write:        ${symbol(report.pipeline.rtdbWrite)}")
        sb.appendLine()
        if (report.deviceState != null) {
            val ds = report.deviceState
            sb.appendLine("DEVICE STATE:")
            sb.appendLine("Model:                ${ds.deviceManufacturer} ${ds.deviceModel} (Android ${ds.androidVersion})")
            sb.appendLine("Screen Interactive:   ${if (ds.isInteractive) "ON" else "LOCKED/OFF"}")
            sb.appendLine("FleetX App:           ${if (ds.isAppInForeground) "FOREGROUND" else "BACKGROUND/CLOSED"}")
            sb.appendLine("Battery Saver:        ${if (ds.isPowerSaveMode) "ENABLED" else "OFF"}")
            sb.appendLine("Battery Optimization: ${if (ds.isIgnoringBatteryOptimizations) "EXEMPTED (Unrestricted)" else "RESTRICTED (Optimized)"}")
            sb.appendLine("Doze Idle Mode:       ${if (ds.isDeviceIdleMode) "ACTIVE" else "INACTIVE"}")
            sb.appendLine("GPS Hardware Enabled: ${ds.locationServicesEnabled}")
            sb.appendLine("Background Location:  ${ds.backgroundLocationGranted}")
            sb.appendLine("Network Connected:    ${ds.networkConnected}")
            sb.appendLine()
        }
        sb.appendLine("EVENT TIMELINE:")
        if (report.events.isEmpty()) {
            sb.appendLine("(No events recorded)")
        } else {
            report.events.forEach { ev ->
                val failTag = if (ev.isFailure) " [FAIL: ${ev.failureType.name}]" else ""
                val detailsStr = if (ev.details.isNotEmpty()) " (${ev.details.entries.joinToString { "${it.key}=${it.value}" }})" else ""
                sb.appendLine("${ev.timeFormatted} [${ev.stage.displayName}] ${ev.eventName}$failTag$detailsStr")
            }
        }
        sb.appendLine()
        if (report.heartbeats.isNotEmpty()) {
            sb.appendLine("HEARTBEAT LOG (${report.heartbeats.size} samples):")
            report.heartbeats.takeLast(10).forEach { hb ->
                sb.appendLine("${hb.timeFormatted} serviceAlive=${hb.serviceAlive}, fixes=${hb.locationCallbacksCount}, lastFixAge=${hb.lastLocationAgeSeconds}s, rtdbWrites=${hb.rtdbWritesCount}, writeLatency=${hb.lastRtdbLatencyMs}ms")
            }
            sb.appendLine()
        }
        sb.appendLine("SUMMARY:")
        sb.appendLine(report.summaryMessage)
        sb.appendLine("==========================================")
        return sb.toString()
    }
}
