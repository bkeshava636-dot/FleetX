package com.college.bustracker.ui.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.college.bustracker.pass.ui.BusPassScreen
import com.college.bustracker.pass.ui.BusPassViewModel
import com.college.bustracker.util.SchedulePdfHelper
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.res.stringResource
import com.college.bustracker.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.LiveLocation
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.ui.common.EmptyStateCard
import com.college.bustracker.ui.common.LoadingCard
import com.college.bustracker.ui.common.SectionHeader
import com.college.bustracker.ui.common.StatusBadge
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusGreenContainer
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.AutomaticRideState
import com.college.bustracker.util.EtaStatus
import com.college.bustracker.util.RouteEtaResult
import com.college.bustracker.util.ScheduleHelper
import com.college.bustracker.util.StopEta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHomeScreen(
    viewModel: StudentViewModel,
    busPassViewModel: BusPassViewModel? = null,
    onNavigateToMap: () -> Unit,
    onNavigateToStaffLogin: () -> Unit,
    onNavigateToBusPass: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val buses by viewModel.buses.collectAsState()
    val isLoadingBuses by viewModel.isLoadingBuses.collectAsState()
    val selectedBus by viewModel.selectedBus.collectAsState()
    val route by viewModel.currentRoute.collectAsState()
    val selectedStop by viewModel.selectedStop.collectAsState()
    val liveLoc by viewModel.liveLocation.collectAsState()
    val trackingState by viewModel.trackingState.collectAsState()
    val nextStop by viewModel.nextStop.collectAsState()
    val nextStopEta by viewModel.nextStopEta.collectAsState()
    val upcomingStopsEta by viewModel.upcomingStopsEta.collectAsState()
    val routeEtaResult by viewModel.routeEtaResult.collectAsState()
    val swapAlert by viewModel.activeSwapForStudent.collectAsState()
    val lastUpdateText by viewModel.lastUpdateText.collectAsState()
    val isHolidayToday by viewModel.isHolidayToday.collectAsState()
    val todayHolidayName by viewModel.todayHolidayName.collectAsState()
    val driverInfo by viewModel.driverDisplayInfo.collectAsState()
    val operatingSchedule by viewModel.operatingSchedule.collectAsState()
    val announcements by viewModel.announcements.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackInitialType by remember { mutableStateOf(com.college.bustracker.data.model.StudentFeedback.TYPE_APP) }
    var feedbackPreselectedBusId by remember { mutableStateOf<String?>(null) }
    var selectedAnnouncementForDetail by remember { mutableStateOf<com.college.bustracker.data.model.Announcement?>(null) }
    var busDropdownOpen by remember { mutableStateOf(false) }
    var stopDropdownOpen by remember { mutableStateOf(false) }

    val stops = remember(route) { route?.stops ?: emptyList() }
    val isSwapActive = remember(swapAlert) { swapAlert != null && !swapAlert?.replacementBusId.isNullOrBlank() }
    val displayBusNumber = remember(isSwapActive, swapAlert, selectedBus) {
        if (isSwapActive) swapAlert?.replacementBusId ?: "" else selectedBus?.busNumber ?: "—"
    }
    val formattedBusTitle = remember(displayBusNumber) {
        when {
            displayBusNumber.isBlank() || displayBusNumber == "—" -> "BUS —"
            displayBusNumber.startsWith("bus", ignoreCase = true) -> displayBusNumber.uppercase()
            else -> "BUS $displayBusNumber"
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    viewModel.pauseLiveTracking()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    viewModel.resumeLiveTracking()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = stringResource(R.string.tab_live_track)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_live_track),
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CollegePrimary,
                        selectedTextColor = CollegePrimary,
                        indicatorColor = CollegePrimary.copy(alpha = 0.12f),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = stringResource(R.string.tab_bus_pass)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_bus_pass),
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CollegePrimary,
                        selectedTextColor = CollegePrimary,
                        indicatorColor = CollegePrimary.copy(alpha = 0.12f),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.tab_schedule_info)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_schedule_info),
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CollegePrimary,
                        selectedTextColor = CollegePrimary,
                        indicatorColor = CollegePrimary.copy(alpha = 0.12f),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: LIVE TRACK
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CollegeSurface)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        StudentTopHeader(
                            onNavigateToStaffLogin = onNavigateToStaffLogin,
                            onLanguageClick = { showLanguageDialog = true }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Campus Announcements Section (Prominently displayed at top)
                        if (announcements.isNotEmpty()) {
                            CampusAnnouncementsBanner(
                                announcements = announcements,
                                onAnnouncementClick = { ann ->
                                    com.college.bustracker.data.repository.AnnouncementPreferences.markAsRead(context, ann.id)
                                    selectedAnnouncementForDetail = ann
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        if (isLoadingBuses) {
                            LoadingCard(message = stringResource(R.string.loading_fleet))
                        } else if (buses.isEmpty()) {
                            EmptyStateCard(
                                title = stringResource(R.string.no_active_buses_title),
                                description = stringResource(R.string.no_active_buses_desc),
                                icon = Icons.Default.DirectionsBus
                            )
                        } else {
                            // Location-Specific Bus Swap Banner
                            if (swapAlert != null) {
                                BusSwapAlertBanner(swapAlert = swapAlert, selectedStop = selectedStop)
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // Prominent Student Home Screen Bus ETA Card or Holiday Notice
                            if (isHolidayToday) {
                                HolidayNoticeCard(todayHolidayName = todayHolidayName)
                                Spacer(modifier = Modifier.height(14.dp))
                            } else {
                                ProminentEtaCard(
                                    formattedBusTitle = formattedBusTitle,
                                    isSwapActive = isSwapActive,
                                    originalBusId = swapAlert?.originalBusId,
                                    trackingState = trackingState,
                                    lastUpdateText = lastUpdateText,
                                    stops = stops,
                                    route = route,
                                    routeEtaResult = routeEtaResult,
                                    nextStopEta = nextStopEta,
                                    liveLoc = liveLoc,
                                    selectedStop = selectedStop,
                                    upcomingStopsEta = upcomingStopsEta,
                                    onNavigateToMap = onNavigateToMap,
                                    onReportIssue = {
                                        feedbackInitialType = com.college.bustracker.data.model.StudentFeedback.TYPE_BUS
                                        feedbackPreselectedBusId = selectedBus?.busId
                                        showFeedbackDialog = true
                                    }
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // Driver Information Card
                            DriverInfoCard(driverInfo = driverInfo)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Bus & Stop Selector Card
                            BusAndStopSelectorCard(
                                buses = buses,
                                selectedBus = selectedBus,
                                route = route,
                                stops = stops,
                                selectedStop = selectedStop,
                                busDropdownOpen = busDropdownOpen,
                                onBusDropdownOpenChange = { busDropdownOpen = it },
                                onSelectBus = { bus ->
                                    viewModel.selectBus(bus)
                                    busDropdownOpen = false
                                },
                                stopDropdownOpen = stopDropdownOpen,
                                onStopDropdownOpenChange = { stopDropdownOpen = it },
                                onSelectStop = { stop ->
                                    viewModel.selectStop(stop)
                                    stopDropdownOpen = false
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // FleetX Updates & About Section
                        com.college.bustracker.update.ui.UpdateCard()

                        Spacer(modifier = Modifier.height(14.dp))

                        // Student Feedback & Transit Reports Card
                        StudentFeedbackCard(
                            onOpenFeedback = { isApp ->
                                feedbackInitialType = if (isApp) com.college.bustracker.data.model.StudentFeedback.TYPE_APP else com.college.bustracker.data.model.StudentFeedback.TYPE_BUS
                                feedbackPreselectedBusId = if (isApp) null else selectedBus?.busId
                                showFeedbackDialog = true
                            }
                        )
                    }
                }

                1 -> {
                    // TAB 1: BUS PASS
                    if (busPassViewModel != null) {
                        BusPassScreen(
                            viewModel = busPassViewModel,
                            onNavigateBack = { selectedTab = 0 },
                            showBackButton = false
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CollegeSurface)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = CollegePrimary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToBusPass,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                Text(stringResource(R.string.view_scan))
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: SCHEDULE & INFO
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CollegeSurface)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        StudentTopHeader(
                            onNavigateToStaffLogin = onNavigateToStaffLogin,
                            onLanguageClick = { showLanguageDialog = true }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Master Timetable PDF Card (College-Wide / All Routes)
                        MasterSchedulePdfCard(
                            pdfUrl = operatingSchedule.allRoutesPdfUrl,
                            pdfName = operatingSchedule.allRoutesPdfName,
                            onViewPdf = { url, name ->
                                coroutineScope.launch {
                                    SchedulePdfHelper.openPdf(context, url, name)
                                }
                            }
                        )

                        // Route-Specific Timetable PDF Card (if route has an individual timetable)
                        val currentRoute = route
                        if (currentRoute != null && !currentRoute.schedulePdfUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            RouteSchedulePdfCard(
                                route = currentRoute,
                                onViewPdf = { url, name ->
                                    coroutineScope.launch {
                                        SchedulePdfHelper.openPdf(context, url, name)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Operating Windows Card
                        OperatingScheduleCard(operatingSchedule = operatingSchedule)

                        // Route Stops Timeline Card
                        if (stops.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            RouteStopsTimelineCard(
                                stops = stops,
                                selectedStop = selectedStop
                            )
                        }

                        // Campus Announcements Card
                        if (announcements.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            CampusAnnouncementsBanner(
                                announcements = announcements,
                                onAnnouncementClick = { ann ->
                                    com.college.bustracker.data.repository.AnnouncementPreferences.markAsRead(context, ann.id)
                                    selectedAnnouncementForDetail = ann
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Automatic Student Transit Backup Card
                        AutomaticStudentTransitBackupCard(
                            viewModel = viewModel,
                            selectedBus = selectedBus
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // FleetX Updates & About Section
                        com.college.bustracker.update.ui.UpdateCard()

                        Spacer(modifier = Modifier.height(14.dp))

                        // Student Feedback & Transit Reports Card
                        StudentFeedbackCard(
                            onOpenFeedback = { isApp ->
                                feedbackInitialType = if (isApp) com.college.bustracker.data.model.StudentFeedback.TYPE_APP else com.college.bustracker.data.model.StudentFeedback.TYPE_BUS
                                feedbackPreselectedBusId = if (isApp) null else selectedBus?.busId
                                showFeedbackDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showLanguageDialog) {
        com.college.bustracker.ui.common.LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (selectedAnnouncementForDetail != null) {
        com.college.bustracker.ui.announcement.AnnouncementDetailDialog(
            announcement = selectedAnnouncementForDetail!!,
            onDismiss = { selectedAnnouncementForDetail = null },
            onMarkAsRead = {
                com.college.bustracker.data.repository.AnnouncementPreferences.markAsRead(context, selectedAnnouncementForDetail!!.id)
            }
        )
    }

    if (showFeedbackDialog) {
        StudentFeedbackDialog(
            buses = buses,
            initialFeedbackType = feedbackInitialType,
            preselectedBusId = feedbackPreselectedBusId,
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { report, onResult ->
                viewModel.submitFeedback(report, onResult)
            }
        )
    }
}

@Composable
private fun StudentTopHeader(
    onNavigateToStaffLogin: () -> Unit,
    onLanguageClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(CollegePrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "FleetX",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onLanguageClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFF0F766E)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (com.college.bustracker.util.LocaleHelper.isKannada()) "ಕನ್ನಡ" else "EN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            OutlinedButton(
                onClick = onNavigateToStaffLogin,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.staff_login),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun QuickTimetablePdfBanner(
    title: String? = null,
    pdfName: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFDBEAFE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title ?: stringResource(R.string.route_schedule_pdf_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E3A8A)
                    )
                    Text(
                        text = pdfName ?: stringResource(R.string.route_schedule_pdf_desc),
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Color(0xFF1D4ED8),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RouteSchedulePdfCard(
    route: Route?,
    onViewPdf: (url: String, name: String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.route_schedule_pdf_title),
                icon = Icons.Default.Description
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (route == null) {
                Text(
                    text = stringResource(R.string.no_route_assigned),
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            } else if (!route.schedulePdfUrl.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.route_schedule_pdf_desc),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onViewPdf(route.schedulePdfUrl!!, route.schedulePdfName) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = route.schedulePdfName ?: stringResource(R.string.view_schedule_pdf_action),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.no_schedule_pdf_available),
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterSchedulePdfCard(
    pdfUrl: String?,
    pdfName: String?,
    onViewPdf: (url: String, name: String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.master_timetable_title),
                icon = Icons.Default.Description,
                action = {
                    if (!pdfUrl.isNullOrBlank()) {
                        Surface(
                            color = StatusActiveGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.active_for_all_users),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusActiveGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!pdfUrl.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.master_timetable_desc),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onViewPdf(pdfUrl, pdfName) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pdfName ?: stringResource(R.string.view_master_timetable_action),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.no_master_timetable_available),
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampusAnnouncementsBanner(
    announcements: List<com.college.bustracker.data.model.Announcement>,
    onAnnouncementClick: (com.college.bustracker.data.model.Announcement) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = CollegePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.campus_announcements),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                val unreadCount = announcements.count {
                    !com.college.bustracker.data.repository.AnnouncementPreferences.isAnnouncementRead(context, it.id)
                }
                if (unreadCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CollegePrimary
                    ) {
                        Text(
                            text = "$unreadCount ${stringResource(R.string.unread_badge)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                announcements.forEach { announcement ->
                    val isRead = com.college.bustracker.data.repository.AnnouncementPreferences.isAnnouncementRead(context, announcement.id)
                    com.college.bustracker.ui.announcement.AnnouncementCard(
                        announcement = announcement,
                        isUnread = !isRead,
                        onClick = { onAnnouncementClick(announcement) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BusSwapAlertBanner(
    swapAlert: BusSwap?,
    selectedStop: BusStop?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = StatusWarningOrange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bus_replacement_notice),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.bus_replacement_operating_today,
                    swapAlert?.replacementBusId ?: "",
                    swapAlert?.originalBusId ?: ""
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF78350F)
            )

            Text(
                text = stringResource(
                    R.string.affects_stop,
                    selectedStop?.name ?: stringResource(R.string.all_route_stops_label)
                ),
                fontSize = 12.sp,
                color = Color(0xFF92400E),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (!swapAlert?.message.isNullOrBlank()) {
                Text(
                    text = swapAlert?.message ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFF78350F)
                )
            }
        }
    }
}

@Composable
private fun HolidayNoticeCard(todayHolidayName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFDBEAFE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF1D4ED8),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.no_bus_service_today),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(
                        R.string.holiday_prefix,
                        todayHolidayName ?: stringResource(R.string.college_holiday)
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2563EB)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusAndStopSelectorCard(
    buses: List<com.college.bustracker.data.model.Bus>,
    selectedBus: com.college.bustracker.data.model.Bus?,
    route: Route?,
    stops: List<BusStop>,
    selectedStop: BusStop?,
    busDropdownOpen: Boolean,
    onBusDropdownOpenChange: (Boolean) -> Unit,
    onSelectBus: (com.college.bustracker.data.model.Bus) -> Unit,
    stopDropdownOpen: Boolean,
    onStopDropdownOpenChange: (Boolean) -> Unit,
    onSelectStop: (BusStop) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.select_bus_and_route),
                icon = Icons.Default.DirectionsBus
            )

            Spacer(modifier = Modifier.height(10.dp))

            ExposedDropdownMenuBox(
                expanded = busDropdownOpen,
                onExpandedChange = onBusDropdownOpenChange
            ) {
                OutlinedTextField(
                    value = selectedBus?.let { "${it.busNumber} • ${it.registrationNumber}" } ?: stringResource(R.string.select_a_bus),
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = CollegePrimary)
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = busDropdownOpen) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CollegePrimary,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )
                ExposedDropdownMenu(
                    expanded = busDropdownOpen,
                    onDismissRequest = { onBusDropdownOpenChange(false) }
                ) {
                    buses.forEach { bus ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${bus.busNumber} • ${bus.registrationNumber}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (bus.active) {
                                        Text(
                                            text = stringResource(R.string.in_active_service),
                                            fontSize = 11.sp,
                                            color = StatusActiveGreen
                                        )
                                    }
                                }
                            },
                            onClick = { onSelectBus(bus) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Route Summary Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CollegePrimary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (route != null) stringResource(R.string.route_prefix, route.name) else stringResource(R.string.no_route_assigned),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (route != null) Color(0xFF1E293B) else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Boarding Stop Selector
            Text(
                text = stringResource(R.string.my_boarding_stop_label),
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = stopDropdownOpen,
                onExpandedChange = { if (stops.isNotEmpty()) onStopDropdownOpenChange(!stopDropdownOpen) }
            ) {
                OutlinedTextField(
                    value = when {
                        stops.isEmpty() -> stringResource(R.string.no_active_stops)
                        selectedStop != null -> "${selectedStop.order}. ${selectedStop.name}"
                        else -> stringResource(R.string.choose_boarding_stop)
                    },
                    onValueChange = {},
                    readOnly = true,
                    enabled = stops.isNotEmpty(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (stops.isNotEmpty()) CollegePrimary else Color(0xFFCBD5E1)
                        )
                    },
                    trailingIcon = {
                        if (stops.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = stopDropdownOpen)
                        }
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CollegePrimary,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )
                if (stops.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = stopDropdownOpen,
                        onDismissRequest = { onStopDropdownOpenChange(false) }
                    ) {
                        stops.forEach { stop ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(CollegePrimary.copy(alpha = 0.1f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${stop.order}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CollegePrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = stop.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                },
                                onClick = { onSelectStop(stop) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatingScheduleCard(
    operatingSchedule: com.college.bustracker.data.model.RouteSchedule
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.operating_windows),
                icon = Icons.Default.Schedule
            )

            Spacer(modifier = Modifier.height(12.dp))

            val sched = operatingSchedule
            if (!sched.morning.enabled && !sched.evening.enabled) {
                Text(
                    text = stringResource(R.string.operating_schedule_disabled),
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            } else {
                if (sched.morning.enabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.morning_route),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "${sched.morning.startTime} – ${sched.morning.endTime}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (sched.evening.enabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.evening_return),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "${sched.evening.startTime} – ${sched.evening.endTime}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteStopsTimelineCard(
    stops: List<BusStop>,
    selectedStop: BusStop?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.route_stops_count, stops.size),
                icon = Icons.Default.LocationOn
            )

            Spacer(modifier = Modifier.height(14.dp))

            stops.forEachIndexed { index, stop ->
                val isSelectedBoardingStop = selectedStop?.stopId == stop.stopId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelectedBoardingStop) CollegePrimary.copy(alpha = 0.06f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (isSelectedBoardingStop) CollegePrimary else CollegePrimary.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stop.order}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelectedBoardingStop) Color.White else CollegePrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stop.name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelectedBoardingStop) FontWeight.Bold else FontWeight.Medium,
                            color = Color(0xFF0F172A)
                        )
                        if (isSelectedBoardingStop) {
                            Text(
                                text = stringResource(R.string.selected_boarding_stop_badge),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CollegePrimary
                            )
                        }
                    }
                }
                if (index < stops.size - 1) {
                    Box(
                        modifier = Modifier
                            .padding(start = 17.dp)
                            .width(2.dp)
                            .height(12.dp)
                            .background(Color(0xFFCBD5E1))
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomaticStudentTransitBackupCard(
    viewModel: StudentViewModel,
    selectedBus: com.college.bustracker.data.model.Bus?
) {
    val context = LocalContext.current
    val isAutoEnabled by viewModel.isAutoSharingEnabled.collectAsState()
    val autoRideState by viewModel.automaticRideState.collectAsState()
    val autoAssignedBusId by viewModel.autoAssignedBusId.collectAsState()
    val isLocationServicesEnabled by viewModel.isLocationServicesEnabled.collectAsState()

    var showConsentDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var locationWarningDismissed by remember { mutableStateOf(false) }

    // Location Services OFF Banner
    if (!isLocationServicesEnabled && isAutoEnabled && !locationWarningDismissed) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(StatusDangerRed.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = StatusDangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.location_turned_off),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = stringResource(R.string.turn_on_location_desc),
                                fontSize = 12.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    }
                    IconButton(
                        onClick = { locationWarningDismissed = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF991B1B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        com.college.bustracker.util.SettingsOpener.openLocationSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.open_settings), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAutoEnabled) Color(0xFFF0FDF4) else Color.White
        ),
        border = if (isAutoEnabled) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isAutoEnabled) StatusActiveGreen.copy(alpha = 0.15f) else CollegePrimary.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolunteerActivism,
                            contentDescription = null,
                            tint = if (isAutoEnabled) StatusActiveGreen else CollegePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.auto_backup_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (isAutoEnabled) stringResource(R.string.configured_for_bus, autoAssignedBusId ?: "Bus") else stringResource(R.string.one_time_setup),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (isAutoEnabled) {
                    val (badgeText, badgeBg, badgeTextColor) = when (autoRideState) {
                        AutomaticRideState.CONTRIBUTING -> Triple("Active Contributor", StatusGreenContainer, StatusActiveGreen)
                        AutomaticRideState.POSSIBLY_RIDING -> Triple("Verifying Transit", Color(0xFFFEF3C7), Color(0xFFB45309))
                        AutomaticRideState.WAITING_FOR_BUS -> Triple("Ready on Route", Color(0xFFDCFCE7), Color(0xFF15803D))
                        AutomaticRideState.WAITING_FOR_WINDOW -> Triple("Standby Window", Color(0xFFF1F5F9), Color(0xFF475569))
                        AutomaticRideState.ENDING_RIDE -> Triple("Ride Concluded", Color(0xFFFFEDD5), Color(0xFFC2410C))
                        else -> Triple("Enabled", Color(0xFFDCFCE7), Color(0xFF15803D))
                    }
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isAutoEnabled) {
                Text(
                    text = stringResource(R.string.auto_backup_help),
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showConsentDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text(stringResource(R.string.enable_auto_sharing), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                val stateDescription = when (autoRideState) {
                    AutomaticRideState.CONTRIBUTING -> "Your phone is currently riding and contributing backup telemetry for ${autoAssignedBusId ?: "your bus"}."
                    AutomaticRideState.POSSIBLY_RIDING -> "Analyzing route alignment and velocity matching to confirm boarding."
                    AutomaticRideState.WAITING_FOR_BUS -> "Monitoring scheduled route windows. Will start automatically when you board ${autoAssignedBusId ?: "your bus"}."
                    AutomaticRideState.WAITING_FOR_WINDOW -> "Currently outside route schedule hours. Passive monitoring is fully sleeping (0% battery drain)."
                    AutomaticRideState.ENDING_RIDE -> "Disembarkation detected. Telemetry upload concluded."
                    else -> "Automatic ride monitoring active."
                }
                Text(
                    text = stateDescription,
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.transit_settings), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { viewModel.disableAutoSharing() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.stop_sharing), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }

    // One-Time Consent Dialog
    if (showConsentDialog) {
        val busNumber = selectedBus?.busNumber ?: "Selected Bus"
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.enable_auto_backup_dialog_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Contribute backup GPS for $busNumber seamlessly without manual toggling.",
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🕒 ", fontSize = 14.sp)
                        Text(
                            text = "Scheduled Hours Only: Operates exclusively during morning and evening route windows. 0% battery drain otherwise.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🔒 ", fontSize = 14.sp)
                        Text(
                            text = "Privacy Protected: Anonymous telemetry only. Your personal location is never visible to students or logged.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🤖 ", fontSize = 14.sp)
                        Text(
                            text = "Automatic Detection: Begins when motion matches the bus route, and stops automatically when you disembark.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedBus?.busId?.let { id ->
                            viewModel.enableAutoSharing(id)
                        }
                        showConsentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.agree_and_enable), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConsentDialog = false }
                ) {
                    Text(stringResource(R.string.not_now), fontSize = 13.sp, color = Color(0xFF64748B))
                }
            }
        )
    }

    // Transit Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(stringResource(R.string.transit_backup_settings_title), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.currently_assigned_to, autoAssignedBusId ?: "None"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = stringResource(R.string.switch_anytime_desc, selectedBus?.busNumber ?: "None"),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    if (selectedBus != null && selectedBus.busId != autoAssignedBusId) {
                        OutlinedButton(
                            onClick = {
                                viewModel.changeAutoSharingBus(selectedBus.busId)
                                showSettingsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.switch_to_bus, selectedBus.busNumber))
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.disableAutoSharing()
                            showSettingsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.disable_auto_sharing), color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun ProminentEtaCard(
    formattedBusTitle: String,
    isSwapActive: Boolean,
    originalBusId: String?,
    trackingState: TrackingDisplayState,
    lastUpdateText: String,
    stops: List<BusStop>,
    route: Route?,
    routeEtaResult: RouteEtaResult?,
    nextStopEta: StopEta?,
    liveLoc: LiveLocation?,
    selectedStop: BusStop?,
    upcomingStopsEta: List<StopEta>,
    onNavigateToMap: () -> Unit,
    onReportIssue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Bus Number & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedBusTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A),
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSwapActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.replacement_tag),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if (isSwapActive) {
                    Text(
                        text = stringResource(R.string.operating_for_bus, originalBusId ?: ""),
                        fontSize = 11.sp,
                        color = Color(0xFF78350F),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.wrapContentWidth()
            ) {
                StatusBadge(state = trackingState)
                if (trackingState == TrackingDisplayState.ACTIVE && lastUpdateText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lastUpdateText,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Next Stop / Destination Row
        val nextStopLabel = when {
            routeEtaResult?.status == EtaStatus.ARRIVED_FINAL_STOP -> stringResource(R.string.final_destination)
            trackingState == TrackingDisplayState.INACTIVE -> stringResource(R.string.first_scheduled_stop)
            else -> stringResource(R.string.next_stop)
        }
        val nextStopDisplayName = when {
            routeEtaResult?.status == EtaStatus.ARRIVED_FINAL_STOP ->
                stops.lastOrNull()?.name ?: stringResource(R.string.campus_stop)
            nextStopEta != null ->
                nextStopEta.stop.name
            stops.isNotEmpty() ->
                stops.first().name
            else ->
                stringResource(R.string.no_route_stops_assigned)
        }

        Column {
            Text(
                text = nextStopLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B),
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = nextStopDisplayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Prominent ETA & Distance Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        routeEtaResult?.status == EtaStatus.ARRIVED_FINAL_STOP -> Color(0xFFF0FDF4)
                        trackingState == TrackingDisplayState.ACTIVE -> Color(0xFFF8FAFC)
                        trackingState == TrackingDisplayState.STALE -> Color(0xFFFFFBEB)
                        else -> Color(0xFFF8FAFC)
                    },
                    RoundedCornerShape(14.dp)
                )
                .border(
                    width = 1.dp,
                    color = when {
                        routeEtaResult?.status == EtaStatus.ARRIVED_FINAL_STOP -> Color(0xFFBBF7D0)
                        trackingState == TrackingDisplayState.ACTIVE -> Color(0xFFE2E8F0)
                        trackingState == TrackingDisplayState.STALE -> Color(0xFFFDE68A)
                        else -> Color(0xFFE2E8F0)
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                when {
                    // 1. ARRIVED AT FINAL DESTINATION
                    routeEtaResult?.status == EtaStatus.ARRIVED_FINAL_STOP -> {
                        Text(
                            text = stringResource(R.string.status_label),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.arrived),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.at_final_destination),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                    }

                    // 2. NO ROUTE / NO STOPS
                    route == null || stops.isEmpty() || routeEtaResult?.status == EtaStatus.NO_ROUTE -> {
                        Text(
                            text = stringResource(R.string.estimated_arrival_caps),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.eta_unavailable),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.no_route_stops_configured),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // 3. ACTIVE BUS WITH LIVE ETA
                    trackingState == TrackingDisplayState.ACTIVE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = stringResource(R.string.eta_label),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = nextStopEta?.etaFormatted ?: "~5 min",
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF15803D),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            // Distance Pill
                            val distFormatted = nextStopEta?.distanceFormatted ?: ""
                            if (distFormatted.isNotBlank() && distFormatted != "—") {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFDCFCE7), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.away_suffix, distFormatted),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Motion / speed row
                        val speedKmH = ((liveLoc?.speed ?: 0f) * 3.6).toInt()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = StatusActiveGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (speedKmH > 2) stringResource(R.string.in_motion, speedKmH) else stringResource(R.string.stationary_at_stop),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                        }

                        // Boarding stop ETA (if user selected a specific boarding stop different from next stop)
                        if (selectedStop != null && nextStopEta != null && selectedStop.stopId != nextStopEta.stop.stopId) {
                            val boardingEta = remember(upcomingStopsEta, selectedStop) {
                                upcomingStopsEta.find { it.stop.stopId == selectedStop.stopId }
                            }
                            if (boardingEta != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = CollegePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.your_stop_eta_format,
                                            selectedStop.name,
                                            boardingEta.etaFormatted,
                                            boardingEta.distanceFormatted
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }

                    // 4. WAITING FOR GPS
                    trackingState == TrackingDisplayState.WAITING_FOR_GPS -> {
                        Text(
                            text = stringResource(R.string.estimated_arrival_caps),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.eta_unavailable),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.waiting_for_location),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // 5. STALE LOCATION
                    trackingState == TrackingDisplayState.STALE -> {
                        Text(
                            text = stringResource(R.string.estimated_arrival_caps),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.eta_unavailable),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.location_stale_msg, lastUpdateText),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB45309)
                        )
                    }

                    // 6. INACTIVE / BUS NOT STARTED
                    else -> {
                        Text(
                            text = stringResource(R.string.estimated_arrival_caps),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.bus_not_started),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.refer_to_scheduled_times),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Map Navigation Button: VIEW BUS MAP ->
        Button(
            onClick = onNavigateToMap,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (trackingState == TrackingDisplayState.ACTIVE) CollegePrimary else Color(0xFF334155)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.view_bus_map_action),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Report Issue with this Bus Button
        OutlinedButton(
            onClick = onReportIssue,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFFFFBEB),
                contentColor = Color(0xFFB45309)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ReportProblem,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFD97706)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.report_bus_issue),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB45309)
            )
        }
    }
}
}

@Composable
private fun DriverInfoCard(
    driverInfo: DriverDisplayInfo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.assigned_driver),
                icon = Icons.Default.Badge,
                action = {
                    if (driverInfo != null && driverInfo.busTitle.isNotBlank() && driverInfo.busTitle != "—") {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (driverInfo.isReplacement) Color(0xFFFEF3C7) else Color(0xFFEFF6FF)
                            )
                        ) {
                            Text(
                                text = if (driverInfo.isReplacement) stringResource(R.string.replacement_driver_tag, driverInfo.busTitle) else stringResource(R.string.your_bus_tag, driverInfo.busTitle),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (driverInfo.isReplacement) Color(0xFF92400E) else CollegePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Driver Name & Calling Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = stringResource(R.string.driver_caps),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = driverInfo?.driverName?.ifBlank { stringResource(R.string.driver_unassigned) } ?: stringResource(R.string.driver_unassigned),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (driverInfo?.isAssigned == true) Color(0xFF1E293B) else Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Phone / Call Driver Button
                val phone = driverInfo?.phoneNumber
                if (!phone.isNullOrBlank()) {
                    Button(
                        onClick = {
                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${phone.trim()}")
                            }
                            context.startActivity(dialIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call Driver",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.call_driver),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else if (driverInfo?.isAssigned == true) {
                    Text(
                        text = stringResource(R.string.phone_not_available),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}
