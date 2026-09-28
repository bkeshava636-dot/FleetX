package com.college.bustracker.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.TemporaryAssignment
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.college.bustracker.util.TemporaryDurationHelper

@Composable
fun AdminOperationsScreen(
    currentSubTab: String?,
    onSelectSubTab: (String?) -> Unit,
    buses: List<Bus>,
    routes: List<Route>,
    allDrivers: List<UserProfile>,
    temporaryAssignments: List<TemporaryAssignment>,
    swaps: List<BusSwap>,
    onAddBusClick: () -> Unit,
    onToggleBusActive: (String, Boolean) -> Unit,
    onAssignDriverClick: (Bus) -> Unit,
    onUnassignDriverClick: (Bus) -> Unit,
    onChangeRouteClick: (Bus) -> Unit,
    onDeleteBusClick: (Bus) -> Unit,
    onAddDriverClick: () -> Unit,
    onEditDriverClick: (UserProfile) -> Unit,
    onToggleDriverActive: (UserProfile, Boolean) -> Unit,
    onDeleteDriverClick: (UserProfile) -> Unit,
    onResetDriverPasswordClick: (UserProfile) -> Unit,
    onAddReplacementClick: () -> Unit,
    onCancelReplacementClick: (TemporaryAssignment) -> Unit,
    onAddSwapClick: () -> Unit,
    onCancelSwapClick: (BusSwap) -> Unit
) {
    when (currentSubTab) {
        "buses" -> {
            BusesSubScreen(
                buses = buses,
                routes = routes,
                allDrivers = allDrivers,
                onBack = { onSelectSubTab(null) },
                onAddBusClick = onAddBusClick,
                onToggleBusActive = onToggleBusActive,
                onAssignDriverClick = onAssignDriverClick,
                onUnassignDriverClick = onUnassignDriverClick,
                onChangeRouteClick = onChangeRouteClick,
                onDeleteBusClick = onDeleteBusClick
            )
        }
        "drivers" -> {
            DriversSubScreen(
                drivers = allDrivers,
                buses = buses,
                onBack = { onSelectSubTab(null) },
                onAddDriverClick = onAddDriverClick,
                onEditDriverClick = onEditDriverClick,
                onToggleDriverActive = onToggleDriverActive,
                onDeleteDriverClick = onDeleteDriverClick,
                onResetPasswordClick = onResetDriverPasswordClick,
                onAssignBusClick = { driver ->
                    val bus = buses.find { it.driverId == driver.uid } ?: buses.firstOrNull()
                    if (bus != null) onAssignDriverClick(bus)
                }
            )
        }
        "replacements" -> {
            ReplacementsSubScreen(
                replacements = temporaryAssignments,
                onBack = { onSelectSubTab(null) },
                onAddReplacementClick = onAddReplacementClick,
                onCancelReplacementClick = onCancelReplacementClick
            )
        }
        "swaps" -> {
            BusSwapsSubScreen(
                swaps = swaps,
                onBack = { onSelectSubTab(null) },
                onAddSwapClick = onAddSwapClick,
                onCancelSwapClick = onCancelSwapClick
            )
        }
        else -> {
            OperationsHubScreen(
                busesCount = buses.size,
                driversCount = allDrivers.size,
                activeReplacementsCount = temporaryAssignments.count { it.isActive(System.currentTimeMillis()) },
                activeSwapsCount = swaps.count { it.status == "ACTIVE" },
                onNavigate = onSelectSubTab
            )
        }
    }
}

/**
 * Operations Hub main screen.
 */
@Composable
private fun OperationsHubScreen(
    busesCount: Int,
    driversCount: Int,
    activeReplacementsCount: Int,
    activeSwapsCount: Int,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
            .padding(16.dp)
    ) {
        AdminSectionTitle(title = "OPERATIONS")

        AdminMenuRow(
            icon = Icons.Default.DirectionsBus,
            title = "Buses",
            subtitle = "Manage $busesCount buses and driver assignments",
            onClick = { onNavigate("buses") }
        )

        AdminMenuRow(
            icon = Icons.Default.Person,
            title = "Drivers",
            subtitle = "Manage $driversCount drivers and bus assignments",
            onClick = { onNavigate("drivers") }
        )

        AdminMenuRow(
            icon = Icons.Default.Cached,
            title = "Replacements",
            subtitle = "Temporary driver replacements ($activeReplacementsCount active)",
            badgeCount = activeReplacementsCount,
            badgeColor = Color(0xFF2563EB),
            onClick = { onNavigate("replacements") }
        )

        AdminMenuRow(
            icon = Icons.Default.SwapHoriz,
            title = "Bus Swaps",
            subtitle = "Manage temporary bus swaps ($activeSwapsCount active)",
            badgeCount = activeSwapsCount,
            badgeColor = StatusWarningOrange,
            onClick = { onNavigate("swaps") }
        )
    }
}

/**
 * Buses Sub-screen: Search, filters, and compact rows with overflow menu.
 */
@Composable
private fun BusesSubScreen(
    buses: List<Bus>,
    routes: List<Route>,
    allDrivers: List<UserProfile>,
    onBack: () -> Unit,
    onAddBusClick: () -> Unit,
    onToggleBusActive: (String, Boolean) -> Unit,
    onAssignDriverClick: (Bus) -> Unit,
    onUnassignDriverClick: (Bus) -> Unit,
    onChangeRouteClick: (Bus) -> Unit,
    onDeleteBusClick: (Bus) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredBuses = remember(buses, searchQuery, selectedFilter) {
        buses.filter { bus ->
            val matchesSearch = searchQuery.isBlank() ||
                    bus.busNumber.contains(searchQuery, ignoreCase = true) ||
                    bus.busId.contains(searchQuery, ignoreCase = true) ||
                    (bus.driverName ?: "").contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "Active" -> bus.active
                "Unassigned" -> bus.driverId.isNullOrBlank()
                else -> true
            }
            matchesSearch && matchesFilter
        }.sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
    ) {
        AdminSubScreenHeader(
            title = "Buses",
            subtitle = "${filteredBuses.size} of ${buses.size} buses",
            onBack = onBack,
            actions = {
                Button(
                    onClick = onAddBusClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Bus", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AdminSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search buses by number, route, or driver..."
            )

            Spacer(modifier = Modifier.height(8.dp))

            AdminFilterChips(
                options = listOf("All", "Active", "Unassigned"),
                selectedOption = selectedFilter,
                onOptionSelected = { selectedFilter = it }
            )
        }

        if (filteredBuses.isEmpty()) {
            AdminEmptyPlaceholder(
                icon = Icons.Default.DirectionsBus,
                title = "No buses found",
                subtitle = if (searchQuery.isNotBlank()) "Try another search term" else "Add a bus to get started"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredBuses, key = { it.busId }) { bus ->
                    val routeName = routes.find { it.routeId.equals(bus.routeId, ignoreCase = true) }?.name ?: bus.routeId
                    val driverName = bus.driverName ?: allDrivers.find { it.uid == bus.driverId }?.name ?: "Unassigned"

                    BusCompactRow(
                        bus = bus,
                        routeName = routeName,
                        driverName = driverName,
                        onToggleActive = { onToggleBusActive(bus.busId, !bus.active) },
                        onAssignDriver = { onAssignDriverClick(bus) },
                        onUnassignDriver = { onUnassignDriverClick(bus) },
                        onChangeRoute = { onChangeRouteClick(bus) },
                        onDelete = { onDeleteBusClick(bus) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun BusCompactRow(
    bus: Bus,
    routeName: String,
    driverName: String,
    onToggleActive: () -> Unit,
    onAssignDriver: () -> Unit,
    onUnassignDriver: () -> Unit,
    onChangeRoute: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bus.busNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    AdminStatusBadge(
                        text = if (bus.active) "Active" else "Inactive",
                        isActive = bus.active
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Route: $routeName",
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Driver: $driverName",
                    fontSize = 12.sp,
                    color = if (driverName != "Unassigned") Color(0xFF0F172A) else Color(0xFF94A3B8),
                    fontWeight = if (driverName != "Unassigned") FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Bus Actions",
                        tint = Color(0xFF64748B)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Assign Driver") },
                        onClick = {
                            showMenu = false
                            onAssignDriver()
                        }
                    )
                    if (!bus.driverId.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("Unassign Driver") },
                            onClick = {
                                showMenu = false
                                onUnassignDriver()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Change Route") },
                        onClick = {
                            showMenu = false
                            onChangeRoute()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (bus.active) "Set Inactive" else "Set Active") },
                        onClick = {
                            showMenu = false
                            onToggleActive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Bus", color = StatusDangerRed) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Drivers Sub-screen: Search, filters, and compact rows with overflow menu.
 */
@Composable
private fun DriversSubScreen(
    drivers: List<UserProfile>,
    buses: List<Bus>,
    onBack: () -> Unit,
    onAddDriverClick: () -> Unit,
    onEditDriverClick: (UserProfile) -> Unit,
    onToggleDriverActive: (UserProfile, Boolean) -> Unit,
    onDeleteDriverClick: (UserProfile) -> Unit,
    onResetPasswordClick: (UserProfile) -> Unit,
    onAssignBusClick: (UserProfile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredDrivers = remember(drivers, buses, searchQuery, selectedFilter) {
        drivers.filter { it.role == UserRole.DRIVER }.filter { driver ->
            val matchesSearch = searchQuery.isBlank() ||
                    driver.name.contains(searchQuery, ignoreCase = true) ||
                    driver.customId.contains(searchQuery, ignoreCase = true) ||
                    (driver.phoneNumber ?: "").contains(searchQuery)
            val assignedBus = buses.find { it.driverId == driver.uid }
            val matchesFilter = when (selectedFilter) {
                "Assigned" -> assignedBus != null
                "Unassigned" -> assignedBus == null
                "Inactive" -> !driver.active
                else -> true
            }
            matchesSearch && matchesFilter
        }.sortedBy { it.name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
    ) {
        AdminSubScreenHeader(
            title = "Drivers",
            subtitle = "${filteredDrivers.size} of ${drivers.count { it.role == UserRole.DRIVER }} drivers",
            onBack = onBack,
            actions = {
                Button(
                    onClick = onAddDriverClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Driver", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AdminSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search drivers by name, staff ID, or phone..."
            )

            Spacer(modifier = Modifier.height(8.dp))

            AdminFilterChips(
                options = listOf("All", "Assigned", "Unassigned", "Inactive"),
                selectedOption = selectedFilter,
                onOptionSelected = { selectedFilter = it }
            )
        }

        if (filteredDrivers.isEmpty()) {
            AdminEmptyPlaceholder(
                icon = Icons.Default.Person,
                title = "No drivers found",
                subtitle = if (searchQuery.isNotBlank()) "Try another search term" else "Add a driver to get started"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredDrivers, key = { it.uid }) { driver ->
                    val assignedBus = buses.find { it.driverId == driver.uid }
                    val busLabel = assignedBus?.busNumber ?: "No assigned bus"

                    DriverCompactRow(
                        driver = driver,
                        assignedBusLabel = busLabel,
                        onEdit = { onEditDriverClick(driver) },
                        onToggleActive = { onToggleDriverActive(driver, !driver.active) },
                        onAssignBus = { onAssignBusClick(driver) },
                        onResetPassword = { onResetPasswordClick(driver) },
                        onDelete = { onDeleteDriverClick(driver) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DriverCompactRow(
    driver: UserProfile,
    assignedBusLabel: String,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onAssignBus: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = driver.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    AdminStatusBadge(
                        text = if (driver.active) "Active" else "Inactive",
                        isActive = driver.active
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "ID: ${driver.customId.ifBlank { "N/A" }} • $assignedBusLabel",
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!driver.phoneNumber.isNullOrBlank()) {
                    Text(
                        text = "Phone: ${driver.phoneNumber}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Driver Actions",
                        tint = Color(0xFF64748B)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Details") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Assign / Change Bus") },
                        onClick = {
                            showMenu = false
                            onAssignBus()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reset Password") },
                        onClick = {
                            showMenu = false
                            onResetPassword()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (driver.active) "Deactivate Driver" else "Activate Driver") },
                        onClick = {
                            showMenu = false
                            onToggleActive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Driver", color = StatusDangerRed) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Replacements Sub-screen: Clean list of temporary replacements with status badges & overflow menu.
 */
@Composable
private fun ReplacementsSubScreen(
    replacements: List<TemporaryAssignment>,
    onBack: () -> Unit,
    onAddReplacementClick: () -> Unit,
    onCancelReplacementClick: (TemporaryAssignment) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val now = System.currentTimeMillis()

    val filteredReplacements = remember(replacements, selectedFilter) {
        replacements.filter { r ->
            when (selectedFilter) {
                "Active" -> r.isActive(now)
                "Upcoming" -> r.isUpcoming(now)
                "Cancelled" -> r.isCancelled()
                else -> true
            }
        }.sortedByDescending { it.startAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
    ) {
        AdminSubScreenHeader(
            title = "Temporary Replacements",
            subtitle = "${filteredReplacements.size} replacements",
            onBack = onBack,
            actions = {
                Button(
                    onClick = onAddReplacementClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AdminFilterChips(
                options = listOf("All", "Active", "Upcoming", "Cancelled"),
                selectedOption = selectedFilter,
                onOptionSelected = { selectedFilter = it }
            )
        }

        if (filteredReplacements.isEmpty()) {
            AdminEmptyPlaceholder(
                icon = Icons.Default.Cached,
                title = "No replacements",
                subtitle = "No temporary driver replacements match this filter."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredReplacements, key = { it.assignmentId }) { assignment ->
                    ReplacementCompactRow(
                        assignment = assignment,
                        now = now,
                        onCancel = { onCancelReplacementClick(assignment) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ReplacementCompactRow(
    assignment: TemporaryAssignment,
    now: Long,
    onCancel: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val status = assignment.getStatus()
    val isCancelled = assignment.isCancelled()
    val isActive = assignment.isActive(now)

    val badgeColor = when {
        isCancelled -> Color(0xFF64748B)
        isActive -> StatusActiveGreen
        else -> Color(0xFF2563EB)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = assignment.busId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "● $status",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${assignment.permanentDriverName} → ${assignment.replacementDriverName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )

                val windowStr = TemporaryDurationHelper.formatCancelWindow(assignment.startAt, assignment.endAt)
                Text(
                    text = windowStr,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            if (!isCancelled) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Replacement Actions",
                            tint = Color(0xFF64748B)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cancel Replacement", color = StatusDangerRed) },
                            onClick = {
                                showMenu = false
                                onCancel()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bus Swaps Sub-screen.
 */
@Composable
private fun BusSwapsSubScreen(
    swaps: List<BusSwap>,
    onBack: () -> Unit,
    onAddSwapClick: () -> Unit,
    onCancelSwapClick: (BusSwap) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
    ) {
        AdminSubScreenHeader(
            title = "Bus Swaps",
            subtitle = "${swaps.size} total swaps",
            onBack = onBack,
            actions = {
                Button(
                    onClick = onAddSwapClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Swap", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        if (swaps.isEmpty()) {
            AdminEmptyPlaceholder(
                icon = Icons.Default.SwapHoriz,
                title = "No bus swaps",
                subtitle = "Active and historical bus swaps will appear here."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(swaps, key = { it.swapId }) { swap ->
                    BusSwapCompactRow(
                        swap = swap,
                        onCancel = { onCancelSwapClick(swap) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun BusSwapCompactRow(
    swap: BusSwap,
    onCancel: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isActive = swap.status == "ACTIVE"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${swap.originalBusId} → ${swap.replacementBusId}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    AdminStatusBadge(
                        text = swap.status,
                        isActive = isActive
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Reason: ${swap.message.ifBlank { "Maintenance / operational swap" }}",
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isActive) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Swap Actions",
                            tint = Color(0xFF64748B)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cancel Swap", color = StatusDangerRed) },
                            onClick = {
                                showMenu = false
                                onCancel()
                            }
                        )
                    }
                }
            }
        }
    }
}
