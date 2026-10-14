package com.college.bustracker.ui.driver

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import com.college.bustracker.R
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.ui.common.SectionHeader
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
fun DriverReplacementCard(
    activeReplacement: TemporaryAssignment?,
    operatingAsTempBus: TemporaryAssignment?,
    assignedBus: Bus?,
    eligibleDrivers: List<UserProfile>,
    schedule: RouteSchedule?,
    isLoading: Boolean,
    onActivateReplacement: (replacementUid: String, startAt: Long, endAt: Long, (Boolean, String?) -> Unit) -> Unit,
    onCancelReplacement: (assignmentId: String, (Boolean, String?) -> Unit) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                title = stringResource(R.string.temporary_replacement_title),
                icon = Icons.Default.PersonOutline
            )
            Text(
                text = stringResource(R.string.temporary_replacement_desc),
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (actionMessage != null) {
                Text(
                    text = actionMessage!!,
                    fontSize = 12.sp,
                    color = StatusActiveGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (actionError != null) {
                Text(
                    text = actionError!!,
                    fontSize = 12.sp,
                    color = StatusDangerRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Case A: Current driver has assigned another driver as temporary operator
            if (activeReplacement != null) {
                val status = activeReplacement.getStatus()
                val isCurrentlyActive = status == "ACTIVE"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentlyActive) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isCurrentlyActive) Color(0xFF3B82F6) else Color(0xFFCBD5E1)
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCurrentlyActive) stringResource(R.string.active_replacement_badge) else stringResource(R.string.upcoming_replacement_badge),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isCurrentlyActive) Color(0xFF1D4ED8) else Color(0xFF475569)
                            )
                            Text(
                                text = stringResource(R.string.perm_bus_label, assignedBus?.busNumber ?: activeReplacement.busId),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.temporary_driver_label, activeReplacement.replacementDriverName),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        if (!activeReplacement.replacementDriverPhone.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.contact_label, activeReplacement.replacementDriverPhone ?: ""),
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val startStr = TemporaryDurationHelper.formatFullDateTime(activeReplacement.startAt)
                        val endStr = TemporaryDurationHelper.formatFullDateTime(activeReplacement.endAt)
                        Text(
                            text = "$startStr →\n$endStr",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showCancelConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.cancel_replacement_action), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Case B: Current driver is operating another bus as temporary replacement
            if (operatingAsTempBus != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StatusActiveGreen)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = StatusActiveGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.temp_operator_for, operatingAsTempBus.busId),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF065F46)
                            )
                            Text(
                                text = stringResource(R.string.active_until_format, TemporaryDurationHelper.formatFullDateTime(operatingAsTempBus.endAt)),
                                fontSize = 11.sp,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            }

            // Button to set temporary replacement
            if (activeReplacement == null) {
                OutlinedButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = assignedBus != null && !isLoading
                ) {
                    Icon(Icons.Default.PersonOutline, contentDescription = null, tint = CollegePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.temporary_replacement_button),
                        fontWeight = FontWeight.Bold,
                        color = CollegePrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CollegePrimary)
                }
            }
        }
    }

    if (showCancelConfirm && activeReplacement != null) {
        val targetAssignment = activeReplacement
        val busLabel = assignedBus?.busNumber ?: targetAssignment.busId
        val permDriver = targetAssignment.permanentDriverName.ifBlank { "You" }
        val replDriver = targetAssignment.replacementDriverName.ifBlank { "Replacement Driver" }
        val timeWindowStr = TemporaryDurationHelper.formatCancelWindow(targetAssignment.startAt, targetAssignment.endAt)

        val msgCancelled = stringResource(R.string.temp_replacement_cancelled)
        val msgFailed = stringResource(R.string.failed_to_cancel_replacement)
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = {
                Text(stringResource(R.string.cancel_temp_replacement_dialog_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                        text = stringResource(R.string.will_revert_driver_desc, permDriver),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        onCancelReplacement(targetAssignment.assignmentId) { success, err ->
                            if (success) {
                                actionMessage = msgCancelled
                                actionError = null
                            } else {
                                actionError = err ?: msgFailed
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel_replacement_confirm_action), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCancelConfirm = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.keep_replacement_action), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        )
    }

    if (showDialog && assignedBus != null) {
        val msgSuccess = stringResource(R.string.temp_replacement_activated_success)
        DriverReplacementDialog(
            bus = assignedBus,
            eligibleDrivers = eligibleDrivers,
            schedule = schedule,
            isLoading = isLoading,
            onDismiss = { showDialog = false },
            onConfirm = { replacementUid, startAt, endAt ->
                onActivateReplacement(replacementUid, startAt, endAt) { success, err ->
                    if (success) {
                        showDialog = false
                        actionMessage = msgSuccess
                        actionError = null
                    } else {
                        actionError = err
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverReplacementDialog(
    bus: Bus,
    eligibleDrivers: List<UserProfile>,
    schedule: RouteSchedule?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (replacementUid: String, startAt: Long, endAt: Long) -> Unit
) {
    val context = LocalContext.current

    var selectedDriver by remember { mutableStateOf<UserProfile?>(eligibleDrivers.firstOrNull()) }
    var isDriverDropdownExpanded by remember { mutableStateOf(false) }

    val defaultStartTime = remember { TemporaryDurationHelper.resolveOperatingStartTime(schedule) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(defaultStartTime) }

    var selectedDuration by remember { mutableStateOf(DurationOption.ONE_DAY) }

    var customEndDate by remember { mutableStateOf(LocalDate.now()) }
    var customEndTime by remember { mutableStateOf(TemporaryDurationHelper.resolveOperatingEndTime(schedule)) }

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val computedWindow = remember(startDate, startTime, selectedDuration, customEndDate, customEndTime) {
        TemporaryDurationHelper.computeTimeWindow(
            startDate = startDate,
            startTime = startTime,
            option = selectedDuration,
            schedule = schedule,
            customEndDate = customEndDate,
            customEndTime = customEndTime
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.temporary_replacement_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = stringResource(R.string.temp_replacement_subtitle),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_action))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bus field (locked)
                Text(text = stringResource(R.string.bus_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = CollegePrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.bus_permanently_assigned, bus.busNumber.ifBlank { bus.busId }),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Replacement Driver selector
                Text(text = stringResource(R.string.replacement_driver_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isDriverDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = CollegePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedDriver?.let { "${it.name} (${it.customId.ifBlank { it.uid }})" }
                                ?: stringResource(R.string.select_replacement_driver_placeholder),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (selectedDriver != null) Color(0xFF0F172A) else Color(0xFF94A3B8),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = isDriverDropdownExpanded,
                        onDismissRequest = { isDriverDropdownExpanded = false }
                    ) {
                        if (eligibleDrivers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_eligible_drivers_available)) },
                                onClick = { isDriverDropdownExpanded = false }
                            )
                        } else {
                            eligibleDrivers.forEach { driver ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("ID: ${driver.customId} • ${driver.phoneNumber ?: "No phone"}", fontSize = 11.sp, color = Color(0xFF64748B))
                                        }
                                    },
                                    onClick = {
                                        selectedDriver = driver
                                        isDriverDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Start Date + Time
                Text(text = stringResource(R.string.start_date_time_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val dpd = DatePickerDialog(
                                context,
                                { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d) },
                                startDate.year,
                                startDate.monthValue - 1,
                                startDate.dayOfMonth
                            )
                            dpd.datePicker.minDate = System.currentTimeMillis() - 1000L
                            dpd.show()
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(startDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min -> startTime = LocalTime.of(h, min) },
                                startTime.hour,
                                startTime.minute,
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(startTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Duration quick selector
                Text(text = stringResource(R.string.duration_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DurationOption.values().forEach { opt ->
                        val optLabel = when (opt) {
                            DurationOption.ONE_DAY -> stringResource(R.string.duration_one_day)
                            DurationOption.TWO_DAYS -> stringResource(R.string.duration_two_days)
                            DurationOption.THREE_DAYS -> stringResource(R.string.duration_three_days)
                            DurationOption.CUSTOM -> stringResource(R.string.duration_custom_range)
                        }
                        FilterChip(
                            selected = selectedDuration == opt,
                            onClick = { selectedDuration = opt },
                            label = { Text(optLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CollegePrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // If CUSTOM, show End Date + Time pickers
                if (selectedDuration == DurationOption.CUSTOM) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.custom_end_date_time_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val dpd = DatePickerDialog(
                                    context,
                                    { _, y, m, d -> customEndDate = LocalDate.of(y, m + 1, d) },
                                    customEndDate.year,
                                    customEndDate.monthValue - 1,
                                    customEndDate.dayOfMonth
                                )
                                dpd.datePicker.minDate = System.currentTimeMillis() - 1000L
                                dpd.show()
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(customEndDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, min -> customEndTime = LocalTime.of(h, min) },
                                    customEndTime.hour,
                                    customEndTime.minute,
                                    false
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(customEndTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Calculated Summary Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = stringResource(R.string.effective_window_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${TemporaryDurationHelper.formatDateTime(computedWindow.startDate, computedWindow.startTime)} →\n${TemporaryDurationHelper.formatDateTime(computedWindow.endDate, computedWindow.endTime)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            lineHeight = 18.sp
                        )
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError!!,
                        fontSize = 12.sp,
                        color = StatusDangerRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val driver = selectedDriver
                        if (driver == null) {
                            validationError = "Please select a replacement driver."
                            return@Button
                        }
                        if (computedWindow.startMillis >= computedWindow.endMillis) {
                            validationError = "Start time must be strictly before end time."
                            return@Button
                        }
                        if (computedWindow.endMillis <= System.currentTimeMillis()) {
                            validationError = "End time must be in the future."
                            return@Button
                        }
                        validationError = null
                        showConfirmationDialog = true
                    },
                    enabled = !isLoading && selectedDriver != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(stringResource(R.string.review_and_activate_action), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showConfirmationDialog && selectedDriver != null) {
        val summary = TemporaryDurationHelper.buildSummary(
            busNumber = bus.busNumber.ifBlank { bus.busId },
            replacementDriverName = selectedDriver!!.name,
            window = computedWindow
        )

        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(stringResource(R.string.confirm_replacement_activation_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(summary, fontSize = 13.sp, lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.replacement_window_explanation,
                            selectedDriver!!.name,
                            bus.busNumber.ifBlank { bus.busId }
                        ),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        onConfirm(
                            selectedDriver!!.uid,
                            computedWindow.startMillis,
                            computedWindow.endMillis
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text(stringResource(R.string.activate_replacement_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(stringResource(R.string.back_action))
                }
            }
        )
    }
}
