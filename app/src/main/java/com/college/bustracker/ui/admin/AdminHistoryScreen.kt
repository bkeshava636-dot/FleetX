package com.college.bustracker.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.data.model.BusSwapHistory
import com.college.bustracker.data.model.DriverReplacementHistory
import com.college.bustracker.data.model.HistoryAuditLog
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.KolkataTimeFormatter

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AdminHistoryScreen(
    viewModel: AdminViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val busSwapHistory by viewModel.busSwapHistory.collectAsState()
    val driverReplacementHistory by viewModel.driverReplacementHistory.collectAsState()
    val auditLogs by viewModel.historyAuditLogs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTimeFilter by remember { mutableStateOf("ALL") } // ALL, TODAY, 7D, 30D
    var selectedStatusFilter by remember { mutableStateOf("ALL") }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var clearTargetType by remember { mutableStateOf("BUS_SWAP") } // BUS_SWAP or DRIVER_REPLACEMENT
    var clearStage by remember { mutableIntStateOf(1) } // 1: warning, 2: final confirm
    var isClearing by remember { mutableStateOf(false) }
    var clearResultMsg by remember { mutableStateOf<String?>(null) }

    val now = System.currentTimeMillis()
    val msPerDay = 86_400_000L

    // Filter Bus Swaps
    val filteredBusSwaps = remember(busSwapHistory, searchQuery, selectedTimeFilter, selectedStatusFilter) {
        busSwapHistory.filter { item ->
            val matchSearch = searchQuery.isBlank() ||
                    item.originalBusId.contains(searchQuery, ignoreCase = true) ||
                    item.replacementBusId.contains(searchQuery, ignoreCase = true) ||
                    item.reason.contains(searchQuery, ignoreCase = true) ||
                    item.changedByName.contains(searchQuery, ignoreCase = true)

            val matchTime = when (selectedTimeFilter) {
                "TODAY" -> (now - item.createdAt) <= msPerDay
                "7D" -> (now - item.createdAt) <= (7 * msPerDay)
                "30D" -> (now - item.createdAt) <= (30 * msPerDay)
                else -> true
            }

            val matchStatus = when (selectedStatusFilter) {
                "ACTIVE" -> item.status.equals("ACTIVE", ignoreCase = true)
                "CANCELLED" -> item.status.equals("CANCELLED", ignoreCase = true)
                "COMPLETED" -> item.status.equals("COMPLETED", ignoreCase = true) || item.status.equals("RESOLVED", ignoreCase = true)
                else -> true
            }

            matchSearch && matchTime && matchStatus
        }
    }

    // Filter Driver Replacements
    val filteredDriverReplacements = remember(driverReplacementHistory, searchQuery, selectedTimeFilter, selectedStatusFilter) {
        driverReplacementHistory.filter { item ->
            val matchSearch = searchQuery.isBlank() ||
                    item.previousDriverName.contains(searchQuery, ignoreCase = true) ||
                    item.replacementDriverName.contains(searchQuery, ignoreCase = true) ||
                    item.busId.contains(searchQuery, ignoreCase = true) ||
                    item.reason.contains(searchQuery, ignoreCase = true) ||
                    item.changedByName.contains(searchQuery, ignoreCase = true)

            val matchTime = when (selectedTimeFilter) {
                "TODAY" -> (now - item.createdAt) <= msPerDay
                "7D" -> (now - item.createdAt) <= (7 * msPerDay)
                "30D" -> (now - item.createdAt) <= (30 * msPerDay)
                else -> true
            }

            val matchStatus = when (selectedStatusFilter) {
                "ACTIVE" -> item.status.equals("ACTIVE", ignoreCase = true)
                "CANCELLED" -> item.status.equals("CANCELLED", ignoreCase = true)
                "COMPLETED" -> item.status.equals("COMPLETED", ignoreCase = true) || item.status.equals("EXPIRED", ignoreCase = true)
                else -> true
            }

            matchSearch && matchTime && matchStatus
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // Top Header
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Operations History", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("Asia/Kolkata Server Time", fontSize = 12.sp, color = Color(0xFF64748B))
                    }

                    OutlinedButton(
                        onClick = {
                            clearTargetType = if (selectedTab == 1) "DRIVER_REPLACEMENT" else "BUS_SWAP"
                            clearStage = 1
                            clearResultMsg = null
                            showClearHistoryDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear History", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = CollegePrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Bus Swaps", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(if (selectedTab == 0) CollegePrimary else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${busSwapHistory.size}", fontSize = 10.sp, color = if (selectedTab == 0) Color.White else Color(0xFF475569))
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Driver Replacements", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(if (selectedTab == 1) CollegePrimary else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${driverReplacementHistory.size}", fontSize = 10.sp, color = if (selectedTab == 1) Color.White else Color(0xFF475569))
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Audit Logs", fontSize = 13.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(if (selectedTab == 2) CollegePrimary else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${auditLogs.size}", fontSize = 10.sp, color = if (selectedTab == 2) Color.White else Color(0xFF475569))
                                }
                            }
                        }
                    )
                }
            }
        }

        // Filters (For Swaps and Replacements)
        if (selectedTab != 2) {
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by bus number, driver, or reason...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CollegePrimary,
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Filter Chips
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "All Time",
                            "TODAY" to "Today",
                            "7D" to "Last 7 Days",
                            "30D" to "Last 30 Days"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = selectedTimeFilter == key,
                                onClick = { selectedTimeFilter = key },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        listOf("ALL" to "All Status", "ACTIVE" to "Active", "CANCELLED" to "Cancelled").forEach { (key, label) ->
                            FilterChip(
                                selected = selectedStatusFilter == key,
                                onClick = { selectedStatusFilter = key },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Content
        when (selectedTab) {
            0 -> {
                if (filteredBusSwaps.isEmpty()) {
                    EmptyHistoryView(message = "No bus swap history matching criteria.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredBusSwaps, key = { it.historyId }) { item ->
                            BusSwapHistoryCard(item)
                        }
                    }
                }
            }
            1 -> {
                if (filteredDriverReplacements.isEmpty()) {
                    EmptyHistoryView(message = "No driver replacement history matching criteria.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredDriverReplacements, key = { it.historyId }) { item ->
                            DriverReplacementHistoryCard(item)
                        }
                    }
                }
            }
            2 -> {
                if (auditLogs.isEmpty()) {
                    EmptyHistoryView(message = "No clear history audit logs recorded.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(auditLogs, key = { it.auditId }) { log ->
                            AuditLogCard(log)
                        }
                    }
                }
            }
        }
    }

    // Two-stage Clear History Confirmation Dialog
    if (showClearHistoryDialog) {
        val targetName = if (clearTargetType == "BUS_SWAP") "Bus Swap History" else "Driver Replacement History"
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearHistoryDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusDangerRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    if (clearStage == 1) "Clear $targetName?" else "Confirm Permanent Archival",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column {
                    if (clearResultMsg != null) {
                        Text(clearResultMsg ?: "", color = CollegePrimary, fontWeight = FontWeight.Bold)
                    } else if (clearStage == 1) {
                        Text("You are requesting to clear $targetName.", fontSize = 13.sp, color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Safety Guarantees:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = StatusDangerRed)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• Active bus swaps will NEVER be cancelled.", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                                Text("• Active driver assignments & GPS tracking will NEVER be modified.", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                                Text("• Bus routes, driver accounts, and student data are 100% untouched.", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                                Text("• Only archived / completed / cancelled records will be cleared.", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Do you want to proceed to the confirmation step?", fontSize = 12.sp, color = Color(0xFF64748B))
                    } else {
                        Text("FINAL CONFIRMATION", fontWeight = FontWeight.Bold, color = StatusDangerRed, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "This action will archive historical records and record an unalterable entry in the Admin Audit Log.",
                            fontSize = 12.sp,
                            color = Color(0xFF334155)
                        )
                    }
                }
            },
            confirmButton = {
                if (clearResultMsg != null) {
                    Button(onClick = { showClearHistoryDialog = false }) {
                        Text("Close")
                    }
                } else if (clearStage == 1) {
                    Button(
                        onClick = { clearStage = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                    ) {
                        Text("Proceed")
                    }
                } else {
                    Button(
                        onClick = {
                            isClearing = true
                            if (clearTargetType == "BUS_SWAP") {
                                viewModel.clearBusSwapHistory { res ->
                                    isClearing = false
                                    res.fold(
                                        onSuccess = { count -> clearResultMsg = "Successfully cleared $count bus swap history records." },
                                        onFailure = { err -> clearResultMsg = "Failed: ${err.message}" }
                                    )
                                }
                            } else {
                                viewModel.clearDriverReplacementHistory { res ->
                                    isClearing = false
                                    res.fold(
                                        onSuccess = { count -> clearResultMsg = "Successfully cleared $count driver replacement history records." },
                                        onFailure = { err -> clearResultMsg = "Failed: ${err.message}" }
                                    )
                                }
                            }
                        },
                        enabled = !isClearing,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Clear Records Now")
                        }
                    }
                }
            },
            dismissButton = {
                if (clearResultMsg == null) {
                    TextButton(onClick = { showClearHistoryDialog = false }, enabled = !isClearing) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun BusSwapHistoryCard(item: BusSwapHistory) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Bus mapping row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(item.originalBusId, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CollegePrimary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(item.replacementBusId, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = StatusActiveGreen)
                    }
                }

                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reason
            Text("Reason: ${item.reason}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))

            Spacer(modifier = Modifier.height(6.dp))

            // Timestamps (Asia/Kolkata)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF64748B))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = KolkataTimeFormatter.formatFullDateTime(item.createdAt),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Changed by: ${item.changedByName}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                if (item.effectiveUntil > 0L) {
                    Text("End: ${KolkataTimeFormatter.formatTimeOnly(item.effectiveUntil)}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun DriverReplacementHistoryCard(item: DriverReplacementHistory) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(CollegePrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(item.busId, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CollegePrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${item.previousDriverName} → ${item.replacementDriverName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B)
                    )
                }

                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Reason: ${item.reason}", fontSize = 12.sp, color = Color(0xFF334155))

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF64748B))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = KolkataTimeFormatter.formatFullDateTime(item.createdAt),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Changed by: ${item.changedByName}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                if (item.effectiveUntil > 0L) {
                    Text("Until: ${KolkataTimeFormatter.formatDateOnly(item.effectiveUntil)}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun AuditLogCard(log: HistoryAuditLog) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CollegePrimary)
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${log.clearedCount} cleared", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CollegePrimary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Admin: ${log.adminName} (${log.adminUid})", fontSize = 11.sp, color = Color(0xFF475569))
            Text("Date: ${log.dateFormatted} at ${log.timeFormatted} (Asia/Kolkata)", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, fg) = when (status.uppercase()) {
        "ACTIVE" -> Color(0xFFDCFCE7) to StatusActiveGreen
        "CANCELLED" -> Color(0xFFFEE2E2) to StatusDangerRed
        else -> Color(0xFFF1F5F9) to Color(0xFF64748B)
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun EmptyHistoryView(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFCBD5E1))
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, fontSize = 13.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
        }
    }
}
