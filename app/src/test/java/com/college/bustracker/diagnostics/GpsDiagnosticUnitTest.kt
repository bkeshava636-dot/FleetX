package com.college.bustracker.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Unit tests verifying continuous diagnostic recording, storage models,
 * privacy guarantees, process interruption tracking, and root-cause synthesis.
 */
class GpsDiagnosticUnitTest {

    @Test
    fun testPipelineStatuses() {
        val pipeline = PipelineStatus(
            alarm = PipelineStepStatus.PASS,
            permissions = PipelineStepStatus.PASS,
            gpsProvider = PipelineStepStatus.PASS,
            serviceStart = PipelineStepStatus.FAIL,
            locationCallback = PipelineStepStatus.NOT_REACHED,
            firebaseInit = PipelineStepStatus.NOT_REACHED,
            rtdbWrite = PipelineStepStatus.NOT_REACHED
        )

        assertEquals(PipelineStepStatus.PASS, pipeline.alarm)
        assertEquals(PipelineStepStatus.PASS, pipeline.permissions)
        assertEquals(PipelineStepStatus.PASS, pipeline.gpsProvider)
        assertEquals(PipelineStepStatus.FAIL, pipeline.serviceStart)
        assertEquals(PipelineStepStatus.NOT_REACHED, pipeline.locationCallback)
    }

    @Test
    fun testPrimaryAndSecondaryFailureClassification() {
        val primary = FailureType.FOREGROUND_SERVICE_FAILURE
        val secondary = listOf(FailureType.LOCATION_CALLBACK_FAILURE, FailureType.RTDB_WRITE_FAILURE)

        val report = GpsDiagnosticReport(
            sessionId = "test-1234",
            date = "2026-10-08",
            windowName = "Morning",
            windowStart = "07:00",
            windowEnd = "09:00",
            busId = "BUS-01",
            driverIdMasked = "DRIVER_***01",
            sessionStatus = SessionStatus.FAILED,
            primaryFailure = primary,
            secondaryFailures = secondary,
            pipeline = PipelineStatus(serviceStart = PipelineStepStatus.FAIL),
            events = emptyList(),
            heartbeats = emptyList(),
            deviceState = null,
            summaryMessage = "Service start failed",
            createdAtMs = System.currentTimeMillis()
        )

        assertEquals(FailureType.FOREGROUND_SERVICE_FAILURE, report.primaryFailure)
        assertEquals(2, report.secondaryFailures.size)
        assertTrue(report.secondaryFailures.contains(FailureType.LOCATION_CALLBACK_FAILURE))
        assertTrue(report.secondaryFailures.contains(FailureType.RTDB_WRITE_FAILURE))
    }

    @Test
    fun testDiagnosticEventNoCoordinates() {
        val details = mapOf(
            "accuracyMeters" to "12.4",
            "provider" to "gps",
            "locationAgeMs" to "450",
            "elapsedTimeMs" to "2300"
        )

        val event = DiagnosticEvent(
            timestampMs = 1728400000000L,
            timeFormatted = "07:00:05",
            stage = DiagnosticStage.LOCATION_CALLBACK,
            eventName = "LOCATION_CALLBACK_RECEIVED",
            details = details
        )

        assertFalse(event.details.containsKey("latitude"))
        assertFalse(event.details.containsKey("longitude"))
        assertFalse(event.details.containsKey("lat"))
        assertFalse(event.details.containsKey("lng"))
    }

    @Test
    fun testRecordingStatePersistenceAcrossRestarts() {
        val state = ContinuousRecordingState(
            isRecording = true,
            startedAtMs = 1728475000000L,
            startedAtFormatted = "2026-10-09 18:00:00",
            recordingSessionId = "rec_test_123",
            totalEventsRecorded = 55L,
            lastEventTimestampMs = 1728475060000L,
            lastEventName = "LOCATION_CALLBACK_RECEIVED"
        )

        val json = JSONObject().apply {
            put("isRecording", state.isRecording)
            put("startedAtMs", state.startedAtMs)
            put("startedAtFormatted", state.startedAtFormatted)
            put("recordingSessionId", state.recordingSessionId)
            put("totalEventsRecorded", state.totalEventsRecorded)
            put("lastEventTimestampMs", state.lastEventTimestampMs)
            put("lastEventName", state.lastEventName)
        }

        val restored = ContinuousRecordingState(
            isRecording = json.optBoolean("isRecording", false),
            startedAtMs = json.optLong("startedAtMs", 0L),
            startedAtFormatted = json.optString("startedAtFormatted", ""),
            recordingSessionId = json.optString("recordingSessionId", ""),
            totalEventsRecorded = json.optLong("totalEventsRecorded", 0L),
            lastEventTimestampMs = json.optLong("lastEventTimestampMs", 0L),
            lastEventName = json.optString("lastEventName", "")
        )

        assertTrue(restored.isRecording)
        assertEquals("rec_test_123", restored.recordingSessionId)
        assertEquals(55L, restored.totalEventsRecorded)
        assertEquals("LOCATION_CALLBACK_RECEIVED", restored.lastEventName)
    }

    @Test
    fun testRecordingOffPreservesExistingRecords() {
        val state = ContinuousRecordingState(
            isRecording = false,
            startedAtMs = 1728475000000L,
            startedAtFormatted = "2026-10-09 18:00:00",
            recordingSessionId = "rec_test_123",
            totalEventsRecorded = 120L
        )

        assertFalse(state.isRecording)
        assertEquals(120L, state.totalEventsRecorded)
    }

    @Test
    fun testEventsAppendWithoutCorruptingJsonl() {
        val storage = GpsDiagnosticStorage(null)
        val event1 = DiagnosticEvent(
            timestampMs = 1728475000000L,
            timeFormatted = "07:00:01",
            stage = DiagnosticStage.SERVICE_START,
            eventName = "FOREGROUND_SERVICE_STARTED",
            details = mapOf("busId" to "BUS-01")
        )
        val event2 = DiagnosticEvent(
            timestampMs = 1728475002000L,
            timeFormatted = "07:00:03",
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_WRITE_SUCCEEDED",
            details = mapOf("latencyMs" to "320")
        )

        val json1 = storage.serializeEventToJson(event1)
        val json2 = storage.serializeEventToJson(event2)

        val line1 = json1.toString() + "\n"
        val line2 = json2.toString() + "\n"

        assertTrue(line1.endsWith("\n"))
        assertTrue(line2.endsWith("\n"))

        val deserialized1 = storage.deserializeEventFromJson(JSONObject(line1.trim()))
        val deserialized2 = storage.deserializeEventFromJson(JSONObject(line2.trim()))

        assertEquals("FOREGROUND_SERVICE_STARTED", deserialized1.eventName)
        assertEquals("RTDB_WRITE_SUCCEEDED", deserialized2.eventName)
        assertEquals("320", deserialized2.details["latencyMs"])
    }

    @Test
    fun testMidnightCreatesNewDailyFileNaming() {
        val epochBeforeMidnight = 1728584999000L // 2026-10-10 23:59:59 IST (+05:30)
        val epochAfterMidnight = 1728585001000L  // 2026-10-11 00:00:01 IST (+05:30)

        val zone = com.college.bustracker.util.ScheduleHelper.KOLKATA_ZONE
        val dateBefore = Instant.ofEpochMilli(epochBeforeMidnight).atZone(zone).toLocalDate()
        val dateAfter = Instant.ofEpochMilli(epochAfterMidnight).atZone(zone).toLocalDate()

        val fileBefore = "${dateBefore}.jsonl"
        val fileAfter = "${dateAfter}.jsonl"

        assertEquals(dateBefore.plusDays(1), dateAfter)
        assertFalse(fileBefore == fileAfter)
    }

    @Test
    fun testMultipleEventsOrderedByTimestamp() {
        val events = listOf(
            DiagnosticEvent(1003L, "07:00:03", DiagnosticStage.RTDB_WRITE, "C"),
            DiagnosticEvent(1001L, "07:00:01", DiagnosticStage.SERVICE_START, "A"),
            DiagnosticEvent(1002L, "07:00:02", DiagnosticStage.LOCATION_CALLBACK, "B")
        )

        val sorted = events.sortedBy { it.timestampMs }
        assertEquals("A", sorted[0].eventName)
        assertEquals("B", sorted[1].eventName)
        assertEquals("C", sorted[2].eventName)
    }

    @Test
    fun testExportDoesNotContainCoordinatesOrSecrets() {
        val storage = GpsDiagnosticStorage(null)
        val report = GpsDiagnosticReport(
            sessionId = "sess-8899",
            date = "2026-10-09",
            windowName = "Morning",
            windowStart = "07:00",
            windowEnd = "09:00",
            busId = "BUS-05",
            driverIdMasked = "DR****05",
            sessionStatus = SessionStatus.COMPLETED,
            primaryFailure = FailureType.NONE,
            secondaryFailures = emptyList(),
            pipeline = PipelineStatus(
                alarm = PipelineStepStatus.PASS,
                serviceStart = PipelineStepStatus.PASS,
                rtdbWrite = PipelineStepStatus.PASS
            ),
            events = listOf(
                DiagnosticEvent(
                    timestampMs = 1728475000000L,
                    timeFormatted = "07:00:01",
                    stage = DiagnosticStage.LOCATION_CALLBACK,
                    eventName = "LOCATION_CALLBACK_RECEIVED",
                    details = mapOf("accuracyMeters" to "14.0", "provider" to "gps")
                )
            ),
            heartbeats = emptyList(),
            deviceState = null,
            summaryMessage = "Tracking verified",
            createdAtMs = 1728475000000L
        )

        val json = storage.serializeReport(report)
        assertFalse(json.contains("latitude"))
        assertFalse(json.contains("longitude"))
        assertFalse(json.contains("password"))
        assertFalse(json.contains("apiKey"))
        assertFalse(json.contains("token"))

        val text = storage.exportAsText(report)
        assertFalse(text.contains("latitude"))
        assertFalse(text.contains("longitude"))
        assertTrue(text.contains("BUS-05"))
        assertTrue(text.contains("sess-8899"))
    }

    @Test
    fun testProcessInterruptionRepresentation() {
        val lastEventMs = 1728475000000L
        val restartMs = 1728475045000L
        val gapSeconds = (restartMs - lastEventMs) / 1000

        val restartEvent = DiagnosticEvent(
            timestampMs = restartMs,
            timeFormatted = "07:00:45",
            stage = DiagnosticStage.PROCESS_LIFECYCLE,
            eventName = "APP_PROCESS_RESTARTED",
            details = mapOf(
                "gapSeconds" to gapSeconds.toString(),
                "lastKnownEvent" to "TASK_REMOVED_CALLBACK_RECEIVED",
                "diagnosticNote" to "Process revived after interruption"
            )
        )

        assertEquals("45", restartEvent.details["gapSeconds"])
        assertEquals("TASK_REMOVED_CALLBACK_RECEIVED", restartEvent.details["lastKnownEvent"])
    }

    @Test
    fun testRootCauseSummaryDoesNotInventCertainty() {
        val summary = ContinuousRootCauseSummary(
            primaryObservation = "TASK_REMOVED_CALLBACK_RECEIVED recorded at 07:30:00. No further events recorded.",
            lastConfirmedHealthyEvent = "07:29:55 LOCATION_CALLBACK_RECEIVED",
            lastSuccessfulRtdbWrite = "07:29:56 latency=340ms",
            firstObservedFailure = "None detected prior to task removal",
            serviceState = "TASK_REMOVED at 07:30:00 (Process Terminated)",
            possibleCauses = "Samsung One UI killed the hosting process when swiped from Recents.",
            nextEvidenceNeeded = "Check device logcat for am_kill / lowmemorykiller entries after swiping from Recents."
        )

        assertTrue(summary.possibleCauses.contains("killed"))
        assertNotNull(summary.nextEvidenceNeeded)
        assertFalse(summary.primaryObservation.isBlank())
    }

    @Test
    fun testSerializationAndDeserializationRoundtrip() {
        val storage = GpsDiagnosticStorage(null)
        val originalEvent = DiagnosticEvent(
            timestampMs = 1728475100000L,
            timeFormatted = "07:01:40",
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_WRITE_SUCCEEDED",
            details = mapOf("latencyMs" to "210", "busId" to "BUS-02"),
            isFailure = false,
            failureType = FailureType.NONE,
            appVisibility = "BACKGROUND",
            screenInteractive = false
        )

        val json = storage.serializeEventToJson(originalEvent)
        val deserialized = storage.deserializeEventFromJson(json)

        assertEquals(originalEvent.timestampMs, deserialized.timestampMs)
        assertEquals(originalEvent.eventName, deserialized.eventName)
        assertEquals(originalEvent.stage, deserialized.stage)
        assertEquals(originalEvent.details["latencyMs"], deserialized.details["latencyMs"])
        assertEquals("BACKGROUND", deserialized.appVisibility)
        assertEquals(false, deserialized.screenInteractive)
    }

    @Test
    fun testLiveLocationOperatorEpochNullable() {
        val normalDriverLoc = com.college.bustracker.data.model.LiveLocation(
            busId = "BUS-01",
            latitude = 12.9716,
            longitude = 77.5946,
            trackingStatus = "ACTIVE",
            lastUpdated = System.currentTimeMillis()
        )
        // Null by default so Firebase drops operatorEpoch and satisfies !newData.child('operatorEpoch').exists()
        assertEquals(null, normalDriverLoc.operatorEpoch)

        val tempDriverLoc = normalDriverLoc.copy(
            operatorEpoch = 1774849302L,
            temporaryAssignmentId = "TEMP-99"
        )
        assertEquals(1774849302L, tempDriverLoc.operatorEpoch)
        assertEquals("TEMP-99", tempDriverLoc.temporaryAssignmentId)
    }

    @Test
    fun testServiceDestroyedStopReasonClassification() {
        val storage = GpsDiagnosticStorage(null)
        val normalWindowEndEvent = DiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            timeFormatted = "20:25:01",
            stage = DiagnosticStage.SERVICE_LIFECYCLE,
            eventName = "SERVICE_DESTROYED",
            details = mapOf(
                "busId" to "BUS-01",
                "wasTracking" to "true",
                "stopReason" to "OPERATING_WINDOW_ENDED",
                "stopCaller" to "DriverScheduleReceiver",
                "activeDurationSeconds" to "540",
                "callbacksReceived" to "108",
                "locationsDispatched" to "108"
            ),
            isFailure = false,
            failureType = FailureType.NONE
        )

        val json = storage.serializeEventToJson(normalWindowEndEvent)
        val deserialized = storage.deserializeEventFromJson(json)

        assertEquals("OPERATING_WINDOW_ENDED", deserialized.details["stopReason"])
        assertEquals("DriverScheduleReceiver", deserialized.details["stopCaller"])
        assertEquals("540", deserialized.details["activeDurationSeconds"])
        assertFalse(deserialized.isFailure)

        val unexpectedTerminationEvent = DiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            timeFormatted = "20:25:01",
            stage = DiagnosticStage.SERVICE_LIFECYCLE,
            eventName = "SERVICE_DESTROYED",
            details = mapOf(
                "busId" to "BUS-01",
                "wasTracking" to "true",
                "stopReason" to "SYSTEM_OR_EXTERNAL_TERMINATION",
                "stopCaller" to "ANDROID_FRAMEWORK"
            ),
            isFailure = true,
            failureType = FailureType.SERVICE_STOPPED_UNEXPECTEDLY
        )
        assertTrue(unexpectedTerminationEvent.isFailure)
        assertEquals(FailureType.SERVICE_STOPPED_UNEXPECTEDLY, unexpectedTerminationEvent.failureType)
    }

    @Test
    fun testRtdbFailureSanitizationAndRetryEvents() {
        val storage = GpsDiagnosticStorage(null)
        val rawErrorMsg = "Client doesn't have permission to write to /live_locations/BUS-01 for auth auth_user_token_abcdef1234567890xyz"
        val sanitized = rawErrorMsg.replace(Regex("[a-zA-Z0-9_-]{28,}"), "[REDACTED_ID]")
        assertFalse(sanitized.contains("auth_user_token_abcdef1234567890xyz"))
        assertTrue(sanitized.contains("[REDACTED_ID]"))

        val retryEvent = DiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            timeFormatted = "20:15:04",
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_RETRY_SCHEDULED",
            details = mapOf(
                "busId" to "BUS-01",
                "attempt" to "1",
                "delayMs" to "1000",
                "reason" to "Transient RTDB write error (-24)"
            )
        )
        val json = storage.serializeEventToJson(retryEvent)
        val deserialized = storage.deserializeEventFromJson(json)

        assertEquals("RTDB_RETRY_SCHEDULED", deserialized.eventName)
        assertEquals("1", deserialized.details["attempt"])
        assertEquals("1000", deserialized.details["delayMs"])
    }

    @Test
    fun testAlarmScheduledDiagnosticDetails() {
        val storage = GpsDiagnosticStorage(null)
        val alarmEvent = DiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            timeFormatted = "06:55:00",
            stage = DiagnosticStage.ALARM,
            eventName = "ALARM_SCHEDULED",
            details = mapOf(
                "time" to "07:00",
                "requestCode" to "1001",
                "delayMinutes" to "5",
                "targetTime" to "07:00:00",
                "isExact" to "true",
                "alarmMethod" to "setAlarmClock"
            )
        )

        val json = storage.serializeEventToJson(alarmEvent)
        val deserialized = storage.deserializeEventFromJson(json)

        assertEquals("ALARM_SCHEDULED", deserialized.eventName)
        assertEquals("07:00", deserialized.details["time"])
        assertEquals("1001", deserialized.details["requestCode"])
        assertEquals("true", deserialized.details["isExact"])
        assertEquals("setAlarmClock", deserialized.details["alarmMethod"])
    }

    @Test
    fun testPreWindowTimeCalculation() {
        val morningPre = com.college.bustracker.service.AutoTrackingScheduler.calculatePreStartTime("07:00", 2L)
        val eveningPre = com.college.bustracker.service.AutoTrackingScheduler.calculatePreStartTime("16:00", 2L)
        val customPre = com.college.bustracker.service.AutoTrackingScheduler.calculatePreStartTime("08:30", 5L)
        val midnightWrapPre = com.college.bustracker.service.AutoTrackingScheduler.calculatePreStartTime("00:01", 2L)
        val zeroWrapPre = com.college.bustracker.service.AutoTrackingScheduler.calculatePreStartTime("00:00", 2L)

        assertEquals("06:58", morningPre)
        assertEquals("15:58", eveningPre)
        assertEquals("08:25", customPre)
        assertEquals("23:59", midnightWrapPre)
        assertEquals("23:58", zeroWrapPre)
    }

    @Test
    fun testWindowRecordingStateLifecycleEvents() {
        val storage = GpsDiagnosticStorage(null)

        val windowStartEvent = DiagnosticEvent(
            timestampMs = 1728525480000L,
            timeFormatted = "06:58:00",
            stage = DiagnosticStage.SYSTEM_HEALTH,
            eventName = "WINDOW_RECORDING_STARTED",
            details = mapOf(
                "recordingSessionId" to "win_abc12345",
                "startedAt" to "2026-10-10 06:58:00",
                "note" to "Pre-window alarm (2 min prior to 07:00)"
            ),
            recordingSessionId = "win_abc12345"
        )

        val jsonStart = storage.serializeEventToJson(windowStartEvent)
        val desStart = storage.deserializeEventFromJson(jsonStart)

        assertEquals("WINDOW_RECORDING_STARTED", desStart.eventName)
        assertTrue(desStart.recordingSessionId.startsWith("win_"))
        assertEquals("Pre-window alarm (2 min prior to 07:00)", desStart.details["note"])

        val windowStopEvent = DiagnosticEvent(
            timestampMs = 1728532800000L,
            timeFormatted = "09:00:00",
            stage = DiagnosticStage.SYSTEM_HEALTH,
            eventName = "WINDOW_RECORDING_STOPPED",
            details = mapOf(
                "note" to "Operating window ended (09:00)"
            ),
            recordingSessionId = "win_abc12345"
        )

        val jsonStop = storage.serializeEventToJson(windowStopEvent)
        val desStop = storage.deserializeEventFromJson(jsonStop)

        assertEquals("WINDOW_RECORDING_STOPPED", desStop.eventName)
        assertEquals("Operating window ended (09:00)", desStop.details["note"])
    }
}
