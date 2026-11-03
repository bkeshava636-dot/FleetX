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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.Route
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.util.KolkataTimeFormatter

@Composable
fun AdminCreateBusSwapDialog(
    buses: List<Bus>,
    routes: List<Route>,
    activeSwaps: List<BusSwap>,
    onDismiss: () -> Unit,
    onCreateSwap: (
        originalBusId: String,
        replacementBusId: String,
        routeId: String,
        reason: String,
        scopeType: String,
        affectedStops: List<String>,
        effectiveDate: String,
        onComplete: (Boolean, String?) -> Unit
    ) -> Unit
) {
    var selectedOriginalBus by remember { mutableStateOf<Bus?>(null) }
    var selectedReplacementBus by remember { mutableStateOf<Bus?>(null) }
    var originalDropdownOpen by remember { mutableStateOf(false) }
    var replacementDropdownOpen by remember { mutableStateOf(false) }

    var selectedReasonPreset by remember { mutableStateOf("Mechanical Breakdown") }
    var customReason by remember { mutableStateOf("") }

    var scopeType by remember { mutableStateOf(BusSwap.SCOPE_ALL_STOPS) }
    var selectedStopIds by remember { mutableStateOf(setOf<String>()) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activeSwappedBusIds = remember(activeSwaps) {
        val set = mutableSetOf<String>()
        activeSwaps.filter { it.status == "ACTIVE" }.forEach {
            if (it.originalBusId.isNotBlank()) set.add(it.originalBusId.trim().uppercase())
            if (it.replacementBusId.isNotBlank()) set.add(it.replacementBusId.trim().uppercase())
        }
        set
    }

    val eligibleOriginalBuses = remember(buses, activeSwappedBusIds) {
        buses.filter { bus ->
            bus.active && !activeSwappedBusIds.contains(bus.busId.trim().uppercase())
        }.sortedBy { it.busNumber }
    }

    val eligibleReplacementBuses = remember(buses, selectedOriginalBus, activeSwappedBusIds) {
        val origId = selectedOriginalBus?.busId?.trim()?.uppercase() ?: ""
        buses.filter { bus ->
            bus.active &&
            bus.busId.trim().uppercase() != origId &&
            !activeSwappedBusIds.contains(bus.busId.trim().uppercase())
        }.sortedBy { it.busNumber }
    }

    val matchedRoute = remember(selectedOriginalBus, routes) {
        val bus = selectedOriginalBus
        if (bus == null || bus.routeId.isBlank()) null
        else routes.find { it.routeId == bus.routeId }
    }

    val reasonPresets = listOf(
        "Mechanical Breakdown",
        "Scheduled Maintenance",
        "Flat Tire",
        "Engine/Heating Issue",
        "Driver Request",
        "Other"
    )

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(CollegePrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = CollegePrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("New Bus Swap", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Text("Authorize temporary bus substitution", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusDangerRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(errorMessage ?: "", color = StatusDangerRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Step 1: Select Original Bus
                Text("1. Original Bus (To Be Replaced)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { originalDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedOriginalBus?.let { "${it.busNumber} (${it.busId})" } ?: "Select Original Bus",
                                color = if (selectedOriginalBus != null) Color(0xFF1E293B) else Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = if (selectedOriginalBus != null) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                        }
                    }
                    DropdownMenu(
                        expanded = originalDropdownOpen,
                        onDismissRequest = { originalDropdownOpen = false }
                    ) {
                        if (eligibleOriginalBuses.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No eligible buses available", fontSize = 13.sp) },
                                onClick = { originalDropdownOpen = false }
                            )
                        } else {
                            eligibleOriginalBuses.forEach { bus ->
                                DropdownMenuItem(
                                    text = { Text("${bus.busNumber} (${bus.busId})", fontSize = 13.sp) },
                                    onClick = {
                                        selectedOriginalBus = bus
                                        if (selectedReplacementBus?.busId == bus.busId) {
                                            selectedReplacementBus = null
                                        }
                                        selectedStopIds = emptySet()
                                        originalDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (matchedRoute != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Assigned Route: ${matchedRoute.name} (${matchedRoute.routeId})", fontSize = 11.sp, color = CollegePrimary, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 2: Select Replacement Bus
                Text("2. Replacement Bus", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { replacementDropdownOpen = true },
                        enabled = selectedOriginalBus != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedReplacementBus?.let { "${it.busNumber} (${it.busId})" }
                                    ?: if (selectedOriginalBus == null) "Select original bus first" else "Select Replacement Bus",
                                color = if (selectedReplacementBus != null) Color(0xFF1E293B) else Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = if (selectedReplacementBus != null) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                        }
                    }
                    DropdownMenu(
                        expanded = replacementDropdownOpen,
                        onDismissRequest = { replacementDropdownOpen = false }
                    ) {
                        if (eligibleReplacementBuses.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No replacement buses available", fontSize = 13.sp) },
                                onClick = { replacementDropdownOpen = false }
                            )
                        } else {
                            eligibleReplacementBuses.forEach { bus ->
                                DropdownMenuItem(
                                    text = { Text("${bus.busNumber} (${bus.busId})", fontSize = 13.sp) },
                                    onClick = {
                                        selectedReplacementBus = bus
                                        replacementDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 3: Scope Selection
                Text("3. Swap Scope", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scopeType == BusSwap.SCOPE_ALL_STOPS,
                        onClick = { scopeType = BusSwap.SCOPE_ALL_STOPS },
                        label = { Text("All Stops on Route", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = scopeType == BusSwap.SCOPE_SELECTED_STOPS,
                        onClick = { scopeType = BusSwap.SCOPE_SELECTED_STOPS },
                        label = { Text("Selected Stops Only", fontSize = 12.sp) }
                    )
                }

                if (scopeType == BusSwap.SCOPE_SELECTED_STOPS && matchedRoute != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select affected stops:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(4.dp))
                    matchedRoute.stops.forEach { stop ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedStopIds = if (selectedStopIds.contains(stop.stopId)) {
                                        selectedStopIds - stop.stopId
                                    } else {
                                        selectedStopIds + stop.stopId
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = selectedStopIds.contains(stop.stopId),
                                onCheckedChange = { checked ->
                                    selectedStopIds = if (checked) selectedStopIds + stop.stopId else selectedStopIds - stop.stopId
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stop.name, fontSize = 12.sp, color = Color(0xFF1E293B))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 4: Reason for Swap
                Text("4. Reason for Swap", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reasonPresets.take(3).forEach { preset ->
                            FilterChip(
                                selected = selectedReasonPreset == preset,
                                onClick = { selectedReasonPreset = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reasonPresets.drop(3).forEach { preset ->
                            FilterChip(
                                selected = selectedReasonPreset == preset,
                                onClick = { selectedReasonPreset = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    label = { Text("Additional notes (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Bus 12 operating in place of Bus 25 for morning run", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CollegePrimary,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 5: Summary Review Card
                if (selectedOriginalBus != null && selectedReplacementBus != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = CollegePrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Swap Review Summary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("• Original Bus: ${selectedOriginalBus?.busNumber} (${selectedOriginalBus?.busId})", fontSize = 12.sp, color = Color(0xFF334155))
                            Text("• Replacement Bus: ${selectedReplacementBus?.busNumber} (${selectedReplacementBus?.busId})", fontSize = 12.sp, color = StatusActiveGreen, fontWeight = FontWeight.Bold)
                            Text("• Route: ${matchedRoute?.name ?: "Current route"}", fontSize = 12.sp, color = Color(0xFF334155))
                            val finalReason = if (customReason.isNotBlank()) "$selectedReasonPreset - $customReason" else selectedReasonPreset
                            Text("• Reason: $finalReason", fontSize = 12.sp, color = Color(0xFF334155))
                            Text("• Live GPS: Automatically mapped to replacement bus", fontSize = 12.sp, color = CollegePrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val origBus = selectedOriginalBus
                            val replBus = selectedReplacementBus
                            if (origBus == null) {
                                errorMessage = "Please select the original bus to replace."
                                return@Button
                            }
                            if (replBus == null) {
                                errorMessage = "Please select the replacement bus."
                                return@Button
                            }
                            if (origBus.busId == replBus.busId) {
                                errorMessage = "Original bus and replacement bus cannot be identical."
                                return@Button
                            }
                            if (scopeType == BusSwap.SCOPE_SELECTED_STOPS && selectedStopIds.isEmpty()) {
                                errorMessage = "Please select at least one affected stop."
                                return@Button
                            }

                            errorMessage = null
                            isSubmitting = true

                            val finalReason = if (customReason.isNotBlank()) "$selectedReasonPreset - $customReason" else selectedReasonPreset
                            val effectiveDate = KolkataTimeFormatter.getTodayIso()

                            onCreateSwap(
                                origBus.busId,
                                replBus.busId,
                                origBus.routeId,
                                finalReason,
                                scopeType,
                                selectedStopIds.toList(),
                                effectiveDate
                            ) { success, error ->
                                isSubmitting = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = error ?: "Failed to create bus swap. Please retry."
                                }
                            }
                        },
                        enabled = !isSubmitting && selectedOriginalBus != null && selectedReplacementBus != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Swap", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
