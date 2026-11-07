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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.R
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.ui.common.EmptyStateCard
import com.college.bustracker.ui.common.SectionHeader
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.KolkataTimeFormatter

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
    val activeSwaps by viewModel.activeSwapsForDriver.collectAsState()
    val isSwapLoading by viewModel.isSwapLoading.collectAsState()

    val stops = route?.stops ?: emptyList()
    val todayIso = remember { KolkataTimeFormatter.getTodayIso() }

    val replacementOptions = remember(allBuses, assignedBus) {
        DriverViewModel.filterEligibleReplacementBuses(allBuses, assignedBus)
    }

    var editingSwap by remember { mutableStateOf<BusSwap?>(null) }
    var isAddingAdditionalSwap by remember { mutableStateOf(false) }
    var swapToCancel by remember { mutableStateOf<BusSwap?>(null) }

    // Form fields
    var selectedReplacement by remember { mutableStateOf("") }
    var selectedScopeType by remember { mutableStateOf(BusSwap.SCOPE_ALL_STOPS) }
    val affectedStopIds = remember { mutableStateListOf<String>() }
    var optionalMessage by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    // Dialog states
    var showSendConfirmationDialog by remember { mutableStateOf(false) }
    var showUpdateConfirmationDialog by remember { mutableStateOf(false) }

    // Identify which stops are already assigned to active swaps
    val assignedStopMap = remember(activeSwaps) {
        val map = mutableMapOf<String, String>() // stopId -> replacementBusId
        activeSwaps.forEach { swap ->
            if (swap.scopeType == BusSwap.SCOPE_ALL_STOPS) {
                stops.forEach { s -> map[s.stopId] = swap.replacementBusId }
            } else {
                swap.affectedStopIds.forEach { id -> map[id] = swap.replacementBusId }
            }
        }
        map
    }

    val hasAllStopsActive = remember(activeSwaps) {
        activeSwaps.any { it.scopeType == BusSwap.SCOPE_ALL_STOPS }
    }

    val unassignedStops = remember(stops, assignedStopMap) {
        stops.filter { !assignedStopMap.containsKey(it.stopId) }
    }

    // Initialize/Reset form fields when editing or creating
    LaunchedEffect(editingSwap, isAddingAdditionalSwap, replacementOptions) {
        if (editingSwap != null) {
            val target = editingSwap!!
            selectedReplacement = target.replacementBusId
            selectedScopeType = target.scopeType
            affectedStopIds.clear()
            affectedStopIds.addAll(target.affectedStopIds)
            val msg = target.message.trim()
            optionalMessage = if (msg.startsWith("Bus swap reported:", ignoreCase = true)) "" else msg
        } else if (isAddingAdditionalSwap) {
            if (replacementOptions.isNotEmpty() && selectedReplacement.isBlank()) {
                selectedReplacement = replacementOptions.first().busNumber
            }
            selectedScopeType = BusSwap.SCOPE_SELECTED_STOPS
            affectedStopIds.clear()
            optionalMessage = ""
        } else if (activeSwaps.isEmpty()) {
            if (replacementOptions.isNotEmpty()) {
                selectedReplacement = replacementOptions.first().busNumber
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
                    contentDescription = stringResource(R.string.back_action),
                    tint = Color(0xFF1E293B)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (editingSwap != null) stringResource(R.string.edit_vehicle_swap_title)
                    else if (activeSwaps.isNotEmpty()) "Active Bus Swaps"
                    else stringResource(R.string.report_vehicle_swap_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (activeSwaps.isNotEmpty()) "${activeSwaps.size} swap alert(s) active for your bus"
                    else stringResource(R.string.broadcast_substitute_bus_subtitle),
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

        // 1. ACTIVE SWAPS LIST (Shown when there are active swaps and not currently editing a specific swap)
        if (activeSwaps.isNotEmpty() && editingSwap == null) {
            Text(
                text = "Currently Active Swaps (${activeSwaps.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF334155),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            activeSwaps.forEach { swap ->
                val isFromPreviousDay = swap.date.isNotBlank() && swap.date != todayIso

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = StatusWarningOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Swap Alert Active",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E),
                                    fontSize = 12.sp
                                )
                            }

                            if (isFromPreviousDay) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFDC2626), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "From ${swap.date}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFD97706), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Today",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "${assignedBus?.busNumber ?: swap.originalBusId}  ➔  ${swap.replacementBusId}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Scope Information
                        val isAllStops = swap.scopeType == BusSwap.SCOPE_ALL_STOPS || swap.affectedStopIds.isEmpty()
                        val scopeText = if (isAllStops) {
                            stringResource(R.string.scope_all_stops_format, stops.size)
                        } else {
                            val names = swap.affectedStopIds.mapNotNull { id -> stops.find { it.stopId == id }?.name ?: id }.joinToString(", ")
                            stringResource(R.string.scope_selected_stops_format, swap.affectedStopIds.size, names)
                        }
                        Text(
                            text = scopeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E)
                        )

                        val rawMsg = swap.message.trim()
                        if (rawMsg.isNotBlank() && !rawMsg.startsWith("Bus swap reported:", ignoreCase = true)) {
                            Text(
                                text = "Reason: $rawMsg",
                                fontSize = 12.sp,
                                color = Color(0xFF78350F),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Actions: Edit and Cancel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editingSwap = swap
                                    isAddingAdditionalSwap = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = CollegePrimary
                                ),
                                border = BorderStroke(1.dp, CollegePrimary),
                                enabled = !isSwapLoading
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.edit_alert_action), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { swapToCancel = swap },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = StatusDangerRed
                                ),
                                border = BorderStroke(1.dp, StatusDangerRed),
                                enabled = !isSwapLoading
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.cancel_alert_action), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Button to Add Another Swap if unassigned stops exist
            if (!hasAllStopsActive && unassignedStops.isNotEmpty() && !isAddingAdditionalSwap) {
                Button(
                    onClick = {
                        isAddingAdditionalSwap = true
                        editingSwap = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Another Swap (${unassignedStops.size} stops unassigned)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 2. FORM CARD (Shown when: 0 active swaps, or editing a swap, or adding an additional swap)
        if (activeSwaps.isEmpty() || editingSwap != null || isAddingAdditionalSwap) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    val formTitle = when {
                        editingSwap != null -> "Edit Bus Swap (${editingSwap?.replacementBusId})"
                        isAddingAdditionalSwap -> "Add Additional Bus Swap"
                        else -> stringResource(R.string.swap_config_title)
                    }

                    SectionHeader(
                        title = formTitle,
                        icon = Icons.Default.SwapHoriz
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step 1: Current Assigned Bus
                    Text(
                        text = stringResource(R.string.step1_current_bus),
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
                        text = stringResource(R.string.step2_select_replacement_bus),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (replacementOptions.isEmpty()) {
                        EmptyStateCard(
                            title = stringResource(R.string.no_replacement_buses_title),
                            description = stringResource(R.string.no_replacement_buses_desc),
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
                                replacementOptions.forEach { busOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(busOption.busNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (busOption.registrationNumber.isNotBlank()) {
                                                    Text(busOption.registrationNumber, fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedReplacement = busOption.busNumber
                                            expandedDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 3: Scope Selection
                    Text(
                        text = stringResource(R.string.step3_target_scope),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val canSelectAllStops = activeSwaps.isEmpty() || (editingSwap != null && activeSwaps.size == 1)

                    if (canSelectAllStops) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // All Stops option
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
                                        Text(stringResource(R.string.all_stops_scope_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        Text(stringResource(R.string.entire_route_desc, stops.size), fontSize = 10.sp, color = Color(0xFF64748B))
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
                                        Text(stringResource(R.string.specific_stops_scope_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        Text(stringResource(R.string.select_subset_desc), fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                }
                            }
                        }
                    } else {
                        // When adding additional swap, only specific stops is allowed
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Specific Stops Scope (Select from remaining unassigned route stops)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CollegePrimary
                            )
                        }
                    }

                    // Checkboxes for stops
                    if (selectedScopeType == BusSwap.SCOPE_SELECTED_STOPS || !canSelectAllStops) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.select_affected_stops_format, affectedStopIds.size, stops.size),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        stops.forEach { stop ->
                            val assignedBusForThisStop = assignedStopMap[stop.stopId]
                            val isAssignedToOtherSwap = assignedBusForThisStop != null && (editingSwap == null || assignedBusForThisStop != editingSwap?.replacementBusId)
                            val isChecked = affectedStopIds.contains(stop.stopId)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    enabled = !isAssignedToOtherSwap,
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
                                Column {
                                    Text(
                                        text = "${stop.order}. ${stop.name}",
                                        fontSize = 13.sp,
                                        color = if (isAssignedToOtherSwap) Color(0xFF94A3B8) else Color(0xFF0F172A),
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (isAssignedToOtherSwap) {
                                        Text(
                                            text = "Assigned to $assignedBusForThisStop",
                                            fontSize = 10.sp,
                                            color = Color(0xFFD97706),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 4: Reason / Notice
                    Text(
                        text = stringResource(R.string.reason_notice_optional),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = optionalMessage,
                        onValueChange = { optionalMessage = it },
                        placeholder = { Text("e.g. Engine breakdown, tire repair (optional)", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val isScopeValid = selectedScopeType == BusSwap.SCOPE_ALL_STOPS || affectedStopIds.isNotEmpty()
                    val canSubmit = replacementOptions.isNotEmpty() && selectedReplacement.isNotBlank() && isScopeValid && !isSwapLoading

                    if (editingSwap != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { editingSwap = null },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSwapLoading
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.cancel_action), fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { showUpdateConfirmationDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = canSubmit,
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                if (isSwapLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.save_changes_action), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (isAddingAdditionalSwap) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isAddingAdditionalSwap = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSwapLoading
                            ) {
                                Text(stringResource(R.string.cancel_action))
                            }

                            Button(
                                onClick = { showSendConfirmationDialog = true },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = canSubmit,
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                if (isSwapLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Broadcast Swap", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = { showSendConfirmationDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = canSubmit,
                            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                        ) {
                            if (isSwapLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(
                                    text = stringResource(R.string.broadcast_vehicle_swap_action),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Adding/Sending Swap
    if (showSendConfirmationDialog) {
        val repBusId = replacementOptions.find { it.busNumber == selectedReplacement }?.busId ?: selectedReplacement
        val scopeDesc = if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) {
            stringResource(R.string.entire_route_desc, stops.size)
        } else {
            "${affectedStopIds.size} stops selected"
        }

        AlertDialog(
            onDismissRequest = { if (!isSwapLoading) showSendConfirmationDialog = false },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(32.dp)) },
            title = { Text(stringResource(R.string.send_bus_swap_alert_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(stringResource(R.string.confirm_swap_details_intro), fontSize = 13.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.orig_bus_prefix, assignedBus?.busNumber ?: "Assigned Bus"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.rep_bus_prefix, selectedReplacement), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.scope_prefix, scopeDesc), fontSize = 13.sp)
                    if (optionalMessage.isNotBlank()) {
                        Text(stringResource(R.string.reason_prefix, optionalMessage), fontSize = 13.sp)
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
                            onSuccess = {
                                isAddingAdditionalSwap = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    enabled = !isSwapLoading
                ) {
                    Text(stringResource(R.string.confirm_and_broadcast_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSendConfirmationDialog = false },
                    enabled = !isSwapLoading
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    // Confirmation Dialog for Updating Swap
    if (showUpdateConfirmationDialog && editingSwap != null) {
        val target = editingSwap!!
        val repBusId = replacementOptions.find { it.busNumber == selectedReplacement }?.busId ?: selectedReplacement
        val scopeDesc = if (selectedScopeType == BusSwap.SCOPE_ALL_STOPS) {
            stringResource(R.string.entire_route_desc, stops.size)
        } else {
            "${affectedStopIds.size} stops selected"
        }

        AlertDialog(
            onDismissRequest = { if (!isSwapLoading) showUpdateConfirmationDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(32.dp)) },
            title = { Text(stringResource(R.string.update_bus_swap_alert_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(stringResource(R.string.update_active_swap_intro), fontSize = 13.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.rep_bus_prefix, selectedReplacement), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.scope_prefix, scopeDesc), fontSize = 13.sp)
                    if (optionalMessage.isNotBlank()) {
                        Text(stringResource(R.string.reason_prefix, optionalMessage), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateConfirmationDialog = false
                        viewModel.updateBusSwap(
                            swapId = target.swapId,
                            replacementBusId = repBusId,
                            scopeType = selectedScopeType,
                            affectedStopIds = affectedStopIds.toList(),
                            optionalMessage = optionalMessage,
                            onSuccess = {
                                editingSwap = null
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    enabled = !isSwapLoading
                ) {
                    Text(stringResource(R.string.save_changes_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateConfirmationDialog = false },
                    enabled = !isSwapLoading
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    // Confirmation Dialog for Cancelling Swap
    if (swapToCancel != null) {
        val target = swapToCancel!!
        AlertDialog(
            onDismissRequest = { if (!isSwapLoading) swapToCancel = null },
            title = {
                Text(
                    text = stringResource(R.string.cancel_bus_swap_alert_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel the swap ${target.originalBusId} ➔ ${target.replacementBusId}? Your regular bus assignment will be restored for these stops.",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toCancel = swapToCancel
                        swapToCancel = null
                        if (toCancel != null) {
                            viewModel.cancelSwap(toCancel) {
                                if (editingSwap?.swapId == toCancel.swapId) {
                                    editingSwap = null
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                    enabled = !isSwapLoading
                ) {
                    Text(stringResource(R.string.yes_cancel_swap_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { swapToCancel = null },
                    enabled = !isSwapLoading
                ) {
                    Text(stringResource(R.string.keep_active_action))
                }
            }
        )
    }
}
