package com.college.bustracker.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.college.bustracker.service.AutoTrackingScheduler
import com.college.bustracker.service.TrackingServiceHelper
import com.college.bustracker.util.OperatingScheduleService
import com.college.bustracker.util.ScheduleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Central coordinator for continuous background GPS diagnostics.
 * Observes the entire tracking pipeline without competing with GPS or requesting separate location fixes.
 * Durably persists all events locally across process deaths and app restarts.
 */
object GpsDiagnosticManager {

    private const val TAG = "GpsDiagnosticManager"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var storage: GpsDiagnosticStorage? = null
    private var activeSession: MutableSessionState? = null
    private var observationJob: Job? = null

    // Reactive State
    private val _recordingState = MutableStateFlow(ContinuousRecordingState())
    val recordingState: StateFlow<ContinuousRecordingState> = _recordingState.asStateFlow()

    private val _storageStats = MutableStateFlow(DiagnosticStorageStats(0, 0, 0, "0 B", 0, 0))
    val storageStats: StateFlow<DiagnosticStorageStats> = _storageStats.asStateFlow()

    private val _currentReport = MutableStateFlow<GpsDiagnosticReport?>(null)
    val currentReport: StateFlow<GpsDiagnosticReport?> = _currentReport.asStateFlow()

    private val _latestEvent = MutableStateFlow<DiagnosticEvent?>(null)
    val latestEvent: StateFlow<DiagnosticEvent?> = _latestEvent.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    // Metrics tracking
    private var locationCallbackCount = 0
    private var rtdbWriteCount = 0
    private var lastLocationTimestamp = 0L
    private var lastRtdbWriteLatencyMs = 0L
    private var lastRtdbWriteSuccessTimestamp = 0L
    private var isServiceAlive = false
    private var lastKnownError: String? = null
    private var activeAppVisibility = "BACKGROUND"

    @Synchronized
    fun init(context: Context) {
        if (storage == null) {
            val store = GpsDiagnosticStorage(context.applicationContext)
            storage = store

            // 1. Restore recording state from disk
            val savedState = store.loadRecordingState()
            _recordingState.value = savedState

            // 2. Load latest operating window report & storage stats
            _currentReport.value = store.getLatestReport()
            refreshStorageStats()

            // 3. Start observation loop if recording is active
            if (savedState.isRecording) {
                startObservationLoop(context.applicationContext)
            }
        }
    }

    private fun getStorage(context: Context): GpsDiagnosticStorage {
        if (storage == null) {
            init(context)
        }
        return storage ?: GpsDiagnosticStorage(context.applicationContext)
    }

    fun refreshStorageStats() {
        storage?.let {
            _storageStats.value = it.getStorageStats()
        }
    }

    // ==========================================
    // RECORDING CONTROL (ON / OFF)
    // ==========================================

    @Synchronized
    fun startRecording(context: Context) {
        init(context)
        val now = System.currentTimeMillis()
        val nowFormatted = Instant.ofEpochMilli(now).atZone(ScheduleHelper.KOLKATA_ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
        val sessionId = "rec_" + UUID.randomUUID().toString().take(8)

        val newState = ContinuousRecordingState(
            isRecording = true,
            startedAtMs = now,
            startedAtFormatted = nowFormatted,
            recordingSessionId = sessionId,
            totalEventsRecorded = _recordingState.value.totalEventsRecorded,
            lastEventTimestampMs = now,
            lastEventName = "RECORDING_STARTED"
        )
        _recordingState.value = newState
        getStorage(context).saveRecordingState(newState)

        logEvent(
            context = context,
            stage = DiagnosticStage.SYSTEM_HEALTH,
            eventName = "RECORDING_STARTED",
            details = mapOf(
                "recordingSessionId" to sessionId,
                "startedAt" to nowFormatted,
                "note" to "Continuous diagnostic recording enabled by user"
            )
        )

        startObservationLoop(context.applicationContext)
        refreshStorageStats()
    }

    @Synchronized
    fun stopRecording(context: Context) {
        init(context)
        logEvent(
            context = context,
            stage = DiagnosticStage.SYSTEM_HEALTH,
            eventName = "RECORDING_STOPPED",
            details = mapOf("note" to "Diagnostic recording disabled by user. All records preserved.")
        )

        val updated = _recordingState.value.copy(
            isRecording = false,
            lastEventTimestampMs = System.currentTimeMillis(),
            lastEventName = "RECORDING_STOPPED"
        )
        _recordingState.value = updated
        getStorage(context).saveRecordingState(updated)

        observationJob?.cancel()
        observationJob = null
        refreshStorageStats()
    }

    fun isRecordingActive(): Boolean = _recordingState.value.isRecording

    @Synchronized
    fun startWindowRecording(
        context: Context,
        reason: String = "Operating window scheduled",
        busId: String = "",
        driverId: String = ""
    ) {
        init(context)
        if (_recordingState.value.isRecording) {
            // Already recording; attach session if busId provided
            if (busId.isNotBlank()) {
                startOrGetSession(context, busId, driverId)
            }
            return
        }

        val now = System.currentTimeMillis()
        val nowFormatted = Instant.ofEpochMilli(now).atZone(ScheduleHelper.KOLKATA_ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
        val sessionId = "win_" + UUID.randomUUID().toString().take(8)

        val newState = ContinuousRecordingState(
            isRecording = true,
            startedAtMs = now,
            startedAtFormatted = nowFormatted,
            recordingSessionId = sessionId,
            totalEventsRecorded = _recordingState.value.totalEventsRecorded,
            lastEventTimestampMs = now,
            lastEventName = "WINDOW_RECORDING_STARTED"
        )
        _recordingState.value = newState
        getStorage(context).saveRecordingState(newState)

        if (busId.isNotBlank()) {
            startOrGetSession(context, busId, driverId)
        }

        logEvent(
            context = context,
            stage = DiagnosticStage.SYSTEM_HEALTH,
            eventName = "WINDOW_RECORDING_STARTED",
            details = mapOf(
                "recordingSessionId" to sessionId,
                "startedAt" to nowFormatted,
                "note" to reason
            )
        )

        startObservationLoop(context.applicationContext)
        refreshStorageStats()
    }

    @Synchronized
    fun stopWindowRecording(context: Context, reason: String = "Operating window ended") {
        init(context)
        if (!_recordingState.value.isRecording) return

        logEvent(
            context = context,
            stage = DiagnosticStage.SYSTEM_HEALTH,
            eventName = "WINDOW_RECORDING_STOPPED",
            details = mapOf("note" to reason)
        )

        val updated = _recordingState.value.copy(
            isRecording = false,
            lastEventTimestampMs = System.currentTimeMillis(),
            lastEventName = "WINDOW_RECORDING_STOPPED"
        )
        _recordingState.value = updated
        getStorage(context).saveRecordingState(updated)

        observationJob?.cancel()
        observationJob = null
        refreshStorageStats()
    }

    // ==========================================
    // PROCESS & APPLICATION LIFECYCLE HOOKS
    // ==========================================

    @Synchronized
    fun onProcessStarted(context: Context) {
        init(context)
        val state = _recordingState.value
        val now = System.currentTimeMillis()

        if (state.isRecording) {
            val gapSeconds = if (state.lastEventTimestampMs > 0) (now - state.lastEventTimestampMs) / 1000 else 0
            val isRestart = state.lastEventTimestampMs > 0

            logEvent(
                context = context,
                stage = DiagnosticStage.PROCESS_LIFECYCLE,
                eventName = if (isRestart) "APP_PROCESS_RESTARTED" else "APP_PROCESS_STARTED",
                details = mapOf(
                    "gapSeconds" to gapSeconds.toString(),
                    "lastKnownEvent" to state.lastEventName,
                    "diagnosticNote" to if (isRestart) "Application process revived after interruption. Prior records preserved." else "Process cold start"
                )
            )
        }
    }

    @Synchronized
    fun onTaskRemoved(context: Context, busId: String, isTracking: Boolean) {
        init(context)
        logEvent(
            context = context,
            stage = DiagnosticStage.SERVICE_LIFECYCLE,
            eventName = "TASK_REMOVED_CALLBACK_RECEIVED",
            details = mapOf(
                "busId" to busId,
                "isTracking" to isTracking.toString(),
                "diagnosticNote" to "User swiped FleetX from Recent Apps. Android may terminate process or invoke START_STICKY recovery."
            ),
            isFailure = false
        )
    }

    @Synchronized
    fun onServiceDestroyed(
        context: Context,
        busId: String,
        wasTracking: Boolean,
        stopReason: String = "SYSTEM_OR_EXTERNAL_TERMINATION",
        stopCaller: String = "ANDROID_FRAMEWORK",
        activeDurationSeconds: Long = 0L,
        callbacksReceived: Int = 0,
        locationsDispatched: Int = 0
    ) {
        init(context)
        isServiceAlive = false
        val isUnexpected = wasTracking && stopReason == "SYSTEM_OR_EXTERNAL_TERMINATION"
        logEvent(
            context = context,
            stage = DiagnosticStage.SERVICE_LIFECYCLE,
            eventName = "SERVICE_DESTROYED",
            details = mapOf(
                "busId" to busId,
                "wasTracking" to wasTracking.toString(),
                "stopReason" to stopReason,
                "stopCaller" to stopCaller,
                "activeDurationSeconds" to activeDurationSeconds.toString(),
                "callbacksReceived" to callbacksReceived.toString(),
                "locationsDispatched" to locationsDispatched.toString(),
                "diagnosticNote" to when {
                    stopReason == "OPERATING_WINDOW_ENDED" -> "Operating window completed. Normal scheduled shutdown."
                    stopReason == "USER_MANUAL_STOP" -> "User explicitly stopped tracking."
                    stopReason == "COLLEGE_HOLIDAY" -> "Tracking stopped: college holiday detected."
                    isUnexpected -> "Service destroyed unexpectedly while tracking was active (no explicit stop intent received from app)."
                    else -> "Service destroyed with reason: $stopReason"
                }
            ),
            isFailure = isUnexpected,
            failureType = if (isUnexpected) FailureType.SERVICE_STOPPED_UNEXPECTEDLY else FailureType.NONE
        )
    }

    @Synchronized
    fun onBootReceived(context: Context, action: String) {
        init(context)
        logEvent(
            context = context,
            stage = DiagnosticStage.PROCESS_LIFECYCLE,
            eventName = "DEVICE_BOOT_RECEIVED",
            details = mapOf(
                "action" to action,
                "diagnosticNote" to "Device restart or package update event delivered to AutoTrackingBootReceiver"
            )
        )
    }

    fun onActivityResumed(context: Context, activityName: String) {
        activeAppVisibility = "FOREGROUND"
        if (isRecordingActive()) {
            logEvent(
                context = context,
                stage = DiagnosticStage.APP_LIFECYCLE,
                eventName = "APP_ACTIVITY_RESUMED",
                details = mapOf("activity" to activityName)
            )
        }
    }

    fun onActivityPaused(context: Context, activityName: String) {
        if (isRecordingActive()) {
            logEvent(
                context = context,
                stage = DiagnosticStage.APP_LIFECYCLE,
                eventName = "APP_ACTIVITY_PAUSED",
                details = mapOf("activity" to activityName)
            )
        }
    }

    fun onAppMovedToBackground(context: Context) {
        activeAppVisibility = "BACKGROUND"
        if (isRecordingActive()) {
            logEvent(
                context = context,
                stage = DiagnosticStage.APP_LIFECYCLE,
                eventName = "APP_MOVED_TO_BACKGROUND",
                details = mapOf("note" to "No UI activities visible; app executing in background")
            )
        }
    }

    // ==========================================
    // PIPELINE OBSERVATION HOOKS
    // ==========================================

    fun onAlarmScheduled(
        context: Context,
        timeStr: String,
        triggerMillis: Long,
        requestCode: Int,
        isExact: Boolean = true,
        alarmMethod: String = "setAlarmClock"
    ) {
        val delayMs = triggerMillis - System.currentTimeMillis()
        val inMinutes = delayMs / 60000
        val targetFormatted = java.time.Instant.ofEpochMilli(triggerMillis)
            .atZone(com.college.bustracker.util.ScheduleHelper.KOLKATA_ZONE)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        logEvent(
            context = context,
            stage = DiagnosticStage.ALARM,
            eventName = "ALARM_SCHEDULED",
            details = mapOf(
                "time" to timeStr,
                "requestCode" to requestCode.toString(),
                "delayMinutes" to inMinutes.toString(),
                "targetTime" to targetFormatted,
                "isExact" to isExact.toString(),
                "alarmMethod" to alarmMethod
            )
        )
    }

    fun onAlarmReceived(
        context: Context,
        action: String = "UNKNOWN_ACTION",
        isExpectedTime: Boolean = true,
        delayMs: Long = 0L,
        expectedTriggerMillis: Long = 0L,
        requestCode: Int = 0,
        timeStr: String = ""
    ) {
        val delay = if (expectedTriggerMillis > 0) System.currentTimeMillis() - expectedTriggerMillis else delayMs
        val isDelayed = delay > 180_000 // 3 minutes late

        logEvent(
            context = context,
            stage = DiagnosticStage.ALARM,
            eventName = if (isDelayed) "ALARM_DELIVERY_DELAYED" else "ALARM_RECEIVED",
            details = mapOf(
                "action" to action,
                "delaySeconds" to (delay / 1000).toString(),
                "requestCode" to requestCode.toString(),
                "scheduledTime" to timeStr
            ),
            isFailure = isDelayed,
            failureType = if (isDelayed) FailureType.ALARM_FAILURE else FailureType.NONE
        )
    }

    fun onPermissionsChecked(
        context: Context,
        hasFine: Boolean,
        hasCoarse: Boolean,
        hasBackground: Boolean,
        locationEnabled: Boolean
    ) {
        onLocationPermissionCheck(
            context = context,
            fineGranted = hasFine,
            coarseGranted = hasCoarse,
            bgGranted = hasBackground,
            notifGranted = true
        )
        onGpsProviderState(context, locationEnabled)
    }

    fun onTrackingPreferenceChanged(context: Context, enabled: Boolean, busId: String) {
        logEvent(
            context = context,
            stage = DiagnosticStage.TRACKING_PREFERENCE,
            eventName = "TRACKING_PREFERENCE_CHANGED",
            details = mapOf(
                "autoTrackingEnabled" to enabled.toString(),
                "assignedBus" to busId
            )
        )
    }

    fun onLocationPermissionCheck(context: Context, fineGranted: Boolean, coarseGranted: Boolean, bgGranted: Boolean, notifGranted: Boolean) {
        val allGranted = fineGranted && coarseGranted && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || bgGranted)
        logEvent(
            context = context,
            stage = DiagnosticStage.PERMISSIONS,
            eventName = if (allGranted) "LOCATION_PERMISSION_GRANTED" else "LOCATION_PERMISSION_MISSING",
            details = mapOf(
                "fineLocation" to fineGranted.toString(),
                "coarseLocation" to coarseGranted.toString(),
                "backgroundLocation" to bgGranted.toString(),
                "notifications" to notifGranted.toString()
            ),
            isFailure = !allGranted,
            failureType = if (!allGranted) FailureType.PERMISSION_FAILURE else FailureType.NONE
        )
    }

    fun onGpsProviderState(context: Context, isEnabled: Boolean) {
        logEvent(
            context = context,
            stage = DiagnosticStage.GPS_PROVIDER,
            eventName = if (isEnabled) "GPS_PROVIDER_ENABLED" else "GPS_PROVIDER_DISABLED",
            details = mapOf("locationServices" to if (isEnabled) "ACTIVE" else "OFF"),
            isFailure = !isEnabled,
            failureType = if (!isEnabled) FailureType.GPS_PROVIDER_FAILURE else FailureType.NONE
        )
    }

    fun onServiceStartRequested(
        context: Context,
        busId: String,
        driverId: String = "",
        mode: String = "MANUAL"
    ) {
        logEvent(
            context = context,
            stage = DiagnosticStage.SERVICE_START,
            eventName = "FOREGROUND_SERVICE_START_REQUESTED",
            details = mapOf("busId" to busId, "driverId" to driverId, "mode" to mode)
        )
    }

    fun onServiceStarted(context: Context, busId: String) {
        isServiceAlive = true
        logEvent(
            context = context,
            stage = DiagnosticStage.SERVICE_START,
            eventName = "FOREGROUND_SERVICE_STARTED",
            details = mapOf("busId" to busId, "state" to "FOREGROUND_ACTIVE")
        )
    }

    fun onServiceStartFailed(
        context: Context,
        exception: Exception? = null,
        stage: String = "SERVICE_START",
        errorType: String = exception?.javaClass?.simpleName ?: "Exception",
        message: String = exception?.message ?: "startForegroundService restricted"
    ) {
        isServiceAlive = false
        lastKnownError = "$errorType: $message"
        val isFgNotAllowed = exception is android.app.ForegroundServiceStartNotAllowedException || errorType.contains("ForegroundServiceStartNotAllowed")

        logEvent(
            context = context,
            stage = DiagnosticStage.SERVICE_START,
            eventName = "FOREGROUND_SERVICE_START_FAILED",
            details = mapOf(
                "exception" to errorType,
                "stage" to stage,
                "errorReason" to message,
                "isForegroundStartNotAllowed" to isFgNotAllowed.toString()
            ),
            isFailure = true,
            failureType = if (isFgNotAllowed) FailureType.BACKGROUND_RESTRICTION_FAILURE else FailureType.FOREGROUND_SERVICE_FAILURE
        )
    }

    fun onServiceStopped(context: Context, reason: String) {
        isServiceAlive = false
        logEvent(
            context = context,
            stage = DiagnosticStage.SERVICE_LIFECYCLE,
            eventName = "FOREGROUND_SERVICE_STOPPED",
            details = mapOf("reason" to reason)
        )
    }

    fun onLocationRequestRegistered(
        context: Context,
        providerOrClient: String = "FusedLocationProviderClient",
        intervalMs: Long = 5000L,
        fastestIntervalMs: Long = 3000L
    ) {
        logEvent(
            context = context,
            stage = DiagnosticStage.LOCATION_REQUEST,
            eventName = "LOCATION_REQUEST_REGISTERED",
            details = mapOf(
                "provider" to providerOrClient,
                "intervalMs" to intervalMs.toString(),
                "fastestIntervalMs" to fastestIntervalMs.toString()
            )
        )
    }

    fun onLocationCallbackReceived(
        context: Context,
        provider: String? = "unknown",
        accuracyMeters: Float = 0f,
        locationAgeMs: Long = 0L,
        ageMs: Long = locationAgeMs
    ) {
        locationCallbackCount++
        val now = System.currentTimeMillis()
        val intervalSinceLastFix = if (lastLocationTimestamp > 0) now - lastLocationTimestamp else 0
        lastLocationTimestamp = now
        val effectiveAge = if (locationAgeMs > 0) locationAgeMs else ageMs

        logEvent(
            context = context,
            stage = DiagnosticStage.LOCATION_CALLBACK,
            eventName = "LOCATION_CALLBACK_RECEIVED",
            details = mapOf(
                "provider" to (provider ?: "unknown"),
                "accuracyMeters" to String.format(Locale.US, "%.1f", accuracyMeters),
                "locationAgeMs" to effectiveAge.toString(),
                "intervalMs" to intervalSinceLastFix.toString(),
                "totalFixes" to locationCallbackCount.toString()
            )
        )
    }

    fun onFirebaseState(context: Context, isInitialized: Boolean, authUidAvailable: Boolean) {
        logEvent(
            context = context,
            stage = DiagnosticStage.FIREBASE_INIT,
            eventName = if (isInitialized) "FIREBASE_INITIALIZATION_SUCCEEDED" else "FIREBASE_INITIALIZATION_FAILED",
            details = mapOf(
                "initialized" to isInitialized.toString(),
                "authUidAvailable" to authUidAvailable.toString()
            ),
            isFailure = !isInitialized,
            failureType = if (!isInitialized) FailureType.FIREBASE_INITIALIZATION_FAILURE else FailureType.NONE
        )
    }

    fun onRtdbWriteAttempted(context: Context, busId: String) {
        logEvent(
            context = context,
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_WRITE_ATTEMPTED",
            details = mapOf("busId" to busId)
        )
    }

    fun onRtdbWriteSuccess(context: Context, busId: String, latencyMs: Long) {
        rtdbWriteCount++
        lastRtdbWriteLatencyMs = latencyMs
        lastRtdbWriteSuccessTimestamp = System.currentTimeMillis()
        lastKnownError = null

        logEvent(
            context = context,
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_WRITE_SUCCEEDED",
            details = mapOf(
                "busId" to busId,
                "latencyMs" to latencyMs.toString(),
                "totalWrites" to rtdbWriteCount.toString()
            )
        )
    }

    fun onRtdbWriteFailed(
        context: Context,
        busId: String,
        errorCode: Int,
        errorMessage: String,
        latencyMs: Long = 0L,
        exceptionClass: String? = null,
        hasAuthUser: Boolean = false,
        isNetworkConnected: Boolean = true,
        isRetryable: Boolean = false,
        attempt: Int = 1,
        hasOperatorEpoch: Boolean = false
    ) {
        val sanitizedError = errorMessage.replace(Regex("[a-zA-Z0-9_-]{28,}"), "[REDACTED_ID]")
        lastKnownError = "RTDB Error $errorCode: $sanitizedError"
        val errorCategory = when (errorCode) {
            -3 -> "PERMISSION_DENIED"
            -4 -> "DISCONNECTED"
            -24 -> "NETWORK_ERROR"
            -1 -> "OPERATION_FAILED"
            -6 -> "EXPIRED_TOKEN"
            -7 -> "INVALID_TOKEN"
            -10 -> "UNAVAILABLE"
            -25 -> "WRITE_CANCELED"
            else -> exceptionClass ?: "UNKNOWN_ERROR"
        }
        val isAuthFailure = errorCode == -6 || errorCode == -7 || (!hasAuthUser && errorCode == -3)

        logEvent(
            context = context,
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_WRITE_FAILED",
            details = mapOf(
                "busId" to busId,
                "errorCode" to errorCode.toString(),
                "errorReason" to sanitizedError,
                "errorCategory" to errorCategory,
                "durationMs" to latencyMs.toString(),
                "exceptionClass" to (exceptionClass ?: "FirebaseDatabaseError"),
                "hasAuthUser" to hasAuthUser.toString(),
                "isNetworkConnected" to isNetworkConnected.toString(),
                "isRetryable" to isRetryable.toString(),
                "attempt" to attempt.toString(),
                "hasOperatorEpoch" to hasOperatorEpoch.toString(),
                "diagnosticNote" to when {
                    isAuthFailure -> "Authentication/authorization issue: driver token invalid or missing"
                    errorCode == -3 -> "RTDB Security Rules or validation rejected write (check assignedBusId or epoch)"
                    !isNetworkConnected -> "Network disconnected: write buffered or dropped"
                    isRetryable -> "Transient network error: queued for backoff retry"
                    else -> "RTDB write rejected"
                }
            ),
            isFailure = true,
            failureType = FailureType.RTDB_WRITE_FAILURE
        )
    }

    fun onRtdbRetryScheduled(context: Context, busId: String, attempt: Int, delayMs: Long, reason: String) {
        logEvent(
            context = context,
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_RETRY_SCHEDULED",
            details = mapOf(
                "busId" to busId,
                "attempt" to attempt.toString(),
                "delayMs" to delayMs.toString(),
                "reason" to reason
            )
        )
    }

    fun onRtdbRetrySuccess(context: Context, busId: String, attempt: Int, latencyMs: Long) {
        rtdbWriteCount++
        lastRtdbWriteLatencyMs = latencyMs
        lastRtdbWriteSuccessTimestamp = System.currentTimeMillis()
        lastKnownError = null
        logEvent(
            context = context,
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_RETRY_SUCCEEDED",
            details = mapOf(
                "busId" to busId,
                "attempt" to attempt.toString(),
                "latencyMs" to latencyMs.toString()
            )
        )
    }

    fun onRtdbRetryExhausted(context: Context, busId: String, totalAttempts: Int, lastErrorCode: Int, lastErrorMessage: String) {
        val sanitizedError = lastErrorMessage.replace(Regex("[a-zA-Z0-9_-]{28,}"), "[REDACTED_ID]")
        logEvent(
            context = context,
            stage = DiagnosticStage.RTDB_WRITE,
            eventName = "RTDB_RETRY_EXHAUSTED",
            details = mapOf(
                "busId" to busId,
                "totalAttempts" to totalAttempts.toString(),
                "lastErrorCode" to lastErrorCode.toString(),
                "lastErrorMessage" to sanitizedError,
                "diagnosticNote" to "Max retry limit reached ($totalAttempts attempts). Write buffered for network recovery."
            ),
            isFailure = true,
            failureType = FailureType.RTDB_WRITE_FAILURE
        )
    }

    // ==========================================
    // CORE EVENT LOGGING (DURABLE & ATOMIC)
    // ==========================================

    @Synchronized
    fun logEvent(
        context: Context,
        stage: DiagnosticStage,
        eventName: String,
        details: Map<String, String> = emptyMap(),
        isFailure: Boolean = false,
        failureType: FailureType = FailureType.NONE
    ) {
        init(context)
        val now = System.currentTimeMillis()
        val timeStr = LocalTime.now(ScheduleHelper.KOLKATA_ZONE).format(timeFormatter)
        val (winName, _, _) = resolveActiveWindow()
        val isWindowActive = isOperatingWindowActive(context)

        val devState = captureDeviceState(context)

        val event = DiagnosticEvent(
            timestampMs = now,
            timeFormatted = timeStr,
            stage = stage,
            eventName = eventName,
            details = details,
            isFailure = isFailure,
            failureType = failureType,
            recordingSessionId = _recordingState.value.recordingSessionId,
            operatingWindow = winName,
            isWindowActive = isWindowActive,
            appVisibility = activeAppVisibility,
            screenInteractive = devState.isInteractive,
            trackingPreference = AutoTrackingScheduler.isAutoTrackingEnabled(context),
            backgroundLocationPermission = devState.backgroundLocationGranted,
            gpsEnabled = devState.locationServicesEnabled,
            errorType = if (isFailure) failureType.name else null,
            errorStage = if (isFailure) stage.name else null
        )

        _latestEvent.value = event

        // 1. If continuous recording is ON, write to daily JSONL
        if (_recordingState.value.isRecording) {
            val store = getStorage(context)
            store.appendDailyEvent(event)

            val updatedState = _recordingState.value.copy(
                totalEventsRecorded = _recordingState.value.totalEventsRecorded + 1,
                lastEventTimestampMs = now,
                lastEventName = eventName
            )
            _recordingState.value = updatedState
            store.saveRecordingState(updatedState)
        }

        // 2. Also forward to operating window session if active
        val session = activeSession
        if (session != null && session.sessionStatus == SessionStatus.RUNNING) {
            session.events.add(event)
            updatePipelineStatus(session, stage, isFailure)
            if (isFailure && failureType != FailureType.NONE) {
                if (session.primaryFailure == FailureType.NONE) {
                    session.primaryFailure = failureType
                    session.sessionStatus = SessionStatus.FAILED
                    session.summaryMessage = "Failure at ${stage.displayName}: ${failureType.userReason}"
                } else if (!session.secondaryFailures.contains(failureType)) {
                    session.secondaryFailures.add(failureType)
                }
            }
            val rep = session.toReport()
            _currentReport.value = rep
            getStorage(context).saveReport(rep)
        }

        refreshStorageStats()
    }

    // ==========================================
    // OPERATING WINDOW SESSIONS
    // ==========================================

    @Synchronized
    fun startOrGetSession(
        context: Context,
        busId: String = "",
        driverId: String = "",
        customWindowName: String? = null
    ): GpsDiagnosticReport {
        init(context)
        val now = System.currentTimeMillis()
        val (defWinName, defStart, defEnd) = resolveActiveWindow()
        val winName = customWindowName ?: defWinName
        val todayStr = LocalDate.now(ScheduleHelper.KOLKATA_ZONE).format(dateFormatter)

        val current = activeSession
        if (current != null && current.date == todayStr && current.windowName == winName && current.sessionStatus == SessionStatus.RUNNING) {
            if (busId.isNotBlank()) current.busId = busId
            if (driverId.isNotBlank()) current.driverIdMasked = maskDriverId(driverId)
            return current.toReport()
        }

        val sessionId = UUID.randomUUID().toString().take(8)
        val session = MutableSessionState(
            sessionId = sessionId,
            date = todayStr,
            windowName = winName,
            windowStart = defStart,
            windowEnd = defEnd,
            busId = busId.ifBlank { "UNKNOWN_BUS" },
            driverIdMasked = maskDriverId(driverId),
            createdAtMs = now
        )
        session.deviceState = captureDeviceState(context)
        activeSession = session
        _isSessionActive.value = true

        locationCallbackCount = 0
        rtdbWriteCount = 0
        lastLocationTimestamp = 0L
        lastRtdbWriteLatencyMs = 0L
        isServiceAlive = false

        logEvent(
            context = context,
            stage = DiagnosticStage.OPERATING_WINDOW,
            eventName = "OPERATING_WINDOW_STARTED",
            details = mapOf(
                "sessionId" to sessionId,
                "window" to winName,
                "range" to "$defStart-$defEnd",
                "bus" to session.busId
            )
        )

        val report = session.toReport()
        _currentReport.value = report
        getStorage(context).saveReport(report)
        return report
    }

    @Synchronized
    fun completeSession(context: Context, reason: String = "Operating window ended") {
        val session = activeSession ?: return
        val now = System.currentTimeMillis()
        session.completedAtMs = now

        if (session.sessionStatus == SessionStatus.RUNNING) {
            session.sessionStatus = if (locationCallbackCount > 0 && rtdbWriteCount > 0) {
                SessionStatus.COMPLETED
            } else if (locationCallbackCount > 0) {
                SessionStatus.PARTIAL
            } else {
                SessionStatus.FAILED
            }
            if (session.sessionStatus == SessionStatus.COMPLETED) {
                session.summaryMessage = "Tracking verified: $locationCallbackCount fixes, $rtdbWriteCount RTDB updates."
            } else {
                session.summaryMessage = reason
            }
        }

        logEvent(
            context = context,
            stage = DiagnosticStage.OPERATING_WINDOW,
            eventName = "OPERATING_WINDOW_ENDED",
            details = mapOf(
                "status" to session.sessionStatus.name,
                "reason" to reason,
                "fixes" to locationCallbackCount.toString(),
                "rtdbWrites" to rtdbWriteCount.toString()
            )
        )

        val report = session.toReport()
        _currentReport.value = report
        _isSessionActive.value = false
        getStorage(context).saveReport(report)
        activeSession = null
    }

    // ==========================================
    // BACKGROUND OBSERVATION & HEALTH CHECK LOOP
    // ==========================================

    private fun startObservationLoop(appContext: Context) {
        observationJob?.cancel()
        observationJob = scope.launch {
            while (_recordingState.value.isRecording) {
                delay(60_000L) // 60 seconds interval
                try {
                    performPeriodicHealthCheck(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Health check error: ${e.message}")
                }
            }
        }
    }

    private fun performPeriodicHealthCheck(context: Context) {
        if (!_recordingState.value.isRecording) return

        val now = System.currentTimeMillis()
        val isWindow = isOperatingWindowActive(context)
        val isTrackingExpected = isWindow && AutoTrackingScheduler.isAutoTrackingEnabled(context)

        val timeSinceFixSec = if (lastLocationTimestamp > 0) (now - lastLocationTimestamp) / 1000 else -1L
        val timeSinceRtdbSec = if (lastRtdbWriteSuccessTimestamp > 0) (now - lastRtdbWriteSuccessTimestamp) / 1000 else -1L

        val details = mutableMapOf(
            "serviceAlive" to isServiceAlive.toString(),
            "isOperatingWindow" to isWindow.toString(),
            "trackingExpected" to isTrackingExpected.toString(),
            "fixesInWindow" to locationCallbackCount.toString(),
            "rtdbWritesInWindow" to rtdbWriteCount.toString()
        )

        if (timeSinceFixSec >= 0) details["secondsSinceLastFix"] = timeSinceFixSec.toString()
        if (timeSinceRtdbSec >= 0) details["secondsSinceLastRtdbWrite"] = timeSinceRtdbSec.toString()
        lastKnownError?.let { details["lastError"] = it }

        var isFailure = false
        var failureType = FailureType.NONE

        if (isTrackingExpected && isServiceAlive) {
            if (timeSinceFixSec > 120) {
                isFailure = true
                failureType = FailureType.LOCATION_CALLBACK_FAILURE
                details["healthAlert"] = "No location fixes received in over 2 minutes while tracking is expected."
            }
        }

        logEvent(
            context = context,
            stage = DiagnosticStage.SYSTEM_HEALTH,
            eventName = if (isFailure) "NO_LOCATION_CALLBACK_DETECTED" else "SERVICE_HEALTH_CHECK",
            details = details,
            isFailure = isFailure,
            failureType = failureType
        )

        // Record heartbeat to active operating session if present
        activeSession?.let { sess ->
            val timeStr = LocalTime.now(ScheduleHelper.KOLKATA_ZONE).format(timeFormatter)
            val hb = DiagnosticHeartbeat(
                timestampMs = now,
                timeFormatted = timeStr,
                serviceAlive = isServiceAlive,
                locationCallbacksCount = locationCallbackCount,
                lastLocationAgeSeconds = if (timeSinceFixSec >= 0) timeSinceFixSec else 0L,
                rtdbWritesCount = rtdbWriteCount,
                lastRtdbLatencyMs = lastRtdbWriteLatencyMs
            )
            sess.heartbeats.add(hb)
            val rep = sess.toReport()
            _currentReport.value = rep
            getStorage(context).saveReport(rep)
        }
    }

    // ==========================================
    // AUTOMATIC ROOT-CAUSE SYNTHESIS
    // ==========================================

    fun generateRootCauseSummary(): ContinuousRootCauseSummary {
        val store = storage ?: return ContinuousRootCauseSummary(
            primaryObservation = "Diagnostic recorder uninitialized",
            lastConfirmedHealthyEvent = "None",
            lastSuccessfulRtdbWrite = "None",
            firstObservedFailure = "None",
            serviceState = "Unknown",
            possibleCauses = "No diagnostic events have been recorded yet.",
            nextEvidenceNeeded = "Start diagnostic recording and reproduce the issue."
        )

        val events = store.getTodayEvents().ifEmpty { store.getAllDailyEvents() }
        if (events.isEmpty()) {
            return ContinuousRootCauseSummary(
                primaryObservation = "No diagnostic events recorded yet today.",
                lastConfirmedHealthyEvent = "None recorded",
                lastSuccessfulRtdbWrite = "None recorded",
                firstObservedFailure = "None recorded",
                serviceState = if (isServiceAlive) "Foreground Service Running" else "Inactive",
                possibleCauses = "Waiting for driver activity or scheduled operating window.",
                nextEvidenceNeeded = "Turn recording ON and test the tracking flow."
            )
        }

        var lastHealthy = "None recorded"
        var lastRtdbWrite = "None recorded"
        var firstFailure = "None detected"
        var foundFailure = false
        var taskRemovedEvent: DiagnosticEvent? = null
        var serviceDestroyedEvent: DiagnosticEvent? = null
        var processRestartedEvent: DiagnosticEvent? = null
        var fgServiceFailedEvent: DiagnosticEvent? = null

        for (ev in events) {
            if (ev.eventName == "LOCATION_CALLBACK_RECEIVED" || ev.eventName == "RTDB_WRITE_SUCCEEDED") {
                lastHealthy = "${ev.timeFormatted} ${ev.eventName}"
            }
            if (ev.eventName == "RTDB_WRITE_SUCCEEDED") {
                lastRtdbWrite = "${ev.timeFormatted} latency=${ev.details["latencyMs"] ?: "N/A"}ms"
            }
            if (ev.eventName == "TASK_REMOVED_CALLBACK_RECEIVED") {
                taskRemovedEvent = ev
            }
            if (ev.eventName == "SERVICE_DESTROYED") {
                serviceDestroyedEvent = ev
            }
            if (ev.eventName == "APP_PROCESS_RESTARTED") {
                processRestartedEvent = ev
            }
            if (ev.eventName == "FOREGROUND_SERVICE_START_FAILED") {
                fgServiceFailedEvent = ev
            }
            if (ev.isFailure && !foundFailure) {
                firstFailure = "${ev.timeFormatted} ${ev.eventName} (${ev.failureType.userReason})"
                foundFailure = true
            }
        }

        // Evaluate root cause based on factual observations
        val primaryObs: String
        val causes: String
        val nextEvidence: String

        val rom = DeviceRomDetector.getRomInfo()

        when {
            taskRemovedEvent != null && processRestartedEvent != null -> {
                primaryObs = "FleetX was removed from Recent Apps at ${taskRemovedEvent.timeFormatted}. The process was subsequently terminated by Android and revived at ${processRestartedEvent.timeFormatted} (gap: ${processRestartedEvent.details["gapSeconds"] ?: "unknown"}s)."
                causes = "${rom.displayName} (${rom.romVersion}) killed the application task when swiped from Recents. Standard background services in the same process are halted upon task removal unless protected or revived via AlarmManager. Recommended fix: ${rom.advice}"
                nextEvidence = "Verify whether AlarmManager resurrection or persistent Foreground Service keeps tracking alive."
            }
            taskRemovedEvent != null && serviceDestroyedEvent != null -> {
                primaryObs = "Service onDestroy() executed immediately following task removal at ${taskRemovedEvent.timeFormatted}."
                causes = "${rom.displayName} tore down the foreground service during task dismissal, actively calling stopTrackingSession() and clearing notification. Recommended fix: ${rom.advice}"
                nextEvidence = "Inspect onTaskRemoved handling and verify if START_STICKY or alarms recover tracking."
            }
            taskRemovedEvent != null -> {
                primaryObs = "TASK_REMOVED_CALLBACK_RECEIVED recorded at ${taskRemovedEvent.timeFormatted}. No further events recorded until next app launch."
                causes = "The host process was abruptly killed by ${rom.displayName} shortly after onTaskRemoved before any further diagnostics could execute. Recommended fix: ${rom.advice}"
                nextEvidence = "Check device logcat for am_kill / lowmemorykiller entries after swiping from Recents."
            }
            fgServiceFailedEvent != null -> {
                primaryObs = "Foreground service failed to start: ${fgServiceFailedEvent.details["errorReason"] ?: "Unknown error"}"
                causes = "${rom.displayName} background start restrictions (Android 12+ ForegroundServiceStartNotAllowedException) or missing background location permission."
                nextEvidence = "Verify battery optimization exemption (Unrestricted) and background location permission ('Allow all the time')."
            }
            foundFailure -> {
                primaryObs = "Pipeline failed at $firstFailure."
                causes = "Identified failure during background execution pipeline."
                nextEvidence = "Review device state snapshot and permission settings."
            }
            else -> {
                primaryObs = "No failures detected. Pipeline is healthy or waiting for operating window."
                causes = "All recorded events succeeded within operational parameters."
                nextEvidence = "Continue recording during the next active driving session."
            }
        }

        val servState = when {
            isServiceAlive -> "FOREGROUND_SERVICE_RUNNING (Active)"
            serviceDestroyedEvent != null -> "SERVICE_DESTROYED at ${serviceDestroyedEvent.timeFormatted}"
            taskRemovedEvent != null -> "TASK_REMOVED at ${taskRemovedEvent.timeFormatted} (Process Terminated)"
            else -> "INACTIVE / STOPPED"
        }

        return ContinuousRootCauseSummary(
            primaryObservation = primaryObs,
            lastConfirmedHealthyEvent = lastHealthy,
            lastSuccessfulRtdbWrite = lastRtdbWrite,
            firstObservedFailure = firstFailure,
            serviceState = servState,
            possibleCauses = causes,
            nextEvidenceNeeded = nextEvidence
        )
    }

    // ==========================================
    // DEVICE STATE & ENVIRONMENT UTILITIES
    // ==========================================

    fun captureDeviceState(context: Context): DeviceStateSnapshot {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: false
        val isPowerSave = pm?.isPowerSaveMode ?: false
        val isIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm?.isDeviceIdleMode ?: false else false
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true

        val fineLoc = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLoc = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bgLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locEnabled = locManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val netCap = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isOnline = netCap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isAppInFg = activeAppVisibility == "FOREGROUND"

        return DeviceStateSnapshot(
            isInteractive = isInteractive,
            isPowerSaveMode = isPowerSave,
            isIgnoringBatteryOptimizations = isIgnoringBattery,
            isDeviceIdleMode = isIdle,
            isAppInForeground = isAppInFg,
            fineLocationGranted = fineLoc,
            coarseLocationGranted = coarseLoc,
            backgroundLocationGranted = bgLoc,
            notificationGranted = notifGranted,
            locationServicesEnabled = locEnabled,
            networkConnected = isOnline,
            androidVersion = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL
        )
    }

    private fun resolveActiveWindow(): Triple<String, String, String> {
        val schedule = OperatingScheduleService.getSchedule()
        val now = LocalTime.now(ScheduleHelper.KOLKATA_ZONE)
        return when {
            ScheduleHelper.isTimeInWindow(now, schedule.morning) ->
                Triple("Morning", schedule.morning.startTime, schedule.morning.endTime)
            ScheduleHelper.isTimeInWindow(now, schedule.evening) ->
                Triple("Evening", schedule.evening.startTime, schedule.evening.endTime)
            now.isBefore(LocalTime.of(12, 0)) ->
                Triple("Morning", schedule.morning.startTime, schedule.morning.endTime)
            else ->
                Triple("Evening", schedule.evening.startTime, schedule.evening.endTime)
        }
    }

    private fun isOperatingWindowActive(context: Context): Boolean {
        val schedule = AutoTrackingScheduler.getSavedSchedule(context)
        val isWorkingDay = com.college.bustracker.util.CollegeCalendarService.isWorkingDay()
        return ScheduleHelper.isWithinOperatingWindow(schedule) && isWorkingDay
    }

    private fun maskDriverId(id: String): String {
        if (id.isBlank()) return "UNASSIGNED"
        if (id.length <= 4) return "****"
        return id.take(2) + "****" + id.takeLast(2)
    }

    private fun updatePipelineStatus(session: MutableSessionState, stage: DiagnosticStage, isFailure: Boolean) {
        val cur = session.pipeline
        session.pipeline = when (stage) {
            DiagnosticStage.ALARM -> cur.copy(alarm = if (isFailure) PipelineStepStatus.FAIL else PipelineStepStatus.PASS)
            DiagnosticStage.PERMISSIONS -> cur.copy(permissions = if (isFailure) PipelineStepStatus.FAIL else PipelineStepStatus.PASS)
            DiagnosticStage.GPS_PROVIDER -> cur.copy(gpsProvider = if (isFailure) PipelineStepStatus.FAIL else PipelineStepStatus.PASS)
            DiagnosticStage.SERVICE_START -> cur.copy(serviceStart = if (isFailure) PipelineStepStatus.FAIL else PipelineStepStatus.PASS)
            DiagnosticStage.LOCATION_CALLBACK -> cur.copy(locationCallback = if (isFailure) PipelineStepStatus.FAIL else PipelineStepStatus.PASS)
            DiagnosticStage.FIREBASE_INIT -> cur.copy(firebaseInit = if (isFailure) PipelineStepStatus.FAIL else PipelineStepStatus.PASS)
            DiagnosticStage.RTDB_WRITE -> cur.copy(rtdbWrite = if (isFailure) PipelineStepStatus.FAIL else PipelineStepStatus.PASS)
            else -> cur
        }
    }

    private class MutableSessionState(
        val sessionId: String,
        val date: String,
        val windowName: String,
        val windowStart: String,
        val windowEnd: String,
        var busId: String,
        var driverIdMasked: String,
        val createdAtMs: Long
    ) {
        var sessionStatus = SessionStatus.RUNNING
        var primaryFailure = FailureType.NONE
        val secondaryFailures = mutableListOf<FailureType>()
        var pipeline = PipelineStatus()
        val events = mutableListOf<DiagnosticEvent>()
        val heartbeats = mutableListOf<DiagnosticHeartbeat>()
        var deviceState: DeviceStateSnapshot? = null
        var summaryMessage: String = "Diagnostic session in progress"
        var completedAtMs: Long? = null

        fun toReport(): GpsDiagnosticReport {
            return GpsDiagnosticReport(
                sessionId = sessionId,
                date = date,
                windowName = windowName,
                windowStart = windowStart,
                windowEnd = windowEnd,
                busId = busId,
                driverIdMasked = driverIdMasked,
                sessionStatus = sessionStatus,
                primaryFailure = primaryFailure,
                secondaryFailures = secondaryFailures.toList(),
                pipeline = pipeline,
                events = events.toList(),
                heartbeats = heartbeats.toList(),
                deviceState = deviceState,
                summaryMessage = summaryMessage,
                createdAtMs = createdAtMs,
                completedAtMs = completedAtMs
            )
        }
    }
}
