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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolunteerActivism
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    onNavigateToMap: () -> Unit,
    onNavigateToStaffLogin: () -> Unit,
    onNavigateToBusPass: () -> Unit = {}
) {
    val context = LocalContext.current
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

    var busDropdownOpen by remember { mutableStateOf(false) }
    var stopDropdownOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // App Header
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
                        text = "Campus Transit Portal",
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
                    onClick = onNavigateToBusPass,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CollegePrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = CollegePrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pass",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
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
                        text = "Staff",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (isLoadingBuses) {
            LoadingCard(message = "Loading campus fleet routes…")
        } else if (buses.isEmpty()) {
            EmptyStateCard(
                title = "No Active Buses Available",
                description = "There are currently no active buses operating in the fleet. Please check back later or contact transport administration.",
                icon = Icons.Default.DirectionsBus
            )
        } else {
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

            // Location-Specific Bus Swap Banner
            if (swapAlert != null) {
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
                                text = "Bus Replacement Notice",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${swapAlert?.replacementBusId} is operating in place of ${swapAlert?.originalBusId} today",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F)
                        )

                        Text(
                            text = "Affects stop: ${selectedStop?.name ?: "All route stops"}",
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
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Prominent Student Home Screen Bus ETA Card or Holiday Notice
            if (isHolidayToday) {
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
                                text = "No bus service today",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Holiday — ${todayHolidayName ?: "College Holiday"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }
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
                    onNavigateToMap = onNavigateToMap
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Driver Information Card
            DriverInfoCard(driverInfo = driverInfo)
            Spacer(modifier = Modifier.height(14.dp))

            // Bus & Stop Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SectionHeader(
                        title = "Select Bus & Route",
                        icon = Icons.Default.DirectionsBus
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = busDropdownOpen,
                        onExpandedChange = { busDropdownOpen = !busDropdownOpen }
                    ) {
                        OutlinedTextField(
                            value = selectedBus?.let { "${it.busNumber} • ${it.registrationNumber}" } ?: "Select a bus",
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
                            onDismissRequest = { busDropdownOpen = false }
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
                                                    text = "In active service",
                                                    fontSize = 11.sp,
                                                    color = StatusActiveGreen
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectBus(bus)
                                        busDropdownOpen = false
                                    }
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
                            text = if (route != null) "Route: ${route?.name}" else "No active route assigned to this bus",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (route != null) Color(0xFF1E293B) else Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Boarding Stop Selector
                    Text(
                        text = "My Boarding Stop (For arrival notices)",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    ExposedDropdownMenuBox(
                        expanded = stopDropdownOpen,
                        onExpandedChange = { if (stops.isNotEmpty()) stopDropdownOpen = !stopDropdownOpen }
                    ) {
                        OutlinedTextField(
                            value = when {
                                stops.isEmpty() -> "No active stops on this route"
                                selectedStop != null -> "${selectedStop?.order}. ${selectedStop?.name}"
                                else -> "Choose your boarding stop"
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
                                onDismissRequest = { stopDropdownOpen = false }
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
                                        onClick = {
                                            viewModel.selectStop(stop)
                                            stopDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Automatic Student Transit Backup Card
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
                                        text = "Location is Turned Off",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = "Turn on Location to enable automatic bus tracking.",
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
                                val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Open Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                    text = "Automatic Transit Backup",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = if (isAutoEnabled) "Configured for ${autoAssignedBusId ?: "Bus"}" else "One-time setup for your bus",
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
                            text = "Help keep tracking accurate when driver GPS dips. Once enabled, your phone seamlessly provides backup GPS when you ride your bus during route hours.",
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
                            Text("Enable Automatic Sharing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                Text("Transit Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { viewModel.disableAutoSharing() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Text("Stop Sharing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                            text = "Enable Automatic Bus Backup",
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
                            Text("Agree & Enable", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showConsentDialog = false }
                        ) {
                            Text("Not Now", fontSize = 13.sp, color = Color(0xFF64748B))
                        }
                    }
                )
            }

            // Transit Settings Dialog
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = {
                        Text("Transit Backup Settings", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Currently assigned to: ${autoAssignedBusId ?: "None"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "You can switch this anytime to your currently selected bus (${selectedBus?.busNumber ?: "None"}).",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            if (selectedBus != null && selectedBus?.busId != autoAssignedBusId) {
                                OutlinedButton(
                                    onClick = {
                                        selectedBus?.busId?.let { viewModel.changeAutoSharingBus(it) }
                                        showSettingsDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Switch to ${selectedBus?.busNumber}")
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
                                Text("Disable Automatic Sharing", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettingsDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Operating Schedule Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SectionHeader(
                        title = "Operating Windows",
                        icon = Icons.Default.Schedule
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val sched = operatingSchedule
                    if (!sched.morning.enabled && !sched.evening.enabled) {
                        Text(
                            text = "Operating schedule is currently disabled.",
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
                                    text = "Morning Route",
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
                                    text = "Evening Return",
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

            Spacer(modifier = Modifier.height(14.dp))

            // My Bus Pass Quick Access Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                onClick = onNavigateToBusPass
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(CollegePrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = CollegePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "My Bus Pass",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Offline digital copy of your physical pass",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToBusPass,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("View / Scan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Route Stops Timeline Card
            if (stops.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        SectionHeader(
                            title = "Route Stops (${stops.size})",
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
                                            text = "Your Selected Boarding Stop",
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
        }

        Spacer(modifier = Modifier.height(14.dp))

        // FleetX Updates & About Section
        com.college.bustracker.update.ui.UpdateCard()
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
                                    text = "REPLACEMENT",
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
                            text = "Operating for Bus ${originalBusId ?: ""}",
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
                routeEtaResult?.status == EtaStatus.ARRIVED_FINAL_STOP -> "Final Destination"
                trackingState == TrackingDisplayState.INACTIVE -> "First Scheduled Stop"
                else -> "Next Stop"
            }
            val nextStopDisplayName = when {
                routeEtaResult?.status == EtaStatus.ARRIVED_FINAL_STOP ->
                    stops.lastOrNull()?.name ?: "Campus"
                nextStopEta != null ->
                    nextStopEta.stop.name
                stops.isNotEmpty() ->
                    stops.first().name
                else ->
                    "No route stops assigned"
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
                                text = "STATUS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Arrived",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF15803D)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "At Final Destination",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569)
                            )
                        }

                        // 2. NO ROUTE / NO STOPS
                        route == null || stops.isEmpty() || routeEtaResult?.status == EtaStatus.NO_ROUTE -> {
                            Text(
                                text = "ESTIMATED ARRIVAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "ETA unavailable",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "No route stops configured",
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
                                        text = "ETA",
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
                                            text = "$distFormatted away",
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
                                    text = if (speedKmH > 2) "$speedKmH km/h • In Motion" else "Stationary / At Stop",
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
                                            text = "Your Stop (${selectedStop.name}): ETA ${boardingEta.etaFormatted} (${boardingEta.distanceFormatted} away)",
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
                                text = "ESTIMATED ARRIVAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "ETA unavailable",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Waiting for live location",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        // 5. STALE LOCATION
                        trackingState == TrackingDisplayState.STALE -> {
                            Text(
                                text = "ESTIMATED ARRIVAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "ETA unavailable",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Location is stale • Last updated $lastUpdateText",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB45309)
                            )
                        }

                        // 6. INACTIVE / BUS NOT STARTED
                        else -> {
                            Text(
                                text = "ESTIMATED ARRIVAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Bus has not started",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Refer to scheduled operating times below",
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
                    text = "VIEW BUS MAP →",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    title = "Assigned Driver",
                    icon = Icons.Default.Badge
                )
                if (driverInfo != null && driverInfo.busTitle.isNotBlank() && driverInfo.busTitle != "—") {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (driverInfo.isReplacement) Color(0xFFFEF3C7) else Color(0xFFEFF6FF)
                        )
                    ) {
                        Text(
                            text = if (driverInfo.isReplacement) "Replacement: ${driverInfo.busTitle}" else "Your Bus: ${driverInfo.busTitle}",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Driver Name & Calling Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "DRIVER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = driverInfo?.driverName?.ifBlank { "Driver not assigned" } ?: "Driver not assigned",
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
                            text = "Call Driver",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else if (driverInfo?.isAssigned == true) {
                    Text(
                        text = "Phone not available",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}
