package com.college.bustracker.util

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationManagerCompat
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.service.StudentAutoTrackingScheduler
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lifecycle states of the Automatic Student Location Sharing system.
 */
enum class AutomaticRideState {
    DISABLED,            // One-time consent not granted or turned off
    WAITING_FOR_WINDOW,  // Outside morning/evening route schedule windows (0% battery drain)
    WAITING_FOR_BUS,     // In operating window, waiting to detect transit activity
    POSSIBLY_RIDING,     // Motion or route proximity detected, gathering validation fixes
    CONTRIBUTING,        // Confirmed riding (score >= 70%), contributing backup GPS fixes
    ENDING_RIDE          // Ride concluded or diverged, cleaning up contributor node
}

data class ConfidenceEvaluation(
    val score: Int,
    val proximityScore: Int,
    val speedScore: Int,
    val routeScore: Int,
    val temporalScore: Int,
    val isRiding: Boolean,
    val reason: String
)

data class StateTransitionDecision(
    val nextState: AutomaticRideState,
    val consecutiveHighCount: Int,
    val consecutiveDivergenceCount: Int,
    val firstDivergenceTimestamp: Long,
    val shouldStartSharing: Boolean,
    val shouldStopSharing: Boolean
)

/**
 * Manages the automatic student location sharing lifecycle:
 * - One-time consent persistence
 * - Schedule-gated battery conservation
 * - Multi-signal ride confidence evaluation
 * - Autonomous start/stop of backup telemetry contribution
 */
object AutomaticRideManager {

    private const val TAG = "AutomaticRideManager"
    private const val PREFS_NAME = "auto_ride_prefs"
    private const val KEY_AUTO_ENABLED = "auto_sharing_enabled"
    private const val KEY_SELECTED_BUS_ID = "auto_selected_bus_id"
    private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"

    const val START_CONFIDENCE_THRESHOLD = 75
    const val CONTINUE_CONFIDENCE_THRESHOLD = 35
    const val REQUIRED_START_CONSECUTIVE_FIXES = 2
    const val REQUIRED_STOP_CONSECUTIVE_FIXES = 3
    const val STOP_TIMEOUT_MS = 45_000L

    private val _rideState = MutableStateFlow(AutomaticRideState.DISABLED)
    val rideState: StateFlow<AutomaticRideState> = _rideState.asStateFlow()

    private val _isAutoSharingEnabled = MutableStateFlow(false)
    val isAutoSharingEnabled: StateFlow<Boolean> = _isAutoSharingEnabled.asStateFlow()

    private val _isLocationServicesEnabled = MutableStateFlow(true)
    val isLocationServicesEnabled: StateFlow<Boolean> = _isLocationServicesEnabled.asStateFlow()

    private val _assignedBusId = MutableStateFlow<String?>(null)
    val assignedBusId: StateFlow<String?> = _assignedBusId.asStateFlow()

    private val _currentConfidence = MutableStateFlow(0)
    val currentConfidence: StateFlow<Int> = _currentConfidence.asStateFlow()

    private var consecutiveHighConfidenceCount = 0
    private var consecutiveDivergenceCount = 0
    private var firstDivergenceTimestamp = 0L
    private var isInitialized = false
    private var appContext: Context? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var providerReceiver: android.content.BroadcastReceiver? = null

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    private fun registerProviderReceiver(context: Context) {
        if (providerReceiver != null) return
        providerReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val ctx = c ?: appContext ?: return
                val nowEnabled = isLocationEnabled(ctx)
                val wasEnabled = _isLocationServicesEnabled.value
                _isLocationServicesEnabled.value = nowEnabled
                Log.i(TAG, "Location provider state changed: nowEnabled=$nowEnabled (was=$wasEnabled)")

                if (nowEnabled && !wasEnabled && _isAutoSharingEnabled.value) {
                    val sched = StudentAutoTrackingScheduler.getSavedSchedule(ctx)
                    if (ScheduleHelper.isWithinOperatingWindow(sched) && CollegeCalendarService.isWorkingDay()) {
                        Log.i(TAG, "Location resumed during active operating window -> auto-resuming monitoring")
                        startMonitoring(ctx)
                    }
                } else if (!nowEnabled) {
                    stopMonitoring()
                }
            }
        }
        try {
            val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            context.registerReceiver(providerReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register PROVIDERS_CHANGED_ACTION receiver: ${e.message}")
        }
    }

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        _isLocationServicesEnabled.value = isLocationEnabled(ctx)
        registerProviderReceiver(ctx)

        if (isInitialized) return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_AUTO_ENABLED, false)
        val busId = prefs.getString(KEY_SELECTED_BUS_ID, null)

        _isAutoSharingEnabled.value = enabled
        _assignedBusId.value = busId

        if (enabled && !busId.isNullOrBlank()) {
            _rideState.value = AutomaticRideState.WAITING_FOR_WINDOW
            if (_isLocationServicesEnabled.value) {
                startMonitoring(ctx)
            }
        } else {
            _rideState.value = AutomaticRideState.DISABLED
        }
        isInitialized = true
        Log.i(TAG, "Initialized: autoEnabled=$enabled, busId=$busId, state=${_rideState.value}, locationEnabled=${_isLocationServicesEnabled.value}")
    }

    fun enableAutoSharing(context: Context, busId: String, schedule: RouteSchedule? = null) {
        if (busId.isBlank()) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_ENABLED, true)
            .putString(KEY_SELECTED_BUS_ID, busId)
            .putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
            .apply()

        _isAutoSharingEnabled.value = true
        _assignedBusId.value = busId
        _rideState.value = AutomaticRideState.WAITING_FOR_WINDOW
        consecutiveHighConfidenceCount = 0
        consecutiveDivergenceCount = 0
        firstDivergenceTimestamp = 0L

        // Schedule exact alarms for route operating windows
        val sched = schedule ?: StudentAutoTrackingScheduler.getSavedSchedule(ctx)
        StudentAutoTrackingScheduler.scheduleAlarms(ctx, sched)

        _isLocationServicesEnabled.value = isLocationEnabled(ctx)
        if (_isLocationServicesEnabled.value) {
            startMonitoring(ctx)
        }
        Log.i(TAG, "Automatic sharing ENABLED for bus $busId")
    }

    fun disableAutoSharing(context: Context) {
        val oldBusId = _assignedBusId.value
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_ENABLED, false)
            .remove(KEY_SELECTED_BUS_ID)
            .apply()

        _isAutoSharingEnabled.value = false
        _assignedBusId.value = null
        _rideState.value = AutomaticRideState.DISABLED
        _currentConfidence.value = 0
        consecutiveHighConfidenceCount = 0
        consecutiveDivergenceCount = 0
        firstDivergenceTimestamp = 0L

        // Cancel background alarms
        StudentAutoTrackingScheduler.cancelAlarms(ctx)

        stopMonitoring()
        // Stop sharing and remove contributor from Firebase immediately
        StudentLocationAssistant.stopSharing()
        Log.i(TAG, "Automatic sharing DISABLED (cleared previous bus: $oldBusId)")
    }

    fun updateSchedule(context: Context, schedule: RouteSchedule) {
        val ctx = context.applicationContext
        StudentAutoTrackingScheduler.saveSchedule(ctx, schedule)
        if (_isAutoSharingEnabled.value) {
            StudentAutoTrackingScheduler.scheduleAlarms(ctx, schedule)
        }
    }

    fun changeAssignedBus(context: Context, newBusId: String, schedule: RouteSchedule? = null) {
        if (newBusId.isBlank() || newBusId == _assignedBusId.value) return

        val oldBusId = _assignedBusId.value
        val ctx = context.applicationContext
        // Stop sharing old bus and remove its Firebase node
        StudentLocationAssistant.stopSharing()

        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SELECTED_BUS_ID, newBusId)
            .apply()

        _assignedBusId.value = newBusId
        _rideState.value = AutomaticRideState.WAITING_FOR_WINDOW
        _currentConfidence.value = 0
        consecutiveHighConfidenceCount = 0
        consecutiveDivergenceCount = 0
        firstDivergenceTimestamp = 0L

        if (schedule != null) {
            StudentAutoTrackingScheduler.scheduleAlarms(ctx, schedule)
        }

        stopMonitoring()
        _isLocationServicesEnabled.value = isLocationEnabled(ctx)
        if (_isLocationServicesEnabled.value) {
            startMonitoring(ctx)
        }
        Log.i(TAG, "Switched assigned bus from $oldBusId to $newBusId")
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring(context: Context) {
        if (!_isAutoSharingEnabled.value) return
        if (fusedClient != null) return

        val ctx = context.applicationContext
        appContext = ctx

        // Check if device Location Services are turned on
        val locationOn = isLocationEnabled(ctx)
        _isLocationServicesEnabled.value = locationOn
        if (!locationOn) {
            Log.w(TAG, "Cannot start automatic ride monitoring: Location services are OFF")
            return
        }
        fusedClient = LocationServices.getFusedLocationProviderClient(ctx)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            30_000L
        ).apply {
            setMinUpdateIntervalMillis(15_000L)
            setMaxUpdateDelayMillis(45_000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                evaluateTelemetry(ctx, loc)
            }
        }

        try {
            fusedClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.i(TAG, "Automatic ride monitoring started for ${_assignedBusId.value}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing for automatic ride monitoring")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start automatic ride monitoring: ${e.message}")
        }
    }

    fun stopMonitoring() {
        locationCallback?.let {
            try {
                fusedClient?.removeLocationUpdates(it)
            } catch (ignored: Exception) {}
        }
        locationCallback = null
        fusedClient = null
        Log.i(TAG, "Automatic ride monitoring stopped")
    }

    fun evaluateTelemetry(context: Context, location: Location) {
        val busId = _assignedBusId.value ?: return
        val bus = CollegeBusApp.instance.busRepository.getCachedBus(busId)
        val route = bus?.routeId?.let { CollegeBusApp.instance.routeRepository.getCachedRoute(it) }
        val busLoc = CollegeBusApp.instance.trackingRepository.getCachedLiveLocation(busId)

        processTelemetryUpdate(
            context = context,
            location = location,
            busLocation = busLoc,
            route = route,
            schedule = route?.schedules
        )
    }

    fun onContributorLocationFix(location: Location) {
        if (!_isAutoSharingEnabled.value || _rideState.value != AutomaticRideState.CONTRIBUTING) return
        appContext?.let { ctx ->
            evaluateTelemetry(ctx, location)
        }
    }

    /**
     * Pure function to calculate ride confidence score (0 to 100).
     */
    fun calculateConfidenceScore(
        studentLat: Double,
        studentLng: Double,
        studentSpeedMps: Float,
        studentAccuracy: Float,
        busLocation: LiveLocation?,
        route: Route?,
        consecutiveHighCount: Int
    ): ConfidenceEvaluation {
        if (studentLat == 0.0 && studentLng == 0.0) {
            return ConfidenceEvaluation(0, 0, 0, 0, 0, false, "Invalid student coordinates")
        }

        var proximityScore = 0
        var speedScore = 0
        var routeScore = 0
        var temporalScore = 0

        val studentSpeedKmH = studentSpeedMps * 3.6f

        // 1. Spatial Proximity to bus (Max 35 points)
        val hasValidBus = busLocation != null &&
                busLocation.latitude != 0.0 &&
                busLocation.longitude != 0.0 &&
                (System.currentTimeMillis() - busLocation.lastUpdated) < 180_000L

        if (hasValidBus) {
            val distToBus = LocationThrottler.calculateDistanceMeters(
                studentLat, studentLng,
                busLocation.latitude, busLocation.longitude
            )

            proximityScore = when {
                distToBus <= 40.0 -> 35
                distToBus <= 80.0 -> 22
                distToBus <= 150.0 -> 10
                distToBus > 250.0 -> -25 // Strong penalty if far from bus
                else -> 0
            }
        } else {
            // Driver GPS is missing or stale: fallback to route stops
            val stops = route?.stops ?: emptyList()
            if (stops.isNotEmpty()) {
                val minStopDist = stops.minOfOrNull { stop ->
                    LocationThrottler.calculateDistanceMeters(
                        studentLat, studentLng,
                        stop.latitude, stop.longitude
                    )
                } ?: Double.MAX_VALUE

                if (minStopDist <= 40.0) {
                    proximityScore = 20 // Directly at scheduled route stop
                } else if (minStopDist <= 80.0) {
                    proximityScore = 15 // Near scheduled route stop
                }
            }
        }

        // 2. Kinematic Velocity Matching (Max 30 points)
        if (studentSpeedKmH > 12.0f) {
            // Student is moving at vehicular transit speed (> 12 km/h)
            if (hasValidBus) {
                val busSpeedKmH = busLocation.speed * 3.6f
                val speedDiff = abs(studentSpeedKmH - busSpeedKmH)
                speedScore = when {
                    speedDiff <= 15.0f -> 30
                    speedDiff <= 30.0f -> 18
                    else -> 5
                }
            } else {
                // Bus speed unknown; student is moving at transit speed on route
                speedScore = 20
            }
        } else if (studentSpeedKmH < 4.0f && hasValidBus && (busLocation?.speed ?: 0f) * 3.6f > 20.0f) {
            // Student is stationary or walking, but bus is driving away fast -> NOT riding
            speedScore = -30
        }

        // 3. Route Alignment (Max 20 points)
        val stops = route?.stops ?: emptyList()
        if (stops.size >= 2) {
            val minDistToRoute = findDistanceToRoutePolyline(studentLat, studentLng, stops)
            routeScore = when {
                minDistToRoute <= 50.0 -> 20
                minDistToRoute <= 120.0 -> 10
                minDistToRoute > 300.0 -> -20 // Diverged off route
                else -> 0
            }
        } else if (stops.isNotEmpty()) {
            val minStopDist = stops.minOf { stop ->
                LocationThrottler.calculateDistanceMeters(studentLat, studentLng, stop.latitude, stop.longitude)
            }
            if (minStopDist <= 100.0) routeScore = 15
        }

        // 4. Temporal Coherence (Max 15 points)
        if (consecutiveHighCount >= 2) {
            temporalScore = 15
        } else if (consecutiveHighCount == 1) {
            temporalScore = 8
        }

        val rawScore = proximityScore + speedScore + routeScore + temporalScore
        val finalScore = max(0, min(100, rawScore))
        val isRiding = finalScore >= START_CONFIDENCE_THRESHOLD

        val reason = "Score: $finalScore (Prox: $proximityScore, Spd: $speedScore, Rt: $routeScore, Temp: $temporalScore)"
        return ConfidenceEvaluation(
            score = finalScore,
            proximityScore = proximityScore,
            speedScore = speedScore,
            routeScore = routeScore,
            temporalScore = temporalScore,
            isRiding = isRiding,
            reason = reason
        )
    }

    /**
     * Pure function evaluating FSM transitions with Schmitt-trigger hysteresis.
     * - Start contribution requires confidence >= 75% for 2 consecutive fixes.
     * - Stop contribution requires confidence < 35% for 3 consecutive fixes OR 45s sustained timeout.
     * - Temporary GPS dropouts (< 3 fixes and < 45s) do not oscillate state.
     */
    fun evaluateStateTransition(
        currentState: AutomaticRideState,
        score: Int,
        now: Long,
        consecutiveHighCount: Int,
        consecutiveDivergenceCount: Int,
        firstDivergenceTimestamp: Long,
        isWithinWindow: Boolean
    ): StateTransitionDecision {
        if (!isWithinWindow) {
            val shouldStop = (currentState == AutomaticRideState.CONTRIBUTING)
            return StateTransitionDecision(
                nextState = AutomaticRideState.WAITING_FOR_WINDOW,
                consecutiveHighCount = 0,
                consecutiveDivergenceCount = 0,
                firstDivergenceTimestamp = 0L,
                shouldStartSharing = false,
                shouldStopSharing = shouldStop
            )
        }

        return when (currentState) {
            AutomaticRideState.DISABLED -> StateTransitionDecision(
                nextState = AutomaticRideState.DISABLED,
                consecutiveHighCount = 0,
                consecutiveDivergenceCount = 0,
                firstDivergenceTimestamp = 0L,
                shouldStartSharing = false,
                shouldStopSharing = false
            )

            AutomaticRideState.WAITING_FOR_WINDOW, AutomaticRideState.WAITING_FOR_BUS -> {
                if (score >= START_CONFIDENCE_THRESHOLD) {
                    // First strong candidate fix -> gather 2nd fix before starting contribution
                    StateTransitionDecision(
                        nextState = AutomaticRideState.POSSIBLY_RIDING,
                        consecutiveHighCount = 1,
                        consecutiveDivergenceCount = 0,
                        firstDivergenceTimestamp = 0L,
                        shouldStartSharing = false,
                        shouldStopSharing = false
                    )
                } else if (score in CONTINUE_CONFIDENCE_THRESHOLD until START_CONFIDENCE_THRESHOLD) {
                    StateTransitionDecision(
                        nextState = AutomaticRideState.POSSIBLY_RIDING,
                        consecutiveHighCount = 0,
                        consecutiveDivergenceCount = 0,
                        firstDivergenceTimestamp = 0L,
                        shouldStartSharing = false,
                        shouldStopSharing = false
                    )
                } else {
                    StateTransitionDecision(
                        nextState = AutomaticRideState.WAITING_FOR_BUS,
                        consecutiveHighCount = 0,
                        consecutiveDivergenceCount = 0,
                        firstDivergenceTimestamp = 0L,
                        shouldStartSharing = false,
                        shouldStopSharing = false
                    )
                }
            }

            AutomaticRideState.POSSIBLY_RIDING -> {
                if (score >= START_CONFIDENCE_THRESHOLD) {
                    val newHighCount = consecutiveHighCount + 1
                    if (newHighCount >= REQUIRED_START_CONSECUTIVE_FIXES) {
                        // Confirmed riding: 2 consecutive fixes >= 75%
                        StateTransitionDecision(
                            nextState = AutomaticRideState.CONTRIBUTING,
                            consecutiveHighCount = newHighCount,
                            consecutiveDivergenceCount = 0,
                            firstDivergenceTimestamp = 0L,
                            shouldStartSharing = true,
                            shouldStopSharing = false
                        )
                    } else {
                        // Gathered 1 fix, need 1 more
                        StateTransitionDecision(
                            nextState = AutomaticRideState.POSSIBLY_RIDING,
                            consecutiveHighCount = newHighCount,
                            consecutiveDivergenceCount = 0,
                            firstDivergenceTimestamp = 0L,
                            shouldStartSharing = false,
                            shouldStopSharing = false
                        )
                    }
                } else if (score < CONTINUE_CONFIDENCE_THRESHOLD) {
                    // Dropped below continue threshold -> false alarm, return to waiting
                    StateTransitionDecision(
                        nextState = AutomaticRideState.WAITING_FOR_BUS,
                        consecutiveHighCount = 0,
                        consecutiveDivergenceCount = 0,
                        firstDivergenceTimestamp = 0L,
                        shouldStartSharing = false,
                        shouldStopSharing = false
                    )
                } else {
                    // Moderate confidence (35..74%), remain in POSSIBLY_RIDING without qualifying count
                    StateTransitionDecision(
                        nextState = AutomaticRideState.POSSIBLY_RIDING,
                        consecutiveHighCount = 0,
                        consecutiveDivergenceCount = 0,
                        firstDivergenceTimestamp = 0L,
                        shouldStartSharing = false,
                        shouldStopSharing = false
                    )
                }
            }

            AutomaticRideState.CONTRIBUTING -> {
                if (score < CONTINUE_CONFIDENCE_THRESHOLD) {
                    val newDivCount = consecutiveDivergenceCount + 1
                    val newFirstDivTime = if (firstDivergenceTimestamp == 0L) now else firstDivergenceTimestamp
                    val isTimeout = (now - newFirstDivTime) >= STOP_TIMEOUT_MS

                    if (newDivCount >= REQUIRED_STOP_CONSECUTIVE_FIXES || isTimeout) {
                        // Sustained low evidence: 3 consecutive fixes OR 45s timeout reached
                        StateTransitionDecision(
                            nextState = AutomaticRideState.ENDING_RIDE,
                            consecutiveHighCount = 0,
                            consecutiveDivergenceCount = newDivCount,
                            firstDivergenceTimestamp = newFirstDivTime,
                            shouldStartSharing = false,
                            shouldStopSharing = true
                        )
                    } else {
                        // Temporary GPS dropout / single transient low fix: hold CONTRIBUTING
                        StateTransitionDecision(
                            nextState = AutomaticRideState.CONTRIBUTING,
                            consecutiveHighCount = consecutiveHighCount,
                            consecutiveDivergenceCount = newDivCount,
                            firstDivergenceTimestamp = newFirstDivTime,
                            shouldStartSharing = false,
                            shouldStopSharing = false
                        )
                    }
                } else {
                    // Healthy fix received (score >= 35): recover and reset divergence counters
                    StateTransitionDecision(
                        nextState = AutomaticRideState.CONTRIBUTING,
                        consecutiveHighCount = max(REQUIRED_START_CONSECUTIVE_FIXES, consecutiveHighCount + 1),
                        consecutiveDivergenceCount = 0,
                        firstDivergenceTimestamp = 0L,
                        shouldStartSharing = false,
                        shouldStopSharing = false
                    )
                }
            }

            AutomaticRideState.ENDING_RIDE -> {
                // Cooldown: return to waiting for bus
                StateTransitionDecision(
                    nextState = AutomaticRideState.WAITING_FOR_BUS,
                    consecutiveHighCount = 0,
                    consecutiveDivergenceCount = 0,
                    firstDivergenceTimestamp = 0L,
                    shouldStartSharing = false,
                    shouldStopSharing = false
                )
            }
        }
    }

    /**
     * Processes new student telemetry, evaluates schedule windows, and drives state transitions.
     */
    fun processTelemetryUpdate(
        context: Context,
        location: Location,
        busLocation: LiveLocation?,
        route: Route?,
        schedule: RouteSchedule?
    ) {
        if (!_isAutoSharingEnabled.value) {
            _rideState.value = AutomaticRideState.DISABLED
            return
        }

        val targetBusId = _assignedBusId.value ?: return
        val now = System.currentTimeMillis()

        // 1. Schedule Window Check: Outside operating windows OR on a holiday -> 0% battery usage
        val isWorkingDay = CollegeCalendarService.isWorkingDay()
        val effectiveSchedule = schedule ?: route?.schedules ?: OperatingScheduleService.getSchedule()
        val isWithinWindow = isWorkingDay && ScheduleHelper.isWithinOperatingWindow(effectiveSchedule)

        // 2. Evaluate Ride Confidence
        val evaluation = calculateConfidenceScore(
            studentLat = location.latitude,
            studentLng = location.longitude,
            studentSpeedMps = location.speed,
            studentAccuracy = if (location.hasAccuracy()) location.accuracy else 20.0f,
            busLocation = busLocation,
            route = route,
            consecutiveHighCount = consecutiveHighConfidenceCount
        )

        _currentConfidence.value = evaluation.score
        Log.d(TAG, "Ride evaluation: ${evaluation.reason}, current state=${_rideState.value}")

        val decision = evaluateStateTransition(
            currentState = _rideState.value,
            score = evaluation.score,
            now = now,
            consecutiveHighCount = consecutiveHighConfidenceCount,
            consecutiveDivergenceCount = consecutiveDivergenceCount,
            firstDivergenceTimestamp = firstDivergenceTimestamp,
            isWithinWindow = isWithinWindow
        )

        _rideState.value = decision.nextState
        consecutiveHighConfidenceCount = decision.consecutiveHighCount
        consecutiveDivergenceCount = decision.consecutiveDivergenceCount
        firstDivergenceTimestamp = decision.firstDivergenceTimestamp

        if (decision.shouldStartSharing) {
            Log.i(TAG, "Confidence confirmed (>= 75% for 2 fixes)! Started CONTRIBUTING for $targetBusId")
            StudentLocationAssistant.startSharing(context, targetBusId)
        }
        if (decision.shouldStopSharing) {
            Log.i(TAG, "Sustained divergence detected. Stopped CONTRIBUTING for $targetBusId")
            StudentLocationAssistant.stopSharing()
        }
    }

    private fun findDistanceToRoutePolyline(
        lat: Double,
        lng: Double,
        stops: List<com.college.bustracker.data.model.BusStop>
    ): Double {
        if (stops.size < 2) return Double.MAX_VALUE
        var minDistance = Double.MAX_VALUE

        for (i in 0 until stops.size - 1) {
            val dist = distanceToSegment(
                lat, lng,
                stops[i].latitude, stops[i].longitude,
                stops[i + 1].latitude, stops[i + 1].longitude
            )
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    private fun distanceToSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): Double {
        val distA = LocationThrottler.calculateDistanceMeters(pLat, pLng, aLat, aLng)
        val distB = LocationThrottler.calculateDistanceMeters(pLat, pLng, bLat, bLng)
        val distAB = LocationThrottler.calculateDistanceMeters(aLat, aLng, bLat, bLng)

        if (distAB < 1.0) return distA

        // Projection check using dot product approximation
        val t = ((pLat - aLat) * (bLat - aLat) + (pLng - aLng) * (bLng - aLng)) /
                ((bLat - aLat) * (bLat - aLat) + (bLng - aLng) * (bLng - aLng))

        return when {
            t <= 0.0 -> distA
            t >= 1.0 -> distB
            else -> {
                val projLat = aLat + t * (bLat - aLat)
                val projLng = aLng + t * (bLng - aLng)
                LocationThrottler.calculateDistanceMeters(pLat, pLng, projLat, projLng)
            }
        }
    }
}
