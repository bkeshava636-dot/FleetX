package com.college.bustracker.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.StopRequest
import com.college.bustracker.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel : ViewModel() {

    private val app = CollegeBusApp.instance
    private val busRepo = app.busRepository
    private val routeRepo = app.routeRepository
    private val swapRepo = app.busSwapRepository
    private val tempAssignRepo = app.temporaryAssignmentRepository

    val buses: StateFlow<List<Bus>> = busRepo.getBusesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val routes: StateFlow<List<Route>> = routeRepo.getRoutesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val swaps: StateFlow<List<BusSwap>> = swapRepo.getSwapsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val temporaryAssignments: StateFlow<List<com.college.bustracker.data.model.TemporaryAssignment>> = tempAssignRepo.getAllAssignmentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val announcementRepo = app.announcementRepository

    val announcements: StateFlow<List<com.college.bustracker.data.model.Announcement>> = announcementRepo.getAnnouncementsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publishedAnnouncements: StateFlow<List<com.college.bustracker.data.model.Announcement>> = announcements.map { list ->
        list.filter { it.status == com.college.bustracker.data.model.Announcement.STATUS_PUBLISHED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftAnnouncements: StateFlow<List<com.college.bustracker.data.model.Announcement>> = announcements.map { list ->
        list.filter { it.status == com.college.bustracker.data.model.Announcement.STATUS_DRAFT }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedAnnouncements: StateFlow<List<com.college.bustracker.data.model.Announcement>> = announcements.map { list ->
        list.filter { it.status == com.college.bustracker.data.model.Announcement.STATUS_ARCHIVED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createAnnouncement(announcement: com.college.bustracker.data.model.Announcement, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = announcementRepo.createAnnouncement(announcement)
            onResult(res)
        }
    }

    fun updateAnnouncement(announcement: com.college.bustracker.data.model.Announcement, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = announcementRepo.updateAnnouncement(announcement)
            onResult(res)
        }
    }

    fun publishAnnouncement(id: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = announcementRepo.publishAnnouncement(id)
            onResult(res)
        }
    }

    fun archiveAnnouncement(id: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = announcementRepo.archiveAnnouncement(id)
            onResult(res)
        }
    }

    fun deleteAnnouncement(id: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = announcementRepo.deleteAnnouncement(id)
            onResult(res)
        }
    }

    val activeReplacementsCount: StateFlow<Int> = temporaryAssignments.map { list ->
        val now = tempAssignRepo.getEstimatedServerTime()
        list.count { it.isActive(now) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Real-time all driver profiles (role == DRIVER)
    val allDrivers: StateFlow<List<UserProfile>> = busRepo.getAllDriversFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time active driver profiles (only role == DRIVER and active == true)
    val activeDrivers: StateFlow<List<UserProfile>> = busRepo.getDriversFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(0) // 0: Buses, 1: Drivers, 2: Routes & Stops, 3: Swaps, 4: Calendar, 5: Stop Requests, 6: Replacements
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun createTemporaryReplacement(
        busId: String,
        replacementDriverUid: String,
        startAt: Long,
        endAt: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        val bus = buses.value.find { it.busId.equals(busId, ignoreCase = true) }
        val replacementDriver = allDrivers.value.find { it.uid == replacementDriverUid }
        val permanentDriver = allDrivers.value.find { it.uid == bus?.driverId }
        val adminUid = app.authRepository.getActiveProfile()?.uid ?: "admin"

        if (bus == null || replacementDriver == null) {
            onResult(false, "Bus or replacement driver not found.")
            return
        }

        viewModelScope.launch {
            try {
                val res = tempAssignRepo.createTemporaryAssignment(
                    busId = bus.busId,
                    replacementDriverUid = replacementDriverUid,
                    startAt = startAt,
                    endAt = endAt,
                    callerUid = adminUid,
                    callerRole = "ADMIN",
                    permanentDriverUid = permanentDriver?.uid ?: bus.driverId ?: "",
                    permanentDriverId = permanentDriver?.customId ?: bus.driverId ?: "",
                    permanentDriverName = permanentDriver?.name ?: "Permanent Driver",
                    permanentDriverPhone = permanentDriver?.phoneNumber,
                    replacementDriverProfile = replacementDriver
                )
                if (res.isSuccess) {
                    onResult(true, null)
                } else {
                    onResult(false, res.exceptionOrNull()?.message ?: "Failed to create replacement")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to create replacement")
            }
        }
    }

    fun cancelTemporaryReplacement(
        assignmentId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val assignment = temporaryAssignments.value.find { it.assignmentId == assignmentId }
                val busId = assignment?.busId ?: ""
                val adminUid = app.authRepository.getActiveProfile()?.uid ?: "admin"
                val res = tempAssignRepo.cancelTemporaryAssignment(
                    assignmentId = assignmentId,
                    busId = busId,
                    cancelledByUid = adminUid,
                    cancelledByRole = "ADMIN"
                )
                if (res.isSuccess) {
                    onResult(true, null)
                } else {
                    onResult(false, res.exceptionOrNull()?.message ?: "Failed to cancel replacement")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to cancel replacement")
            }
        }
    }

    fun addBus(busId: String, busNumber: String, regNo: String, routeId: String) {
        viewModelScope.launch {
            val bus = Bus(
                busId = busId.trim().uppercase(),
                busNumber = busNumber.trim(),
                registrationNumber = regNo.trim().uppercase(),
                routeId = routeId,
                capacity = 50,
                active = true
            )
            busRepo.addOrUpdateBus(bus)
        }
    }

    fun toggleBusActive(busId: String, isActive: Boolean) {
        viewModelScope.launch {
            busRepo.toggleBusActive(busId, isActive)
        }
    }

    fun updateBusRoute(busId: String, newRouteId: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = busRepo.updateBusRoute(busId, newRouteId)
            onResult?.invoke(res)
        }
    }

    fun assignDriver(busId: String, driverId: String, driverName: String) {
        viewModelScope.launch {
            busRepo.assignDriver(busId, driverId, driverName)
        }
    }

    fun unassignDriver(busId: String) {
        viewModelScope.launch {
            busRepo.unassignDriver(busId)
        }
    }

    fun createDriver(
        context: Context,
        name: String,
        customId: String,
        password: String,
        active: Boolean,
        phoneNumber: String? = null,
        assignedBusId: String? = null,
        onResult: (Result<UserProfile>) -> Unit
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val res = busRepo.createDriverAccount(appContext, name, customId, password, active, phoneNumber, assignedBusId)
            onResult(res)
        }
    }

    fun updateDriver(
        originalDriver: UserProfile,
        newName: String,
        newCustomId: String,
        newPhoneNumber: String?,
        newActive: Boolean,
        newAssignedBusId: String?,
        onResult: (Result<UserProfile>) -> Unit
    ) {
        viewModelScope.launch {
            val res = busRepo.updateDriverProfile(
                originalDriver,
                newName,
                newCustomId,
                newPhoneNumber,
                newActive,
                newAssignedBusId
            )
            onResult(res)
        }
    }

    fun toggleDriverActive(
        driver: UserProfile,
        active: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = busRepo.toggleDriverActive(driver, active)
            onResult(res)
        }
    }

    fun resetDriverPassword(
        driverUid: String,
        newPassword: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = busRepo.resetDriverPassword(driverUid, newPassword)
            onResult(res)
        }
    }

    val allBusesList: StateFlow<List<Bus>> = busRepo.getBusesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allRoutesList: StateFlow<List<Route>> = routeRepo.getRoutesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createRoute(
        routeId: String,
        name: String,
        description: String,
        active: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanId = routeId.trim().uppercase()
        val cleanName = name.trim()
        val cleanDesc = description.trim()

        if (cleanId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Route ID cannot be blank.")))
            return
        }
        if (cleanName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Route name cannot be blank.")))
            return
        }

        viewModelScope.launch {
            val existing = allRoutesList.value.find { it.routeId.equals(cleanId, ignoreCase = true) }
            if (existing != null) {
                onResult(Result.failure(IllegalArgumentException("Route ID $cleanId already exists.")))
                return@launch
            }

            val route = Route(
                routeId = cleanId,
                name = cleanName,
                description = cleanDesc,
                active = active,
                stops = emptyList()
            )
            routeRepo.saveRoute(route)
            onResult(Result.success(Unit))
        }
    }

    fun editRoute(
        routeId: String,
        name: String,
        description: String,
        active: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanName = name.trim()
        val cleanDesc = description.trim()

        if (cleanName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Route name cannot be blank.")))
            return
        }

        viewModelScope.launch {
            routeRepo.updateRouteDetails(routeId, cleanName, cleanDesc, active)
            onResult(Result.success(Unit))
        }
    }

    fun toggleRouteActive(routeId: String, active: Boolean) {
        viewModelScope.launch {
            routeRepo.toggleRouteActive(routeId, active)
        }
    }

    fun deleteRoute(routeId: String, currentBuses: List<Bus> = emptyList(), onResult: (Result<Unit>) -> Unit) {
        val busList = if (currentBuses.isNotEmpty()) currentBuses else allBusesList.value
        val assignedBus = busList.find { it.routeId.equals(routeId, ignoreCase = true) }
        if (assignedBus != null) {
            onResult(Result.failure(IllegalStateException("Cannot delete route: Bus ${assignedBus.busNumber} is assigned to this route. Please unassign or reassign the bus first.")))
            return
        }

        viewModelScope.launch {
            routeRepo.deleteRoute(routeId)
            onResult(Result.success(Unit))
        }
    }

    fun addStop(
        routeId: String,
        currentRoute: Route,
        stopName: String,
        lat: Double,
        lng: Double,
        active: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanName = stopName.trim()
        if (cleanName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Stop name cannot be blank.")))
            return
        }
        if (lat !in -90.0..90.0) {
            onResult(Result.failure(IllegalArgumentException("Latitude must be between -90 and 90.")))
            return
        }
        if (lng !in -180.0..180.0) {
            onResult(Result.failure(IllegalArgumentException("Longitude must be between -180 and 180.")))
            return
        }

        viewModelScope.launch {
            val currentStops = currentRoute.stops.toMutableList()
            val newStop = BusStop(
                stopId = "STOP-${System.currentTimeMillis() % 100000}",
                name = cleanName,
                latitude = lat,
                longitude = lng,
                order = currentStops.size + 1,
                active = active
            )
            currentStops.add(newStop)
            routeRepo.updateStops(routeId, currentStops)
            onResult(Result.success(Unit))
        }
    }

    /**
     * Replaces the stops array with an admin-curated list (e.g. from Map-based Stop Creator).
     * Preserves IDs, sequence order, and coordinates.
     */
    fun updateRouteStops(routeId: String, stops: List<BusStop>, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            try {
                routeRepo.updateStops(routeId, stops)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun editStop(
        routeId: String,
        currentRoute: Route,
        stopId: String,
        name: String,
        lat: Double,
        lng: Double,
        active: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Stop name cannot be blank.")))
            return
        }
        if (lat !in -90.0..90.0) {
            onResult(Result.failure(IllegalArgumentException("Latitude must be between -90 and 90.")))
            return
        }
        if (lng !in -180.0..180.0) {
            onResult(Result.failure(IllegalArgumentException("Longitude must be between -180 and 180.")))
            return
        }

        viewModelScope.launch {
            val updatedStops = currentRoute.stops.map {
                if (it.stopId == stopId) {
                    it.copy(name = cleanName, latitude = lat, longitude = lng, active = active)
                } else it
            }
            routeRepo.updateStops(routeId, updatedStops)
            onResult(Result.success(Unit))
        }
    }

    fun deleteStop(
        routeId: String,
        currentRoute: Route,
        stopId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val remainingStops = currentRoute.stops.filter { it.stopId != stopId }
            val reindexedStops = remainingStops.mapIndexed { idx, stop ->
                stop.copy(order = idx + 1)
            }
            routeRepo.updateStops(routeId, reindexedStops)
            onResult(Result.success(Unit))
        }
    }

    fun reorderStop(
        routeId: String,
        currentRoute: Route,
        stopId: String,
        moveUp: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val sortedStops = currentRoute.stops.sortedBy { it.order }.toMutableList()
            val currentIndex = sortedStops.indexOfFirst { it.stopId == stopId }
            if (currentIndex < 0) {
                onResult(Result.failure(IllegalArgumentException("Stop not found.")))
                return@launch
            }

            val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
            if (targetIndex in sortedStops.indices) {
                val temp = sortedStops[currentIndex]
                sortedStops[currentIndex] = sortedStops[targetIndex]
                sortedStops[targetIndex] = temp

                val reordered = sortedStops.mapIndexed { idx, stop ->
                    stop.copy(order = idx + 1)
                }
                routeRepo.updateStops(routeId, reordered)
                onResult(Result.success(Unit))
            } else {
                onResult(Result.success(Unit))
            }
        }
    }

    fun toggleStopActive(
        routeId: String,
        currentRoute: Route,
        stopId: String,
        active: Boolean
    ) {
        viewModelScope.launch {
            val updated = currentRoute.stops.map {
                if (it.stopId == stopId) it.copy(active = active) else it
            }
            routeRepo.updateStops(routeId, updated)
        }
    }

    fun addStopToRoute(routeId: String, currentRoute: Route, stopName: String, lat: Double, lng: Double) {
        addStop(routeId, currentRoute, stopName, lat, lng, true) {}
    }

    private val timeRegex = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    fun updateSchedule(
        routeId: String,
        morningEnabled: Boolean,
        morningStart: String,
        morningEnd: String,
        eveningEnabled: Boolean,
        eveningStart: String,
        eveningEnd: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        val mStart = morningStart.trim()
        val mEnd = morningEnd.trim()
        val eStart = eveningStart.trim()
        val eEnd = eveningEnd.trim()

        if (morningEnabled) {
            if (mStart.isBlank() || mEnd.isBlank()) {
                onResult(Result.failure(IllegalArgumentException("Morning start and end times are required when enabled.")))
                return
            }
            if (!timeRegex.matches(mStart) || !timeRegex.matches(mEnd)) {
                onResult(Result.failure(IllegalArgumentException("Morning time must be in 24-hour HH:mm format (e.g. 07:00).")))
                return
            }
            if (mStart >= mEnd) {
                onResult(Result.failure(IllegalArgumentException("Morning end time must be after start time.")))
                return
            }
        }

        if (eveningEnabled) {
            if (eStart.isBlank() || eEnd.isBlank()) {
                onResult(Result.failure(IllegalArgumentException("Evening start and end times are required when enabled.")))
                return
            }
            if (!timeRegex.matches(eStart) || !timeRegex.matches(eEnd)) {
                onResult(Result.failure(IllegalArgumentException("Evening time must be in 24-hour HH:mm format (e.g. 16:00).")))
                return
            }
            if (eStart >= eEnd) {
                onResult(Result.failure(IllegalArgumentException("Evening end time must be after start time.")))
                return
            }
        }

        viewModelScope.launch {
            val currentSched = com.college.bustracker.util.OperatingScheduleService.getSchedule()
            val sched = RouteSchedule(
                morning = com.college.bustracker.data.model.ScheduleWindow(
                    startTime = if (mStart.isNotBlank()) mStart else currentSched.morning.startTime,
                    endTime = if (mEnd.isNotBlank()) mEnd else currentSched.morning.endTime,
                    enabled = morningEnabled
                ),
                evening = com.college.bustracker.data.model.ScheduleWindow(
                    startTime = if (eStart.isNotBlank()) eStart else currentSched.evening.startTime,
                    endTime = if (eEnd.isNotBlank()) eEnd else currentSched.evening.endTime,
                    enabled = eveningEnabled
                )
            )
            val res = routeRepo.updateSchedule(routeId, sched)
            onResult(res)
        }
    }

    fun cancelSwap(swapId: String) {
        viewModelScope.launch {
            swapRepo.updateSwapStatus(swapId, "CANCELLED")
        }
    }

    fun deleteBus(busId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = busRepo.deleteBus(busId)
            onResult(res)
        }
    }

    fun deleteDriver(driverId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = busRepo.deleteDriver(driverId)
            onResult(res)
        }
    }

    // --- Calendar Management (Phase 14) ---
    private val calendarRepo = app.calendarRepository
    private val operatingScheduleRepo = app.operatingScheduleRepository

    val calendarExceptions: StateFlow<List<com.college.bustracker.data.model.CalendarException>> =
        calendarRepo.getCalendarExceptionsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val operatingSchedule: StateFlow<RouteSchedule> =
        operatingScheduleRepo.getOperatingScheduleFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.college.bustracker.util.OperatingScheduleService.getSchedule())

    val todayEvaluation = MutableStateFlow(com.college.bustracker.util.CollegeCalendarService.evaluateDay())

    init {
        viewModelScope.launch {
            calendarExceptions.collect {
                todayEvaluation.value = com.college.bustracker.util.CollegeCalendarService.evaluateDay()
            }
        }
    }

    fun updateOperatingSchedule(
        schedule: RouteSchedule,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val currentUid = app.authRepository.getActiveProfile()?.uid ?: "admin"
            val res = operatingScheduleRepo.saveOperatingSchedule(schedule, currentUid)
            onResult(res)
        }
    }

    fun saveCalendarException(
        date: String,
        type: String,
        name: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val currentUid = app.authRepository.getActiveProfile()?.uid ?: "admin"
            val exception = com.college.bustracker.data.model.CalendarException(
                date = date.trim(),
                type = type,
                name = name.trim(),
                updatedAt = System.currentTimeMillis(),
                updatedBy = currentUid
            )
            val res = calendarRepo.saveException(exception)
            onResult(res)
        }
    }

    fun deleteCalendarException(dateStr: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = calendarRepo.deleteException(dateStr)
            onResult(res)
        }
    }

    // --- Stop Request Management (Component 3) ---
    private val stopRequestRepo = app.stopRequestRepository

    val stopRequests: StateFlow<List<StopRequest>> = stopRequestRepo.getStopRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingStopRequestsCount: StateFlow<Int> = stopRequests.map { list ->
        list.count { it.status == StopRequest.STATUS_PENDING }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun approveStopRequest(
        request: StopRequest,
        finalName: String,
        finalLat: Double,
        finalLng: Double,
        adminNote: String? = null,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val cleanName = finalName.trim()
            if (cleanName.isBlank()) {
                onResult(Result.failure(IllegalArgumentException("Stop name cannot be blank.")))
                return@launch
            }
            if (finalLat !in -90.0..90.0 || finalLng !in -180.0..180.0) {
                onResult(Result.failure(IllegalArgumentException("Coordinates must be valid.")))
                return@launch
            }

            val route = allRoutesList.value.find { it.routeId.equals(request.routeId, ignoreCase = true) }
            if (route == null) {
                onResult(Result.failure(IllegalStateException("Route ${request.routeId} not found.")))
                return@launch
            }

            // Generate unique, collision-safe stopId
            val nextOrder = (route.stops.maxOfOrNull { it.order } ?: 0) + 1
            val uniqueId = "stop_${System.currentTimeMillis()}_${(100..999).random()}"
            val newStop = BusStop(
                stopId = uniqueId,
                name = cleanName,
                latitude = finalLat,
                longitude = finalLng,
                order = nextOrder,
                active = true
            )

            // 1. Append new stop to official route stops (preserve existing stops and IDs)
            val updatedStops = route.stops + newStop
            routeRepo.updateStops(route.routeId, updatedStops)

            // 2. Mark stop request as APPROVED in Firestore
            val currentUid = app.authRepository.getActiveProfile()?.uid ?: "admin"
            val approveResult = stopRequestRepo.approveStopRequest(
                requestId = request.requestId,
                adminUid = currentUid,
                finalName = cleanName,
                finalLat = finalLat,
                finalLng = finalLng,
                adminNote = adminNote?.trim()?.ifBlank { null }
            )
            onResult(approveResult)
        }
    }

    fun rejectStopRequest(
        request: StopRequest,
        rejectionReason: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val currentUid = app.authRepository.getActiveProfile()?.uid ?: "admin"
            val res = stopRequestRepo.rejectStopRequest(
                requestId = request.requestId,
                adminUid = currentUid,
                rejectionReason = rejectionReason?.trim()?.ifBlank { null }
            )
            onResult(res)
        }
    }

    fun updateStopRequestDraft(
        request: StopRequest,
        name: String,
        lat: Double,
        lng: Double,
        adminNote: String? = null,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = stopRequestRepo.updateStopRequestDraft(
                requestId = request.requestId,
                name = name.trim(),
                lat = lat,
                lng = lng,
                adminNote = adminNote?.trim()?.ifBlank { null }
            )
            onResult(res)
        }
    }

    fun uploadRouteSchedulePdf(
        context: android.content.Context,
        routeId: String,
        uri: android.net.Uri,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val uploadRes = com.college.bustracker.util.SchedulePdfHelper.uploadPdf(context, routeId, uri)
            uploadRes.fold(
                onSuccess = { (url, fileName) ->
                    val saveRes = routeRepo.updateSchedulePdf(routeId, url, fileName)
                    if (saveRes.isSuccess) {
                        onResult(Result.success(url))
                    } else {
                        onResult(Result.failure(saveRes.exceptionOrNull() ?: Exception("Failed to save PDF to route")))
                    }
                },
                onFailure = { err ->
                    onResult(Result.failure(err))
                }
            )
        }
    }

    fun removeRouteSchedulePdf(
        routeId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = routeRepo.updateSchedulePdf(routeId, null, null)
            onResult(res)
        }
    }

    fun uploadAllRoutesSchedulePdf(
        context: Context,
        uri: android.net.Uri,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val uploadRes = com.college.bustracker.util.SchedulePdfHelper.uploadPdf(context, "master_all_routes", uri)
            uploadRes.fold(
                onSuccess = { (url, fileName) ->
                    val currentUid = app.authRepository.getActiveProfile()?.uid ?: "admin"
                    val saveRes = operatingScheduleRepo.updateAllRoutesPdf(url, fileName, currentUid)
                    if (saveRes.isSuccess) {
                        onResult(Result.success(url))
                    } else {
                        onResult(Result.failure(saveRes.exceptionOrNull() ?: Exception("Failed to save master PDF")))
                    }
                },
                onFailure = { err ->
                    onResult(Result.failure(err))
                }
            )
        }
    }

    fun removeAllRoutesSchedulePdf(
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val currentUid = app.authRepository.getActiveProfile()?.uid ?: "admin"
            val res = operatingScheduleRepo.updateAllRoutesPdf(null, null, currentUid)
            onResult(res)
        }
    }
}
