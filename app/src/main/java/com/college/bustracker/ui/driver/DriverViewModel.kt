package com.college.bustracker.ui.driver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.service.AutoTrackingScheduler
import com.college.bustracker.service.TrackingServiceHelper
import com.college.bustracker.util.ScheduleHelper
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DriverViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CollegeBusApp
    private val busRepo = app.busRepository
    private val routeRepo = app.routeRepository
    private val swapRepo = app.busSwapRepository
    private val stopRequestRepo = app.stopRequestRepository
    private val tempAssignRepo = app.temporaryAssignmentRepository
    private val networkMonitor = app.networkMonitor
    private val announcementRepo = app.announcementRepository

    private val _assignedBus = MutableStateFlow<Bus?>(null)
    val assignedBus: StateFlow<Bus?> = _assignedBus.asStateFlow()

    private val _assignedRoute = MutableStateFlow<Route?>(null)
    val assignedRoute: StateFlow<Route?> = _assignedRoute.asStateFlow()

    val driverAnnouncements: StateFlow<List<com.college.bustracker.data.model.Announcement>> = combine(
        announcementRepo.getAnnouncementsFlow(),
        _assignedBus,
        _assignedRoute
    ) { list, bus, route ->
        val now = System.currentTimeMillis()
        list.filter { it.isRelevantToDriver(bus?.busId, route?.routeId, now) }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _capturedStopLocation = MutableStateFlow<android.location.Location?>(null)
    val capturedStopLocation: StateFlow<android.location.Location?> = _capturedStopLocation.asStateFlow()

    private val _isSubmittingStop = MutableStateFlow(false)
    val isSubmittingStop: StateFlow<Boolean> = _isSubmittingStop.asStateFlow()

    private val _stopSubmissionMessage = MutableStateFlow<String?>(null)
    val stopSubmissionMessage: StateFlow<String?> = _stopSubmissionMessage.asStateFlow()

    private val _stopSubmissionError = MutableStateFlow<String?>(null)
    val stopSubmissionError: StateFlow<String?> = _stopSubmissionError.asStateFlow()

    private val _isAutoTrackingEnabled = MutableStateFlow(
        AutoTrackingScheduler.isAutoTrackingEnabled(application)
    )
    val isAutoTrackingEnabled: StateFlow<Boolean> = _isAutoTrackingEnabled.asStateFlow()

    val isTrackingLive = TrackingServiceHelper.isTracking
    val gpsStatus = TrackingServiceHelper.gpsStatus
    val isInternetConnected = networkMonitor.isConnected

    private val _isLocationServicesEnabled = MutableStateFlow(
        com.college.bustracker.util.DriverConnectivityHelper.isLocationEnabled(application)
    )
    val isLocationServicesEnabled: StateFlow<Boolean> = _isLocationServicesEnabled.asStateFlow()

    private val _isMobileDataEnabled = MutableStateFlow(
        com.college.bustracker.util.DriverConnectivityHelper.isMobileDataEnabled(application)
    )
    val isMobileDataEnabled: StateFlow<Boolean> = _isMobileDataEnabled.asStateFlow()

    private val _lastUpdateText = MutableStateFlow("Never")
    val lastUpdateText: StateFlow<String> = _lastUpdateText.asStateFlow()

    private val _allBuses = MutableStateFlow<List<Bus>>(emptyList())
    val allBuses: StateFlow<List<Bus>> = _allBuses.asStateFlow()

    private val _swapSuccessMessage = MutableStateFlow<String?>(null)
    val swapSuccessMessage: StateFlow<String?> = _swapSuccessMessage.asStateFlow()

    private val _swapErrorMessage = MutableStateFlow<String?>(null)
    val swapErrorMessage: StateFlow<String?> = _swapErrorMessage.asStateFlow()

    private val _isSwapLoading = MutableStateFlow(false)
    val isSwapLoading: StateFlow<Boolean> = _isSwapLoading.asStateFlow()

    private val _activeSwapForDriver = MutableStateFlow<BusSwap?>(null)
    val activeSwapForDriver: StateFlow<BusSwap?> = _activeSwapForDriver.asStateFlow()

    private val _holidayWarningMessage = MutableStateFlow<String?>(null)
    val holidayWarningMessage: StateFlow<String?> = _holidayWarningMessage.asStateFlow()

    private val _todayHolidayName = MutableStateFlow<String?>(
        com.college.bustracker.util.CollegeCalendarService.getHolidayName()
    )
    val todayHolidayName: StateFlow<String?> = _todayHolidayName.asStateFlow()

    private val _isHolidayToday = MutableStateFlow(
        com.college.bustracker.util.CollegeCalendarService.isHoliday()
    )
    val isHolidayToday: StateFlow<Boolean> = _isHolidayToday.asStateFlow()

    private val _activeReplacementForDriver = MutableStateFlow<TemporaryAssignment?>(null)
    val activeReplacementForDriver: StateFlow<TemporaryAssignment?> = _activeReplacementForDriver.asStateFlow()

    private val _operatingAsTemporaryBus = MutableStateFlow<TemporaryAssignment?>(null)
    val operatingAsTemporaryBus: StateFlow<TemporaryAssignment?> = _operatingAsTemporaryBus.asStateFlow()

    private val _eligibleReplacementDrivers = MutableStateFlow<List<UserProfile>>(emptyList())
    val eligibleReplacementDrivers: StateFlow<List<UserProfile>> = _eligibleReplacementDrivers.asStateFlow()

    private val _isReplacementLoading = MutableStateFlow(false)
    val isReplacementLoading: StateFlow<Boolean> = _isReplacementLoading.asStateFlow()

    private val _replacementMessage = MutableStateFlow<String?>(null)
    val replacementMessage: StateFlow<String?> = _replacementMessage.asStateFlow()

    private val _replacementError = MutableStateFlow<String?>(null)
    val replacementError: StateFlow<String?> = _replacementError.asStateFlow()

    val operatingSchedule: StateFlow<RouteSchedule> = com.college.bustracker.util.OperatingScheduleService.operatingSchedule

    private val _isWindowOverriddenOff = MutableStateFlow(
        AutoTrackingScheduler.isWindowOverriddenOff(app)
    )
    val isWindowOverriddenOff: StateFlow<Boolean> = _isWindowOverriddenOff.asStateFlow()

    fun refreshWindowOverrideState() {
        _isWindowOverriddenOff.value = AutoTrackingScheduler.isWindowOverriddenOff(app)
    }

    init {
        // Collect calendar exceptions to keep today's evaluation reactive
        viewModelScope.launch {
            app.calendarRepository.getCalendarExceptionsFlow().collect {
                val isHoliday = com.college.bustracker.util.CollegeCalendarService.isHoliday()
                val holidayName = com.college.bustracker.util.CollegeCalendarService.getHolidayName()
                _isHolidayToday.value = isHoliday
                _todayHolidayName.value = holidayName
                refreshWindowOverrideState()

                if (isHoliday && isTrackingLive.value) {
                    stopTracking("CALENDAR_HOLIDAY_OVERRIDE")
                } else if (!isHoliday && !isTrackingLive.value && _isAutoTrackingEnabled.value) {
                    AutoTrackingScheduler.checkAndTriggerAutoTracking(getApplication())
                }
            }
        }
        // Collect assigned bus strictly based on the logged-in driver's profile
        viewModelScope.launch {
            combine(
                busRepo.getBusesFlow(),
                routeRepo.getRoutesFlow(),
                app.authRepository.currentProfile
            ) { buses, routes, authProfile ->
                _allBuses.value = buses
                val targetBusId = authProfile?.assignedBusId?.trim()
                val bus = if (!targetBusId.isNullOrBlank()) {
                    buses.find { it.busId.equals(targetBusId, ignoreCase = true) }
                        ?: Bus(
                            busId = targetBusId,
                            busNumber = targetBusId,
                            routeId = "ROUTE-01",
                            driverId = authProfile.uid,
                            driverName = authProfile.name,
                            active = true
                        )
                } else null

                val route = if (bus != null) {
                    routes.find { it.routeId.equals(bus.routeId, ignoreCase = true) }
                } else null

                Pair(bus, route)
            }.collect { (bus, route) ->
                _assignedBus.value = bus
                _assignedRoute.value = route

                if (_isAutoTrackingEnabled.value && bus != null) {
                    val activeUid = app.authRepository.getActiveProfile()?.uid
                        ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        ?: bus.driverId
                        ?: ""
                    android.util.Log.i("DriverViewModel", "[DRIVER-UI] Auto tracking active on UI init: bus=${bus.busId}, driver=$activeUid")
                    val schedule = com.college.bustracker.util.OperatingScheduleService.getSchedule()
                    AutoTrackingScheduler.setAutoTrackingEnabled(
                        context = getApplication(),
                        enabled = true,
                        busId = bus.busId,
                        routeId = bus.routeId,
                        driverId = activeUid,
                        schedule = schedule
                    )
                    AutoTrackingScheduler.checkAndTriggerAutoTracking(getApplication())
                }
            }
        }

        // Live ticker for "Last updated X seconds ago"
        viewModelScope.launch {
            while (true) {
                val ts = TrackingServiceHelper.lastUpdateTimestamp.value
                _lastUpdateText.value = ScheduleHelper.formatRelativeTime(ts)
                delay(5000L)
            }
        }

        // Observe active swap reported for the current driver's assigned bus
        viewModelScope.launch {
            combine(
                swapRepo.getSwapsFlow(),
                app.authRepository.currentProfile,
                _assignedBus
            ) { swaps, authProfile, assignedBus ->
                val assignedBusId = authProfile?.assignedBusId ?: assignedBus?.busId ?: ""
                swaps.find { swap ->
                    swap.status == BusSwap.STATUS_ACTIVE &&
                    swap.originalBusId.equals(assignedBusId, ignoreCase = true)
                }
            }.collect { matchingSwap ->
                _activeSwapForDriver.value = matchingSwap
            }
        }

        // Collect eligible replacement drivers (active == true, role == DRIVER, not current driver)
        viewModelScope.launch {
            combine(
                busRepo.getDriversFlow(),
                app.authRepository.currentProfile
            ) { drivers, currentProfile ->
                val currentUid = currentProfile?.uid ?: ""
                drivers.filter { it.active && it.role == UserRole.DRIVER && it.uid != currentUid }
                    .sortedBy { it.name }
            }.collect { eligible ->
                _eligibleReplacementDrivers.value = eligible
            }
        }

        // Observe temporary replacements for the current driver's assigned bus and for this driver
        viewModelScope.launch {
            combine(
                tempAssignRepo.getAllAssignmentsFlow(),
                app.authRepository.currentProfile,
                _assignedBus
            ) { assignments, authProfile, assignedBus ->
                val currentUid = authProfile?.uid ?: assignedBus?.driverId ?: ""
                val currentBusId = authProfile?.assignedBusId ?: assignedBus?.busId ?: ""
                val now = tempAssignRepo.getEstimatedServerTime()

                // Active or upcoming replacement where current driver's bus is replaced
                val outgoing = assignments.firstOrNull {
                    it.busId.equals(currentBusId, ignoreCase = true) &&
                    (it.isActive(now) || it.isUpcoming(now))
                }

                // Active replacement where current driver is the temporary replacement driver
                val incoming = assignments.firstOrNull {
                    it.replacementDriverUid == currentUid && it.isActive(now)
                }

                Pair(outgoing, incoming)
            }.collect { (outgoing, incoming) ->
                _activeReplacementForDriver.value = outgoing
                _operatingAsTemporaryBus.value = incoming
            }
        }

        // Collect driver announcements and notify driver status bar immediately
        viewModelScope.launch {
            driverAnnouncements.collect { list ->
                com.college.bustracker.util.AnnouncementNotificationHelper.notifyIfNew(getApplication(), list)
            }
        }

        // Monitor GPS and Mobile Data connectivity during driver shift window
        viewModelScope.launch {
            while (true) {
                val context = getApplication<Application>()
                val locEnabled = com.college.bustracker.util.DriverConnectivityHelper.isLocationEnabled(context)
                val dataEnabled = com.college.bustracker.util.DriverConnectivityHelper.isMobileDataEnabled(context)
                _isLocationServicesEnabled.value = locEnabled
                _isMobileDataEnabled.value = dataEnabled

                val currentSchedule = operatingSchedule.value
                val isHoliday = _isHolidayToday.value
                val isOperatingWindow = (ScheduleHelper.isWithinOperatingWindow(currentSchedule) && !isHoliday) || isTrackingLive.value
                val busNum = _assignedBus.value?.busNumber ?: "Bus"

                if (isOperatingWindow) {
                    if (!locEnabled) {
                        com.college.bustracker.util.DriverConnectivityHelper.tryEnableLocationProgrammatically(context)
                        com.college.bustracker.util.DriverConnectivityHelper.showLocationDisabledNotification(context, busNum)
                    }
                    if (!dataEnabled) {
                        com.college.bustracker.util.DriverConnectivityHelper.tryEnableMobileData(context)
                        com.college.bustracker.util.DriverConnectivityHelper.showMobileDataDisabledNotification(context, busNum)
                    }
                }

                delay(4000L)
            }
        }
    }

    fun toggleAutoTracking(enabled: Boolean) {
        val bus = _assignedBus.value ?: return
        val route = _assignedRoute.value
        _isAutoTrackingEnabled.value = enabled

        val activeUid = app.authRepository.getActiveProfile()?.uid
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: bus.driverId
            ?: ""

        android.util.Log.i("DriverViewModel", "[DRIVER-UI] toggleAutoTracking: enabled=$enabled, bus=${bus.busId}, driver=$activeUid")
        val effectiveSchedule = com.college.bustracker.util.OperatingScheduleService.getSchedule()
        AutoTrackingScheduler.setAutoTrackingEnabled(
            context = getApplication(),
            enabled = enabled,
            busId = bus.busId,
            routeId = bus.routeId,
            driverId = activeUid,
            schedule = effectiveSchedule
        )
    }

    fun startManualTracking() {
        if (com.college.bustracker.util.CollegeCalendarService.isHoliday()) {
            val name = com.college.bustracker.util.CollegeCalendarService.getHolidayName() ?: "College Holiday"
            _holidayWarningMessage.value = "Today is a college holiday ($name). Tracking unavailable."
            return
        }

        val now = tempAssignRepo.getEstimatedServerTime()
        val activeReplacement = _activeReplacementForDriver.value
        if (activeReplacement != null && activeReplacement.isActive(now)) {
            val repName = activeReplacement.replacementDriverName ?: "Replacement Driver"
            val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(activeReplacement.endAt))
            _holidayWarningMessage.value = "You are currently replaced by $repName until $timeFmt. Tracking for your permanent bus is disabled."
            return
        }

        val operatingTemp = _operatingAsTemporaryBus.value
        val activeUid = app.authRepository.getActiveProfile()?.uid ?: ""

        if (operatingTemp != null && operatingTemp.isActive(now)) {
            val targetBusId = operatingTemp.busId
            val foundBus = _allBuses.value.firstOrNull { it.busId.equals(targetBusId, ignoreCase = true) }
            val routeId = foundBus?.routeId ?: ""
            TrackingServiceHelper.startTracking(
                context = getApplication(),
                busId = targetBusId,
                routeId = routeId,
                driverId = activeUid,
                mode = "MANUAL"
            )
        } else {
            val bus = _assignedBus.value ?: return
            TrackingServiceHelper.startTracking(
                context = getApplication(),
                busId = bus.busId,
                routeId = bus.routeId,
                driverId = if (activeUid.isNotBlank()) activeUid else (bus.driverId ?: ""),
                mode = "MANUAL"
            )
        }
        _isWindowOverriddenOff.value = false
    }

    fun dismissHolidayWarning() {
        _holidayWarningMessage.value = null
    }

    fun stopTracking(reason: String = "USER_MANUAL_STOP") {
        TrackingServiceHelper.stopTracking(getApplication(), reason, "DriverViewModel.stopTracking")
        _isWindowOverriddenOff.value = AutoTrackingScheduler.isWindowOverriddenOff(getApplication())
    }

    fun onLogout() {
        _isAutoTrackingEnabled.value = false
        AutoTrackingScheduler.onDriverLogout(getApplication())
        TrackingServiceHelper.stopTracking(getApplication(), "DRIVER_LOGGED_OUT", "DriverViewModel.onLogout")
    }

    fun reportBusSwap(
        replacementBusId: String,
        scopeType: String = BusSwap.SCOPE_ALL_STOPS,
        affectedStopIds: List<String> = emptyList(),
        optionalMessage: String = "",
        onSuccess: () -> Unit
    ) {
        val originalBus = _assignedBus.value
        if (originalBus == null) {
            _swapErrorMessage.value = "No assigned bus found for current driver."
            return
        }

        if (_activeSwapForDriver.value != null) {
            _swapErrorMessage.value = "An active bus swap alert already exists for ${originalBus.busNumber}. Please edit or cancel the active alert."
            return
        }

        if (replacementBusId.isBlank()) {
            _swapErrorMessage.value = "Please select a valid replacement bus."
            return
        }

        if (replacementBusId.equals(originalBus.busId, ignoreCase = true) || replacementBusId.equals(originalBus.busNumber, ignoreCase = true)) {
            _swapErrorMessage.value = "Replacement bus cannot be the same as the original assigned bus."
            return
        }

        if (scopeType == BusSwap.SCOPE_SELECTED_STOPS && affectedStopIds.isEmpty()) {
            _swapErrorMessage.value = "Please select at least one route stop for selected stops scope."
            return
        }

        val activeProfile = app.authRepository.getActiveProfile()
        val driverUid = activeProfile?.uid ?: originalBus.driverId ?: ""
        val driverName = activeProfile?.name ?: originalBus.driverName ?: "Driver"
        val swap = BusSwap(
            swapId = "SWAP-${System.currentTimeMillis()}",
            originalBusId = originalBus.busId,
            replacementBusId = replacementBusId,
            routeId = originalBus.routeId,
            driverId = driverUid,
            driverName = driverName,
            scopeType = scopeType,
            affectedStopIds = if (scopeType == BusSwap.SCOPE_ALL_STOPS) emptyList() else affectedStopIds,
            message = optionalMessage.ifBlank { "Bus swap reported: ${originalBus.busNumber} → $replacementBusId" },
            status = BusSwap.STATUS_ACTIVE,
            date = LocalDate.now().toString(),
            createdAt = System.currentTimeMillis(),
            createdByUid = driverUid
        )

        _isSwapLoading.value = true
        _swapErrorMessage.value = null
        viewModelScope.launch {
            val result = swapRepo.reportSwap(swap)
            _isSwapLoading.value = false
            result.onSuccess {
                val scopeSummary = if (scopeType == BusSwap.SCOPE_ALL_STOPS) "All stops" else "${affectedStopIds.size} stop(s)"
                _swapSuccessMessage.value = "Bus swap alert activated ($scopeSummary). Status: ACTIVE"
                onSuccess()
            }.onFailure { e ->
                _swapErrorMessage.value = e.message ?: "Failed to report bus swap."
            }
        }
    }

    fun updateBusSwap(
        swapId: String,
        replacementBusId: String,
        scopeType: String,
        affectedStopIds: List<String>,
        optionalMessage: String,
        onSuccess: () -> Unit
    ) {
        val originalBus = _assignedBus.value
        val activeSwap = _activeSwapForDriver.value

        if (activeSwap == null || activeSwap.swapId != swapId) {
            _swapErrorMessage.value = "Active bus swap alert not found."
            return
        }

        if (replacementBusId.isBlank()) {
            _swapErrorMessage.value = "Please select a valid replacement bus."
            return
        }

        if (originalBus != null && (replacementBusId.equals(originalBus.busId, ignoreCase = true) || replacementBusId.equals(originalBus.busNumber, ignoreCase = true))) {
            _swapErrorMessage.value = "Replacement bus cannot be the same as the original assigned bus."
            return
        }

        if (scopeType == BusSwap.SCOPE_SELECTED_STOPS && affectedStopIds.isEmpty()) {
            _swapErrorMessage.value = "Please select at least one route stop for selected stops scope."
            return
        }

        val updatedSwap = activeSwap.copy(
            replacementBusId = replacementBusId,
            scopeType = scopeType,
            affectedStopIds = if (scopeType == BusSwap.SCOPE_ALL_STOPS) emptyList() else affectedStopIds,
            message = optionalMessage.ifBlank { "Bus swap reported: ${originalBus?.busNumber ?: activeSwap.originalBusId} → $replacementBusId" },
            updatedAt = System.currentTimeMillis()
        )

        _isSwapLoading.value = true
        _swapErrorMessage.value = null
        viewModelScope.launch {
            val result = swapRepo.updateSwap(updatedSwap)
            _isSwapLoading.value = false
            result.onSuccess {
                _swapSuccessMessage.value = "Bus swap alert updated successfully."
                onSuccess()
            }.onFailure { e ->
                _swapErrorMessage.value = e.message ?: "Failed to update bus swap."
            }
        }
    }

    fun cancelSwap(
        swap: BusSwap,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        val currentProfile = app.authRepository.getActiveProfile()
        val bus = _assignedBus.value
        val driverUid = currentProfile?.uid ?: bus?.driverId ?: ""
        val assignedBusId = currentProfile?.assignedBusId ?: bus?.busId ?: ""

        // Backend authorization & client guard
        if (swap.driverId != driverUid && !swap.originalBusId.equals(assignedBusId, ignoreCase = true)) {
            val error = SecurityException("Driver is not authorized to cancel this swap. Only the driver assigned to ${swap.originalBusId} who created this swap can cancel it.")
            _swapErrorMessage.value = error.message
            onResult?.invoke(Result.failure(error))
            return
        }

        _isSwapLoading.value = true
        _swapErrorMessage.value = null
        viewModelScope.launch {
            val result = swapRepo.cancelSwap(swap.swapId, driverUid)
            _isSwapLoading.value = false
            result.onSuccess {
                _swapSuccessMessage.value = "Bus swap alert cancelled successfully."
                onResult?.invoke(Result.success(Unit))
            }.onFailure { e ->
                _swapErrorMessage.value = e.message ?: "Failed to cancel bus swap."
                onResult?.invoke(Result.failure(e))
            }
        }
    }

    fun clearSwapMessage() {
        _swapSuccessMessage.value = null
    }

    fun clearSwapErrorMessage() {
        _swapErrorMessage.value = null
    }

    fun clearCapturedStopLocation() {
        _capturedStopLocation.value = null
    }

    fun clearStopSubmissionFeedback() {
        _stopSubmissionMessage.value = null
        _stopSubmissionError.value = null
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun captureCurrentStopLocation(onResult: (Boolean, String?) -> Unit) {
        val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(app)
        try {
            fusedClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { loc ->
                if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
                    _capturedStopLocation.value = loc
                    onResult(true, null)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null && lastLoc.latitude != 0.0 && lastLoc.longitude != 0.0) {
                            _capturedStopLocation.value = lastLoc
                            onResult(true, null)
                        } else {
                            onResult(false, "Could not acquire GPS fix. Please ensure Location is ON.")
                        }
                    }.addOnFailureListener {
                        onResult(false, "Failed to get location: ${it.message}")
                    }
                }
            }.addOnFailureListener { e ->
                onResult(false, "GPS capture failed: ${e.message}")
            }
        } catch (e: SecurityException) {
            onResult(false, "Location permission not granted")
        } catch (e: Exception) {
            onResult(false, "Location error: ${e.message}")
        }
    }

    fun submitStopRequest(stopName: String) {
        val loc = _capturedStopLocation.value
        if (loc == null) {
            _stopSubmissionError.value = "No GPS fix captured"
            return
        }
        val trimmedName = stopName.trim()
        if (trimmedName.isBlank()) {
            _stopSubmissionError.value = "Please enter a stop name"
            return
        }
        val authProfile = app.authRepository.currentProfile.value
        val bus = _assignedBus.value
        val route = _assignedRoute.value

        val driverUid = authProfile?.uid ?: ""
        val driverId = authProfile?.customId ?: ""
        val driverName = authProfile?.name ?: "Driver"
        val busId = authProfile?.assignedBusId ?: bus?.busId ?: ""
        val routeId = bus?.routeId ?: route?.routeId ?: ""

        if (driverUid.isBlank() || busId.isBlank()) {
            _stopSubmissionError.value = "Cannot submit: Driver is not assigned to an active bus"
            return
        }

        val requestId = "req_${System.currentTimeMillis()}_${driverId.ifBlank { "drv" }}"
        val request = com.college.bustracker.data.model.StopRequest(
            requestId = requestId,
            driverUid = driverUid,
            driverId = driverId,
            driverName = driverName,
            busId = busId,
            routeId = routeId,
            proposedStopName = trimmedName,
            latitude = loc.latitude,
            longitude = loc.longitude,
            status = com.college.bustracker.data.model.StopRequest.STATUS_PENDING,
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            _isSubmittingStop.value = true
            _stopSubmissionError.value = null
            val result = stopRequestRepo.submitStopRequest(request)
            _isSubmittingStop.value = false
            if (result.isSuccess) {
                _stopSubmissionMessage.value = "Stop request submitted for Admin review!"
                _capturedStopLocation.value = null
            } else {
                _stopSubmissionError.value = result.exceptionOrNull()?.message ?: "Failed to submit request"
            }
        }
    }

    fun activateTemporaryReplacement(
        replacementDriverUid: String,
        startAt: Long,
        endAt: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        val bus = _assignedBus.value
        val currentProfile = app.authRepository.getActiveProfile()
        if (bus == null || currentProfile == null) {
            onResult(false, "Driver profile or assigned bus unavailable.")
            return
        }

        val targetDriver = _eligibleReplacementDrivers.value.find { it.uid == replacementDriverUid }
        if (targetDriver == null) {
            onResult(false, "Selected replacement driver is not eligible.")
            return
        }

        viewModelScope.launch {
            _isReplacementLoading.value = true
            _replacementError.value = null
            _replacementMessage.value = null

            val result = tempAssignRepo.createTemporaryAssignment(
                busId = bus.busId,
                replacementDriverUid = replacementDriverUid,
                startAt = startAt,
                endAt = endAt,
                callerUid = currentProfile.uid,
                callerRole = "DRIVER",
                permanentDriverUid = currentProfile.uid,
                permanentDriverId = currentProfile.customId.ifBlank { currentProfile.uid },
                permanentDriverName = currentProfile.name,
                replacementDriverProfile = targetDriver
            )

            _isReplacementLoading.value = false
            if (result.isSuccess) {
                _replacementMessage.value = "Temporary replacement activated for ${bus.busNumber}!"
                onResult(true, null)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to activate temporary replacement."
                _replacementError.value = err
                onResult(false, err)
            }
        }
    }

    fun cancelTemporaryReplacement(assignmentId: String, onResult: (Boolean, String?) -> Unit) {
        val busId = _assignedBus.value?.busId ?: ""
        val currentProfile = app.authRepository.getActiveProfile()
        val driverUid = currentProfile?.uid ?: ""
        viewModelScope.launch {
            _isReplacementLoading.value = true
            _replacementError.value = null
            val result = tempAssignRepo.cancelTemporaryAssignment(
                assignmentId = assignmentId,
                busId = busId,
                cancelledByUid = driverUid,
                cancelledByRole = "DRIVER"
            )
            _isReplacementLoading.value = false
            if (result.isSuccess) {
                _replacementMessage.value = "Temporary replacement cancelled."
                onResult(true, null)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to cancel temporary replacement."
                _replacementError.value = err
                onResult(false, err)
            }
        }
    }

    companion object {
        /**
         * Dynamically filters the production fleet to determine eligible replacement buses:
         * 1. Only active buses (bus.active == true).
         * 2. Excludes currently assigned bus (matching both busId and busNumber).
         * 3. Sorted in numerical order (BUS 1, BUS 2 ... BUS 10, BUS 25).
         * 4. Returns empty list if no other active buses exist (NEVER hardcodes fallbacks).
         */
        fun filterEligibleReplacementBuses(fleet: List<Bus>, currentBus: Bus?): List<Bus> {
            val currentBusId = currentBus?.busId?.trim()?.uppercase() ?: ""
            val currentBusNumber = currentBus?.busNumber?.trim()?.uppercase() ?: ""
            return fleet
                .filter { bus ->
                    bus.active &&
                    bus.busId.trim().uppercase() != currentBusId &&
                    bus.busNumber.trim().uppercase() != currentBusNumber &&
                    (currentBusId.isBlank() || !bus.busNumber.trim().equals(currentBusId, ignoreCase = true)) &&
                    (currentBusNumber.isBlank() || !bus.busId.trim().equals(currentBusNumber, ignoreCase = true))
                }
                .sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))
        }
    }
}
