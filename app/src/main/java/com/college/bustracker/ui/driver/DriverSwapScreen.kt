package com.college.bustracker.ui.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.ui.common.EmptyStateCard
import com.college.bustracker.ui.common.SectionHeader
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSwapScreen(
    viewModel: DriverViewModel,
    onNavigateBack: () -> Unit
) {
    val assignedBus by viewModel.assignedBus.collectAsState()
    val route by viewModel.assignedRoute.collectAsState()
    val allBuses by viewModel.allBuses.collectAsState()
    val successMsg by viewModel.swapSuccessMessage.collectAsState()
    val swapErrorMsg by viewModel.swapErrorMessage.collectAsState()
    val activeSwap by viewModel.activeSwapForDriver.collectAsState()
    val isSwapLoading by viewModel.isSwapLoading.collectAsState()

    val stops = route?.stops ?: emptyList()

    val replacementOptions = remember(allBuses, assignedBus) {
        DriverViewModel.filterEligibleReplacementBuses(allBuses, assignedBus)
    }

    var isEditMode by remember { mutableStateOf(false) }

    // Form fields
    var selectedReplacement by remember { mutableStateOf("") }
    var selectedScopeType by remember { mutableStateOf(BusSwap.SCOPE_ALL_STOPS) }
    val affectedStopIds = remember { mutableStateListOf<String>() }
    var optionalMessage by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    // Dialog states
    var showSendConfirmationDialog by remember { mutableStateOf(false) }
    var showUpdateConfirmationDialog by remember { mutableStateOf(false) }
    var showCancelConfirmationDialog by remember { mutableStateOf(false) }

    // Initialize/Reset form on load or active swap change
    LaunchedEffect(activeSwap, replacementOptions, isEditMode) {
        if (activeSwap != null) {
            if (isEditMode) {
                selectedReplacement = activeSwap?.replacementBusId ?: ""
                selectedScopeType = activeSwap?.scopeType ?: BusSwap.SCOPE_ALL_STOPS
                affectedStopIds.clear()
                affectedStopIds.addAll(activeSwap?.affectedStopIds ?: emptyList())
                optionalMessage = activeSwap?.message ?: ""
            }
        } else {
            isEditMode = false
            if (replacementOptions.isNotEmpty()) {
                if (replacementOptions.none { it.busNumber == selectedReplacement || it.busId == selectedReplacement }) {
                    selectedReplacement = replacementOptions.first().busNumber
                }
            } else {
                selectedReplacement = ""
            }
            selectedScopeType = BusSwap.SCOPE_ALL_STOPS
            affectedStopIds.clear()
            optionalMessage = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF1E293B)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (activeSwap != null && !isEditMode) "Active Vehicle Swap" else if (isEditMode) "Edit Vehicle Swap" else "Report Vehicle Swap",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (activeSwap != null && !isEditMode) "Single active swap currently broadcasting" else "Broadcast substitute bus to waiting students",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Success banner
        if (successMsg != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDEF7EC))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusActiveGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = successMsg ?: "",
                        color = Color(0xFF03543F),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Error banner
        if (swapErrorMsg != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE8E8))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusDangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = swapErrorMsg ?: "",
                        color = StatusDangerRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 1. ACTIVE ALERT CARD (Shown when activeSwap != null and NOT in Edit Mode)
        if (activeSwap != null && !isEditMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
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
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusWarningOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACTIVE VEHICLE SWAP ALERT",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFD97706), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${assignedBus?.busNumber ?: activeSwap?.originalBusId}  ➔  ${activeSwap?.replacementBusId}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF78350F)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Scope Information
                    val isAllStops = activeSwap?.scopeType == BusSwap.SCOPE_ALL_STOPS || (activeSwap?.affectedStopIds?.isEmpty() == true)
                    val scopeText = if (isAllStops) {
                        "Target Scope: All Route Stops (${stops.size} stops)"
                    } else {
                        val names = activeSwap?.affectedStopIds?.mapNotNull { id -> stops.find { it.stopId == id }?.name ?: id }?.joinToString(", ") ?: ""
                        "Target Scope: ${activeSwap?.affectedStopIds?.size} Selected Stop(s) ($names)"
                    }
                    Text(
                        text = scopeText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF92400E)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!activeSwap?.message.isNullOrBlank()) {
                        Text(
                            text = "Reason: ${activeSwap?.message}",
                            fontSize = 13.sp,
                            color = Color(0xFF78350F)
                        )
                    }

                    Text(
                        text = "Date: ${activeSwap?.date}",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons: Edit and Cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditMode = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = CollegePrimary
                            ),
                            border = BorderStroke(1.dp, CollegePrimary),
                            enabled = !isSwapLoading
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EDIT ALERT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showCancelConfirmationDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = StatusDangerRed
                            ),
                            border = BorderStroke(1.dp, StatusDangerRed),
                            enabled = !isSwapLoading
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CANCEL ALERT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. FORM CARD (Shown when NO active swap, OR when active swap is in Edit Mode)
        if (activeSwap == null || isEditMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionHeader(
                        title = if (isEditMode) "Edit Swap Configuration" else "Swap Configuration",
                        icon = Icons.Default.SwapHoriz
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step 1: Current Assigned Bus
                    Text(
                        text = "1. Current Assigned Bus",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = CollegePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = assignedBus?.let { "${it.busNumber} (${it.registrationNumber})" } ?: "BUS-25",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 2: Select Replacement Bus
                    Text(
                        text = "2. Select Replacement Bus",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (replacementOptions.isEmpty()) {
                        EmptyStateCard(
                            title = "No Replacement Buses Available",
                            description = "There are no other active fleet buses registered in Firestore. Contact administration to add active buses.",
                            icon = Icons.Default.DirectionsBus
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expandedDropdown,
                            onExpandedChange = { expandedDropdown = !expandedDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedReplacement,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
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
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false }
                            ) {
                                replacementOptions.forEach { bus ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(bus.busNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Plate: ${bus.registrationNumber}", fontSize = 11.sp, color = Color(0xFF64748B))
                                            }
                                        },
                                        onClick = {
                                            selectedReplacement = bus.busNumber
                                            expandedDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Step 3: Stop Scope Selection (ALL_STOPS vs SELECTED_STOPS)
                    Text(
                        text = "3. Target Notification Scope",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // All Route Stops option
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedScopeType = BusSwap.SCOPE_ALL_STOPS
                                    affectedStopIds.clear()
                                },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.5.dp,
                                if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) CollegePrimary else Color(0xFFE2E8F0)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) CollegePrimary else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("All Stops", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    Text("Entire route (${stops.size})", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }

                        // Specific Stops option
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedScopeType = BusSwap.SCOPE_SELECTED_STOPS
                                    if (affectedStopIds.isEmpty() && stops.isNotEmpty()) {
                                        affectedStopIds.addAll(stops.map { it.stopId })
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.5.dp,
                                if (selectedScopeType == BusSwap.SCOPE_SELECTED_STOPS) CollegePrimary else Color(0xFFE2E8F0)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedScopeType == BusSwap.SCOPE_SELECTED_STOPS) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedScopeType == BusSwap.SCOPE_SELECTED_STOPS) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (selectedScopeType == BusSwap.SCOPE_SELECTED_STOPS) CollegePrimary else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Specific Stops", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    Text("Select subset", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }

                    // Show checkboxes if SELECTED_STOPS
                    if (selectedScopeType == BusSwap.SCOPE_SELECTED_STOPS) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Affected Stops (${affectedStopIds.size}/${stops.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B)
                            )
                            if (stops.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        if (affectedStopIds.size == stops.size) {
                                            affectedStopIds.clear()
                                        } else {
                                            affectedStopIds.clear()
                                            affectedStopIds.addAll(stops.map { it.stopId })
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (affectedStopIds.size == stops.size) "Deselect All" else "Select All",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CollegePrimary
                                    )
                                }
                            }
                        }

                        if (stops.isEmpty()) {
                            Text(
                                text = "No route stops configured.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            stops.forEach { stop ->
                                val isChecked = affectedStopIds.contains(stop.stopId)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                affectedStopIds.add(stop.stopId)
                                            } else {
                                                affectedStopIds.remove(stop.stopId)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = CollegePrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${stop.order}. ${stop.name}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A),
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 4: Reason / Notice
                    Text(
                        text = "4. Reason / Notice (Optional)",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = optionalMessage,
                        onValueChange = { optionalMessage = it },
                        placeholder = { Text("e.g. Scheduled maintenance, puncture", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    val isScopeValid = selectedScopeType == BusSwap.SCOPE_ALL_STOPS || affectedStopIds.isNotEmpty()
                    val canSubmit = replacementOptions.isNotEmpty() && selectedReplacement.isNotBlank() && isScopeValid && !isSwapLoading

                    if (isEditMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isEditMode = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSwapLoading
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { showUpdateConfirmationDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = canSubmit,
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                if (isSwapLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Changes", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = { showSendConfirmationDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = canSubmit,
                            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                        ) {
                            if (isSwapLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "BROADCAST VEHICLE SWAP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. Send Confirmation Dialog
    if (showSendConfirmationDialog) {
        val repBusId = replacementOptions.find { it.busNumber == selectedReplacement }?.busId ?: selectedReplacement
        val scopeDesc = if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) {
            "All route stops (${stops.size} stops)"
        } else {
            "${affectedStopIds.size} selected stops (${affectedStopIds.mapNotNull { id -> stops.find { it.stopId == id }?.name }.joinToString(", ")})"
        }

        AlertDialog(
            onDismissRequest = { if (!isSwapLoading) showSendConfirmationDialog = false },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(32.dp)) },
            title = { Text("Send Bus Swap Alert?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text("Please confirm the swap details before broadcasting:", fontSize = 13.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Original Bus: ${assignedBus?.busNumber ?: "Assigned Bus"}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Replacement Bus: $selectedReplacement", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Scope: $scopeDesc", fontSize = 13.sp)
                    if (optionalMessage.isNotBlank()) {
                        Text("• Reason: $optionalMessage", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSendConfirmationDialog = false
                        viewModel.reportBusSwap(
                            replacementBusId = repBusId,
                            scopeType = selectedScopeType,
                            affectedStopIds = affectedStopIds.toList(),
                            optionalMessage = optionalMessage,
                            onSuccess = {}
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    enabled = !isSwapLoading
                ) {
                    Text("Confirm & Broadcast", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSendConfirmationDialog = false },
                    enabled = !isSwapLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Update Confirmation Dialog
    if (showUpdateConfirmationDialog && activeSwap != null) {
        val repBusId = replacementOptions.find { it.busNumber == selectedReplacement }?.busId ?: selectedReplacement
        val scopeDesc = if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) {
            "All route stops (${stops.size} stops)"
        } else {
            "${affectedStopIds.size} selected stops"
        }

        AlertDialog(
            onDismissRequest = { if (!isSwapLoading) showUpdateConfirmationDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(32.dp)) },
            title = { Text("Update Bus Swap Alert?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text("Update active swap with the following changes?", fontSize = 13.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Replacement: $selectedReplacement", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Scope: $scopeDesc", fontSize = 13.sp)
                    if (optionalMessage.isNotBlank()) {
                        Text("• Reason: $optionalMessage", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateConfirmationDialog = false
                        viewModel.updateBusSwap(
                            swapId = activeSwap?.swapId ?: "",
                            replacementBusId = repBusId,
                            scopeType = selectedScopeType,
                            affectedStopIds = affectedStopIds.toList(),
                            optionalMessage = optionalMessage,
                            onSuccess = {
                                isEditMode = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    enabled = !isSwapLoading
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateConfirmationDialog = false },
                    enabled = !isSwapLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Cancel confirmation dialog
    if (showCancelConfirmationDialog && activeSwap != null) {
        AlertDialog(
            onDismissRequest = { if (!isSwapLoading) showCancelConfirmationDialog = false },
            title = {
                Text(
                    text = "Cancel Bus Swap Alert?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel the swap alert for ${assignedBus?.busNumber ?: activeSwap?.originalBusId}?\n\nStudents will immediately be notified that normal bus service has resumed.",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeSwap?.let { swap ->
                            viewModel.cancelSwap(swap) {
                                showCancelConfirmationDialog = false
                                isEditMode = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                    enabled = !isSwapLoading
                ) {
                    Text("Yes, Cancel Swap", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelConfirmationDialog = false },
                    enabled = !isSwapLoading
                ) {
                    Text("Keep Active")
                }
            }
        )
    }
}
