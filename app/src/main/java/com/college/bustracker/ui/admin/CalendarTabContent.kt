package com.college.bustracker.ui.admin

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.data.model.CalendarException
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.CollegeCalendarService
import com.college.bustracker.util.DayEvaluation
import java.time.LocalDate

import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import com.college.bustracker.data.model.RouteSchedule
import com.college.bustracker.util.OperatingScheduleService

@Composable
fun CalendarTabContent(
    operatingSchedule: RouteSchedule,
    exceptions: List<CalendarException>,
    todayEvaluation: DayEvaluation,
    onEditOperatingScheduleClick: (isMorning: Boolean) -> Unit,
    onAddExceptionClick: () -> Unit,
    onEditExceptionClick: (CalendarException) -> Unit,
    onDeleteExceptionClick: (CalendarException) -> Unit,
    onUploadMasterPdfClick: (() -> Unit)? = null,
    onViewMasterPdfClick: ((url: String, name: String?) -> Unit)? = null,
    onDeleteMasterPdfClick: (() -> Unit)? = null,
    onSaveMasterPdfUrlClick: ((url: String, name: String) -> Unit)? = null
) {
    val sortedExceptions = remember(exceptions) {
        exceptions.sortedBy { it.date }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Today Status Banner
        item {
            TodayStatusCard(todayEvaluation = todayEvaluation)
        }

        // Operating Schedule Section
        item {
            OperatingScheduleCard(
                schedule = operatingSchedule,
                onEditMorningClick = { onEditOperatingScheduleClick(true) },
                onEditEveningClick = { onEditOperatingScheduleClick(false) }
            )
        }

        // Master College Bus Timetable (All Routes PDF) Section
        item {
            AdminMasterSchedulePdfCard(
                schedule = operatingSchedule,
                onUploadClick = { onUploadMasterPdfClick?.invoke() },
                onViewClick = { url, name -> onViewMasterPdfClick?.invoke(url, name) },
                onDeleteClick = { onDeleteMasterPdfClick?.invoke() },
                onSaveUrlClick = { url, name -> onSaveMasterPdfUrlClick?.invoke(url, name) }
            )
        }

        // Add Exception Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calendar Overrides",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${exceptions.size} special holiday/working day exception(s)",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAddExceptionClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Override",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // List of Custom Exceptions
        if (sortedExceptions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Special Exceptions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Default recurring calendar rules are currently in effect. Add special dates like festivals or compensatory working days using the button above.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(sortedExceptions, key = { it.date }) { ex ->
                CalendarExceptionCard(
                    exception = ex,
                    onEditClick = { onEditExceptionClick(ex) },
                    onDeleteClick = { onDeleteExceptionClick(ex) }
                )
            }
        }

        // Informational: Default Recurring Rules Card
        item {
            DefaultRulesCard()
        }

        // Upcoming Default Holidays
        item {
            UpcomingHolidaysCard()
        }
    }
}

@Composable
private fun TodayStatusCard(todayEvaluation: DayEvaluation) {
    val isWorking = todayEvaluation.isWorkingDay
    val containerBg = if (isWorking) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
    val contentColor = if (isWorking) Color(0xFF166534) else Color(0xFF92400E)
    val iconTint = if (isWorking) StatusActiveGreen else StatusWarningOrange
    val icon = if (isWorking) Icons.Default.CheckCircle else Icons.Default.EventBusy

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY: ${todayEvaluation.date}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Text(
                    text = if (isWorking) "Working Day" else (todayEvaluation.summary),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                val trackingStateDesc = if (isWorking) {
                    "GPS tracking & schedules active"
                } else {
                    "GPS tracking automatically disabled"
                }
                val sourceDesc = if (todayEvaluation.isOverride) " (Admin Override)" else " (Default Rule)"
                Text(
                    text = "$trackingStateDesc$sourceDesc",
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun CalendarExceptionCard(
    exception: CalendarException,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isHoliday = exception.type == CalendarException.TYPE_FULL_DAY_HOLIDAY
    val badgeBg = if (isHoliday) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)
    val badgeText = if (isHoliday) "FULL-DAY HOLIDAY" else "WORKING DAY OVERRIDE"
    val badgeColor = if (isHoliday) StatusDangerRed else Color(0xFF166534)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = exception.date,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = exception.name.ifBlank { if (isHoliday) "Special Holiday" else "Compensatory Working Day" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Exception", tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Exception", tint = StatusDangerRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DefaultRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Default College Calendar Rules",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Every Sunday = Holiday (GPS tracking disabled)\n" +
                        "• 1st & 3rd Saturday = Holiday (GPS tracking disabled)\n" +
                        "• 2nd, 4th & 5th Saturday = Working Day by default\n" +
                        "• Monday–Friday = Working Day (Tracking governed by route schedule)\n" +
                        "• Admin Overrides above take highest priority",
                fontSize = 11.sp,
                color = Color(0xFF475569),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun UpcomingHolidaysCard() {
    val upcoming = remember {
        val today = LocalDate.now()
        val list = mutableListOf<DayEvaluation>()
        var cursor = today.plusDays(1)
        while (list.size < 4 && cursor.isBefore(today.plusDays(40))) {
            val eval = CollegeCalendarService.evaluateDay(cursor)
            if (eval.isHoliday) {
                list.add(eval)
            }
            cursor = cursor.plusDays(1)
        }
        list
    }

    if (upcoming.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Upcoming Scheduled Holidays",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                upcoming.forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${day.date} (${day.date.dayOfWeek.name.take(3)})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = day.holidayName ?: "Holiday",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditHolidayDialog(
    initialException: CalendarException?,
    onDismiss: () -> Unit,
    onConfirm: (date: String, type: String, name: String, onError: (String) -> Unit) -> Unit
) {
    var dateText by remember { mutableStateOf(initialException?.date ?: LocalDate.now().toString()) }
    var selectedType by remember {
        mutableStateOf(initialException?.type ?: CalendarException.TYPE_FULL_DAY_HOLIDAY)
    }
    var reasonText by remember { mutableStateOf(initialException?.name ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val isEdit = initialException != null

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = if (isEdit) "Edit Calendar Override" else "Add Calendar Override",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Date Input
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.trim() },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    enabled = !isSubmitting && !isEdit, // Keep date immutable when editing to maintain doc ID
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick helpers when adding new
                if (!isEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { dateText = LocalDate.now().toString() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Today", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { dateText = LocalDate.now().plusDays(1).toString() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Tomorrow", fontSize = 11.sp)
                        }
                    }
                }

                // Override Type
                Text("Override Type:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedType = CalendarException.TYPE_FULL_DAY_HOLIDAY },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == CalendarException.TYPE_FULL_DAY_HOLIDAY,
                        onClick = { selectedType = CalendarException.TYPE_FULL_DAY_HOLIDAY },
                        colors = RadioButtonDefaults.colors(selectedColor = CollegePrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Full-Day Holiday", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Disables GPS tracking & student backup", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedType = CalendarException.TYPE_WORKING_DAY },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == CalendarException.TYPE_WORKING_DAY,
                        onClick = { selectedType = CalendarException.TYPE_WORKING_DAY },
                        colors = RadioButtonDefaults.colors(selectedColor = CollegePrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Working-Day Override", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Allows tracking on a normally-off day", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                // Reason / Name Input
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason / Name (e.g. Independence Day)") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = CollegeCalendarService.parseDate(dateText)
                    if (parsed == null) {
                        errorMessage = "Invalid date format. Use YYYY-MM-DD (e.g. 2026-09-15)"
                        return@Button
                    }
                    if (reasonText.trim().isBlank()) {
                        errorMessage = "Please enter a name or reason for this override."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null
                    onConfirm(dateText, selectedType, reasonText) { err ->
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
                    Text(if (isEdit) "Update" else "Save Override")
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
fun DeleteHolidayDialog(
    exception: CalendarException,
    onDismiss: () -> Unit,
    onConfirm: (onError: (String) -> Unit) -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Delete Override", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Are you sure you want to remove the override for ${exception.date} (${exception.name})?\n\nThis date will immediately revert to its default calendar rule.",
                    fontSize = 14.sp
                )
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
                    isDeleting = true
                    errorMessage = null
                    onConfirm { err ->
                        isDeleting = false
                        errorMessage = err
                    }
                },
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OperatingScheduleCard(
    schedule: RouteSchedule,
    onEditMorningClick: () -> Unit,
    onEditEveningClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Operating Schedule",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Standard daily operating windows for tracking & route directions",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Morning Trip Row
            OperatingScheduleRow(
                title = "Morning Trip",
                icon = Icons.Default.WbSunny,
                timeDisplay = OperatingScheduleService.formatWindowDisplay(
                    schedule.morning.startTime,
                    schedule.morning.endTime
                ),
                isEnabled = schedule.morning.enabled,
                onEditClick = onEditMorningClick
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(10.dp))

            // Evening Trip Row
            OperatingScheduleRow(
                title = "Evening Trip",
                icon = Icons.Default.NightsStay,
                timeDisplay = OperatingScheduleService.formatWindowDisplay(
                    schedule.evening.startTime,
                    schedule.evening.endTime
                ),
                isEnabled = schedule.evening.enabled,
                onEditClick = onEditEveningClick
            )
        }
    }
}

@Composable
private fun OperatingScheduleRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    timeDisplay: String,
    isEnabled: Boolean,
    onEditClick: () -> Unit
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
                    .size(40.dp)
                    .background(Color(0xFFF1F5F9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    if (!isEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "DISABLED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusDangerRed
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeDisplay,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) Color(0xFF334155) else Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedButton(
            onClick = onEditClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Edit",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EditOperatingScheduleDialog(
    isMorning: Boolean,
    schedule: RouteSchedule,
    onDismiss: () -> Unit,
    onSave: (startTime: String, endTime: String, enabled: Boolean, onError: (String) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentWindow = if (isMorning) schedule.morning else schedule.evening
    val title = if (isMorning) "Edit Morning Trip Window" else "Edit Evening Trip Window"

    var startTime by remember { mutableStateOf(currentWindow.startTime) }
    var endTime by remember { mutableStateOf(currentWindow.endTime) }
    var isEnabled by remember { mutableStateOf(currentWindow.enabled) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun showTimePicker(initialTime: String, onSelected: (String) -> Unit) {
        val parts = initialTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: if (isMorning) 7 else 16
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                val formatted = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                onSelected(formatted)
                errorMessage = null
            },
            h,
            m,
            false
        ).show()
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Configure standard start and end times for this operating trip window.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                // Start Time Field
                OutlinedTextField(
                    value = "${OperatingScheduleService.formatTime12h(startTime)} ($startTime)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Start Time") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker(startTime) { startTime = it } },
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker(startTime) { startTime = it } }) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Pick Start Time", tint = CollegePrimary)
                        }
                    },
                    enabled = !isSubmitting
                )

                // End Time Field
                OutlinedTextField(
                    value = "${OperatingScheduleService.formatTime12h(endTime)} ($endTime)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("End Time") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker(endTime) { endTime = it } },
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker(endTime) { endTime = it } }) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Pick End Time", tint = CollegePrimary)
                        }
                    },
                    enabled = !isSubmitting
                )

                // Enabled Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Trip Enabled", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Include in daily schedule & auto-tracking", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        enabled = !isSubmitting
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusDangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (valid, error) = OperatingScheduleService.validateWindow(startTime, endTime)
                    if (!valid) {
                        errorMessage = error
                        return@Button
                    }
                    isSubmitting = true
                    errorMessage = null
                    onSave(startTime, endTime, isEnabled) { err ->
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
                    Text("Save Window")
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
private fun AdminMasterSchedulePdfCard(
    schedule: RouteSchedule,
    onUploadClick: () -> Unit,
    onViewClick: (url: String, name: String?) -> Unit,
    onDeleteClick: () -> Unit,
    onSaveUrlClick: (url: String, name: String) -> Unit
) {
    var showLinkUrlDialog by remember { mutableStateOf(false) }
    var showReplaceOptionsDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    if (showLinkUrlDialog) {
        AlertDialog(
            onDismissRequest = { showLinkUrlDialog = false },
            title = { Text("Link Timetable (Web / Google Drive)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Paste a public Google Drive link, college portal URL, or web PDF link:",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("PDF / Google Drive URL") },
                        placeholder = { Text("https://drive.google.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name (optional)") },
                        placeholder = { Text("College Bus Schedule (All Routes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = urlInput.trim()
                        if (trimmed.isNotBlank()) {
                            onSaveUrlClick(trimmed, nameInput.trim().ifBlank { "College Bus Timetable.pdf" })
                            showLinkUrlDialog = false
                            urlInput = ""
                            nameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("Save Link")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLinkUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReplaceOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceOptionsDialog = false },
            title = { Text("Replace Timetable PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "Choose how you want to update the college bus timetable:",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceOptionsDialog = false
                        onUploadClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text("Upload File")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showReplaceOptionsDialog = false
                        urlInput = schedule.allRoutesPdfUrl ?: ""
                        nameInput = schedule.allRoutesPdfName ?: ""
                        showLinkUrlDialog = true
                    }
                ) {
                    Text("Paste Web / Drive Link")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(CollegePrimary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = CollegePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "College Bus Timetable (All Routes)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Synced automatically to all students & drivers",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (!schedule.allRoutesPdfUrl.isNullOrBlank()) {
                    Surface(
                        color = StatusActiveGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusActiveGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!schedule.allRoutesPdfUrl.isNullOrBlank()) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = CollegePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = schedule.allRoutesPdfName ?: "master_timetable.pdf",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Visible on all student and driver dashboards",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onViewClick(schedule.allRoutesPdfUrl!!, schedule.allRoutesPdfName) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showReplaceOptionsDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Replace", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .background(StatusDangerRed.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete PDF", tint = StatusDangerRed, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No master college bus route PDF uploaded yet.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onUploadClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showLinkUrlDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Link Web/Drive", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}


