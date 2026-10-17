package com.college.bustracker.ui.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.college.bustracker.R
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.StudentFeedback
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentFeedbackDialog(
    buses: List<Bus>,
    preselectedBusId: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (StudentFeedback, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(StudentFeedback.CATEGORY_GPS_ISSUE) }
    var selectedBusId by remember { mutableStateOf(preselectedBusId ?: "") }
    var busDropdownOpen by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var studentName by remember { mutableStateOf("") }
    var studentContact by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmittedSuccess by remember { mutableStateOf(false) }

    val selectedBus = remember(buses, selectedBusId) {
        buses.find { it.busId == selectedBusId }
    }

    Dialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            if (isSubmittedSuccess) {
                // Success View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFDCFCE7), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusActiveGreen,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.feedback_submitted_success_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.feedback_submitted_success_desc),
                        fontSize = 13.sp,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                    ) {
                        Text(
                            text = stringResource(R.string.close),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Form View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Row
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
                                    .size(36.dp)
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Feedback,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.student_feedback_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = stringResource(R.string.student_feedback_subtitle),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Category Selection Chips
                    Text(
                        text = stringResource(R.string.select_category_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StudentFeedback.ALL_CATEGORIES.forEach { category ->
                            val isSelected = category == selectedCategory
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) CollegePrimary else Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedCategory = category }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = category,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF334155)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Affected Bus Selector
                    Text(
                        text = stringResource(R.string.select_bus_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { busDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.DirectionsBus,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = CollegePrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (selectedBus != null) {
                                            "Bus ${selectedBus.busNumber}" + (if (!selectedBus.routeId.isNullOrBlank()) " (${selectedBus.routeId})" else "")
                                        } else {
                                            stringResource(R.string.general_no_bus)
                                        },
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = busDropdownOpen,
                            onDismissRequest = { busDropdownOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.general_no_bus), fontSize = 13.sp) },
                                onClick = {
                                    selectedBusId = ""
                                    busDropdownOpen = false
                                }
                            )
                            buses.forEach { bus ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Bus ${bus.busNumber}" + (if (!bus.routeId.isNullOrBlank()) " (${bus.routeId})" else ""),
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        selectedBusId = bus.busId
                                        busDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subject Field
                    Text(
                        text = stringResource(R.string.subject_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        placeholder = { Text(stringResource(R.string.subject_hint), fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description Field
                    Text(
                        text = stringResource(R.string.description_label) + " *",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text(stringResource(R.string.description_hint), fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional Student Name & USN / Contact Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.student_name_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = studentName,
                                onValueChange = { studentName = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CollegePrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.student_contact_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = studentContact,
                                onValueChange = { studentContact = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CollegePrimary,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }

                    // Error Message Banner
                    val err = errorMessage
                    if (!err.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = err,
                            fontSize = 12.sp,
                            color = StatusDangerRed,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting
                        ) {
                            Text(stringResource(R.string.cancel), color = Color(0xFF64748B))
                        }

                        Button(
                            onClick = {
                                if (description.trim().isBlank()) {
                                    errorMessage = "Please provide a brief description."
                                    return@Button
                                }
                                errorMessage = null
                                isSubmitting = true
                                val report = StudentFeedback(
                                    feedbackId = "",
                                    busId = selectedBusId,
                                    routeId = selectedBus?.routeId ?: "",
                                    category = selectedCategory,
                                    subject = subject.trim(),
                                    description = description.trim(),
                                    studentName = studentName.trim(),
                                    studentContact = studentContact.trim(),
                                    timestamp = System.currentTimeMillis()
                                )
                                onSubmit(report) { success, submitError ->
                                    isSubmitting = false
                                    if (success) {
                                        isSubmittedSuccess = true
                                    } else {
                                        errorMessage = submitError ?: "Submission failed. Please check network."
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(2f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                            enabled = !isSubmitting
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Feedback,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.submit_feedback_action),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
