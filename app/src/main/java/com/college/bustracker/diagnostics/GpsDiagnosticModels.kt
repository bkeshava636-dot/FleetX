package com.college.bustracker.diagnostics

/**
 * Diagnostic stages along the background GPS lifecycle.
 */
enum class DiagnosticStage(val displayName: String) {
    OPERATING_WINDOW("Operating Window"),
    ALARM("Schedule Alarm"),
    DRIVER_ASSIGNMENT("Driver Assignment"),
    TRACKING_PREFERENCE("Tracking Preference"),
    PERMISSIONS("Permissions"),
    GPS_PROVIDER("GPS Provider"),
    ANDROID_RESTRICTIONS("Android Restrictions"),
    SERVICE_START("Service Start"),
    SERVICE_LIFECYCLE("Service Lifecycle"),
    LOCATION_REQUEST("Location Request"),
    LOCATION_CALLBACK("Location Callback"),
    FIREBASE_INIT("Firebase Init"),
    RTDB_WRITE("RTDB Write"),
    HEARTBEAT("Heartbeat Tracking"),
    APP_LIFECYCLE("App Lifecycle"),
    PROCESS_LIFECYCLE("Process Lifecycle"),
    SYSTEM_HEALTH("System Health")
}

/**
 * Root cause failure classification.
 */
enum class FailureType(val userReason: String) {
    NONE("No failure detected"),
    OUTSIDE_OPERATING_WINDOW("Outside operating window or college holiday"),
    ALARM_FAILURE("Scheduled alarm did not fire or was delayed significantly"),
    DRIVER_ASSIGNMENT_MISSING("No bus or driver identity assigned for tracking"),
    TRACKING_PREFERENCE_DISABLED("Automatic tracking is disabled in driver settings"),
    PERMISSION_FAILURE("Required location or notification permission missing"),
    GPS_PROVIDER_FAILURE("Device Location Services (GPS) are turned off"),
    BACKGROUND_RESTRICTION_FAILURE("Android background execution or battery restrictions blocked start"),
    FOREGROUND_SERVICE_FAILURE("Foreground service failed to start from background"),
    SERVICE_STOPPED_UNEXPECTEDLY("Service was stopped or killed unexpectedly"),
    LOCATION_CALLBACK_FAILURE("Location request registered but no location fixes received"),
    FIREBASE_INITIALIZATION_FAILURE("Firebase was not initialized when location arrived"),
    RTDB_WRITE_FAILURE("RTDB database write failed or was denied"),
    NETWORK_FAILURE("Network disconnected and could not sync to RTDB"),
    HEARTBEAT_TIMEOUT("GPS or database writes went silent during operating window"),
    PROCESS_TERMINATED("Process terminated while tracking was active"),
    TASK_REMOVED_KILL("App cleared from Recent Apps by user"),
    BATTERY_RESTRICTION("Battery optimization restricted background tracking"),
    UNKNOWN("Unknown failure")
}

enum class SessionStatus {
    WAITING,
    RUNNING,
    COMPLETED,
    FAILED,
    PARTIAL
}

enum class PipelineStepStatus {
    PASS,
    FAIL,
    WARNING,
    NOT_REACHED
}

/**
 * Single diagnostic timestamped event.
 * Never stores latitude/longitude, credentials, or personal information.
 */
data class DiagnosticEvent(
    val timestampMs: Long,
    val timeFormatted: String,
    val stage: DiagnosticStage,
    val eventName: String,
    val details: Map<String, String> = emptyMap(),
    val isFailure: Boolean = false,
    val failureType: FailureType = FailureType.NONE,
    val recordingSessionId: String = "",
    val operatingWindow: String = "",
    val isWindowActive: Boolean = false,
    val appVisibility: String = "UNKNOWN", // "FOREGROUND", "BACKGROUND", "DESTROYED"
    val screenInteractive: Boolean = false,
    val trackingPreference: Boolean = false,
    val backgroundLocationPermission: Boolean = false,
    val gpsEnabled: Boolean = false,
    val errorType: String? = null,
    val errorStage: String? = null
)

/**
 * Snapshot of Android device background environment at point in time.
 */
data class DeviceStateSnapshot(
    val isInteractive: Boolean,
    val isPowerSaveMode: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val isDeviceIdleMode: Boolean, // Doze mode
    val isAppInForeground: Boolean,
    val fineLocationGranted: Boolean,
    val coarseLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val notificationGranted: Boolean,
    val locationServicesEnabled: Boolean,
    val networkConnected: Boolean,
    val androidVersion: Int,
    val deviceManufacturer: String,
    val deviceModel: String
)

/**
 * Periodic local heartbeat event.
 */
data class DiagnosticHeartbeat(
    val timestampMs: Long,
    val timeFormatted: String,
    val serviceAlive: Boolean,
    val locationCallbacksCount: Int,
    val lastLocationAgeSeconds: Long,
    val rtdbWritesCount: Int,
    val lastRtdbLatencyMs: Long
)

/**
 * Summary status of each pipeline stage.
 */
data class PipelineStatus(
    val alarm: PipelineStepStatus = PipelineStepStatus.NOT_REACHED,
    val permissions: PipelineStepStatus = PipelineStepStatus.NOT_REACHED,
    val gpsProvider: PipelineStepStatus = PipelineStepStatus.NOT_REACHED,
    val serviceStart: PipelineStepStatus = PipelineStepStatus.NOT_REACHED,
    val locationCallback: PipelineStepStatus = PipelineStepStatus.NOT_REACHED,
    val firebaseInit: PipelineStepStatus = PipelineStepStatus.NOT_REACHED,
    val rtdbWrite: PipelineStepStatus = PipelineStepStatus.NOT_REACHED
)

/**
 * Complete GPS diagnostic report for an operating window session.
 */
data class GpsDiagnosticReport(
    val sessionId: String,
    val date: String,
    val windowName: String, // "Morning", "Evening", or "Custom"
    val windowStart: String,
    val windowEnd: String,
    val busId: String,
    val driverIdMasked: String,
    val sessionStatus: SessionStatus,
    val primaryFailure: FailureType,
    val secondaryFailures: List<FailureType>,
    val pipeline: PipelineStatus,
    val events: List<DiagnosticEvent>,
    val heartbeats: List<DiagnosticHeartbeat>,
    val deviceState: DeviceStateSnapshot?,
    val summaryMessage: String,
    val createdAtMs: Long,
    val completedAtMs: Long? = null
)

/**
 * Persistent state for continuous diagnostic recording across entire days and app lifecycles.
 */
data class ContinuousRecordingState(
    val isRecording: Boolean = false,
    val startedAtMs: Long = 0L,
    val startedAtFormatted: String = "",
    val recordingSessionId: String = "",
    val totalEventsRecorded: Long = 0L,
    val lastEventTimestampMs: Long = 0L,
    val lastEventName: String = ""
)

/**
 * Storage statistics for diagnostic records.
 */
data class DiagnosticStorageStats(
    val totalEventsRecorded: Long,
    val todayEventsRecorded: Long,
    val storageSizeBytes: Long,
    val storageSizeFormatted: String,
    val activeDaysCount: Int,
    val sessionsCount: Int
)

/**
 * Automated root-cause synthesis generated from available diagnostic evidence.
 */
data class ContinuousRootCauseSummary(
    val primaryObservation: String,
    val lastConfirmedHealthyEvent: String,
    val lastSuccessfulRtdbWrite: String,
    val firstObservedFailure: String,
    val serviceState: String,
    val possibleCauses: String,
    val nextEvidenceNeeded: String
)
