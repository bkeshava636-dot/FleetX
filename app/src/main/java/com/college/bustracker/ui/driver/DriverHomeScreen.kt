package com.college.bustracker.ui.driver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.ui.common.NetworkBadge
import com.college.bustracker.ui.common.SectionHeader
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.ScheduleHelper

@Composable
fun DriverHomeScreen(
    viewModel: DriverViewModel,
    onNavigateToSwap: () -> Unit,
    onLogout: () -> Unit
) {
    val bus by viewModel.assignedBus.collectAsState()
    val route by viewModel.assignedRoute.collectAsState()
    val isAutoTracking by viewModel.isAutoTrackingEnabled.collectAsState()
    val isTrackingLive by viewModel.isTrackingLive.collectAsState()
    val gpsStatus by viewModel.gpsStatus.collectAsState()
    val isInternet by viewModel.isInternetConnected.collectAsState()
    val lastUpdateText by viewModel.lastUpdateText.collectAsState()
    val activeSwap by viewModel.activeSwapForDriver.collectAsState()
    val holidayWarning by viewModel.holidayWarningMessage.collectAsState()
    val isHolidayToday by viewModel.isHolidayToday.collectAsState()
    val todayHolidayName by viewModel.todayHolidayName.collectAsState()

    val activeReplacement by viewModel.activeReplacementForDriver.collectAsState()
    val operatingAsTempBus by viewModel.operatingAsTemporaryBus.collectAsState()
    val eligibleDrivers by viewModel.eligibleReplacementDrivers.collectAsState()
    val isReplacementLoading by viewModel.isReplacementLoading.collectAsState()

    val capturedStopLocation by viewModel.capturedStopLocation.collectAsState()
    val isSubmittingStop by viewModel.isSubmittingStop.collectAsState()
    val stopSubmissionMsg by viewModel.stopSubmissionMessage.collectAsState()
    val stopSubmissionErr by viewModel.stopSubmissionError.collectAsState()

    val context = LocalContext.current
    var showStopConfirmationDialog by remember { mutableStateOf(false) }
    var showBackgroundLocationDialog by remember { mutableStateOf(false) }
    var isCapturingLocation by remember { mutableStateOf(false) }
    var locationCaptureError by remember { mutableStateOf<String?>(null) }
    var proposedStopName by remember { mutableStateOf("") }
    var stopNameError by remember { mutableStateOf<String?>(null) }

    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDialog = false },
            title = { Text("Set Location to 'Allow all the time'", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "To automatically start bus tracking on schedule while your screen is locked or FleetX is closed, Android requires Location access set to 'Allow all the time'.\n\n1. Tap 'Open Settings' below\n2. Select 'Permissions' -> 'Location'\n3. Choose 'Allow all the time'"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackgroundLocationDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (ignored: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    if (holidayWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHolidayWarning() },
            title = { Text("Tracking Unavailable", fontWeight = FontWeight.Bold) },
            text = { Text(holidayWarning!!) },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissHolidayWarning() },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    BackHandler(enabled = true) {
        if (showStopConfirmationDialog) {
            showStopConfirmationDialog = false
        } else {
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    val operatingSchedule by viewModel.operatingSchedule.collectAsState()

    val nextSessionText = remember(operatingSchedule) {
        ScheduleHelper.getNextSessionDescription(operatingSchedule)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Driver Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bus Driver Console",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Safe & timely college transit",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Logout",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Holiday Notice Banner
        if (isHolidayToday) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusWarningOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "College Holiday Today",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${todayHolidayName ?: "Holiday"} — Bus tracking is disabled today.",
                            fontSize = 12.sp,
                            color = Color(0xFF78350F)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Battery Optimization Warning Banner
        val context = LocalContext.current
        val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
        val isBatteryOptimized = remember(powerManager) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
        }

        if (isBatteryOptimized) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = StatusWarningOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Background Battery Optimization Active",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF92400E)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To guarantee GPS tracking never stops when your screen is locked, allow 'Unrestricted' battery access.",
                        fontSize = 12.sp,
                        color = Color(0xFF78350F)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ignored: Exception) {}
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Enable Unrestricted Background GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Assigned Bus & Route Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(CollegePrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = CollegePrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = bus?.busNumber ?: "No Bus Assigned",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Route: ${route?.name ?: if (bus != null) "Route ${bus?.routeId}" else "Unassigned"}",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Tracking HUD (when active)
        if (isTrackingLive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(StatusActiveGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE TRACKING IN PROGRESS",
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusActiveGreen,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bus?.busNumber ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF065F46),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = if (gpsStatus == "Locked") StatusActiveGreen else StatusWarningOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GPS: $gpsStatus",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        NetworkBadge(isOnline = isInternet)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Last GPS broadcast: $lastUpdateText",
                        fontSize = 12.sp,
                        color = Color(0xFF065F46)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { showStopConfirmationDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STOP TRACKING SESSION",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // AUTO TRACKING Card
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Scheduled Tracking",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (isAutoTracking) "Arming foreground service during shift" else "Auto tracking inactive",
                            fontSize = 12.sp,
                            color = if (isAutoTracking) StatusActiveGreen else Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val hasBgLocPermission = remember(context) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } else true
                    }

                    Switch(
                        checked = isAutoTracking,
                        onCheckedChange = { enable ->
                            if (enable && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val hasBg = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasBg) {
                                    showBackgroundLocationDialog = true
                                    return@Switch
                                }
                            }
                            viewModel.toggleAutoTracking(enable)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = StatusActiveGreen
                        )
                    )
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val hasBg = remember(context) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (!hasBg) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Requires 'Allow all the time' location permission to track automatically when app is closed.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showBackgroundLocationDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Fix", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = nextSessionText,
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // MANUAL TRACKING Card (when not currently live)
        if (!isTrackingLive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SectionHeader(
                        title = "Manual Dispatch",
                        icon = Icons.Default.PlayArrow
                    )
                    Text(
                        text = "Use for unscheduled trips, special exams, holidays, or emergency substitute shifts.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    val isReplacedNow = remember(activeReplacement) {
                        activeReplacement?.isActive(System.currentTimeMillis()) == true
                    }
                    val isActingAsTemp = remember(operatingAsTempBus) {
                        operatingAsTempBus?.isActive(System.currentTimeMillis()) == true
                    }

                    Button(
                        onClick = { viewModel.startManualTracking() },
                        enabled = !isReplacedNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReplacedNow) Color(0xFF94A3B8) else CollegePrimary,
                            disabledContainerColor = Color(0xFFE2E8F0)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isReplacedNow -> "TRACKING DISABLED (REPLACEMENT ACTIVE)"
                                isActingAsTemp -> "START TRACKING (${operatingAsTempBus?.busId})"
                                else -> "START TRACKING NOW"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // BUS SWAP Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                SectionHeader(
                    title = "Vehicle Substitution",
                    icon = Icons.Default.SwapHoriz
                )
                Text(
                    text = "Report temporary vehicle substitutions so waiting students receive notifications.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (activeSwap != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusWarningOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Swap: ${activeSwap?.originalBusId} → ${activeSwap?.replacementBusId}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = activeSwap?.message ?: "",
                                    fontSize = 11.sp,
                                    color = Color(0xFF78350F),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToSwap,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Cached, contentDescription = null, tint = CollegePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeSwap != null) "MANAGE ACTIVE BUS SWAP" else "REPORT BUS SWAP",
                        fontWeight = FontWeight.Bold,
                        color = CollegePrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CollegePrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // TEMPORARY DRIVER REPLACEMENT Card
        DriverReplacementCard(
            activeReplacement = activeReplacement,
            operatingAsTempBus = operatingAsTempBus,
            assignedBus = bus,
            eligibleDrivers = eligibleDrivers,
            schedule = route?.schedules,
            isLoading = isReplacementLoading,
            onActivateReplacement = { repUid, startAt, endAt, cb ->
                viewModel.activateTemporaryReplacement(repUid, startAt, endAt, cb)
            },
            onCancelReplacement = { assignId, cb ->
                viewModel.cancelTemporaryReplacement(assignId, cb)
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // PROPOSE NEW STOP / SAVE STOP Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                SectionHeader(
                    title = "Propose Route Stop",
                    icon = Icons.Default.AddLocationAlt
                )
                Text(
                    text = "Physically standing at a bus stop? Capture your current GPS location to propose a new stop to the Administrator.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Button(
                    onClick = {
                        isCapturingLocation = true
                        locationCaptureError = null
                        proposedStopName = ""
                        stopNameError = null
                        viewModel.captureCurrentStopLocation { success, errorMsg ->
                            isCapturingLocation = false
                            if (!success) {
                                locationCaptureError = errorMsg ?: "Failed to capture GPS location"
                            }
                        }
                    },
                    enabled = !isCapturingLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    if (isCapturingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ACQUIRING GPS FIX...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVE STOP AT CURRENT LOCATION",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }

    // Stop Confirmation Dialog
    if (showStopConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmationDialog = false },
            title = {
                Text(text = "Stop Tracking?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to end live tracking for ${bus?.busNumber}? Students will no longer see live updates.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmationDialog = false
                        viewModel.stopTracking()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("Stop Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save Stop Dialog (Captures current GPS and asks for stop name)
    val locToSave = capturedStopLocation
    if (locToSave != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingStop) viewModel.clearCapturedStopLocation()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Propose Bus Stop", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Assigned: ${bus?.busNumber ?: "Bus"} • ${route?.name ?: "Route"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "GPS: Lat ${"%.5f".format(locToSave.latitude)}, Lng ${"%.5f".format(locToSave.longitude)}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            if (locToSave.hasAccuracy()) {
                                Text(
                                    text = "Accuracy: ±${locToSave.accuracy.toInt()} meters",
                                    fontSize = 11.sp,
                                    color = Color(0xFF0D9488)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = proposedStopName,
                        onValueChange = {
                            proposedStopName = it
                            stopNameError = null
                        },
                        label = { Text("Stop Name") },
                        placeholder = { Text("e.g. Gandhi Circle, Gate 3") },
                        isError = stopNameError != null,
                        supportingText = stopNameError?.let { { Text(it, color = StatusDangerRed) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (proposedStopName.trim().isBlank()) {
                            stopNameError = "Please enter a stop name"
                            return@Button
                        }
                        viewModel.submitStopRequest(proposedStopName)
                    },
                    enabled = !isSubmittingStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    if (isSubmittingStop) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submitting...")
                    } else {
                        Text("Submit to Admin")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearCapturedStopLocation() },
                    enabled = !isSubmittingStop
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Location Capture Error Dialog
    if (locationCaptureError != null) {
        AlertDialog(
            onDismissRequest = { locationCaptureError = null },
            title = { Text("GPS Capture Failed", fontWeight = FontWeight.Bold) },
            text = { Text(locationCaptureError!!) },
            confirmButton = {
                Button(
                    onClick = { locationCaptureError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Submission Success Dialog
    if (stopSubmissionMsg != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearStopSubmissionFeedback() },
            title = { Text("Success", fontWeight = FontWeight.Bold, color = StatusActiveGreen) },
            text = { Text(stopSubmissionMsg!!) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearStopSubmissionFeedback() },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Submission Error Dialog
    if (stopSubmissionErr != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearStopSubmissionFeedback() },
            title = { Text("Submission Failed", fontWeight = FontWeight.Bold, color = StatusDangerRed) },
            text = { Text(stopSubmissionErr!!) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearStopSubmissionFeedback() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("OK")
                }
            }
        )
    }
}
