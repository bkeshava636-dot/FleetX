package com.college.bustracker.ui.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.R
import com.college.bustracker.data.model.Announcement
import com.college.bustracker.data.model.Bus
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated administrative workflow for publishing bus timing change alerts.
 * Communicates timing adjustments clearly to students and drivers without
 * silently modifying the underlying operating schedule or GPS tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusTimingChangeDialog(
    buses: List<Bus>,
    onDismiss: () -> Unit,
    onPublishAlert: (announcement: Announcement) -> Unit,
    onOpenScheduleSettings: () -> Unit
) {
    val isKn = LocaleHelper.isKannada()

    var selectedBus by remember { mutableStateOf(buses.firstOrNull()) }
    var busDropdownOpen by remember { mutableStateOf(false) }

    var timingType by remember { mutableStateOf("Morning departure time") }
    var timingTypeDropdownOpen by remember { mutableStateOf(false) }

    var previousTime by remember { mutableStateOf("07:00") }
    var newTime by remember { mutableStateOf("07:30") }

    val todayFormatted = remember {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
        sdf.format(Date())
    }
    var effectiveDate by remember { mutableStateOf(todayFormatted) }
    var endDate by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("College examination schedule") }

    var priority by remember { mutableStateOf(Announcement.PRIORITY_IMPORTANT) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val targetBusId = selectedBus?.busId ?: "BUS"
    val timingTypeKn = if (timingType.contains("Morning", ignoreCase = true)) "ಬೆಳಗಿನ ಹೊರಡುವಿಕೆ" else "ಸಂಜೆಯ ಹೊರಡುವಿಕೆ"
    val reasonKn = when (reason) {
        "College examination schedule" -> "ಕಾಲೇಜು ಪರೀಕ್ಷಾ ವೇಳಾಪಟ್ಟಿ"
        "Road maintenance / Traffic diversion" -> "ರಸ್ತೆ ಕಾಮಗಾರಿ / ಟ್ರಾಫಿಕ್ ಬದಲಾವಣೆ"
        "Inclement weather" -> "ಹವಾಮಾನ ವೈಪರೀತ್ಯ"
        "Special event / College function" -> "ವಿಶೇಷ ಕಾರ್ಯಕ್ರಮ"
        else -> reason
    }

    val generatedTitleEn = "Bus $targetBusId Timing Alert: $timingType"
    val generatedMessageEn = "Effective $effectiveDate, the $timingType for Bus $targetBusId has changed from $previousTime to $newTime.${if (endDate.isNotBlank()) " Temporary until $endDate." else ""} Reason: $reason."

    val generatedTitleKn = "ಬಸ್ $targetBusId ಸಮಯ ಬದಲಾವಣೆ: $timingTypeKn"
    val generatedMessageKn = "$effectiveDate ರಿಂದ ಜಾರಿಗೆ ಬರುವಂತೆ, ಬಸ್ $targetBusId $timingTypeKn ಸಮಯವನ್ನು $previousTime ರಿಂದ $newTime ಕ್ಕೆ ಬದಲಾಯಿಸಲಾಗಿದೆ.${if (endDate.isNotBlank()) " ಇದು $endDate ವರೆಗೆ ತಾತ್ಕಾಲಿಕವಾಗಿರುತ್ತದೆ." else ""} ಕಾರಣ: $reasonKn."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bus_timing_alert_workflow),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Explanation notice
                Surface(
                    color = Color(0xFFFEF3C7).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.timing_alert_explanation),
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            lineHeight = 15.sp
                        )
                    }
                }

                if (errorText != null) {
                    Text(text = errorText!!, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // 1. Bus Selection
                Text(
                    text = stringResource(R.string.select_bus_for_timing),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                ExposedDropdownMenuBox(
                    expanded = busDropdownOpen,
                    onExpandedChange = { busDropdownOpen = !busDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = selectedBus?.let { "${it.busNumber} (${it.busId})" } ?: "Select Bus",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = busDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = busDropdownOpen,
                        onDismissRequest = { busDropdownOpen = false }
                    ) {
                        buses.forEach { bus ->
                            DropdownMenuItem(
                                text = { Text("${bus.busNumber} (${bus.busId})") },
                                onClick = {
                                    selectedBus = bus
                                    busDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // 2. Service Window / Change Type
                Text(
                    text = stringResource(R.string.timing_change_type),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                ExposedDropdownMenuBox(
                    expanded = timingTypeDropdownOpen,
                    onExpandedChange = { timingTypeDropdownOpen = !timingTypeDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = timingType,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timingTypeDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = timingTypeDropdownOpen,
                        onDismissRequest = { timingTypeDropdownOpen = false }
                    ) {
                        listOf(
                            "Morning departure time",
                            "Evening departure time",
                            "Mid-day departure time",
                            "Full day schedule delay"
                        ).forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    timingType = type
                                    timingTypeDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // 3. Times (Previous and New)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = previousTime,
                        onValueChange = { previousTime = it },
                        label = { Text(stringResource(R.string.previous_time)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = newTime,
                        onValueChange = { newTime = it },
                        label = { Text(stringResource(R.string.new_time)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. Dates
                OutlinedTextField(
                    value = effectiveDate,
                    onValueChange = { effectiveDate = it },
                    label = { Text(stringResource(R.string.effective_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text(stringResource(R.string.end_date_optional)) },
                    placeholder = { Text("e.g. 24 October 2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. Reason
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.reason_for_change)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Alert Preview Box
                Text(
                    text = stringResource(R.string.timing_alert_preview),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = CollegePrimary
                )

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = generatedTitleEn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = generatedMessageEn,
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = generatedTitleKn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = generatedMessageKn,
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bus = selectedBus
                    if (bus == null) {
                        errorText = "Please select a bus"
                        return@Button
                    }
                    if (previousTime.isBlank() || newTime.isBlank()) {
                        errorText = "Please enter previous and new times"
                        return@Button
                    }

                    val now = System.currentTimeMillis()
                    val announcement = Announcement(
                        titleEn = generatedTitleEn,
                        messageEn = generatedMessageEn,
                        titleKn = generatedTitleKn,
                        messageKn = generatedMessageKn,
                        category = Announcement.CATEGORY_TIMING_CHANGE,
                        priority = priority,
                        targetAudience = Announcement.AUDIENCE_SELECTED_BUS,
                        targetId = bus.busId,
                        targetName = bus.busNumber,
                        status = Announcement.STATUS_PUBLISHED,
                        timingBusId = bus.busId,
                        timingChangeType = timingType,
                        previousTime = previousTime.trim(),
                        newTime = newTime.trim(),
                        effectiveDate = effectiveDate.trim(),
                        endDate = endDate.trim().ifBlank { null },
                        changeReason = reason.trim(),
                        createdAt = now,
                        updatedAt = now,
                        expiresAt = now + (7 * 86400_000L) // Active for 7 days by default
                    )
                    onPublishAlert(announcement)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.publish_timing_alert), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}
