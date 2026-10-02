package com.college.bustracker.ui.student

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.getDisplayState
import com.college.bustracker.util.AutomaticRideManager
import com.college.bustracker.util.AutomaticRideState
import com.college.bustracker.data.repository.StudentPreferences
import com.college.bustracker.util.BusTopicManager
import com.college.bustracker.util.EtaEngine
import com.college.bustracker.util.EtaStatus
import com.college.bustracker.util.LocationThrottler
import com.college.bustracker.util.RouteEtaResult
import com.college.bustracker.util.ScheduleHelper
import com.college.bustracker.util.StopEta
import com.college.bustracker.util.StudentLocationAssistant
import com.college.bustracker.data.model.TemporaryAssignment
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StudentViewModel : ViewModel() {

    private val app = CollegeBusApp.instance
    private val busRepo = app.busRepository
    private val routeRepo = app.routeRepository
    private val swapRepo = app.busSwapRepository
    private val trackingRepo = app.trackingRepository
    private val tempAssignRepo = app.temporaryAssignmentRepository

    private val _buses = MutableStateFlow<List<Bus>>(emptyList())
    val buses: StateFlow<List<Bus>> = _buses.asStateFlow()

    private val _isLoadingBuses = MutableStateFlow(true)
    val isLoadingBuses: StateFlow<Boolean> = _isLoadingBuses.asStateFlow()

    private val _selectedBus = MutableStateFlow<Bus?>(null)
    val selectedBus: StateFlow<Bus?> = _selectedBus.asStateFlow()

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()

    private val _selectedStop = MutableStateFlow<BusStop?>(null)
    val selectedStop: StateFlow<BusStop?> = _selectedStop.asStateFlow()

    private val _liveLocation = MutableStateFlow<LiveLocation?>(null)
    val liveLocation: StateFlow<LiveLocation?> = _liveLocation.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingDisplayState.INACTIVE)
    val trackingState: StateFlow<TrackingDisplayState> = _trackingState.asStateFlow()

    private val _activeSwapForStudent = MutableStateFlow<BusSwap?>(null)
    val activeSwapForStudent: StateFlow<BusSwap?> = _activeSwapForStudent.asStateFlow()

    private val _activeTemporaryAssignment = MutableStateFlow<TemporaryAssignment?>(null)
    val activeTemporaryAssignment: StateFlow<TemporaryAssignment?> = _activeTemporaryAssignment.asStateFlow()

    private val _nextStop = MutableStateFlow<BusStop?>(null)
    val nextStop: StateFlow<BusStop?> = _nextStop.asStateFlow()

    private val _routeEtaResult = MutableStateFlow<RouteEtaResult?>(null)
    val routeEtaResult: StateFlow<RouteEtaResult?> = _routeEtaResult.asStateFlow()

    private val _nextStopEta = MutableStateFlow<StopEta?>(null)
    val nextStopEta: StateFlow<StopEta?> = _nextStopEta.asStateFlow()

    private val _upcomingStopsEta = MutableStateFlow<List<StopEta>>(emptyList())
    val upcomingStopsEta: StateFlow<List<StopEta>> = _upcomingStopsEta.asStateFlow()

    private val _drivers = MutableStateFlow<List<UserProfile>>(emptyList())
    val drivers: StateFlow<List<UserProfile>> = _drivers.asStateFlow()

    private val _driverDisplayInfo = MutableStateFlow<DriverDisplayInfo?>(null)
    val driverDisplayInfo: StateFlow<DriverDisplayInfo?> = _driverDisplayInfo.asStateFlow()

    private var lastCalcLat = 0.0
    private var lastCalcLon = 0.0
    private var lastCalcTimeMs = 0L
    private var previousPassedStopIndex = -1
    private var lastCalculatedSpeedMs = -1.0f
    private var lastNextStopEtaMinutes: Int? = null

    private val allRoutes = MutableStateFlow<List<Route>>(emptyList())

    private val _lastUpdateText = MutableStateFlow("Never")
    val lastUpdateText: StateFlow<String> = _lastUpdateText.asStateFlow()

    val isSharingLocation: StateFlow<Boolean> = StudentLocationAssistant.isSharing
    val contributedBusId: StateFlow<String?> = StudentLocationAssistant.contributedBusId

    val automaticRideState: StateFlow<AutomaticRideState> = AutomaticRideManager.rideState
    val isAutoSharingEnabled: StateFlow<Boolean> = AutomaticRideManager.isAutoSharingEnabled
    val isLocationServicesEnabled: StateFlow<Boolean> = AutomaticRideManager.isLocationServicesEnabled
    val autoAssignedBusId: StateFlow<String?> = AutomaticRideManager.assignedBusId
    val rideConfidence: StateFlow<Int> = AutomaticRideManager.currentConfidence

    private val _isHolidayToday = MutableStateFlow(
        com.college.bustracker.util.CollegeCalendarService.isHoliday()
    )
    val isHolidayToday: StateFlow<Boolean> = _isHolidayToday.asStateFlow()

    private val _todayHolidayName = MutableStateFlow(
        com.college.bustracker.util.CollegeCalendarService.getHolidayName()
    )
    val todayHolidayName: StateFlow<String?> = _todayHolidayName.asStateFlow()

    private var liveTrackingJob: Job? = null

    init {
        AutomaticRideManager.init(app.applicationContext)

        // Collect calendar exceptions to keep holiday evaluation reactive
        viewModelScope.launch {
            app.calendarRepository.getCalendarExceptionsFlow().collect {
                _isHolidayToday.value = com.college.bustracker.util.CollegeCalendarService.isHoliday()
                _todayHolidayName.value = com.college.bustracker.util.CollegeCalendarService.getHolidayName()
            }
        }

        // Single shared cache of active fleet routes from Firestore
        viewModelScope.launch {
            routeRepo.getRoutesFlow().collect { routes ->
                allRoutes.value = routes
                val savedStopId = StudentPreferences.getSavedStopId(app.applicationContext)
                updateCurrentRouteForSelectedBus(preferredStopId = savedStopId)
            }
        }

        // Collect active fleet buses only (excluding non-student test buses)
        viewModelScope.launch {
            busRepo.getBusesFlow().collect { busList ->
                val activeBuses = busList.filter { it.active && !it.busId.startsWith("TEST-", ignoreCase = true) }
                    .sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))
                _buses.value = activeBuses
                _isLoadingBuses.value = false
                updateDriverDisplayInfo()

                val currentSelected = _selectedBus.value
                val savedBusId = StudentPreferences.getSavedBusId(app.applicationContext)
                val savedStopId = StudentPreferences.getSavedStopId(app.applicationContext)
                val matchedSavedBus = if (!savedBusId.isNullOrBlank()) {
                    activeBuses.find { it.busId.equals(savedBusId, ignoreCase = true) }
                } else null

                if (currentSelected == null && activeBuses.isNotEmpty()) {
                    if (matchedSavedBus != null) {
                        selectBus(matchedSavedBus, preferredStopId = savedStopId)
                    } else {
                        selectBus(activeBuses.first())
                    }
                } else if (currentSelected != null && activeBuses.none { it.busId == currentSelected.busId }) {
                    if (activeBuses.isNotEmpty()) {
                        selectBus(activeBuses.first())
                    } else {
                        _selectedBus.value = null
                        _currentRoute.value = null
                        _selectedStop.value = null
                        _liveLocation.value = null
                        _trackingState.value = TrackingDisplayState.INACTIVE
                        syncTopicSubscription()
                        updateDriverDisplayInfo()
                    }
                }
            }
        }

        // Collect active drivers from Firestore
        viewModelScope.launch {
            busRepo.getDriversFlow().collect { driverList ->
                _drivers.value = driverList
                updateDriverDisplayInfo()
            }
        }

        // Continuously react to bus swaps for selected bus & stop
        viewModelScope.launch {
            combine(
                swapRepo.getSwapsFlow(),
                _selectedBus,
                _selectedStop
            ) { swaps, bus, stop ->
                if (bus == null) {
                    null
                } else {
                    swaps.find { swap ->
                        swap.status == "ACTIVE" &&
                        swap.originalBusId == bus.busId &&
                        (stop == null || swap.isAffectingStop(stop.stopId))
                    }
                }
            }.collect { matchingSwap ->
                _activeSwapForStudent.value = matchingSwap
                updateDriverDisplayInfo()
                val bus = _selectedBus.value
                if (matchingSwap != null && matchingSwap.replacementBusId.isNotBlank()) {
                    startListeningToLiveBus(matchingSwap.replacementBusId)
                } else if (bus != null) {
                    startListeningToLiveBus(bus.busId)
                }
            }
        }

        // Collect temporary assignments for the selected bus to resolve effective driver in real time
        viewModelScope.launch {
            _selectedBus.flatMapLatest { bus ->
                if (bus == null) flowOf(null)
                else tempAssignRepo.getAssignmentsForBusFlow(bus.busId).map { list ->
                    val now = tempAssignRepo.getEstimatedServerTime()
                    list.firstOrNull { it.isActive(now) }
                }
            }.collect { activeTemp ->
                _activeTemporaryAssignment.value = activeTemp
                updateDriverDisplayInfo()
            }
        }

        // Ticker for stale timer and relative time formatting
        viewModelScope.launch {
            var previousState = TrackingDisplayState.INACTIVE
            var previousText = ""
            while (true) {
                val loc = _liveLocation.value
                val displayState = loc.getDisplayState()
                if (_trackingState.value != displayState) {
                    _trackingState.value = displayState
                }

                val newText = if (loc != null && loc.lastUpdated > 0L) {
                    ScheduleHelper.formatRelativeTime(loc.lastUpdated)
                } else {
                    "Inactive"
                }
                if (newText != previousText) {
                    previousText = newText
                    _lastUpdateText.value = newText
                }

                if (displayState != previousState) {
                    previousState = displayState
                    updateEtaCalculation(loc, force = true)
                }

                delay(5000L)
            }
        }
    }

    fun selectBus(bus: Bus, preferredStopId: String? = null) {
        // If auto sharing is enabled and selected bus changes, update assigned bus in AutomaticRideManager
        if (AutomaticRideManager.isAutoSharingEnabled.value && AutomaticRideManager.assignedBusId.value != bus.busId) {
            AutomaticRideManager.changeAssignedBus(app.applicationContext, bus.busId)
        }

        // If sharing location for a different bus, stop sharing
        if (StudentLocationAssistant.isSharing.value && StudentLocationAssistant.contributedBusId.value != bus.busId) {
            StudentLocationAssistant.stopSharing()
        }

        _selectedBus.value = bus
        previousPassedStopIndex = -1
        lastCalculatedSpeedMs = -1.0f
        lastNextStopEtaMinutes = null
        lastCalcTimeMs = 0L

        updateCurrentRouteForSelectedBus(preferredStopId)
    }

    fun selectBusById(busId: String, preferredStopId: String? = null) {
        val bus = _buses.value.find { it.busId.equals(busId, ignoreCase = true) }
        if (bus != null) {
            selectBus(bus, preferredStopId)
        }
    }

    private fun updateCurrentRouteForSelectedBus(preferredStopId: String? = null) {
        val bus = _selectedBus.value ?: return
        val routes = allRoutes.value
        val r = routes.find { it.active && it.routeId == bus.routeId }
        val sanitizedRoute = r?.copy(
            stops = r.stops.filter { it.active }.sortedBy { it.order }
        )
        _currentRoute.value = sanitizedRoute

        val currentSelected = _selectedStop.value
        val savedStopId = StudentPreferences.getSavedStopId(app.applicationContext)
        val targetStopId = preferredStopId ?: currentSelected?.stopId ?: savedStopId
        if (sanitizedRoute?.stops?.isNotEmpty() == true) {
            val matchingStop = if (!targetStopId.isNullOrBlank()) {
                sanitizedRoute.stops.find { it.stopId.equals(targetStopId, ignoreCase = true) }
            } else null
            _selectedStop.value = matchingStop ?: sanitizedRoute.stops.first()
        } else {
            _selectedStop.value = null
        }
        syncTopicSubscription()
        updateEtaCalculation(_liveLocation.value, force = true)
        updateDriverDisplayInfo()
    }

    private fun updateDriverDisplayInfo() {
        _driverDisplayInfo.value = DriverInfoLogic.resolveDriver(
            selectedBus = _selectedBus.value,
            activeSwap = _activeSwapForStudent.value,
            allBuses = _buses.value,
            allDrivers = _drivers.value,
            activeTemporaryAssignment = _activeTemporaryAssignment.value,
            nowMillis = tempAssignRepo.getEstimatedServerTime()
        )
    }

    fun selectStop(stop: BusStop) {
        _selectedStop.value = stop
        syncTopicSubscription()
    }

    private fun syncTopicSubscription() {
        val busId = _selectedBus.value?.busId
        val stopId = _selectedStop.value?.stopId
        BusTopicManager.updateSubscription(app.applicationContext, busId, stopId)
    }

    fun startListeningToLiveBus(busId: String) {
        liveTrackingJob?.cancel()
        liveTrackingJob = viewModelScope.launch {
            trackingRepo.getLiveLocationFlow(busId).collect { loc ->
                _liveLocation.value = loc
                updateEtaCalculation(loc, force = false)
            }
        }
    }

    fun stopListeningToLiveBus() {
        liveTrackingJob?.cancel()
        liveTrackingJob = null
    }

    private var lastCalculatedDirection: com.college.bustracker.util.RouteDirection? = null

    private fun updateEtaCalculation(loc: LiveLocation?, force: Boolean = false) {
        val route = _currentRoute.value
        val rawStops = route?.stops ?: emptyList()
        val trackingState = loc.getDisplayState()

        if (rawStops.isEmpty()) {
            _routeEtaResult.value = RouteEtaResult(
                nextStopEta = null,
                upcomingStops = emptyList(),
                status = EtaStatus.NO_ROUTE,
                statusMessage = "ETA unavailable",
                isReliable = false
            )
            _nextStopEta.value = null
            _nextStop.value = null
            _upcomingStopsEta.value = emptyList()
            lastCalculatedSpeedMs = -1.0f
            lastNextStopEtaMinutes = null
            return
        }

        val schedule = route?.schedules ?: com.college.bustracker.util.OperatingScheduleService.getSchedule()
        val currentDirection = ScheduleHelper.determineRouteDirection(schedule)
        if (lastCalculatedDirection != currentDirection) {
            lastCalculatedDirection = currentDirection
            previousPassedStopIndex = -1
        }

        val stops = if (currentDirection == com.college.bustracker.util.RouteDirection.COLLEGE_TO_STOPS) {
            rawStops.reversed()
        } else {
            rawStops
        }

        val now = System.currentTimeMillis()
        val lat = loc?.latitude ?: 0.0
        val lon = loc?.longitude ?: 0.0

        if (!force && trackingState == TrackingDisplayState.ACTIVE) {
            val distMoved = LocationThrottler.calculateDistanceMeters(lastCalcLat, lastCalcLon, lat, lon)
            val timeElapsed = now - lastCalcTimeMs
            if (distMoved < 5.0 && timeElapsed < 3000L) {
                return
            }
        }

        val result = EtaEngine.computeRouteEta(
            busLat = lat,
            busLon = lon,
            busSpeedMs = loc?.speed ?: 0.0f,
            busBearing = loc?.bearing ?: 0.0f,
            trackingState = trackingState,
            stops = stops,
            previousPassedIndex = previousPassedStopIndex,
            previousSpeedMs = lastCalculatedSpeedMs,
            previousEtaMinutes = lastNextStopEtaMinutes
        )

        _routeEtaResult.value = result
        _nextStopEta.value = result.nextStopEta
        _nextStop.value = result.nextStopEta?.stop
        _upcomingStopsEta.value = result.upcomingStops

        lastCalculatedSpeedMs = loc?.speed ?: 0.0f
        lastNextStopEtaMinutes = result.nextStopEta?.etaMinutes

        lastCalcLat = lat
        lastCalcLon = lon
        lastCalcTimeMs = now

        val nextIdx = result.nextStopEta?.let { n -> stops.indexOfFirst { it.stopId == n.stop.stopId } } ?: -1
        if (nextIdx > 0) {
            previousPassedStopIndex = max(previousPassedStopIndex, nextIdx - 1)
        }

        android.util.Log.d(
            "EtaEngineDebug",
            "ETA DEBUG: bus=${_selectedBus.value?.busId} loc=($lat,$lon) nextStop=${result.nextStopEta?.stop?.stopId} ${result.nextStopEta?.stop?.name} stopLoc=(${result.nextStopEta?.stop?.latitude},${result.nextStopEta?.stop?.longitude}) distance=${result.nextStopEta?.distanceMeters}m speed=${(loc?.speed ?: 0f)*3.6f}km/h eta=${result.nextStopEta?.etaFormatted} direction=$currentDirection stopIdx=$nextIdx"
        )
    }

    fun startSharingLocation(context: Context) {
        val busId = _selectedBus.value?.busId ?: return
        StudentLocationAssistant.startSharing(context, busId)
    }

    fun stopSharingLocation() {
        StudentLocationAssistant.stopSharing()
        AutomaticRideManager.disableAutoSharing(app.applicationContext)
    }

    fun enableAutoSharing(busId: String) {
        val schedule = _currentRoute.value?.schedules
        AutomaticRideManager.enableAutoSharing(app.applicationContext, busId, schedule)
    }

    fun disableAutoSharing() {
        AutomaticRideManager.disableAutoSharing(app.applicationContext)
    }

    fun changeAutoSharingBus(busId: String) {
        val schedule = _currentRoute.value?.schedules
        AutomaticRideManager.changeAssignedBus(app.applicationContext, busId, schedule)
    }

    override fun onCleared() {
        // If automatic sharing is enabled, keep background contributor alive during confirmed rides
        if (!AutomaticRideManager.isAutoSharingEnabled.value) {
            StudentLocationAssistant.stopSharing()
        }
        stopListeningToLiveBus()
        super.onCleared()
    }
}
