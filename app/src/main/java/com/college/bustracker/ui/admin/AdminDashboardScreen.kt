package com.college.bustracker.ui.admin

import com.college.bustracker.ui.admin.DriverProfileValidator

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.college.bustracker.util.SchedulePdfHelper
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.college.bustracker.data.model.CalendarException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.college.bustracker.R
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange

@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onNavigateToDiagnostics: (() -> Unit)? = null,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val buses by viewModel.buses.collectAsState(initial = emptyList())
    val routes by viewModel.routes.collectAsState(initial = emptyList())
    val swaps by viewModel.swaps.collectAsState(initial = emptyList())
    val activeDrivers by viewModel.activeDrivers.collectAsState()
    val allDrivers by viewModel.allDrivers.collectAsState()
    val calendarExceptions by viewModel.calendarExceptions.collectAsState()
    val operatingSchedule by viewModel.operatingSchedule.collectAsState()
    val todayEvaluation by viewModel.todayEvaluation.collectAsState()
    val stopRequests by viewModel.stopRequests.collectAsState()
    val pendingStopRequestsCount by viewModel.pendingStopRequestsCount.collectAsState()
    val temporaryAssignments by viewModel.temporaryAssignments.collectAsState()
    val activeReplacementsCount by viewModel.activeReplacementsCount.collectAsState()
    val publishedAnnouncements by viewModel.publishedAnnouncements.collectAsState()
    val draftAnnouncements by viewModel.draftAnnouncements.collectAsState()
    val archivedAnnouncements by viewModel.archivedAnnouncements.collectAsState()

    var selectedSection by remember { mutableStateOf(0) } // 0: Overview, 1: Operations, 2: Routes, 3: Calendar, 4: Announcements
    var operationsSubTab by remember { mutableStateOf<String?>(null) } // null (hub), "buses", "drivers", "replacements", "swaps"
    var routesSubTab by remember { mutableStateOf<String?>(null) } // null (hub), "routes", "stops", "stop_requests"
    var selectedRouteForStops by remember { mutableStateOf<Route?>(null) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCreateAnnouncementDialog by remember { mutableStateOf(false) }
    var showBusTimingChangeDialog by remember { mutableStateOf(false) }
    var announcementToEdit by remember { mutableStateOf<com.college.bustracker.data.model.Announcement?>(null) }
    var announcementToDelete by remember { mutableStateOf<com.college.bustracker.data.model.Announcement?>(null) }

    var showAddBusDialog by remember { mutableStateOf(false) }
    var showAddDriverDialog by remember { mutableStateOf(false) }
    var showAddReplacementDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showEditOperatingScheduleDialog by remember { mutableStateOf(false) }
    var isEditingMorningSchedule by remember { mutableStateOf(true) }
    var showAddEditHolidayDialog by remember { mutableStateOf(false) }
    var holidayToEdit by remember { mutableStateOf<CalendarException?>(null) }
    var holidayToDelete by remember { mutableStateOf<CalendarException?>(null) }
    var targetRouteForEdit by remember { mutableStateOf<Route?>(null) }
    var targetBusForDriverAssignment by remember { mutableStateOf<Bus?>(null) }
    var targetBusForDriverUnassignment by remember { mutableStateOf<Bus?>(null) }
    var targetBusForRouteChange by remember { mutableStateOf<Bus?>(null) }
    var targetBusForDelete by remember { mutableStateOf<Bus?>(null) }
    var isDeletingBus by remember { mutableStateOf(false) }
    var targetDriverForDelete by remember { mutableStateOf<UserProfile?>(null) }
    var driverToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var isDeletingDriver by remember { mutableStateOf(false) }
    var deleteActionError by remember { mutableStateOf<String?>(null) }
    var deactivationError by remember { mutableStateOf<String?>(null) }

    var showAddRouteDialog by remember { mutableStateOf(false) }
    var routeToEdit by remember { mutableStateOf<Route?>(null) }
    var routeForManageStops by remember { mutableStateOf<Route?>(null) }
    var routeActionError by remember { mutableStateOf<String?>(null) }

    var showAddStopDialog by remember { mutableStateOf(false) }
    var stopToEdit by remember { mutableStateOf<BusStop?>(null) }
    var stopToDelete by remember { mutableStateOf<BusStop?>(null) }
    var routeForAddStop by remember { mutableStateOf<Route?>(null) }
    var showMapStopCreator by remember { mutableStateOf(false) }
    var routeForMapStopCreator by remember { mutableStateOf<Route?>(null) }

    var replacementToCancel by remember { mutableStateOf<TemporaryAssignment?>(null) }
    var showAddSwapDialog by remember { mutableStateOf(false) }
    var swapToCancel by remember { mutableStateOf<BusSwap?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var targetRouteForPdf by remember { mutableStateOf<Route?>(null) }
    var isUploadingPdf by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = targetRouteForPdf
        if (uri != null && target != null) {
            isUploadingPdf = true
            viewModel.uploadRouteSchedulePdf(context, target.routeId, uri) { result ->
                isUploadingPdf = false
                result.onSuccess {
                    Toast.makeText(context, context.getString(R.string.route_pdf_uploaded_success), Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Upload failed", Toast.LENGTH_LONG).show()
                }
                targetRouteForPdf = null
            }
        }
    }

    val masterPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPdf = true
            viewModel.uploadAllRoutesSchedulePdf(context, uri) { result ->
                isUploadingPdf = false
                result.onSuccess {
                    Toast.makeText(context, context.getString(R.string.master_pdf_uploaded_success), Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Upload failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            showLanguageDialog -> showLanguageDialog = false
            showCreateAnnouncementDialog -> showCreateAnnouncementDialog = false
            showBusTimingChangeDialog -> showBusTimingChangeDialog = false
            announcementToEdit != null -> announcementToEdit = null
            announcementToDelete != null -> announcementToDelete = null
            showAddBusDialog -> showAddBusDialog = false
            showAddDriverDialog -> showAddDriverDialog = false
            showAddReplacementDialog -> showAddReplacementDialog = false
            driverToEdit != null -> driverToEdit = null
            showScheduleDialog -> showScheduleDialog = false
            showAddRouteDialog -> showAddRouteDialog = false
            showAddEditHolidayDialog -> showAddEditHolidayDialog = false
            holidayToDelete != null -> holidayToDelete = null
            targetRouteForEdit != null -> targetRouteForEdit = null
            targetBusForDriverAssignment != null -> targetBusForDriverAssignment = null
            targetBusForDriverUnassignment != null -> targetBusForDriverUnassignment = null
            targetBusForRouteChange != null -> targetBusForRouteChange = null
            targetBusForDelete != null -> targetBusForDelete = null
            targetDriverForDelete != null -> targetDriverForDelete = null
            routeToEdit != null -> routeToEdit = null
            routeForManageStops != null -> routeForManageStops = null
            showAddStopDialog -> showAddStopDialog = false
            stopToEdit != null -> stopToEdit = null
            stopToDelete != null -> stopToDelete = null
            showMapStopCreator -> showMapStopCreator = false
            replacementToCancel != null -> replacementToCancel = null
            showAddSwapDialog -> showAddSwapDialog = false
            swapToCancel != null -> swapToCancel = null
            deleteActionError != null -> deleteActionError = null
            deactivationError != null -> deactivationError = null
            routeActionError != null -> routeActionError = null
            operationsSubTab != null -> operationsSubTab = null
            routesSubTab != null -> routesSubTab = null
            selectedSection != 0 -> selectedSection = 0
            else -> (context as? Activity)?.moveTaskToBack(true)
        }
    }

    Scaffold(
        topBar = {
            AdminHeader(
                title = when (selectedSection) {
                    0 -> stringResource(R.string.admin_dashboard)
                    1 -> stringResource(R.string.fleet_operations)
                    2 -> stringResource(R.string.routes_and_transit)
                    3 -> stringResource(R.string.college_calendar)
                    4 -> stringResource(R.string.announcements_and_notices)
                    else -> stringResource(R.string.admin_dashboard)
                },
                subtitle = "FleetX",
                todayEvaluation = todayEvaluation,
                onNavigateToDiagnostics = onNavigateToDiagnostics,
                onOpenLanguageSelector = { showLanguageDialog = true },
                onLogout = onLogout
            )
        },
        bottomBar = {
            AdminBottomNavigation(
                selectedSection = selectedSection,
                onSectionSelected = { sectionIndex ->
                    selectedSection = sectionIndex
                    if (sectionIndex == 1) operationsSubTab = null
                    if (sectionIndex == 2) routesSubTab = null
                },
                pendingStopRequestsCount = pendingStopRequestsCount,
                activeReplacementsCount = activeReplacementsCount
            )
        },
        containerColor = CollegeSurface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedSection) {
                0 -> AdminOverviewScreen(
                    buses = buses,
                    allDrivers = allDrivers,
                    activeDrivers = activeDrivers,
                    temporaryAssignments = temporaryAssignments,
                    swaps = swaps,
                    pendingStopRequests = stopRequests.filter { it.status == "PENDING" },
                    onNavigateToOperations = { subTab ->
                        selectedSection = 1
                        operationsSubTab = subTab
                    },
                    onNavigateToRoutes = { subTab ->
                        selectedSection = 2
                        routesSubTab = subTab
                    },
                    onNavigateToCalendar = {
                        selectedSection = 3
                    },
                    onNavigateToAnnouncements = {
                        selectedSection = 4
                    }
                )
                1 -> AdminOperationsScreen(
                    currentSubTab = operationsSubTab,
                    onSelectSubTab = { operationsSubTab = it },
                    buses = buses,
                    routes = routes,
                    allDrivers = allDrivers,
                    temporaryAssignments = temporaryAssignments,
                    swaps = swaps,
                    onAddBusClick = { showAddBusDialog = true },
                    onToggleBusActive = { busId, active -> viewModel.toggleBusActive(busId, active) },
                    onAssignDriverClick = { bus -> targetBusForDriverAssignment = bus },
                    onUnassignDriverClick = { bus -> targetBusForDriverUnassignment = bus },
                    onChangeRouteClick = { bus -> targetBusForRouteChange = bus },
                    onDeleteBusClick = { bus -> targetBusForDelete = bus },
                    onAddDriverClick = { showAddDriverDialog = true },
                    onEditDriverClick = { driver -> driverToEdit = driver },
                    onToggleDriverActive = { driver, active ->
                        viewModel.toggleDriverActive(driver, active) { result ->
                            result.onFailure { err ->
                                deactivationError = err.message ?: "Failed to update driver status"
                            }
                        }
                    },
                    onDeleteDriverClick = { driver -> targetDriverForDelete = driver },
                    onResetDriverPasswordClick = { driver ->
                        driverToEdit = driver
                    },
                    onAddReplacementClick = { showAddReplacementDialog = true },
                    onCancelReplacementClick = { replacement ->
                        replacementToCancel = replacement
                    },
                    onAddSwapClick = { showAddSwapDialog = true },
                    onCancelSwapClick = { swap ->
                        swapToCancel = swap
                    }
                )
                2 -> AdminRoutesScreen(
                    currentSubTab = routesSubTab,
                    onSelectSubTab = { routesSubTab = it },
                    routes = routes,
                    buses = buses,
                    stopRequests = stopRequests,
                    pendingStopRequestsCount = pendingStopRequestsCount,
                    selectedRouteForStops = selectedRouteForStops ?: routes.firstOrNull(),
                    onSelectRouteForStops = { selectedRouteForStops = it },
                    onAddRouteClick = { showAddRouteDialog = true },
                    onEditRouteClick = { route -> routeToEdit = route },
                    onDeleteRouteClick = { route ->
                        viewModel.deleteRoute(route.routeId, buses) { result ->
                            result.onFailure { err ->
                                routeActionError = err.message ?: "Failed to delete route"
                            }
                        }
                    },
                    onAddStopClick = { route ->
                        routeForAddStop = route
                        showAddStopDialog = true
                    },
                    onOpenMapStopCreator = { route ->
                        routeForMapStopCreator = route
                        showMapStopCreator = true
                    },
                    onEditStopClick = { route, stop ->
                        routeForAddStop = route
                        stopToEdit = stop
                    },
                    onMoveStopUp = { route, stop ->
                        viewModel.reorderStop(route.routeId, route, stop.stopId, moveUp = true) {}
                    },
                    onMoveStopDown = { route, stop ->
                        viewModel.reorderStop(route.routeId, route, stop.stopId, moveUp = false) {}
                    },
                    onDeleteStopClick = { route, stop ->
                        routeForAddStop = route
                        stopToDelete = stop
                    },
                    onApproveStopRequest = { req, stopName, lat, lng, note ->
                        viewModel.approveStopRequest(req, stopName, lat, lng, note) { res ->
                            res.onFailure { err ->
                                routeActionError = err.message ?: "Failed to approve stop request"
                            }
                        }
                    },
                    onRejectStopRequest = { req, reason ->
                        viewModel.rejectStopRequest(req, reason) { res ->
                            res.onFailure { err ->
                                routeActionError = err.message ?: "Failed to reject stop request"
                            }
                        }
                    },
                    onEditStopRequestDraft = { req, name, lat, lng, note ->
                        viewModel.updateStopRequestDraft(req, name, lat, lng, note) { res ->
                            res.onFailure { err ->
                                routeActionError = err.message ?: "Failed to update draft"
                            }
                        }
                    },
                    onUploadSchedulePdf = { route ->
                        targetRouteForPdf = route
                        pdfPickerLauncher.launch("application/pdf")
                    },
                    onViewSchedulePdf = { route ->
                        if (!route.schedulePdfUrl.isNullOrBlank()) {
                            coroutineScope.launch {
                                SchedulePdfHelper.openPdf(context, route.schedulePdfUrl!!, route.schedulePdfName)
                            }
                        }
                    },
                    onRemoveSchedulePdf = { route ->
                        viewModel.removeRouteSchedulePdf(route.routeId) { result ->
                            result.onSuccess {
                                Toast.makeText(context, context.getString(R.string.route_pdf_deleted_success), Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to remove PDF", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onNavigateToCalendar = {
                        selectedSection = 3
                    }
                )
                3 -> CalendarTabContent(
                    operatingSchedule = operatingSchedule,
                    exceptions = calendarExceptions,
                    todayEvaluation = todayEvaluation,
                    onEditOperatingScheduleClick = { isMorning ->
                        isEditingMorningSchedule = isMorning
                        showEditOperatingScheduleDialog = true
                    },
                    onAddExceptionClick = {
                        holidayToEdit = null
                        showAddEditHolidayDialog = true
                    },
                    onEditExceptionClick = { ex ->
                        holidayToEdit = ex
                        showAddEditHolidayDialog = true
                    },
                    onDeleteExceptionClick = { ex ->
                        holidayToDelete = ex
                    },
                    onUploadMasterPdfClick = {
                        masterPdfPickerLauncher.launch("application/pdf")
                    },
                    onViewMasterPdfClick = { url, name ->
                        coroutineScope.launch {
                            com.college.bustracker.util.SchedulePdfHelper.openPdf(context, url, name)
                        }
                    },
                    onDeleteMasterPdfClick = {
                        viewModel.removeAllRoutesSchedulePdf { result ->
                            result.onSuccess {
                                Toast.makeText(context, context.getString(R.string.master_pdf_deleted_success), Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to remove PDF", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
                4 -> AdminAnnouncementScreen(
                    published = publishedAnnouncements,
                    drafts = draftAnnouncements,
                    archived = archivedAnnouncements,
                    onAddAnnouncementClick = {
                        announcementToEdit = null
                        showCreateAnnouncementDialog = true
                    },
                    onTimingChangeClick = {
                        showBusTimingChangeDialog = true
                    },
                    onEditAnnouncementClick = { ann ->
                        announcementToEdit = ann
                        showCreateAnnouncementDialog = true
                    },
                    onPublishAnnouncementClick = { ann ->
                        viewModel.publishAnnouncement(ann.id) {}
                    },
                    onArchiveAnnouncementClick = { ann ->
                        viewModel.archiveAnnouncement(ann.id) {}
                    },
                    onDeleteAnnouncementClick = { ann ->
                        announcementToDelete = ann
                    }
                )
            }
        }
    }

    // Edit Operating Schedule Dialog
    if (showEditOperatingScheduleDialog) {
        EditOperatingScheduleDialog(
            isMorning = isEditingMorningSchedule,
            schedule = operatingSchedule,
            onDismiss = { showEditOperatingScheduleDialog = false },
            onSave = { startTime, endTime, enabled, onError ->
                val newMorning = if (isEditingMorningSchedule) {
                    operatingSchedule.morning.copy(startTime = startTime, endTime = endTime, enabled = enabled)
                } else operatingSchedule.morning

                val newEvening = if (!isEditingMorningSchedule) {
                    operatingSchedule.evening.copy(startTime = startTime, endTime = endTime, enabled = enabled)
                } else operatingSchedule.evening

                val updatedSchedule = RouteSchedule(morning = newMorning, evening = newEvening)
                viewModel.updateOperatingSchedule(updatedSchedule) { res ->
                    res.onSuccess {
                        showEditOperatingScheduleDialog = false
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to update operating schedule")
                    }
                }
            }
        )
    }

    // Create Temporary Replacement Dialog
    if (showAddReplacementDialog) {
        AdminCreateReplacementDialog(
            buses = buses,
            drivers = allDrivers,
            routes = routes,
            onDismiss = { showAddReplacementDialog = false },
            onCreateReplacement = { busId, repUid, startAt, endAt, cb ->
                viewModel.createTemporaryReplacement(busId, repUid, startAt, endAt, cb)
            }
        )
    }

    // Add / Edit Holiday Dialog
    if (showAddEditHolidayDialog) {
        AddEditHolidayDialog(
            initialException = holidayToEdit,
            onDismiss = { showAddEditHolidayDialog = false },
            onConfirm = { date, type, name, onError ->
                viewModel.saveCalendarException(date, type, name) { result ->
                    result.onSuccess {
                        showAddEditHolidayDialog = false
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to save calendar override")
                    }
                }
            }
        )
    }

    // Delete Holiday Confirmation Dialog
    if (holidayToDelete != null) {
        DeleteHolidayDialog(
            exception = holidayToDelete!!,
            onDismiss = { holidayToDelete = null },
            onConfirm = { onError ->
                viewModel.deleteCalendarException(holidayToDelete!!.date) { result ->
                    result.onSuccess {
                        holidayToDelete = null
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to delete calendar override")
                    }
                }
            }
        )
    }

    // Add Driver Dialog
    if (showAddDriverDialog) {
        AddDriverDialog(
            allBuses = buses,
            onDismiss = { showAddDriverDialog = false },
            onConfirm = { name, customId, password, active, phone, assignedBusId, onError ->
                viewModel.createDriver(context, name, customId, password, active, phone, assignedBusId) { result ->
                    result.onSuccess {
                        showAddDriverDialog = false
                    }.onFailure { e ->
                        onError(e.message ?: "Failed to create driver")
                    }
                }
            }
        )
    }

    // Edit Driver Dialog
    if (driverToEdit != null) {
        EditDriverDialog(
            driver = driverToEdit!!,
            allBuses = buses,
            onDismiss = { driverToEdit = null },
            onConfirm = { newName, newCustomId, newPhone, newActive, newAssignedBusId, onError ->
                viewModel.updateDriver(
                    driverToEdit!!,
                    newName,
                    newCustomId,
                    newPhone,
                    newActive,
                    newAssignedBusId
                ) { result ->
                    result.onSuccess {
                        driverToEdit = null
                    }.onFailure { e ->
                        onError(e.message ?: "Failed to update driver")
                    }
                }
            },
            onResetPassword = { newPassword, onSuccess, onError ->
                val currentDriver = driverToEdit
                if (currentDriver != null) {
                    viewModel.resetDriverPassword(currentDriver.uid, newPassword) { result ->
                        result.onSuccess {
                            onSuccess()
                        }.onFailure { e ->
                            onError(e.message ?: "Failed to reset password")
                        }
                    }
                } else {
                    onError("No driver selected.")
                }
            }
        )
    }

    // Deactivation Error Dialog
    if (deactivationError != null) {
        AlertDialog(
            onDismissRequest = { deactivationError = null },
            title = { Text("Action Blocked", fontWeight = FontWeight.Bold) },
            text = { Text(deactivationError!!) },
            confirmButton = {
                Button(
                    onClick = { deactivationError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Delete Action Error Dialog
    if (deleteActionError != null) {
        AlertDialog(
            onDismissRequest = { deleteActionError = null },
            title = { Text("Delete Action Failed", fontWeight = FontWeight.Bold, color = StatusDangerRed) },
            text = { Text(deleteActionError!!) },
            confirmButton = {
                Button(
                    onClick = { deleteActionError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Delete Bus Confirmation Dialog
    if (targetBusForDelete != null) {
        val busToDelete = targetBusForDelete!!
        val assignedRoute = routes.find { it.routeId.equals(busToDelete.routeId, ignoreCase = true) }
        val hasDriver = !busToDelete.driverId.isNullOrBlank()
        val hasRoute = busToDelete.routeId.isNotBlank()
        val hasActiveSwap = swaps.any { it.status == "ACTIVE" && (it.originalBusId == busToDelete.busId || it.replacementBusId == busToDelete.busId) }

        DeleteBusConfirmationDialog(
            bus = busToDelete,
            hasAssignedDriver = hasDriver,
            assignedDriverName = busToDelete.driverName ?: busToDelete.driverId,
            hasAssignedRoute = hasRoute,
            routeName = assignedRoute?.name ?: busToDelete.routeId,
            hasActiveSwap = hasActiveSwap,
            isDeleting = isDeletingBus,
            onDismiss = { targetBusForDelete = null },
            onConfirm = {
                isDeletingBus = true
                viewModel.deleteBus(busToDelete.busId) { result ->
                    isDeletingBus = false
                    result.onSuccess {
                        targetBusForDelete = null
                    }.onFailure { err ->
                        deleteActionError = err.message ?: "Failed to delete bus ${busToDelete.busId}"
                    }
                }
            }
        )
    }

    // Delete Driver Confirmation Dialog
    if (targetDriverForDelete != null) {
        val driverToDelete = targetDriverForDelete!!
        DeleteDriverConfirmationDialog(
            driver = driverToDelete,
            assignedBusId = driverToDelete.assignedBusId,
            isDeleting = isDeletingDriver,
            onDismiss = { targetDriverForDelete = null },
            onConfirm = {
                isDeletingDriver = true
                viewModel.deleteDriver(driverToDelete.uid) { result ->
                    isDeletingDriver = false
                    result.onSuccess {
                        targetDriverForDelete = null
                    }.onFailure { err ->
                        deleteActionError = err.message ?: "Failed to delete driver ${driverToDelete.name}"
                    }
                }
            }
        )
    }

    // Assign Driver Dialog
    if (targetBusForDriverAssignment != null) {
        val bus = targetBusForDriverAssignment!!
        var selectedDriverUid by remember { mutableStateOf(bus.driverId ?: "") }

        AlertDialog(
            onDismissRequest = { targetBusForDriverAssignment = null },
            title = {
                Text(
                    text = if (bus.driverId.isNullOrBlank()) "Assign Driver" else "Change Driver",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select an active driver account for ${bus.busNumber}:",
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )

                    if (activeDrivers.isEmpty()) {
                        Text(
                            text = "No active driver accounts found in the system.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(activeDrivers, key = { it.uid }) { driver ->
                                val isSelected = driver.uid == selectedDriverUid
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDriverUid = driver.uid }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedDriverUid = driver.uid }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = driver.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "ID: ${driver.customId}" + if (!driver.assignedBusId.isNullOrBlank() && driver.assignedBusId != bus.busId) " (Currently on ${driver.assignedBusId})" else "",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selected = activeDrivers.find { it.uid == selectedDriverUid }
                        if (selected != null) {
                            viewModel.assignDriver(bus.busId, selected.uid, selected.name)
                        }
                        targetBusForDriverAssignment = null
                    },
                    enabled = selectedDriverUid.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("Confirm Assignment")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { targetBusForDriverAssignment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unassign Driver Dialog
    if (targetBusForDriverUnassignment != null) {
        val bus = targetBusForDriverUnassignment!!
        AlertDialog(
            onDismissRequest = { targetBusForDriverUnassignment = null },
            title = {
                Text(
                    text = "Unassign Driver",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to unassign ${bus.driverName ?: "the driver"} from ${bus.busNumber}? The driver will no longer have permission to write GPS coordinates for this bus.",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unassignDriver(bus.busId)
                        targetBusForDriverUnassignment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("Unassign")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { targetBusForDriverUnassignment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Bus Route Dialog
    if (targetBusForRouteChange != null) {
        val bus = targetBusForRouteChange!!
        ChangeBusRouteDialog(
            bus = bus,
            routes = routes,
            onDismiss = { targetBusForRouteChange = null },
            onConfirm = { newRouteId ->
                viewModel.updateBusRoute(bus.busId, newRouteId)
                targetBusForRouteChange = null
            }
        )
    }

    // Add Bus Dialog
    if (showAddBusDialog) {
        var busId by remember { mutableStateOf("") }
        var busNumber by remember { mutableStateOf("") }
        var regNo by remember { mutableStateOf("") }
        var selectedRouteId by remember { mutableStateOf(routes.firstOrNull()?.routeId ?: "") }

        AlertDialog(
            onDismissRequest = { showAddBusDialog = false },
            title = { Text("Add Fleet Bus", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = busId,
                        onValueChange = { busId = it },
                        label = { Text("Bus ID (e.g. BUS-25)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = busNumber,
                        onValueChange = { busNumber = it },
                        label = { Text("Display Name (e.g. BUS 25)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regNo,
                        onValueChange = { regNo = it },
                        label = { Text("Registration No (e.g. KA-34-A489)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (routes.isNotEmpty()) {
                        Text(
                            text = "Assigned Route:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        routes.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRouteId = r.routeId }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedRouteId == r.routeId,
                                    onClick = { selectedRouteId = r.routeId },
                                    colors = RadioButtonDefaults.colors(selectedColor = CollegePrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "${r.name} (${r.routeId})", fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (busId.isNotBlank() && busNumber.isNotBlank()) {
                            viewModel.addBus(busId, busNumber, regNo, selectedRouteId)
                            showAddBusDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("Create Bus")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddBusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Configure Schedule Dialog
    if (showScheduleDialog && targetRouteForEdit != null) {
        val currentRoute = targetRouteForEdit!!
        ConfigureScheduleDialog(
            route = currentRoute,
            onDismiss = { showScheduleDialog = false },
            onConfirm = { mEnabled, mStart, mEnd, eEnabled, eStart, eEnd, onError ->
                viewModel.updateSchedule(
                    currentRoute.routeId,
                    mEnabled,
                    mStart,
                    mEnd,
                    eEnabled,
                    eStart,
                    eEnd
                ) { result ->
                    result.onSuccess {
                        showScheduleDialog = false
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to save schedule")
                    }
                }
            }
        )
    }

    // Route Action Error Dialog
    if (routeActionError != null) {
        AlertDialog(
            onDismissRequest = { routeActionError = null },
            title = { Text("Route Action Blocked", fontWeight = FontWeight.Bold) },
            text = { Text(routeActionError!!) },
            confirmButton = {
                Button(
                    onClick = { routeActionError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Add Route Dialog
    if (showAddRouteDialog) {
        AddEditRouteDialog(
            route = null,
            onDismiss = { showAddRouteDialog = false },
            onConfirm = { routeId, name, desc, active, onError ->
                viewModel.createRoute(routeId, name, desc, active) { result ->
                    result.onSuccess {
                        showAddRouteDialog = false
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to create route")
                    }
                }
            }
        )
    }

    // Edit Route Dialog
    if (routeToEdit != null) {
        AddEditRouteDialog(
            route = routeToEdit,
            onDismiss = { routeToEdit = null },
            onConfirm = { routeId, name, desc, active, onError ->
                viewModel.editRoute(routeId, name, desc, active) { result ->
                    result.onSuccess {
                        routeToEdit = null
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to update route")
                    }
                }
            }
        )
    }

    // Manage Stops Dialog
    if (routeForManageStops != null) {
        val currentManageRoute = routes.find { it.routeId == routeForManageStops?.routeId } ?: routeForManageStops!!
        ManageStopsDialog(
            route = currentManageRoute,
            onDismiss = { routeForManageStops = null },
            onAddStop = { name, lat, lng, active, onResult ->
                viewModel.addStop(currentManageRoute.routeId, currentManageRoute, name, lat, lng, active, onResult)
            },
            onEditStop = { stopId, name, lat, lng, active, onResult ->
                viewModel.editStop(currentManageRoute.routeId, currentManageRoute, stopId, name, lat, lng, active, onResult)
            },
            onDeleteStop = { stopId ->
                viewModel.deleteStop(currentManageRoute.routeId, currentManageRoute, stopId) {}
            },
            onReorderStop = { stopId, moveUp ->
                viewModel.reorderStop(currentManageRoute.routeId, currentManageRoute, stopId, moveUp) {}
            },
            onToggleStopActive = { stopId, active ->
                viewModel.toggleStopActive(currentManageRoute.routeId, currentManageRoute, stopId, active)
            },
            onSaveAllStops = { stops ->
                viewModel.updateRouteStops(currentManageRoute.routeId, stops)
            }
        )
    }

    // Add Stop Dialog
    if (showAddStopDialog && routeForAddStop != null) {
        val currentRoute = routes.find { it.routeId == routeForAddStop?.routeId } ?: routeForAddStop!!
        AddEditStopDialog(
            stop = null,
            onDismiss = {
                showAddStopDialog = false
                routeForAddStop = null
            },
            onConfirm = { name, lat, lng, active, onError ->
                viewModel.addStop(currentRoute.routeId, currentRoute, name, lat, lng, active) { result ->
                    result.onSuccess {
                        showAddStopDialog = false
                        routeForAddStop = null
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to add stop")
                    }
                }
            }
        )
    }

    // Edit Stop Dialog
    if (stopToEdit != null && routeForAddStop != null) {
        val currentRoute = routes.find { it.routeId == routeForAddStop?.routeId } ?: routeForAddStop!!
        val currentStop = stopToEdit!!
        AddEditStopDialog(
            stop = currentStop,
            onDismiss = {
                stopToEdit = null
                routeForAddStop = null
            },
            onConfirm = { name, lat, lng, active, onError ->
                viewModel.editStop(currentRoute.routeId, currentRoute, currentStop.stopId, name, lat, lng, active) { result ->
                    result.onSuccess {
                        stopToEdit = null
                        routeForAddStop = null
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to update stop")
                    }
                }
            }
        )
    }

    // Delete Stop Dialog
    if (stopToDelete != null && routeForAddStop != null) {
        val currentRoute = routes.find { it.routeId == routeForAddStop?.routeId } ?: routeForAddStop!!
        val currentStop = stopToDelete!!
        AlertDialog(
            onDismissRequest = {
                stopToDelete = null
                routeForAddStop = null
            },
            title = { Text("Delete Stop", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${currentStop.name}'? Stop ordering will be re-indexed automatically.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStop(currentRoute.routeId, currentRoute, currentStop.stopId) {}
                        stopToDelete = null
                        routeForAddStop = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    stopToDelete = null
                    routeForAddStop = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Map Stop Creator Dialog
    if (showMapStopCreator && routeForMapStopCreator != null) {
        val currentRoute = routes.find { it.routeId == routeForMapStopCreator?.routeId } ?: routeForMapStopCreator!!
        MapStopCreatorDialog(
            route = currentRoute,
            onDismiss = {
                showMapStopCreator = false
                routeForMapStopCreator = null
            },
            onSaveStops = { updatedStops ->
                viewModel.updateRouteStops(currentRoute.routeId, updatedStops)
                showMapStopCreator = false
                routeForMapStopCreator = null
            }
        )
    }

    // Cancel Replacement Dialog
    if (replacementToCancel != null) {
        val target = replacementToCancel!!
        AdminCancelReplacementDialog(
            assignment = target,
            onDismiss = { replacementToCancel = null },
            onConfirmCancel = {
                viewModel.cancelTemporaryReplacement(target.assignmentId) { success, _ ->
                    if (success) replacementToCancel = null
                }
            }
        )
    }

    // Cancel Bus Swap Dialog
    if (swapToCancel != null) {
        val swap = swapToCancel!!
        AlertDialog(
            onDismissRequest = { swapToCancel = null },
            title = { Text("Cancel Bus Swap", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to cancel the swap for ${swap.originalBusId} -> ${swap.replacementBusId}? Fleet operations will be restored.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelSwap(swap.swapId)
                        swapToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("Cancel Swap")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { swapToCancel = null }) {
                    Text("Keep Swap")
                }
            }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        com.college.bustracker.ui.common.LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Create / Edit Announcement Dialog
    if (showCreateAnnouncementDialog) {
        CreateAnnouncementDialog(
            announcementToEdit = announcementToEdit,
            buses = buses,
            routes = routes,
            onDismiss = {
                showCreateAnnouncementDialog = false
                announcementToEdit = null
            },
            onSave = { ann, publishImmediately ->
                if (ann.id.isBlank()) {
                    viewModel.createAnnouncement(ann) {
                        showCreateAnnouncementDialog = false
                        announcementToEdit = null
                    }
                } else {
                    viewModel.updateAnnouncement(ann) {
                        showCreateAnnouncementDialog = false
                        announcementToEdit = null
                    }
                }
            }
        )
    }

    // Bus Timing Change Workflow Dialog
    if (showBusTimingChangeDialog) {
        BusTimingChangeDialog(
            buses = buses,
            onDismiss = { showBusTimingChangeDialog = false },
            onPublishAlert = { ann ->
                viewModel.createAnnouncement(ann) {
                    showBusTimingChangeDialog = false
                }
            },
            onOpenScheduleSettings = {
                showBusTimingChangeDialog = false
                selectedSection = 3
                showEditOperatingScheduleDialog = true
            }
        )
    }

    // Delete Announcement Confirmation Dialog
    if (announcementToDelete != null) {
        val ann = announcementToDelete!!
        AlertDialog(
            onDismissRequest = { announcementToDelete = null },
            title = { Text("Delete Announcement", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this announcement? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnnouncement(ann.id) {
                            announcementToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { announcementToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DriversTabContent(
    drivers: List<UserProfile>,
    onAddDriverClick: () -> Unit,
    onEditDriverClick: (UserProfile) -> Unit,
    onToggleActive: (UserProfile, Boolean) -> Unit,
    onDeleteDriverClick: (UserProfile) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onAddDriverClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ADD NEW DRIVER", fontWeight = FontWeight.Bold)
        }

        if (drivers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No driver accounts found in the system.", color = Color.Gray)
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(drivers, key = { it.uid }) { driver ->
                DriverCard(
                    driver = driver,
                    onEditDriver = onEditDriverClick,
                    onToggleActive = onToggleActive,
                    onDeleteDriver = onDeleteDriverClick
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DriverCard(
    driver: UserProfile,
    onEditDriver: (UserProfile) -> Unit,
    onToggleActive: (UserProfile, Boolean) -> Unit,
    onDeleteDriver: (UserProfile) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Driver ID: ${driver.customId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    val phoneText = if (!driver.phoneNumber.isNullOrBlank()) {
                        "Phone: ${driver.phoneNumber}"
                    } else {
                        "Phone: Not listed"
                    }
                    Text(
                        text = phoneText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!driver.phoneNumber.isNullOrBlank()) Color(0xFF2E7D32) else Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                val statusColor = if (driver.active) StatusActiveGreen else Color.Gray
                val statusBg = if (driver.active) StatusActiveGreen.copy(alpha = 0.12f) else Color.LightGray.copy(alpha = 0.3f)
                Text(
                    text = if (driver.active) "Active" else "Disabled",
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val assignedText = if (!driver.assignedBusId.isNullOrBlank()) {
                    "Assigned: ${driver.assignedBusId}"
                } else {
                    "Assigned: Unassigned"
                }
                Text(
                    text = assignedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!driver.assignedBusId.isNullOrBlank()) CollegePrimary else Color.Gray,
                    fontWeight = if (!driver.assignedBusId.isNullOrBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onEditDriver(driver) },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Driver", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (driver.active) {
                        OutlinedButton(
                            onClick = { onToggleActive(driver, false) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        ) {
                            Text("Disable", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { onToggleActive(driver, true) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        ) {
                            Text("Activate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    IconButton(
                        onClick = { onDeleteDriver(driver) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Driver",
                            tint = StatusDangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDriverDialog(
    allBuses: List<Bus>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, customId: String, password: String, active: Boolean, phone: String?, assignedBusId: String?, onError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var customId by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    var selectedBusId by remember { mutableStateOf<String?>(null) }
    var showBusPicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    if (showBusPicker) {
        BusSelectionDialog(
            currentBusId = selectedBusId,
            allBuses = allBuses,
            onDismiss = { showBusPicker = false },
            onSelectBus = {
                selectedBusId = it
                showBusPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Add New Driver", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Driver Name (e.g. Ramesh Kumar)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true
                )

                OutlinedTextField(
                    value = customId,
                    onValueChange = {
                        customId = it.trim()
                        errorMessage = null
                    },
                    label = { Text("Driver ID (e.g. driver02)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        errorMessage = null
                    },
                    label = { Text("Phone Number (Optional, 10 digits)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password (min 6 characters)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    }
                )

                // Bus Assignment Row
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Assigned Bus (Optional):", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSubmitting) { showBusPicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val busDisplay = if (selectedBusId.isNullOrBlank()) {
                            "None (Unassigned)"
                        } else {
                            val b = allBuses.find { it.busId == selectedBusId }
                            b?.busNumber?.ifBlank { selectedBusId } ?: selectedBusId!!
                        }
                        Text(
                            text = busDisplay,
                            fontSize = 14.sp,
                            color = if (selectedBusId.isNullOrBlank()) Color.Gray else CollegePrimary,
                            fontWeight = if (selectedBusId.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold
                        )
                        Text(
                            text = "Change",
                            fontSize = 12.sp,
                            color = CollegePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Status (Enable Login)",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = active,
                        onCheckedChange = { active = it },
                        enabled = !isSubmitting,
                        colors = SwitchDefaults.colors(checkedThumbColor = CollegePrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nameValidation = DriverProfileValidator.validateName(name)
                    if (nameValidation.isFailure) {
                        errorMessage = nameValidation.exceptionOrNull()?.message ?: "Invalid driver name"
                        return@Button
                    }
                    val idValidation = DriverProfileValidator.validateDriverId(customId)
                    if (idValidation.isFailure) {
                        errorMessage = idValidation.exceptionOrNull()?.message ?: "Invalid Driver ID"
                        return@Button
                    }
                    val phoneValidation = DriverProfileValidator.validateAndNormalizePhoneNumber(phoneNumber)
                    if (phoneValidation.isFailure) {
                        errorMessage = phoneValidation.exceptionOrNull()?.message ?: "Invalid phone number"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null
                    onConfirm(
                        nameValidation.getOrThrow(),
                        idValidation.getOrThrow(),
                        password,
                        active,
                        phoneValidation.getOrThrow(),
                        selectedBusId
                    ) { err ->
                        isSubmitting = false
                        errorMessage = err
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Driver")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditDriverDialog(
    driver: UserProfile,
    allBuses: List<Bus>,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, newCustomId: String, newPhone: String?, newActive: Boolean, newAssignedBusId: String?, onError: (String) -> Unit) -> Unit,
    onResetPassword: ((newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null
) {
    var name by remember { mutableStateOf(driver.name) }
    var customId by remember { mutableStateOf(driver.customId) }
    var phoneNumber by remember { mutableStateOf(driver.phoneNumber ?: "") }
    var active by remember { mutableStateOf(driver.active) }
    var selectedBusId by remember { mutableStateOf(driver.assignedBusId) }
    var showBusPicker by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    if (showBusPicker) {
        BusSelectionDialog(
            currentBusId = selectedBusId,
            allBuses = allBuses,
            onDismiss = { showBusPicker = false },
            onSelectBus = {
                selectedBusId = it
                showBusPicker = false
            }
        )
    }

    if (showResetPasswordDialog && onResetPassword != null) {
        ResetDriverPasswordDialog(
            driver = driver,
            onDismiss = { showResetPasswordDialog = false },
            onConfirmReset = onResetPassword
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Edit Driver Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Driver Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OutlinedTextField(
                        value = customId,
                        onValueChange = {
                            customId = it.trim()
                            errorMessage = null
                        },
                        label = { Text("Driver ID (Login ID)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        singleLine = true
                    )
                    Text(
                        text = "Changing Driver ID updates the staff login identifier.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        errorMessage = null
                    },
                    label = { Text("Phone Number (10 digits)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )

                // Bus Assignment Row
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Assigned Bus:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSubmitting) { showBusPicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val busDisplay = if (selectedBusId.isNullOrBlank()) {
                            "None (Unassigned)"
                        } else {
                            val b = allBuses.find { it.busId == selectedBusId }
                            b?.busNumber?.ifBlank { selectedBusId } ?: selectedBusId!!
                        }
                        Text(
                            text = busDisplay,
                            fontSize = 14.sp,
                            color = if (selectedBusId.isNullOrBlank()) Color.Gray else CollegePrimary,
                            fontWeight = if (selectedBusId.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold
                        )
                        Text(
                            text = "Change",
                            fontSize = 12.sp,
                            color = CollegePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Status (Enable Login)",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = active,
                        onCheckedChange = { active = it },
                        enabled = !isSubmitting,
                        colors = SwitchDefaults.colors(checkedThumbColor = CollegePrimary)
                    )
                }

                if (onResetPassword != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedButton(
                        onClick = { showResetPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f)),
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Password", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nameValidation = DriverProfileValidator.validateName(name)
                    if (nameValidation.isFailure) {
                        errorMessage = nameValidation.exceptionOrNull()?.message ?: "Invalid driver name"
                        return@Button
                    }
                    val idValidation = DriverProfileValidator.validateDriverId(customId)
                    if (idValidation.isFailure) {
                        errorMessage = idValidation.exceptionOrNull()?.message ?: "Invalid Driver ID"
                        return@Button
                    }
                    val phoneValidation = DriverProfileValidator.validateAndNormalizePhoneNumber(phoneNumber)
                    if (phoneValidation.isFailure) {
                        errorMessage = phoneValidation.exceptionOrNull()?.message ?: "Invalid phone number"
                        return@Button
                    }

                    if (!active && !selectedBusId.isNullOrBlank()) {
                        errorMessage = "Please unassign the driver from bus '${selectedBusId}' before deactivating."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null
                    onConfirm(
                        nameValidation.getOrThrow(),
                        idValidation.getOrThrow(),
                        phoneValidation.getOrThrow(),
                        active,
                        selectedBusId
                    ) { err ->
                        isSubmitting = false
                        errorMessage = err
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("SAVE CHANGES")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
private fun ResetDriverPasswordDialog(
    driver: UserProfile,
    onDismiss: () -> Unit,
    onConfirmReset: (newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                newPassword = ""
                confirmPassword = ""
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Reset Driver Password",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Reset password for ${driver.name.ifBlank { "Driver" }} (${driver.customId}).",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (successMessage != null) {
                    Text(
                        text = successMessage!!,
                        color = Color(0xFF15803D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("New Password") },
                    placeholder = { Text("Minimum 6 characters") },
                    singleLine = true,
                    enabled = !isSubmitting && successMessage == null,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (newPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm Password") },
                    placeholder = { Text("Re-enter new password") },
                    singleLine = true,
                    enabled = !isSubmitting && successMessage == null,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Password must be at least 6 characters.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            if (successMessage == null) {
                Button(
                    onClick = {
                        if (newPassword.length < 6) {
                            errorMessage = "Password must be at least 6 characters long."
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            errorMessage = "Passwords do not match."
                            return@Button
                        }
                        isSubmitting = true
                        errorMessage = null
                        onConfirmReset(
                            newPassword,
                            {
                                isSubmitting = false
                                newPassword = ""
                                confirmPassword = ""
                                successMessage = "Driver password reset successfully."
                            },
                            { err ->
                                isSubmitting = false
                                newPassword = ""
                                confirmPassword = ""
                                errorMessage = err
                            }
                        )
                    },
                    enabled = !isSubmitting && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("RESET PASSWORD", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        newPassword = ""
                        confirmPassword = ""
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (successMessage == null) {
                OutlinedButton(
                    onClick = {
                        newPassword = ""
                        confirmPassword = ""
                        onDismiss()
                    },
                    enabled = !isSubmitting
                ) {
                    Text("CANCEL")
                }
            }
        }
    )
}

@Composable
private fun BusSelectionDialog(
    currentBusId: String?,
    allBuses: List<Bus>,
    onDismiss: () -> Unit,
    onSelectBus: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Assigned Bus", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Option: Unassigned
                item {
                    val isSelected = currentBusId.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBus(null) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectBus(null) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("None (Unassign)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Driver will not be assigned to any bus", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }

                items(allBuses, key = { it.busId }) { bus ->
                    val isSelected = bus.busId == currentBusId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBus(bus.busId) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectBus(bus.busId) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(bus.busNumber.ifBlank { bus.busId }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val driverNote = if (!bus.driverName.isNullOrBlank()) {
                                "Currently assigned: ${bus.driverName}"
                            } else {
                                "Status: Free"
                            }
                            Text(driverNote, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun BusesTabContent(
    buses: List<Bus>,
    routes: List<Route>,
    onAddBusClick: () -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onAssignDriverClick: (Bus) -> Unit,
    onUnassignDriverClick: (Bus) -> Unit,
    onChangeRouteClick: (Bus) -> Unit,
    onDeleteBusClick: (Bus) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onAddBusClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ADD NEW BUS", fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(buses, key = { it.busId }) { bus ->
                val assignedRoute = routes.find { it.routeId.equals(bus.routeId, ignoreCase = true) }
                BusCard(
                    bus = bus,
                    routeName = assignedRoute?.name ?: bus.routeId,
                    onToggleActive = { onToggleActive(bus.busId, it) },
                    onAssignDriver = { onAssignDriverClick(bus) },
                    onUnassignDriver = { onUnassignDriverClick(bus) },
                    onChangeRoute = { onChangeRouteClick(bus) },
                    onDeleteBus = { onDeleteBusClick(bus) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BusCard(
    bus: Bus,
    routeName: String,
    onToggleActive: (Boolean) -> Unit,
    onAssignDriver: () -> Unit,
    onUnassignDriver: () -> Unit,
    onChangeRoute: () -> Unit,
    onDeleteBus: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bus.busNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Reg: ${bus.registrationNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { onToggleActive(!bus.isActive) },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(if (bus.isActive) "Active" else "Inactive", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onDeleteBus,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Bus",
                            tint = StatusDangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(10.dp))

            // Route Assignment Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = if (routeName.isNotBlank()) CollegePrimary else Color.Gray,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Column {
                        Text(
                            text = if (routeName.isNotBlank()) "Route: $routeName" else "No Route Assigned",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (bus.routeId.isNotBlank()) "ID: ${bus.routeId}" else "Unassigned",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                OutlinedButton(
                    onClick = onChangeRoute,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Change Route", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(8.dp))

            // Driver Assignment Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (!bus.driverId.isNullOrBlank()) CollegePrimary else Color.Gray,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = if (!bus.driverId.isNullOrBlank()) {
                            "Driver: ${bus.driverName ?: bus.driverId}"
                        } else {
                            "Driver: Unassigned"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!bus.driverId.isNullOrBlank()) Color.Black else Color.Gray,
                        fontWeight = if (!bus.driverId.isNullOrBlank()) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                if (bus.driverId.isNullOrBlank()) {
                    Button(
                        onClick = onAssignDriver,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                    ) {
                        Text("Assign Driver", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onAssignDriver,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Change", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onUnassignDriver,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed)
                        ) {
                            Text("Unassign", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteBusConfirmationDialog(
    bus: Bus,
    hasAssignedDriver: Boolean,
    assignedDriverName: String?,
    hasAssignedRoute: Boolean,
    routeName: String?,
    hasActiveSwap: Boolean,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = StatusDangerRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Bus", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Are you sure you want to permanently delete ${bus.busNumber} (${bus.busId})?",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )

                if (hasAssignedDriver || hasAssignedRoute || hasActiveSwap) {
                    Text(
                        text = "Dependency Warnings:",
                        fontWeight = FontWeight.Bold,
                        color = StatusWarningOrange,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (hasAssignedDriver) {
                        Text(
                            text = "• Assigned to Driver: ${assignedDriverName ?: bus.driverId}",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                    if (hasAssignedRoute) {
                        Text(
                            text = "• Assigned to Route: ${routeName ?: bus.routeId}",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                    if (hasActiveSwap) {
                        Text(
                            text = "• Active bus swap is currently in progress for this bus",
                            fontSize = 13.sp,
                            color = StatusDangerRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "This action cannot be undone. All real-time tracking nodes, location sources, and fleet records will be permanently removed.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("DELETE BUS", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteDriverConfirmationDialog(
    driver: UserProfile,
    assignedBusId: String?,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = StatusDangerRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Driver", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Are you sure you want to permanently delete driver account ${driver.name} (${driver.customId})?",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )

                if (!assignedBusId.isNullOrBlank()) {
                    Text(
                        text = "Dependency Warning:",
                        fontWeight = FontWeight.Bold,
                        color = StatusWarningOrange,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "• Driver is currently assigned to Bus: $assignedBusId. Deleting will automatically unassign them.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }

                Text(
                    text = "This driver will no longer be able to log in, and their profile will be removed from the system.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("DELETE DRIVER", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChangeBusRouteDialog(
    bus: Bus,
    routes: List<Route>,
    onDismiss: () -> Unit,
    onConfirm: (newRouteId: String) -> Unit
) {
    var selectedRouteId by remember { mutableStateOf(bus.routeId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Assign Route to Bus",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${bus.busNumber} (${bus.registrationNumber})",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            if (routes.isEmpty()) {
                Text(
                    text = "No routes available. Please create a route in the Routes & Stops tab first.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Select Route:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    routes.forEach { route ->
                        val isSelected = selectedRouteId.equals(route.routeId, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRouteId = route.routeId }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedRouteId = route.routeId },
                                colors = RadioButtonDefaults.colors(selectedColor = CollegePrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = route.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "ID: ${route.routeId} • ${route.stops.size} stops",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedRouteId.isNotBlank()) {
                        onConfirm(selectedRouteId)
                    }
                },
                enabled = selectedRouteId.isNotBlank() && routes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                Text("Save Route")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RoutesTabContent(
    routes: List<Route>,
    buses: List<Bus>,
    onAddNewRouteClick: () -> Unit,
    onEditRouteClick: (Route) -> Unit,
    onToggleRouteActive: (String, Boolean) -> Unit,
    onDeleteRouteClick: (Route) -> Unit,
    onManageStopsClick: (Route) -> Unit,
    onConfigureSchedule: (Route) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onAddNewRouteClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ADD NEW ROUTE", fontWeight = FontWeight.Bold)
        }

        if (routes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No routes found in the system.", color = Color.Gray)
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(routes, key = { it.routeId }) { route ->
                val assignedBuses = buses.filter { it.routeId.equals(route.routeId, ignoreCase = true) }
                RouteCard(
                    route = route,
                    assignedBuses = assignedBuses,
                    onEdit = { onEditRouteClick(route) },
                    onToggleActive = { onToggleRouteActive(route.routeId, it) },
                    onDelete = { onDeleteRouteClick(route) },
                    onManageStops = { onManageStopsClick(route) },
                    onConfigureSchedule = { onConfigureSchedule(route) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RouteCard(
    route: Route,
    assignedBuses: List<Bus>,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onManageStops: () -> Unit,
    onConfigureSchedule: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Route ID: ${route.routeId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                OutlinedButton(
                    onClick = { onToggleActive(!route.active) },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (route.active) "Active" else "Disabled",
                        fontSize = 12.sp,
                        color = if (route.active) StatusActiveGreen else StatusDangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (route.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = route.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stops: ${route.stops.size} (${route.stops.count { it.active }} active)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "Buses: " + if (assignedBuses.isEmpty()) "None" else assignedBuses.joinToString { it.busNumber },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (assignedBuses.isNotEmpty()) CollegePrimary else Color.Gray,
                    fontWeight = if (assignedBuses.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFFF9FAFB),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "SCHEDULE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Morning: ${route.schedules.morning.startTime} → ${route.schedules.morning.endTime}",
                            fontSize = 12.sp,
                            color = if (route.schedules.morning.enabled) Color.Black else Color.Gray
                        )
                        Text(
                            text = if (route.schedules.morning.enabled) "Enabled" else "Disabled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (route.schedules.morning.enabled) StatusActiveGreen else StatusDangerRed
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Evening: ${route.schedules.evening.startTime} → ${route.schedules.evening.endTime}",
                            fontSize = 12.sp,
                            color = if (route.schedules.evening.enabled) Color.Black else Color.Gray
                        )
                        Text(
                            text = if (route.schedules.evening.enabled) "Enabled" else "Disabled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (route.schedules.evening.enabled) StatusActiveGreen else StatusDangerRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = if (!route.schedulePdfUrl.isNullOrBlank()) Color(0xFFEFF6FF) else Color(0xFFF9FAFB),
                shape = RoundedCornerShape(8.dp),
                border = if (!route.schedulePdfUrl.isNullOrBlank()) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = if (!route.schedulePdfUrl.isNullOrBlank()) Color(0xFF1D4ED8) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!route.schedulePdfUrl.isNullOrBlank()) (route.schedulePdfName ?: "Timetable PDF attached") else "No timetable PDF uploaded",
                            fontSize = 12.sp,
                            fontWeight = if (!route.schedulePdfUrl.isNullOrBlank()) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (!route.schedulePdfUrl.isNullOrBlank()) Color(0xFF1D4ED8) else Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(10.dp))

            // Primary action: Manage Stops
            Button(
                onClick = onManageStops,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Manage Stops (${route.stops.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary action row: Edit, Schedule, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onConfigureSchedule,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Schedule", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ManageStopsDialog(
    route: Route,
    onDismiss: () -> Unit,
    onAddStop: (name: String, lat: Double, lng: Double, active: Boolean, (Result<Unit>) -> Unit) -> Unit,
    onEditStop: (stopId: String, name: String, lat: Double, lng: Double, active: Boolean, (Result<Unit>) -> Unit) -> Unit,
    onDeleteStop: (stopId: String) -> Unit,
    onReorderStop: (stopId: String, moveUp: Boolean) -> Unit,
    onToggleStopActive: (stopId: String, active: Boolean) -> Unit,
    onSaveAllStops: (List<BusStop>) -> Unit
) {
    var showMapStopCreator by remember { mutableStateOf(false) }
    var showAddStopDialog by remember { mutableStateOf(false) }
    var stopToEdit by remember { mutableStateOf<BusStop?>(null) }
    var stopToDelete by remember { mutableStateOf<BusStop?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Manage Stops: ${route.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Route ID: ${route.routeId} • ${route.stops.size} Total Stops", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Primary action: Add Stops on Map
                Button(
                    onClick = { showMapStopCreator = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ADD STOPS ON MAP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showAddStopDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MANUAL ENTRY (LAT / LNG)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                if (route.stops.isEmpty()) {
                    Text(
                        "No stops added yet for this route.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    val sortedStops = route.stops.sortedBy { it.order }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(sortedStops, key = { _, stop -> stop.stopId }) { index, stop ->
                            StopItemCard(
                                stop = stop,
                                index = index,
                                totalCount = sortedStops.size,
                                onMoveUp = { onReorderStop(stop.stopId, true) },
                                onMoveDown = { onReorderStop(stop.stopId, false) },
                                onEdit = { stopToEdit = stop },
                                onDelete = { stopToDelete = stop },
                                onToggleActive = { onToggleStopActive(stop.stopId, it) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                Text("Done")
            }
        }
    )

    if (showAddStopDialog) {
        AddEditStopDialog(
            stop = null,
            onDismiss = { showAddStopDialog = false },
            onConfirm = { name, lat, lng, active, onError ->
                onAddStop(name, lat, lng, active) { result ->
                    result.onSuccess {
                        showAddStopDialog = false
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to add stop")
                    }
                }
            }
        )
    }

    val currentStopToEdit = stopToEdit
    if (currentStopToEdit != null) {
        AddEditStopDialog(
            stop = currentStopToEdit,
            onDismiss = { stopToEdit = null },
            onConfirm = { name, lat, lng, active, onError ->
                onEditStop(currentStopToEdit.stopId, name, lat, lng, active) { result ->
                    result.onSuccess {
                        stopToEdit = null
                    }.onFailure { err ->
                        onError(err.message ?: "Failed to update stop")
                    }
                }
            }
        )
    }

    val currentStopToDelete = stopToDelete
    if (currentStopToDelete != null) {
        AlertDialog(
            onDismissRequest = { stopToDelete = null },
            title = { Text("Delete Stop", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${currentStopToDelete.name}'? Stop ordering will be re-indexed automatically.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStop(currentStopToDelete.stopId)
                        stopToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { stopToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMapStopCreator) {
        MapStopCreatorDialog(
            route = route,
            onDismiss = { showMapStopCreator = false },
            onSaveStops = { updatedStops ->
                onSaveAllStops(updatedStops)
                showMapStopCreator = false
            }
        )
    }
}

@Composable
private fun StopItemCard(
    stop: BusStop,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (stop.active) Color(0xFFF9F9F9) else Color(0xFFF0F0F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "#${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CollegePrimary,
                        modifier = Modifier
                            .widthIn(min = 28.dp)
                            .padding(end = 6.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = stop.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Lat: ${"%.4f".format(stop.latitude)}, Lng: ${"%.4f".format(stop.longitude)}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (stop.active) "Active" else "Disabled",
                    color = if (stop.active) StatusActiveGreen else StatusDangerRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFFE5E5E5))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reorder buttons
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            modifier = Modifier.size(18.dp),
                            tint = if (index > 0) CollegePrimary else Color.LightGray
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            modifier = Modifier.size(18.dp),
                            tint = if (index < totalCount - 1) CollegePrimary else Color.LightGray
                        )
                    }
                }

                // Action buttons: Edit, Toggle Active, Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Stop",
                            modifier = Modifier.size(18.dp),
                            tint = CollegePrimary
                        )
                    }
                    OutlinedButton(
                        onClick = { onToggleActive(!stop.active) },
                        modifier = Modifier.height(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(if (stop.active) "Disable" else "Enable", fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Stop",
                            modifier = Modifier.size(18.dp),
                            tint = StatusDangerRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEditRouteDialog(
    route: Route?,
    onDismiss: () -> Unit,
    onConfirm: (routeId: String, name: String, description: String, active: Boolean, onError: (String) -> Unit) -> Unit
) {
    val isEdit = route != null
    var routeId by remember { mutableStateOf(route?.routeId ?: "") }
    var name by remember { mutableStateOf(route?.name ?: "") }
    var description by remember { mutableStateOf(route?.description ?: "") }
    var active by remember { mutableStateOf(route?.active ?: true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Route" else "Add New Route",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isEdit) {
                    OutlinedTextField(
                        value = routeId,
                        onValueChange = { routeId = it },
                        label = { Text("Route ID (e.g. ROUTE-02)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting
                    )
                } else {
                    Text(
                        text = "Route ID: ${route.routeId}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Route Name (e.g. Koppal -> College)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSubmitting
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !isSubmitting
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = if (active) "Route is active and visible to students" else "Route is disabled and hidden from students",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = active,
                        onCheckedChange = { active = it },
                        enabled = !isSubmitting,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CollegePrimary,
                            checkedTrackColor = CollegePrimary.copy(alpha = 0.5f)
                        )
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isEdit && routeId.isBlank()) {
                        errorMessage = "Route ID is required."
                        return@Button
                    }
                    if (name.isBlank()) {
                        errorMessage = "Route name is required."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null
                    onConfirm(routeId.trim(), name.trim(), description.trim(), active) { err ->
                        isSubmitting = false
                        errorMessage = err
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEdit) "Save Changes" else "Create Route")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddEditStopDialog(
    stop: BusStop?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, lat: Double, lng: Double, active: Boolean, onError: (String) -> Unit) -> Unit
) {
    val isEdit = stop != null
    var name by remember { mutableStateOf(stop?.name ?: "") }
    var latStr by remember { mutableStateOf(stop?.latitude?.toString() ?: "") }
    var lngStr by remember { mutableStateOf(stop?.longitude?.toString() ?: "") }
    var active by remember { mutableStateOf(stop?.active ?: true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Stop" else "Add New Stop",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Stop Name (e.g. Central Bus Stand)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSubmitting
                )

                OutlinedTextField(
                    value = latStr,
                    onValueChange = { latStr = it },
                    label = { Text("Latitude (-90.0 to 90.0)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isSubmitting
                )

                OutlinedTextField(
                    value = lngStr,
                    onValueChange = { lngStr = it },
                    label = { Text("Longitude (-180.0 to 180.0)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isSubmitting
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = if (active) "Visible to students on map/route" else "Hidden from student view",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = active,
                        onCheckedChange = { active = it },
                        enabled = !isSubmitting,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CollegePrimary,
                            checkedTrackColor = CollegePrimary.copy(alpha = 0.5f)
                        )
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Stop name is required."
                        return@Button
                    }
                    val lat = latStr.toDoubleOrNull()
                    if (lat == null || lat !in -90.0..90.0) {
                        errorMessage = "Please enter a valid latitude between -90.0 and 90.0."
                        return@Button
                    }
                    val lng = lngStr.toDoubleOrNull()
                    if (lng == null || lng !in -180.0..180.0) {
                        errorMessage = "Please enter a valid longitude between -180.0 and 180.0."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null
                    onConfirm(name.trim(), lat, lng, active) { err ->
                        isSubmitting = false
                        errorMessage = err
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEdit) "Save Changes" else "Add Stop")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SwapsTabContent(
    swaps: List<BusSwap>,
    onCancelSwap: (String) -> Unit
) {
    if (swaps.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No active or historical bus swaps reported.", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(swaps, key = { it.swapId }) { swap ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${swap.originalBusId} -> ${swap.replacementBusId}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        val statusColor = when (swap.status) {
                            "ACTIVE" -> StatusWarningOrange
                            "RESOLVED" -> StatusActiveGreen
                            else -> StatusDangerRed
                        }
                        Text(
                            text = swap.status,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Message: ${swap.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "Reported by: ${swap.driverName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    if (swap.status == "ACTIVE") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { onCancelSwap(swap.swapId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CANCEL & RESTORE FLEET", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureScheduleDialog(
    route: Route,
    onDismiss: () -> Unit,
    onConfirm: (mEnabled: Boolean, mStart: String, mEnd: String, eEnabled: Boolean, eStart: String, eEnd: String, onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val currSched = route.schedules
    var mEnabled by remember { mutableStateOf(currSched.morning.enabled) }
    var mStart by remember { mutableStateOf(currSched.morning.startTime) }
    var mEnd by remember { mutableStateOf(currSched.morning.endTime) }

    var eEnabled by remember { mutableStateOf(currSched.evening.enabled) }
    var eStart by remember { mutableStateOf(currSched.evening.startTime) }
    var eEnd by remember { mutableStateOf(currSched.evening.endTime) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun pickTime(initialTime: String, onSelected: (String) -> Unit) {
        val parts = initialTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                onSelected(String.format(java.util.Locale.US, "%02d:%02d", hour, minute))
            },
            h,
            m,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Column {
                Text("Configure Schedule", fontWeight = FontWeight.Bold)
                Text(route.name, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Morning Window Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Morning Window", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Switch(
                                checked = mEnabled,
                                onCheckedChange = { mEnabled = it },
                                enabled = !isSubmitting,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CollegePrimary,
                                    checkedTrackColor = CollegePrimary.copy(alpha = 0.5f)
                                )
                            )
                        }
                        if (mEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = mStart,
                                    onValueChange = { mStart = it },
                                    label = { Text("Start (HH:mm)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = !isSubmitting,
                                    trailingIcon = {
                                        IconButton(onClick = { pickTime(mStart) { mStart = it } }, enabled = !isSubmitting) {
                                            Icon(Icons.Default.DateRange, contentDescription = "Pick Morning Start Time", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = mEnd,
                                    onValueChange = { mEnd = it },
                                    label = { Text("End (HH:mm)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = !isSubmitting,
                                    trailingIcon = {
                                        IconButton(onClick = { pickTime(mEnd) { mEnd = it } }, enabled = !isSubmitting) {
                                            Icon(Icons.Default.DateRange, contentDescription = "Pick Morning End Time", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                            }
                        } else {
                            Text("Morning Auto-Tracking is disabled.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }

                // Evening Window Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Evening Window", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Switch(
                                checked = eEnabled,
                                onCheckedChange = { eEnabled = it },
                                enabled = !isSubmitting,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CollegePrimary,
                                    checkedTrackColor = CollegePrimary.copy(alpha = 0.5f)
                                )
                            )
                        }
                        if (eEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = eStart,
                                    onValueChange = { eStart = it },
                                    label = { Text("Start (HH:mm)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = !isSubmitting,
                                    trailingIcon = {
                                        IconButton(onClick = { pickTime(eStart) { eStart = it } }, enabled = !isSubmitting) {
                                            Icon(Icons.Default.DateRange, contentDescription = "Pick Evening Start Time", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = eEnd,
                                    onValueChange = { eEnd = it },
                                    label = { Text("End (HH:mm)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = !isSubmitting,
                                    trailingIcon = {
                                        IconButton(onClick = { pickTime(eEnd) { eEnd = it } }, enabled = !isSubmitting) {
                                            Icon(Icons.Default.DateRange, contentDescription = "Pick Evening End Time", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                            }
                        } else {
                            Text("Evening Auto-Tracking is disabled.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    errorMessage = null
                    onConfirm(mEnabled, mStart, mEnd, eEnabled, eStart, eEnd) { err ->
                        isSubmitting = false
                        errorMessage = err
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Schedule")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}

