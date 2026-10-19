package com.college.bustracker.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.R
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.LocationSource
import com.college.bustracker.ui.MainActivity
import com.college.bustracker.util.LocationThrottler
import com.college.bustracker.util.MultiSourceLocationEngine
import com.college.bustracker.util.RouteDirection
import com.college.bustracker.util.ScheduleHelper
import com.college.bustracker.util.SelectedSourceType
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "BusTrackerGPS"
        const val NOTIFICATION_ID = 1001
        private const val MAX_INITIAL_ACCURACY_METERS = 100.0f

        const val PREFS_PERSISTENT_TRACKING = "tracking_persistent_session_state"
        const val KEY_IS_SESSION_ACTIVE = "is_session_active"
        const val KEY_SAVED_BUS_ID = "persisted_bus_id"
        const val KEY_SAVED_ROUTE_ID = "persisted_route_id"
        const val KEY_SAVED_DRIVER_ID = "persisted_driver_id"
        const val KEY_SAVED_MODE = "persisted_mode"

        // BITM Campus Geofence Auto-Shutdown Constants (Ballari Institute of Technology & Management)
        const val BITM_CAMPUS_LAT = 15.1680
        const val BITM_CAMPUS_LNG = 76.8505
        const val BITM_CAMPUS_GEOFENCE_RADIUS_METERS = 500.0 // 500m radius covers campus entrance, bus grounds & parking
        const val BITM_CAMPUS_AUTO_SHUTDOWN_DURATION_MS = 60 * 1000L // 60s total dwell or 25s stationary within campus

        fun isPersistentSessionActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_PERSISTENT_TRACKING, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_SESSION_ACTIVE, false)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationManager: LocationManager? = null
    private var nativeLocationListener: LocationListener? = null
    private var locationHandlerThread: android.os.HandlerThread? = null

    private var isExplicitStop: Boolean = false
    private var activeBusId: String = ""
    private var activeRouteId: String = ""
    private var activeDriverId: String = ""
    private var activeMode: String = "MANUAL"
    private var activeStartTrigger: String = "USER_OPENED_APP_START"
    private var forceWindow: Boolean = false
    private var hasLoggedFirstLocation: Boolean = false
    private var startupPollingJob: Job? = null
    private var noFixWatchdogJob: Job? = null

    private var lastSentLat = 0.0
    private var lastSentLng = 0.0
    private var lastSentBearing = 0f
    private var lastSentTimestamp = 0L
    private var stationaryStartTime = 0L
    private var campusGeofenceStationaryStartTime = 0L
    private lateinit var offlineQueue: OfflineLocationQueue

    private var lastDriverSource: LocationSource? = null
    private var currentStudentSources = listOf<LocationSource>()
    private var lastFusedLocation: LiveLocation? = null
    private var studentSourcesJob: Job? = null
    private var fallbackWatchdogJob: Job? = null
    private var operatorAuthorizationJob: Job? = null
    private var currentOperatorEpoch: Long? = null
    private var currentTemporaryAssignmentId: String? = null
    private var driverAuthStateListener: FirebaseAuth.AuthStateListener? = null

    private var serviceStopReason: String = "SYSTEM_OR_EXTERNAL_TERMINATION"
    private var serviceStopCaller: String = "ANDROID_FRAMEWORK"
    private var serviceStartTimeMs: Long = 0L
    private var totalCallbacksReceived: Int = 0
    private var totalLocationsDispatched: Int = 0

    private var wakeLock: PowerManager.WakeLock? = null
    private var keepAliveOverlay: KeepAliveOverlayHelper? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CollegeBusTracker:LocationWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.i(TAG, "[LOCATION-SERVICE] Partial wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "[LOCATION-SERVICE] Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "[LOCATION-SERVICE] Partial wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[LOCATION-SERVICE] Failed to release wake lock: ${e.message}")
        }
        wakeLock = null
    }

    override fun onCreate() {
        super.onCreate()
        val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        Log.i(TAG, "[AUTO-TRACE] Service onCreate")
        
        locationHandlerThread = android.os.HandlerThread("CollegeBusGpsThread", android.os.Process.THREAD_PRIORITY_FOREGROUND).apply {
            start()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        Log.i(TAG, "[AUTO-TRACE] Location client initialized")

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location == null) {
                    Log.d(TAG, "[LOCATION-SERVICE] GPS_REJECTED: null fused location result")
                    return
                }
                onNewLocation(location)
            }
        }

        nativeLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onNewLocation(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {
                Log.i(TAG, "[LOCATION-SERVICE] Location provider enabled: $provider")
            }
            override fun onProviderDisabled(provider: String) {
                Log.w(TAG, "[LOCATION-SERVICE] Location provider disabled: $provider")
            }
        }

        // Listen for network connectivity to flush offline buffer when back online
        serviceScope.launch {
            try {
                CollegeBusApp.instance.networkMonitor.isConnected.collect { isConnected ->
                    if (isConnected) {
                        CollegeBusApp.instance.trackingRepository.flushOfflineBuffer()
                    }
                }
            } catch (ignored: Exception) {}
        }

        // Initialize dead-zone telemetry queue with .info/connected listener
        offlineQueue = OfflineLocationQueue(serviceScope)
        offlineQueue.startListening()
    }

    private fun persistTrackingSessionState(isActive: Boolean) {
        try {
            val prefs = getSharedPreferences(PREFS_PERSISTENT_TRACKING, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_IS_SESSION_ACTIVE, isActive)
                if (isActive) {
                    putString(KEY_SAVED_BUS_ID, activeBusId)
                    putString(KEY_SAVED_ROUTE_ID, activeRouteId)
                    putString(KEY_SAVED_DRIVER_ID, activeDriverId)
                    putString(KEY_SAVED_MODE, activeMode)
                }
                apply()
            }
            Log.i(TAG, "[AUTO-TRACE] Persisted tracking session state: isActive=$isActive, bus=$activeBusId")
        } catch (e: Exception) {
            Log.e(TAG, "[AUTO-TRACE] Failed to persist tracking session state: ${e.message}")
        }
    }

    private fun scheduleServiceResurrection(delayMs: Long, reason: String) {
        if (isExplicitStop || activeBusId.isBlank() || !AutoTrackingScheduler.isDriverLoggedIn(this)) {
            Log.i(TAG, "[AUTO-TRACE] Resurrection skipped: isExplicitStop=$isExplicitStop, bus=$activeBusId")
            return
        }
        Log.w(TAG, "[AUTO-TRACE] Scheduling immediate service resurrection via AlarmManager setAlarmClock in ${delayMs}ms. Reason: $reason, bus=$activeBusId")
        try {
            val restartIntent = Intent(applicationContext, DriverScheduleReceiver::class.java).apply {
                action = AutoTrackingScheduler.ACTION_DRIVER_SCHEDULE_CHECK
                putExtra("EXTRA_FORCE_WINDOW", forceWindow)
                putExtra("EXTRA_RESURRECTION_REASON", reason)
                putExtra(TrackingServiceHelper.EXTRA_START_TRIGGER, "BACKGROUND_ALARM_RESURRECTION")
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                1005,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val showIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showPendingIntent = PendingIntent.getActivity(
                applicationContext,
                11005,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val triggerAt = System.currentTimeMillis() + delayMs
            try {
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent)
                am?.setAlarmClock(clockInfo, pi)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am?.canScheduleExactAlarms() == true) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                } else {
                    am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[AUTO-TRACE] Failed to schedule service resurrection: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        when (intent?.action) {
            TrackingServiceHelper.ACTION_STOP_TRACKING -> {
                isExplicitStop = true
                serviceStopReason = intent.getStringExtra(TrackingServiceHelper.EXTRA_STOP_REASON) ?: "ACTION_STOP_TRACKING_INTENT"
                serviceStopCaller = intent.getStringExtra(TrackingServiceHelper.EXTRA_STOP_CALLER) ?: "ExternalIntent"
                Log.i(TAG, "[LOCATION-SERVICE] $nowIso onStartCommand: action=ACTION_STOP_TRACKING, reason=$serviceStopReason, caller=$serviceStopCaller, bus=$activeBusId")
                persistTrackingSessionState(false)
                stopTrackingSession(isExplicitUserStop = true)
                stopSelf()
                return START_NOT_STICKY
            }
            TrackingServiceHelper.ACTION_START_TRACKING, null -> {
                isExplicitStop = false
                serviceStopReason = "SYSTEM_OR_EXTERNAL_TERMINATION"
                serviceStopCaller = "ANDROID_FRAMEWORK"
                activeBusId = intent?.getStringExtra(TrackingServiceHelper.EXTRA_BUS_ID) ?: ""
                activeRouteId = intent?.getStringExtra(TrackingServiceHelper.EXTRA_ROUTE_ID) ?: ""
                activeDriverId = intent?.getStringExtra(TrackingServiceHelper.EXTRA_DRIVER_ID) ?: ""
                activeMode = intent?.getStringExtra(TrackingServiceHelper.EXTRA_TRACKING_MODE) ?: "MANUAL"
                activeStartTrigger = intent?.getStringExtra(TrackingServiceHelper.EXTRA_START_TRIGGER) ?: "USER_OPENED_APP_START"
                forceWindow = intent?.getBooleanExtra("EXTRA_FORCE_WINDOW", false) ?: false
                hasLoggedFirstLocation = false

                // If activeBusId is missing (e.g. system START_STICKY recovery or alarm trigger), restore from persistent prefs
                if (activeBusId.isBlank()) {
                    val prefs = getSharedPreferences(PREFS_PERSISTENT_TRACKING, Context.MODE_PRIVATE)
                    if (prefs.getBoolean(KEY_IS_SESSION_ACTIVE, false)) {
                        activeBusId = prefs.getString(KEY_SAVED_BUS_ID, "") ?: ""
                        activeRouteId = prefs.getString(KEY_SAVED_ROUTE_ID, "") ?: ""
                        activeDriverId = prefs.getString(KEY_SAVED_DRIVER_ID, "") ?: ""
                        activeMode = prefs.getString(KEY_SAVED_MODE, "MANUAL") ?: "MANUAL"
                        Log.i(TAG, "[AUTO-TRACE] Restored active tracking session from persistent state: bus=$activeBusId")
                    }
                }

                // Identity fallback recovery for background execution
                resolveDriverIdentityFallbacks()

                if (activeBusId.isNotBlank()) {
                    persistTrackingSessionState(true)
                }

                Log.i(TAG, "[AUTO-TRACE] Service onStartCommand: action=${intent?.action}, bus=$activeBusId, driver=$activeDriverId, mode=$activeMode")

                // Must promote to foreground first to satisfy Android startForegroundService contract
                startForegroundTracking()

                if (!AutoTrackingScheduler.isDriverLoggedIn(this)) {
                    Log.w(TAG, "[LOCATION-SERVICE] TRACKING_BLOCKED: Driver is logged out. Stopping tracking service immediately.")
                    serviceStopReason = "DRIVER_LOGGED_OUT"
                    serviceStopCaller = "AuthVerification"
                    persistTrackingSessionState(false)
                    TrackingServiceHelper.updateServiceState(
                        isTracking = false,
                        busId = null,
                        gps = "Driver Logged Out",
                        timestamp = System.currentTimeMillis()
                    )
                    stopTrackingSession(isExplicitUserStop = true)
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (!forceWindow && activeMode == "AUTO" && !com.college.bustracker.util.CollegeCalendarService.isWorkingDay()) {
                    val holidayName = com.college.bustracker.util.CollegeCalendarService.getHolidayName() ?: "College Holiday"
                    Log.w(TAG, "[LOCATION-SERVICE] TRACKING_BLOCKED: Today is a holiday ($holidayName). Tracking service will not continue.")
                    serviceStopReason = "COLLEGE_HOLIDAY"
                    serviceStopCaller = "CalendarCheck"
                    persistTrackingSessionState(false)
                    TrackingServiceHelper.updateServiceState(
                        isTracking = false,
                        busId = null,
                        gps = "Holiday ($holidayName)",
                        timestamp = System.currentTimeMillis()
                    )
                    stopTrackingSession(isExplicitUserStop = true)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun resolveDriverIdentityFallbacks() {
        val authUid = FirebaseAuth.getInstance().currentUser?.uid
        val staffPrefs = getSharedPreferences("staff_session_prefs", Context.MODE_PRIVATE)
        val staffUid = staffPrefs.getString("session_uid", "") ?: ""

        if (!authUid.isNullOrBlank()) {
            activeDriverId = authUid
        } else if (activeDriverId.isBlank() && staffUid.isNotBlank()) {
            activeDriverId = staffUid
        }

        if (activeBusId.isBlank()) {
            val autoPrefs = getSharedPreferences(AutoTrackingScheduler.PREF_FILE, Context.MODE_PRIVATE)
            activeBusId = autoPrefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_BUS, "") ?: ""
            if (activeBusId.isBlank()) {
                activeBusId = staffPrefs.getString("session_assigned_bus", "") ?: ""
            }
        }

        if (activeRouteId.isBlank()) {
            val autoPrefs = getSharedPreferences(AutoTrackingScheduler.PREF_FILE, Context.MODE_PRIVATE)
            activeRouteId = autoPrefs.getString(AutoTrackingScheduler.KEY_ASSIGNED_ROUTE, "ROUTE-01") ?: "ROUTE-01"
        }
        Log.i(TAG, "[LOCATION-SERVICE] Driver identity resolved: bus=$activeBusId, driver=$activeDriverId, route=$activeRouteId")
    }

    private fun startForegroundTracking() {
        acquireWakeLock()
        serviceStartTimeMs = System.currentTimeMillis()
        val notification = buildTrackingNotification("Starting GPS...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "[AUTO-TRACE] startForeground succeeded")
        com.college.bustracker.diagnostics.GpsDiagnosticManager.onServiceStarted(this, activeBusId, activeStartTrigger)

        // Layer 2: Register privileged GMS hardware piggyback with FusedLocationProviderClient
        GmsHardwareWatchdog.register(this)

        // Layer 4: Attach 1x1 transparent keep-alive overlay window (if permitted)
        if (keepAliveOverlay == null) {
            keepAliveOverlay = KeepAliveOverlayHelper(this)
        }
        keepAliveOverlay?.attachOverlay()

        val fbInit = try { com.google.firebase.FirebaseApp.getApps(this).isNotEmpty() } catch (e: Exception) { false }
        val authUidAvailable = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != null
        Log.i(TAG, "[AUTO-TRACE] Firebase initialized: $fbInit")
        Log.i(TAG, "[AUTO-TRACE] FirebaseAuth UID available: $authUidAvailable")
        com.college.bustracker.diagnostics.GpsDiagnosticManager.onFirebaseState(this, fbInit, authUidAvailable)

        TrackingServiceHelper.updateServiceState(
            isTracking = true,
            busId = activeBusId,
            gps = "Starting GPS",
            timestamp = System.currentTimeMillis()
        )

        // 30-second watchdog for initial GPS fix (Phase 10)
        noFixWatchdogJob?.cancel()
        noFixWatchdogJob = serviceScope.launch {
            delay(30_000L)
            if (!hasLoggedFirstLocation) {
                val hasBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this@LocationTrackingService,
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
                Log.w(TAG, "[AUTO-TRACE] NO_FIX_30S mode=$activeMode, busId=$activeBusId, backgroundPermission=$hasBg")
                val statusMsg = if (!hasBg) {
                    "No GPS fix — check Location permission"
                } else {
                    "No GPS fix — searching for signal"
                }
                updateNotification(statusMsg)
                TrackingServiceHelper.updateServiceState(
                    isTracking = true,
                    busId = activeBusId,
                    gps = if (!hasBg) "Permission Missing" else "No GPS Fix",
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        // Listen for student backup contributor fixes
        studentSourcesJob?.cancel()
        studentSourcesJob = serviceScope.launch {
            if (activeBusId.isNotBlank()) {
                CollegeBusApp.instance.trackingRepository.getLocationSourcesFlow(activeBusId).collect { sources ->
                    currentStudentSources = sources.filter { it.sourceType == "SECONDARY_STUDENT" }
                }
            }
        }

        // Watchdog: If driver GPS drops (>25s) while tracking is active, fail over to student sources
        fallbackWatchdogJob?.cancel()
        fallbackWatchdogJob = serviceScope.launch {
            while (true) {
                delay(10_000L)
                val now = System.currentTimeMillis()
                if ((now - lastSentTimestamp) > 25_000L && currentStudentSources.isNotEmpty() && activeBusId.isNotBlank()) {
                    val fusionResult = MultiSourceLocationEngine.selectBestLocation(
                        busId = activeBusId,
                        routeId = activeRouteId,
                        driverId = activeDriverId,
                        mode = activeMode,
                        previousLocation = lastFusedLocation,
                        driverSource = lastDriverSource,
                        studentSources = currentStudentSources,
                        now = now
                    )

                    if (fusionResult.selectedSourceType == SelectedSourceType.STUDENT_ASSISTED && !fusionResult.isDuplicate) {
                        val hasInternet = CollegeBusApp.instance.networkMonitor.isConnected.value
                        val stamped = fusionResult.liveLocation.copy(
                            operatorEpoch = currentOperatorEpoch,
                            temporaryAssignmentId = currentTemporaryAssignmentId
                        )
                        lastFusedLocation = stamped
                        CollegeBusApp.instance.trackingRepository.updateLiveLocation(stamped, hasInternet)
                        Log.i(
                            TAG,
                            "[LOCATION-SERVICE] MULTI_SOURCE: Driver GPS silent. Failover to ${fusionResult.usedSourcesCount} student source(s)"
                        )
                    }
                }
            }
        }

        // Real-time operator authorization watcher:
        // Automatically stops tracking if temporary replacement starts/expires/is cancelled
        operatorAuthorizationJob?.cancel()
        operatorAuthorizationJob = serviceScope.launch {
            if (activeBusId.isNotBlank()) {
                val tempRepo = CollegeBusApp.instance.temporaryAssignmentRepository
                tempRepo.getAssignmentsForBusFlow(activeBusId).collect { assignments ->
                    val now = tempRepo.getEstimatedServerTime()
                    val activeTemp = assignments.firstOrNull { it.isActive(now) }
                    if (activeTemp != null) {
                        currentOperatorEpoch = activeTemp.operatorEpoch
                        currentTemporaryAssignmentId = activeTemp.assignmentId
                        // If active temporary driver is someone else, stop this driver's tracking session
                        if (activeDriverId.isNotBlank() && activeDriverId != activeTemp.replacementDriverUid) {
                            Log.w(TAG, "[LOCATION-SERVICE] OPERATOR_REPLACED: Driver $activeDriverId replaced by ${activeTemp.replacementDriverName} for bus $activeBusId. Stopping location writes.")
                            serviceStopReason = "OPERATOR_REPLACED"
                            serviceStopCaller = "OperatorAuthorizationJob"
                            stopTrackingSession()
                            stopSelf()
                        }
                    } else {
                        currentOperatorEpoch = null
                        currentTemporaryAssignmentId = null
                    }
                }
            }
        }

        // Dedicated initial location polling loop for fast startup in background
        startupPollingJob?.cancel()
        Log.i(TAG, "[AUTO-TRACE] Startup location request")
        startupPollingJob = serviceScope.launch {
            var attempts = 0
            while (attempts < 10 && !hasLoggedFirstLocation) {
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                        if (loc != null) {
                            Log.i(TAG, "[LOCATION-SERVICE] Polled initial GPS fix: lat=${loc.latitude}, lng=${loc.longitude}, acc=${loc.accuracy}m")
                            onNewLocation(loc)
                        }
                    }
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null && (System.currentTimeMillis() - loc.time) < 120_000L) {
                            Log.i(TAG, "[LOCATION-SERVICE] Polled fresh lastLocation fix: lat=${loc.latitude}, lng=${loc.longitude}, acc=${loc.accuracy}m")
                            onNewLocation(loc)
                        }
                    }
                } catch (ignored: Exception) {}
                delay(3_000L)
                attempts++
            }
        }

        // Real-time auth watcher: Stop tracking immediately if driver logs out
        if (driverAuthStateListener == null) {
            driverAuthStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                if (firebaseAuth.currentUser == null && !AutoTrackingScheduler.isDriverLoggedIn(this@LocationTrackingService)) {
                    Log.w(TAG, "[LOCATION-SERVICE] DRIVER_LOGGED_OUT: Driver signed out while service was running. Stopping tracking immediately.")
                    serviceStopReason = "DRIVER_LOGGED_OUT"
                    serviceStopCaller = "AuthStateListener"
                    TrackingServiceHelper.updateServiceState(
                        isTracking = false,
                        busId = null,
                        gps = "Driver Logged Out",
                        timestamp = System.currentTimeMillis()
                    )
                    stopTrackingSession()
                    stopSelf()
                }
            }
            try {
                FirebaseAuth.getInstance().addAuthStateListener(driverAuthStateListener!!)
            } catch (ignored: Exception) {}
        }

        requestLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        Log.i(TAG, "[LOCATION-SERVICE] $nowIso Requesting location updates")
        val targetLooper = locationHandlerThread?.looper ?: Looper.getMainLooper()

        // 1. High accuracy Fused Location Request with immediate dispatch enabled
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .setMaxUpdateDelayMillis(5_000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                targetLooper
            )
            Log.i(TAG, "[AUTO-TRACE] Location updates registered")
            com.college.bustracker.diagnostics.GpsDiagnosticManager.onLocationRequestRegistered(this, "FusedLocationProviderClient")
        } catch (e: SecurityException) {
            Log.e(TAG, "[LOCATION-SERVICE] Fused location permission denied: ${e.message}")
            com.college.bustracker.diagnostics.GpsDiagnosticManager.logEvent(
                context = this,
                stage = com.college.bustracker.diagnostics.DiagnosticStage.LOCATION_REQUEST,
                eventName = "FUSED_LOCATION_PERMISSION_DENIED",
                details = mapOf("error" to (e.message ?: "SecurityException")),
                isFailure = true,
                failureType = com.college.bustracker.diagnostics.FailureType.PERMISSION_FAILURE
            )
            TrackingServiceHelper.updateServiceState(
                isTracking = false,
                busId = null,
                gps = "Permission Denied",
                timestamp = 0L
            )
            stopSelf()
            return
        } catch (e: Exception) {
            Log.w(TAG, "[LOCATION-SERVICE] Failed to register FusedLocationProviderClient: ${e.message}")
        }

        // 2. Direct LocationManager hardware GNSS registration for robust screen-locked background execution
        try {
            val lm = locationManager
            val listener = nativeLocationListener
            if (lm != null && listener != null) {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5_000L,
                        0f,
                        listener,
                        targetLooper
                    )
                    Log.i(TAG, "[LOCATION-SERVICE] Native LocationManager GPS_PROVIDER registered on dedicated looper")
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5_000L,
                        0f,
                        listener,
                        targetLooper
                    )
                    Log.i(TAG, "[LOCATION-SERVICE] Native LocationManager NETWORK_PROVIDER registered on dedicated looper")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "[LOCATION-SERVICE] Native LocationManager security exception: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "[LOCATION-SERVICE] Native LocationManager registration error: ${e.message}")
        }

        // 3. Immediate query for existing fresh location to avoid startup latency
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val age = System.currentTimeMillis() - loc.time
                    if (age < 120_000L) {
                        Log.i(TAG, "[LOCATION-SERVICE] Immediate lastLocation available (age=${age}ms): lat=${loc.latitude}, lng=${loc.longitude}")
                        onNewLocation(loc)
                    }
                }
            }
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.i(TAG, "[LOCATION-SERVICE] Immediate getCurrentLocation fix received: lat=${loc.latitude}, lng=${loc.longitude}")
                    onNewLocation(loc)
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun onNewLocation(location: Location) {
        if (!AutoTrackingScheduler.isDriverLoggedIn(this)) {
            Log.w(TAG, "[LOCATION-SERVICE] TRACKING_STOPPED: Driver is logged out during location callback. Stopping tracking.")
            serviceStopReason = "DRIVER_LOGGED_OUT"
            serviceStopCaller = "LocationCallback"
            TrackingServiceHelper.updateServiceState(
                isTracking = false,
                busId = null,
                gps = "Driver Logged Out",
                timestamp = System.currentTimeMillis()
            )
            stopTrackingSession()
            stopSelf()
            return
        }

        // 0. Calendar Holiday Check: Stop if date transitioned into a holiday
        if (!forceWindow && activeMode == "AUTO" && !com.college.bustracker.util.CollegeCalendarService.isWorkingDay()) {
            Log.w(TAG, "[LOCATION-SERVICE] TRACKING_STOPPED: Today is a college holiday. Stopping tracking.")
            serviceStopReason = "COLLEGE_HOLIDAY"
            serviceStopCaller = "CalendarCheck"
            stopTrackingSession()
            stopSelf()
            return
        }

        // 1. Basic coordinate sanity
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            Log.w(TAG, "[LOCATION-SERVICE] GPS_REJECTED: zero coordinates (0.0, 0.0)")
            return
        }

        // 2. Accuracy threshold (reject gross inaccuracy > 100m to avoid jumping during initial startup)
        if (location.hasAccuracy() && location.accuracy > MAX_INITIAL_ACCURACY_METERS) {
            Log.w(TAG, "[LOCATION-SERVICE] GPS_REJECTED: poor accuracy ${location.accuracy}m > ${MAX_INITIAL_ACCURACY_METERS}m")
            return
        }

        totalCallbacksReceived++
        val now = System.currentTimeMillis()
        val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val locAge = if (location.time > 0) now - location.time else 0L
        com.college.bustracker.diagnostics.GpsDiagnosticManager.onLocationCallbackReceived(
            context = this,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
            provider = location.provider ?: "unknown",
            ageMs = locAge
        )

        if (!hasLoggedFirstLocation) {
            hasLoggedFirstLocation = true
            noFixWatchdogJob?.cancel()
            Log.i(TAG, "[AUTO-TRACE] First GPS callback: lat=${location.latitude}, lng=${location.longitude}, acc=${location.accuracy}m")
        }

        Log.d(
            TAG,
            "[LOCATION-SERVICE] GPS_RECEIVED: lat=${location.latitude}, lng=${location.longitude}, acc=${location.accuracy}m, speed=${location.speed}m/s"
        )

        // 3. College Campus Geofence Auto-Shutdown Check
        // Shuts down tracking automatically when the driver reaches campus during morning shift
        val nowKolkata = ScheduleHelper.nowKolkata()
        val direction = ScheduleHelper.determineRouteDirection()
        val isHeadingToCampus = direction == RouteDirection.STOPS_TO_COLLEGE || nowKolkata.isBefore(java.time.LocalTime.of(12, 30))

        var isInsideCampus = false
        var minCampusDist = Double.MAX_VALUE

        // 3a. Primary BITM Campus Geofence (Ballari)
        val distToBitm = LocationThrottler.calculateDistanceMeters(
            location.latitude, location.longitude,
            BITM_CAMPUS_LAT, BITM_CAMPUS_LNG
        )
        if (distToBitm <= BITM_CAMPUS_GEOFENCE_RADIUS_METERS) {
            isInsideCampus = true
            minCampusDist = distToBitm
        }

        // 3b. Active Route Destination Stop (e.g. final stop or campus stop on the assigned route)
        if (!isInsideCampus && activeRouteId.isNotBlank()) {
            val route = CollegeBusApp.instance.routeRepository.getCachedRoute(activeRouteId)
            val destinationStop = route?.stops?.lastOrNull()
            if (destinationStop != null && destinationStop.latitude != 0.0 && destinationStop.longitude != 0.0) {
                val distToDest = LocationThrottler.calculateDistanceMeters(
                    location.latitude, location.longitude,
                    destinationStop.latitude, destinationStop.longitude
                )
                if (distToDest <= BITM_CAMPUS_GEOFENCE_RADIUS_METERS) {
                    isInsideCampus = true
                    minCampusDist = distToDest
                }
            }
        }

        // 3c. Legacy coordinates fallback
        if (!isInsideCampus) {
            val distToLegacy = LocationThrottler.calculateDistanceMeters(
                location.latitude, location.longitude,
                15.1425, 76.8925
            )
            if (distToLegacy <= BITM_CAMPUS_GEOFENCE_RADIUS_METERS) {
                isInsideCampus = true
                minCampusDist = distToLegacy
            }
        }

        if (isInsideCampus && isHeadingToCampus) {
            if (campusGeofenceStationaryStartTime == 0L) {
                campusGeofenceStationaryStartTime = now
                Log.i(TAG, "[CAMPUS_GEOFENCE] Bus entered Campus geofence (dist=${minCampusDist.toInt()}m). Started arrival confirmation timer.")
            } else {
                val dwellTimeMs = now - campusGeofenceStationaryStartTime
                // Trigger auto-shutdown if bus has been inside campus for >= 60 seconds, or stationary (< 1.5 m/s) for >= 25 seconds
                val isConfirmedStationary = location.speed < LocationThrottler.SPEED_THRESHOLD_MPS && dwellTimeMs >= 25_000L
                val isDwellExceeded = dwellTimeMs >= BITM_CAMPUS_AUTO_SHUTDOWN_DURATION_MS

                if (isConfirmedStationary || isDwellExceeded) {
                    Log.w(TAG, "[CAMPUS_GEOFENCE] Bus reached Campus (dist=${minCampusDist.toInt()}m, speed=${location.speed}m/s, dwell=${dwellTimeMs / 1000}s). Triggering automatic shift shutdown!")
                    performCampusAutoShutdown(location)
                    return
                }
            }
        } else {
            // Only reset if bus physically moved outside of the campus geofence
            if (!isInsideCampus && campusGeofenceStationaryStartTime != 0L) {
                Log.d(TAG, "[CAMPUS_GEOFENCE] Bus exited campus geofence (dist=${minCampusDist.toInt()}m). Resetting arrival timer.")
                campusGeofenceStationaryStartTime = 0L
            }
        }

        // 4. Velocity-Adaptive Throttling:
        // Moving (speed >= 1.5 m/s or heading change > 15°): 4s cadence
        // Stationary (speed < 1.5 m/s for > 15s): 20s heartbeat with speed: 0
        // Immediate wake-up on acceleration
        val throttlerDecision = LocationThrottler.shouldDispatchAdaptive(
            lastSentLat = lastSentLat,
            lastSentLng = lastSentLng,
            lastSentTimestamp = lastSentTimestamp,
            lastSentBearing = lastSentBearing,
            newLat = location.latitude,
            newLng = location.longitude,
            newTimestamp = now,
            newBearing = location.bearing,
            speedMps = location.speed,
            stationaryStartTime = stationaryStartTime
        )

        stationaryStartTime = throttlerDecision.updatedStationaryStartTime

        if (!throttlerDecision.shouldDispatch) {
            Log.d(TAG, "[LOCATION-SERVICE] GPS_REJECTED: throttled interval (isStationary=${throttlerDecision.isStationaryState})")
            return
        }

        lastSentLat = location.latitude
        lastSentLng = location.longitude
        lastSentBearing = location.bearing
        lastSentTimestamp = now

        val hasInternet = CollegeBusApp.instance.networkMonitor.isConnected.value

        val driverSource = LocationSource(
            busId = activeBusId,
            sourceId = activeDriverId.ifBlank { "PRIMARY_DRIVER" },
            sourceType = "PRIMARY_DRIVER",
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = if (throttlerDecision.overrideSpeedZero) 0.0f else location.speed,
            bearing = location.bearing,
            timestamp = now,
            active = true
        )
        lastDriverSource = driverSource

        if (activeBusId.isNotBlank() && activeDriverId.isNotBlank()) {
            CollegeBusApp.instance.trackingRepository.contributeStudentLocation(driverSource)
        }

        val fusionResult = MultiSourceLocationEngine.selectBestLocation(
            busId = activeBusId,
            routeId = activeRouteId,
            driverId = activeDriverId,
            mode = activeMode,
            previousLocation = lastFusedLocation,
            driverSource = driverSource,
            studentSources = currentStudentSources,
            now = now
        )

        if (!fusionResult.isDuplicate && activeBusId.isNotBlank()) {
            val stamped = fusionResult.liveLocation.copy(
                speed = if (throttlerDecision.overrideSpeedZero) 0.0f else fusionResult.liveLocation.speed,
                operatorEpoch = currentOperatorEpoch,
                temporaryAssignmentId = currentTemporaryAssignmentId
            )
            lastFusedLocation = stamped
            Log.i(TAG, "[LOCATION-SERVICE] Writing location to Firebase: busId=$activeBusId, lat=${stamped.latitude}, lng=${stamped.longitude}, speed=${stamped.speed}, status=${stamped.trackingStatus}, hasInternet=$hasInternet")
            totalLocationsDispatched++

            if (hasInternet) {
                CollegeBusApp.instance.trackingRepository.updateLiveLocation(stamped, true)
            } else {
                Log.w(TAG, "[LOCATION-SERVICE] Cellular dead-zone detected! Buffering payload in OfflineLocationQueue (size=${offlineQueue.size() + 1})")
                offlineQueue.enqueue(stamped)
                CollegeBusApp.instance.trackingRepository.updateLiveLocation(stamped, false)
            }
        }

        val netStatus = if (hasInternet) "Online" else "Offline (Buffered)"
        val notifText = "Bus: $activeBusId • $netStatus"
        updateNotification(notifText)

        TrackingServiceHelper.updateServiceState(
            isTracking = true,
            busId = activeBusId,
            gps = "Locked",
            timestamp = now
        )
    }

    private fun performCampusAutoShutdown(location: Location) {
        val now = System.currentTimeMillis()
        val finalLocation = LiveLocation(
            busId = activeBusId,
            latitude = location.latitude,
            longitude = location.longitude,
            speed = 0f,
            bearing = location.bearing,
            trackingStatus = "ARRIVED_AT_CAMPUS",
            mode = activeMode,
            routeId = activeRouteId,
            driverId = activeDriverId,
            sourceType = "PRIMARY_DRIVER",
            lastUpdated = now
        )

        // 1. Push final RTDB status update
        try {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("live_locations")
                .child(activeBusId)
                .setValue(finalLocation)
            Log.i(TAG, "[CAMPUS_GEOFENCE] Final ARRIVED_AT_CAMPUS status written to RTDB for $activeBusId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push ARRIVED_AT_CAMPUS status: ${e.message}")
        }

        // 2. Mark explicit stop to prevent immediate resurrection
        serviceStopReason = "CAMPUS_GEOFENCE_AUTO_SHUTDOWN"
        serviceStopCaller = "BitmCampusGeofence"
        isExplicitStop = true
        persistTrackingSessionState(false)

        val currentWinId = ScheduleHelper.getCurrentWindowId()
        if (currentWinId != null) {
            AutoTrackingScheduler.setWindowOverriddenOff(this, currentWinId)
            Log.i(TAG, "[CAMPUS_GEOFENCE] Window $currentWinId marked as OVERRIDDEN_OFF to prevent resurrection")
        }

        TrackingServiceHelper.updateServiceState(
            isTracking = false,
            busId = activeBusId,
            gps = "Off",
            timestamp = now
        )

        GmsHardwareWatchdog.unregister(this)
        keepAliveOverlay?.detachOverlay()
        keepAliveOverlay = null

        // 3. Schedule next afternoon shift alarm via AutoTrackingScheduler
        try {
            AutoTrackingScheduler.scheduleAlarms(this)
            Log.i(TAG, "[CAMPUS_GEOFENCE] Scheduled next afternoon shift alarm")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule afternoon shift alarm: ${e.message}")
        }

        stopTrackingSession(isExplicitUserStop = true)
        stopSelf()
    }

    private fun stopTrackingSession(isExplicitUserStop: Boolean = true) {
        val nowIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        Log.i(TAG, "[AUTO-TRACE] $nowIso stopTrackingSession started: bus=$activeBusId, explicitUserStop=$isExplicitUserStop")
        releaseWakeLock()
        hasLoggedFirstLocation = false
        startupPollingJob?.cancel()
        startupPollingJob = null
        noFixWatchdogJob?.cancel()
        noFixWatchdogJob = null
        studentSourcesJob?.cancel()
        studentSourcesJob = null
        fallbackWatchdogJob?.cancel()
        fallbackWatchdogJob = null
        operatorAuthorizationJob?.cancel()
        operatorAuthorizationJob = null
        driverAuthStateListener?.let {
            try {
                FirebaseAuth.getInstance().removeAuthStateListener(it)
            } catch (ignored: Exception) {}
            driverAuthStateListener = null
        }

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (ignored: Exception) {}

        try {
            nativeLocationListener?.let { locationManager?.removeUpdates(it) }
        } catch (ignored: Exception) {}

        // Only mark the bus OFFLINE in Firebase if the user deliberately pressed Stop Tracking!
        // If swiped or killed by OS memory pressure, leave the active state intact while service resurrects.
        if (isExplicitUserStop && activeBusId.isNotBlank()) {
            CollegeBusApp.instance.trackingRepository.removeStudentLocation(activeBusId, activeDriverId)
            CollegeBusApp.instance.trackingRepository.stopTracking(activeBusId, activeDriverId)
        }

        if (isExplicitUserStop) {
            com.college.bustracker.diagnostics.GpsDiagnosticManager.onServiceStopped(this, "Tracking session stopped")

            // Unregister privileged GMS hardware piggyback and detach overlay
            GmsHardwareWatchdog.unregister(this)
            keepAliveOverlay?.detachOverlay()
            keepAliveOverlay = null

            TrackingServiceHelper.updateServiceState(
                isTracking = false,
                busId = null,
                gps = "Stopped",
                timestamp = System.currentTimeMillis()
            )

            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun buildTrackingNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = TrackingServiceHelper.ACTION_STOP_TRACKING
            putExtra(TrackingServiceHelper.EXTRA_STOP_REASON, "NOTIFICATION_STOP_ACTION")
            putExtra(TrackingServiceHelper.EXTRA_STOP_CALLER, "NotificationAction")
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CollegeBusApp.TRACKING_CHANNEL_ID)
            .setContentTitle("FleetX Live Bus Tracking")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Tracking", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun updateNotification(statusText: String) {
        val notification = buildTrackingNotification(statusText)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "[AUTO-TRACE] onTaskRemoved callback received. FleetX was swiped from Recent Apps. bus=$activeBusId")
        com.college.bustracker.diagnostics.GpsDiagnosticManager.onTaskRemoved(
            context = this,
            busId = activeBusId,
            isTracking = activeBusId.isNotBlank()
        )
        // Request system_server rebind via NotificationListenerService (elevates priority and bypasses Vivo Autostart blocker)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(this, ShiftKeepAliveNotificationService::class.java)
                )
                Log.i(TAG, "[TASK_REMOVED] Requested NotificationListenerService rebind from system_server")
            } catch (e: Exception) {
                Log.w(TAG, "[TASK_REMOVED] Failed to request rebind: ${e.message}")
            }
        }
        // Immediately resurrect tracking via 5-second AlarmManager setAlarmClock
        scheduleServiceResurrection(delayMs = 5000L, reason = "TASK_REMOVED_SWIPED")
    }

    override fun onDestroy() {
        Log.w(TAG, "[AUTO-TRACE] LocationTrackingService onDestroy received. bus=$activeBusId, reason=$serviceStopReason, caller=$serviceStopCaller, isExplicitStop=$isExplicitStop")
        val activeSec = if (serviceStartTimeMs > 0) (System.currentTimeMillis() - serviceStartTimeMs) / 1000 else 0L
        com.college.bustracker.diagnostics.GpsDiagnosticManager.onServiceDestroyed(
            context = this,
            busId = activeBusId,
            wasTracking = activeBusId.isNotBlank(),
            stopReason = serviceStopReason,
            stopCaller = serviceStopCaller,
            activeDurationSeconds = activeSec,
            callbacksReceived = totalCallbacksReceived,
            locationsDispatched = totalLocationsDispatched
        )
        // If destroyed by Android system (not user stop), schedule immediate resurrection
        if (!isExplicitStop && activeBusId.isNotBlank()) {
            scheduleServiceResurrection(delayMs = 5000L, reason = "SERVICE_KILLED_BY_OS")
        }
        stopTrackingSession(isExplicitUserStop = isExplicitStop)
        try {
            offlineQueue.stopListening()
        } catch (ignored: Exception) {}
        try {
            locationHandlerThread?.quitSafely()
        } catch (ignored: Exception) {}
        locationHandlerThread = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
