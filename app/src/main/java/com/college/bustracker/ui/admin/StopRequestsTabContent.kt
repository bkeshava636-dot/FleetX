package com.college.bustracker.ui.admin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.StopRequest
import com.college.bustracker.ui.common.EmptyStateCard
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StopRequestsTabContent(
    requests: List<StopRequest>,
    routes: List<Route>,
    onApprove: (request: StopRequest, name: String, lat: Double, lng: Double, note: String?) -> Unit,
    onReject: (request: StopRequest, reason: String?) -> Unit,
    onEditDraft: (request: StopRequest, name: String, lat: Double, lng: Double, note: String?) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(StopRequest.STATUS_PENDING) }

    var requestToEdit by remember { mutableStateOf<StopRequest?>(null) }
    var requestToApprove by remember { mutableStateOf<StopRequest?>(null) }
    var requestToReject by remember { mutableStateOf<StopRequest?>(null) }

    val filteredList = remember(requests, selectedFilter) {
        requests.filter { it.status.equals(selectedFilter, ignoreCase = true) }
    }

    val pendingCount = remember(requests) { requests.count { it.status == StopRequest.STATUS_PENDING } }
    val approvedCount = remember(requests) { requests.count { it.status == StopRequest.STATUS_APPROVED } }
    val rejectedCount = remember(requests) { requests.count { it.status == StopRequest.STATUS_REJECTED } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == StopRequest.STATUS_PENDING,
                onClick = { selectedFilter = StopRequest.STATUS_PENDING },
                label = { Text("Pending ($pendingCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StatusWarningOrange.copy(alpha = 0.15f),
                    selectedLabelColor = StatusWarningOrange
                )
            )
            FilterChip(
                selected = selectedFilter == StopRequest.STATUS_APPROVED,
                onClick = { selectedFilter = StopRequest.STATUS_APPROVED },
                label = { Text("Approved ($approvedCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StatusActiveGreen.copy(alpha = 0.15f),
                    selectedLabelColor = StatusActiveGreen
                )
            )
            FilterChip(
                selected = selectedFilter == StopRequest.STATUS_REJECTED,
                onClick = { selectedFilter = StopRequest.STATUS_REJECTED },
                label = { Text("Rejected ($rejectedCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StatusDangerRed.copy(alpha = 0.15f),
                    selectedLabelColor = StatusDangerRed
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredList.isEmpty()) {
            EmptyStateCard(
                title = "No ${selectedFilter.lowercase().replaceFirstChar { it.uppercase() }} Requests",
                description = when (selectedFilter) {
                    StopRequest.STATUS_PENDING -> "No pending stop proposals submitted by drivers right now."
                    StopRequest.STATUS_APPROVED -> "No stop proposals have been approved yet."
                    else -> "No stop proposals have been rejected."
                },
                icon = Icons.Default.Place
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { it.requestId }) { req ->
                    val matchedRoute = routes.find { it.routeId.equals(req.routeId, ignoreCase = true) }
                    StopRequestCard(
                        request = req,
                        routeName = matchedRoute?.name ?: req.routeId,
                        onEdit = { requestToEdit = req },
                        onApprove = { requestToApprove = req },
                        onReject = { requestToReject = req }
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Edit Dialog
    val editing = requestToEdit
    if (editing != null) {
        EditStopRequestDialog(
            request = editing,
            onDismiss = { requestToEdit = null },
            onSave = { name, lat, lng, note ->
                onEditDraft(editing, name, lat, lng, note)
                requestToEdit = null
            }
        )
    }

    // 2. Approve Confirmation Dialog
    val approving = requestToApprove
    if (approving != null) {
        val matchedRoute = routes.find { it.routeId.equals(approving.routeId, ignoreCase = true) }
        ApproveStopRequestDialog(
            request = approving,
            routeName = matchedRoute?.name ?: approving.routeId,
            onDismiss = { requestToApprove = null },
            onConfirm = { finalName, finalLat, finalLng, note ->
                onApprove(approving, finalName, finalLat, finalLng, note)
                requestToApprove = null
            }
        )
    }

    // 3. Reject Reason Dialog
    val rejecting = requestToReject
    if (rejecting != null) {
        RejectStopRequestDialog(
            request = rejecting,
            onDismiss = { requestToReject = null },
            onConfirm = { reason ->
                onReject(rejecting, reason)
                requestToReject = null
            }
        )
    }
}

@Composable
fun StopRequestCard(
    request: StopRequest,
    routeName: String,
    onEdit: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dateStr = remember(request.createdAt) {
        if (request.createdAt > 0) {
            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(request.createdAt))
        } else "—"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Bus ID + Timestamp + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(CollegePrimary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = request.busId.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CollegePrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                val (badgeBg, badgeColor) = when (request.status) {
                    StopRequest.STATUS_APPROVED -> Pair(Color(0xFFDCFCE7), StatusActiveGreen)
                    StopRequest.STATUS_REJECTED -> Pair(Color(0xFFFEF2F2), StatusDangerRed)
                    else -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                }
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = request.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stop Name
            val displayName = request.finalStopName ?: request.proposedStopName
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = CollegePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Route & Driver Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Route, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = routeName,
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = request.driverName.ifBlank { request.driverId },
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Coordinates
            val displayLat = request.finalLatitude ?: request.latitude
            val displayLng = request.finalLongitude ?: request.longitude
            Text(
                text = "GPS: ${"%.5f".format(displayLat)}, ${"%.5f".format(displayLng)}",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            if (!request.adminNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Admin Note: ${request.adminNote}",
                    fontSize = 11.sp,
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Medium
                )
            }

            if (!request.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${request.rejectionReason}",
                    fontSize = 11.sp,
                    color = StatusDangerRed,
                    fontWeight = FontWeight.Medium
                )
            }

            // Actions for PENDING status
            if (request.status == StopRequest.STATUS_PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditStopRequestDialog(
    request: StopRequest,
    onDismiss: () -> Unit,
    onSave: (name: String, lat: Double, lng: Double, note: String?) -> Unit
) {
    var name by remember { mutableStateOf(request.finalStopName ?: request.proposedStopName) }
    var latStr by remember { mutableStateOf((request.finalLatitude ?: request.latitude).toString()) }
    var lngStr by remember { mutableStateOf((request.finalLongitude ?: request.longitude).toString()) }
    var note by remember { mutableStateOf(request.adminNote ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Stop Proposal", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Stop Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latStr,
                        onValueChange = { latStr = it; error = null },
                        label = { Text("Latitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngStr,
                        onValueChange = { lngStr = it; error = null },
                        label = { Text("Longitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Admin Note (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error ?: "", color = StatusDangerRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isBlank()) {
                        error = "Stop name cannot be blank."
                        return@Button
                    }
                    val lat = latStr.toDoubleOrNull()
                    val lng = lngStr.toDoubleOrNull()
                    if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
                        error = "Valid latitude & longitude required."
                        return@Button
                    }
                    onSave(cleanName, lat, lng, note.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ApproveStopRequestDialog(
    request: StopRequest,
    routeName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, lat: Double, lng: Double, note: String?) -> Unit
) {
    var finalName by remember { mutableStateOf(request.finalStopName ?: request.proposedStopName) }
    var adminNote by remember { mutableStateOf(request.adminNote ?: "") }
    val finalLat = request.finalLatitude ?: request.latitude
    val finalLng = request.finalLongitude ?: request.longitude

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approve Bus Stop", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This stop will be appended to Route: $routeName as an official stop.",
                    fontSize = 13.sp,
                    color = Color(0xFF334155)
                )
                OutlinedTextField(
                    value = finalName,
                    onValueChange = { finalName = it },
                    label = { Text("Confirmed Stop Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Coordinates: ${"%.5f".format(finalLat)}, ${"%.5f".format(finalLng)}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = adminNote,
                    onValueChange = { adminNote = it },
                    label = { Text("Optional Admin Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (finalName.trim().isNotBlank()) {
                        onConfirm(finalName.trim(), finalLat, finalLng, adminNote.ifBlank { null })
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen)
            ) {
                Text("Confirm & Add to Route")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RejectStopRequestDialog(
    request: StopRequest,
    onDismiss: () -> Unit,
    onConfirm: (reason: String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Stop Proposal", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Are you sure you want to reject this stop proposal from ${request.driverName.ifBlank { request.driverId }}?",
                    fontSize = 13.sp,
                    color = Color(0xFF334155)
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Rejection Reason (Optional)") },
                    placeholder = { Text("e.g. Too close to Stop #2, traffic hazard") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
            ) {
                Text("Reject Proposal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
