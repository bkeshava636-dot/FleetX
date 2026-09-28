package com.college.bustracker.ui.admin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.DurationOption
import com.college.bustracker.util.ReplacementTimeWindow
import com.college.bustracker.util.TemporaryDurationHelper
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AdminReplacementsTabContent(
    assignments: List<TemporaryAssignment>,
    buses: List<Bus>,
    drivers: List<UserProfile>,
    routes: List<Route>,
    onNewReplacementClick: () -> Unit,
    onCancelReplacement: (assignmentId: String, (Boolean, String?) -> Unit) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var assignmentToCancel by remember { mutableStateOf<TemporaryAssignment?>(null) }
    var isCancelling by remember { mutableStateOf(false) }
    var cancelError by remember { mutableStateOf<String?>(null) }

    val now = System.currentTimeMillis()
    val filteredAssignments = remember(assignments, selectedFilter, now) {
        when (selectedFilter) {
            "ACTIVE" -> assignments.filter { it.isActive(now) }
            "UPCOMING" -> assignments.filter { it.isUpcoming(now) }
            "EXPIRED" -> assignments.filter { it.isExpired(now) }
            "CANCELLED" -> assignments.filter { it.isCancelled() }
            else -> assignments
        }.sortedByDescending { it.createdAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Temporary Replacements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${assignments.count { it.isActive(now) }} currently active",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Button(
                onClick = onNewReplacementClick,
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Replacement", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "ACTIVE", "UPCOMING", "EXPIRED", "CANCELLED").forEach { filter ->
                val count = when (filter) {
                    "ACTIVE" -> assignments.count { it.isActive(now) }
                    "UPCOMING" -> assignments.count { it.isUpcoming(now) }
                    "EXPIRED" -> assignments.count { it.isExpired(now) }
                    "CANCELLED" -> assignments.count { it.isCancelled() }
                    else -> assignments.size
                }
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text("$filter ($count)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CollegePrimary.copy(alpha = 0.15f),
                        selectedLabelColor = CollegePrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredAssignments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedFilter == "ALL") "No temporary driver replacements on record." else "No $selectedFilter replacements found.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredAssignments, key = { it.assignmentId }) { assignment ->
                    AdminReplacementCard(
                        assignment = assignment,
                        buses = buses,
                        now = now,
                        onCancelClick = { assignmentToCancel = assignment }
                    )
                }
            }
        }
    }

    // Cancellation Confirmation Dialog
    if (assignmentToCancel != null) {
        val targetAssignment = assignmentToCancel!!
        val bus = buses.find { it.busId.equals(targetAssignment.busId, ignoreCase = true) }
        val busLabel = bus?.busNumber ?: targetAssignment.busId
        val permDriver = targetAssignment.permanentDriverName.ifBlank { "Permanent Driver" }
        val replDriver = targetAssignment.replacementDriverName.ifBlank { "Replacement Driver" }
        val timeWindowStr = TemporaryDurationHelper.formatCancelWindow(targetAssignment.startAt, targetAssignment.endAt)

        AlertDialog(
            onDismissRequest = {
                if (!isCancelling) {
                    assignmentToCancel = null
                    cancelError = null
                }
            },
            title = {
                Text("Cancel Temporary Replacement?", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "$busLabel (${targetAssignment.busId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$permDriver → $replDriver",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = CollegePrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = timeWindowStr,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "$permDriver will become the effective driver again.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                    if (cancelError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(cancelError!!, color = StatusDangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val assignId = targetAssignment.assignmentId
                        isCancelling = true
                        cancelError = null
                        onCancelReplacement(assignId) { success, err ->
                            isCancelling = false
                            if (success) {
                                assignmentToCancel = null
                            } else {
                                cancelError = err ?: "Failed to cancel replacement"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isCancelling
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("CANCEL REPLACEMENT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        assignmentToCancel = null
                        cancelError = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isCancelling
                ) {
                    Text("KEEP REPLACEMENT", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun AdminReplacementCard(
    assignment: TemporaryAssignment,
    buses: List<Bus>,
    now: Long,
    onCancelClick: () -> Unit
) {
    val bus = buses.find { it.busId.equals(assignment.busId, ignoreCase = true) }
    val status = assignment.getStatus(now)

    val (statusBg, statusColor) = when (status) {
        "ACTIVE" -> Color(0xFFECFDF5) to StatusActiveGreen
        "UPCOMING" -> Color(0xFFEFF6FF) to Color(0xFF2563EB)
        "CANCELLED" -> Color(0xFFFEF2F2) to StatusDangerRed
        else -> Color(0xFFF1F5F9) to Color(0xFF64748B)
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.getDefault()) }
    val zone = remember { ZoneId.systemDefault() }
    val startStr = remember(assignment.startAt) {
        java.time.Instant.ofEpochMilli(assignment.startAt).atZone(zone).format(dateFormatter)
    }
    val endStr = remember(assignment.endAt) {
        java.time.Instant.ofEpochMilli(assignment.endAt).atZone(zone).format(dateFormatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Bus and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = null,
                        tint = CollegePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bus?.busNumber ?: assignment.busId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${assignment.busId})",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = status,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Driver Transfer Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Permanent", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Text(
                        text = assignment.permanentDriverName ?: "Permanent Driver",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!assignment.permanentDriverPhone.isNullOrBlank()) {
                        Text(assignment.permanentDriverPhone!!, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(18.dp)
                )

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Replacement", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Text(
                        text = assignment.replacementDriverName ?: "Replacement Driver",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CollegePrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!assignment.replacementDriverPhone.isNullOrBlank()) {
                        Text(assignment.replacementDriverPhone!!, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timing Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$startStr → $endStr",
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer info + Cancel Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val footerText = if (assignment.isCancelled()) {
                    "Created by ${assignment.createdByRole} • Cancelled by ${assignment.cancelledByRole ?: "ADMIN"}"
                } else {
                    "Created by ${assignment.createdByRole}"
                }
                Text(
                    text = footerText,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                if (status == "ACTIVE" || status == "UPCOMING") {
                    OutlinedButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Cancel Replacement", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminCreateReplacementDialog(
    buses: List<Bus>,
    drivers: List<UserProfile>,
    routes: List<Route>,
    onDismiss: () -> Unit,
    onCreateReplacement: (busId: String, replacementUid: String, startAt: Long, endAt: Long, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var selectedBus by remember { mutableStateOf<Bus?>(null) }
    var selectedReplacementDriver by remember { mutableStateOf<UserProfile?>(null) }
    var busDropdownOpen by remember { mutableStateOf(false) }
    var driverDropdownOpen by remember { mutableStateOf(false) }

    var selectedDuration by remember { mutableStateOf(DurationOption.ONE_DAY) }

    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var customEndDate by remember { mutableStateOf(LocalDate.now()) }
    var customEndTime by remember { mutableStateOf(LocalTime.of(18, 0)) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val activeBusesWithDrivers = remember(buses) {
        buses.filter { it.active && !it.driverId.isNullOrBlank() }
    }

    val selectedRoute = remember(selectedBus, routes) {
        routes.find { it.routeId.equals(selectedBus?.routeId, ignoreCase = true) }
    }

    val permanentDriver = remember(selectedBus, drivers) {
        drivers.find { it.uid == selectedBus?.driverId }
    }

    val eligibleReplacements = remember(selectedBus, drivers) {
        drivers.filter { it.active && it.uid != selectedBus?.driverId }
    }

    val timeWindow = remember(startDate, startTime, selectedDuration, selectedRoute, customEndDate, customEndTime) {
        TemporaryDurationHelper.computeTimeWindow(
            startDate = startDate,
            startTime = startTime,
            option = selectedDuration,
            schedule = selectedRoute?.schedules,
            customEndDate = if (selectedDuration == DurationOption.CUSTOM) customEndDate else null,
            customEndTime = if (selectedDuration == DurationOption.CUSTOM) customEndTime else null
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Create Temporary Replacement", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Select Bus
                Text("Select Bus", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (selectedBus != null) "${selectedBus!!.busNumber} (${selectedBus!!.busId})" else "Choose a bus",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = CollegePrimary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { busDropdownOpen = true }
                    )
                    DropdownMenu(
                        expanded = busDropdownOpen,
                        onDismissRequest = { busDropdownOpen = false }
                    ) {
                        activeBusesWithDrivers.forEach { bus ->
                            val driverName = drivers.find { it.uid == bus.driverId }?.name ?: "Driver assigned"
                            DropdownMenuItem(
                                text = { Text("${bus.busNumber} (${bus.busId}) — $driverName") },
                                onClick = {
                                    selectedBus = bus
                                    selectedReplacementDriver = null
                                    busDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                if (permanentDriver != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Permanent Driver: ${permanentDriver.name} (${permanentDriver.phoneNumber ?: "No phone"})",
                        fontSize = 11.sp,
                        color = Color(0xFF0D9488),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Select Replacement Driver
                Text("Select Replacement Driver", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedReplacementDriver?.name ?: "Choose replacement driver",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedBus != null,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = CollegePrimary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                    if (selectedBus != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { driverDropdownOpen = true }
                        )
                    }
                    DropdownMenu(
                        expanded = driverDropdownOpen,
                        onDismissRequest = { driverDropdownOpen = false }
                    ) {
                        eligibleReplacements.forEach { driver ->
                            DropdownMenuItem(
                                text = { Text("${driver.name} (${driver.phoneNumber ?: "No phone"})") },
                                onClick = {
                                    selectedReplacementDriver = driver
                                    driverDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Duration Selection Chips
                Text("Duration", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DurationOption.values().forEach { option ->
                        FilterChip(
                            selected = selectedDuration == option,
                            onClick = { selectedDuration = option },
                            label = { Text(option.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CollegePrimary.copy(alpha = 0.15f),
                                selectedLabelColor = CollegePrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Start Date & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start Date", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d) },
                                    startDate.year,
                                    startDate.monthValue - 1,
                                    startDate.dayOfMonth
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(TemporaryDurationHelper.formatDate(startDate), fontSize = 12.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start Time", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> startTime = LocalTime.of(h, m) },
                                    startTime.hour,
                                    startTime.minute,
                                    false
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(TemporaryDurationHelper.formatTime(startTime), fontSize = 12.sp)
                        }
                    }
                }

                if (selectedDuration == DurationOption.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("End Date", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> customEndDate = LocalDate.of(y, m + 1, d) },
                                        customEndDate.year,
                                        customEndDate.monthValue - 1,
                                        customEndDate.dayOfMonth
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(TemporaryDurationHelper.formatDate(customEndDate), fontSize = 12.sp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("End Time", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> customEndTime = LocalTime.of(h, m) },
                                        customEndTime.hour,
                                        customEndTime.minute,
                                        false
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(TemporaryDurationHelper.formatTime(customEndTime), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Schedule Preview Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto Calculated Window", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TemporaryDurationHelper.formatDateTimeWindow(timeWindow),
                            fontSize = 12.sp,
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.Medium
                        )
                        val endT = TemporaryDurationHelper.resolveOperatingEndTime(selectedRoute?.schedules)
                        Text(
                            text = "Route schedule operating day closing: ${TemporaryDurationHelper.formatTime(endT)}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = StatusDangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedBus == null) {
                                errorMessage = "Please select a bus"
                                return@Button
                            }
                            if (selectedReplacementDriver == null) {
                                errorMessage = "Please select a replacement driver"
                                return@Button
                            }
                            if (timeWindow.endMillis <= timeWindow.startMillis) {
                                errorMessage = "End time must be after start time"
                                return@Button
                            }
                            errorMessage = null
                            showConfirmDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Review & Confirm")
                    }
                }
            }
        }
    }

    // Confirmation Summary Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showConfirmDialog = false },
            title = { Text("Confirm Replacement", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Please confirm the following driver replacement details:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Bus: ${selectedBus?.busNumber} (${selectedBus?.busId})", fontWeight = FontWeight.SemiBold)
                    Text("• Permanent Driver: ${permanentDriver?.name}")
                    Text("• Temporary Driver: ${selectedReplacementDriver?.name}", color = CollegePrimary, fontWeight = FontWeight.Bold)
                    Text("• Start: ${TemporaryDurationHelper.formatDate(timeWindow.startDate)}, ${TemporaryDurationHelper.formatTime(timeWindow.startTime)}")
                    Text("• End: ${TemporaryDurationHelper.formatDate(timeWindow.endDate)}, ${TemporaryDurationHelper.formatTime(timeWindow.endTime)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "During this period, only ${selectedReplacementDriver?.name} will be authorized to send GPS updates for this bus.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val busId = selectedBus?.busId ?: return@Button
                        val repUid = selectedReplacementDriver?.uid ?: return@Button
                        isSubmitting = true
                        onCreateReplacement(busId, repUid, timeWindow.startMillis, timeWindow.endMillis) { success, err ->
                            isSubmitting = false
                            if (success) {
                                showConfirmDialog = false
                                onDismiss()
                            } else {
                                errorMessage = err ?: "Failed to create replacement"
                                showConfirmDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Activate Replacement")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }, enabled = !isSubmitting) {
                    Text("Back")
                }
            }
        )
    }
}

/**
 * Top-level reusable Cancel Replacement Dialog for Admin.
 */
@Composable
fun AdminCancelReplacementDialog(
    assignment: TemporaryAssignment,
    onDismiss: () -> Unit,
    onConfirmCancel: () -> Unit
) {
    var isCancelling by remember { mutableStateOf(false) }
    val permDriver = assignment.permanentDriverName.ifBlank { "Permanent Driver" }
    val replDriver = assignment.replacementDriverName.ifBlank { "Replacement Driver" }
    val timeWindowStr = TemporaryDurationHelper.formatCancelWindow(assignment.startAt, assignment.endAt)

    AlertDialog(
        onDismissRequest = { if (!isCancelling) onDismiss() },
        title = {
            Text("Cancel Temporary Replacement?", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Bus ${assignment.busId}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$permDriver → $replDriver",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = CollegePrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = timeWindowStr,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "$permDriver will become the effective driver again.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isCancelling = true
                    onConfirmCancel()
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                shape = RoundedCornerShape(8.dp),
                enabled = !isCancelling
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("CANCEL REPLACEMENT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                enabled = !isCancelling
            ) {
                Text("KEEP REPLACEMENT", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    )
}

