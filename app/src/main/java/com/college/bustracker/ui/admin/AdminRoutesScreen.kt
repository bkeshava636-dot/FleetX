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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.Route
import com.college.bustracker.data.model.StopRequest
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange

@Composable
fun AdminRoutesScreen(
    currentSubTab: String?,
    onSelectSubTab: (String?) -> Unit,
    routes: List<Route>,
    buses: List<Bus>,
    stopRequests: List<StopRequest>,
    pendingStopRequestsCount: Int,
    selectedRouteForStops: Route?,
    onSelectRouteForStops: (Route?) -> Unit,
    onAddRouteClick: () -> Unit,
    onEditRouteClick: (Route) -> Unit,
    onDeleteRouteClick: (Route) -> Unit,
    onAddStopClick: (Route) -> Unit,
    onOpenMapStopCreator: (Route) -> Unit,
    onEditStopClick: (Route, BusStop) -> Unit,
    onMoveStopUp: (Route, BusStop) -> Unit,
    onMoveStopDown: (Route, BusStop) -> Unit,
    onDeleteStopClick: (Route, BusStop) -> Unit,
    onApproveStopRequest: (StopRequest, String, Double, Double, String?) -> Unit,
    onRejectStopRequest: (StopRequest, String?) -> Unit,
    onEditStopRequestDraft: (StopRequest, String, Double, Double, String?) -> Unit,
    onUploadSchedulePdf: ((Route) -> Unit)? = null,
    onViewSchedulePdf: ((Route) -> Unit)? = null,
    onRemoveSchedulePdf: ((Route) -> Unit)? = null,
    onNavigateToCalendar: (() -> Unit)? = null
) {
    when (currentSubTab) {
        "routes" -> {
            RoutesSubScreen(
                routes = routes,
                buses = buses,
                onBack = { onSelectSubTab(null) },
                onAddRouteClick = onAddRouteClick,
                onEditRouteClick = onEditRouteClick,
                onManageStopsClick = { route ->
                    onSelectRouteForStops(route)
                    onSelectSubTab("stops")
                },
                onDeleteRouteClick = onDeleteRouteClick,
                onUploadSchedulePdf = onUploadSchedulePdf,
                onViewSchedulePdf = onViewSchedulePdf,
                onRemoveSchedulePdf = onRemoveSchedulePdf
            )
        }
        "stops" -> {
            StopsSubScreen(
                routes = routes,
                selectedRoute = selectedRouteForStops ?: routes.firstOrNull(),
                onSelectRoute = onSelectRouteForStops,
                onBack = { onSelectSubTab(null) },
                onAddStopClick = onAddStopClick,
                onOpenMapCreator = onOpenMapStopCreator,
                onEditStopClick = onEditStopClick,
                onMoveStopUp = onMoveStopUp,
                onMoveStopDown = onMoveStopDown,
                onDeleteStopClick = onDeleteStopClick
            )
        }
        "stop_requests" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CollegeSurface)
            ) {
                AdminSubScreenHeader(
                    title = "Stop Requests",
                    subtitle = "$pendingStopRequestsCount pending review",
                    onBack = { onSelectSubTab(null) }
                )
                StopRequestsTabContent(
                    requests = stopRequests,
                    routes = routes,
                    onApprove = onApproveStopRequest,
                    onReject = onRejectStopRequest,
                    onEditDraft = onEditStopRequestDraft
                )
            }
        }
        else -> {
            RoutesHubScreen(
                routesCount = routes.size,
                totalStopsCount = routes.sumOf { it.stops.size },
                pendingStopRequestsCount = pendingStopRequestsCount,
                onNavigate = onSelectSubTab,
                onNavigateToCalendar = onNavigateToCalendar
            )
        }
    }
}

/**
 * Routes Hub main screen.
 */
@Composable
private fun RoutesHubScreen(
    routesCount: Int,
    totalStopsCount: Int,
    pendingStopRequestsCount: Int,
    onNavigate: (String) -> Unit,
    onNavigateToCalendar: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
            .padding(16.dp)
    ) {
        AdminSectionTitle(title = "ROUTES & STOPS")

        AdminMenuRow(
            icon = Icons.Default.Route,
            title = "Routes",
            subtitle = "Manage $routesCount transit routes and assigned buses",
            onClick = { onNavigate("routes") }
        )

        AdminMenuRow(
            icon = Icons.Default.Place,
            title = "Stops",
            subtitle = "Manage $totalStopsCount stops and stop ordering",
            onClick = { onNavigate("stops") }
        )

        AdminMenuRow(
            icon = Icons.Default.Place,
            title = "Stop Requests",
            subtitle = "Review driver-proposed stops ($pendingStopRequestsCount pending)",
            badgeCount = pendingStopRequestsCount,
            badgeColor = StatusWarningOrange,
            onClick = { onNavigate("stop_requests") }
        )

        AdminMenuRow(
            icon = Icons.Default.Description,
            title = "All Routes Timetable (PDF)",
            subtitle = "Manage college-wide timetable PDF for all students & drivers",
            onClick = { onNavigateToCalendar?.invoke() }
        )
    }
}

/**
 * Routes List Sub-screen.
 */
@Composable
private fun RoutesSubScreen(
    routes: List<Route>,
    buses: List<Bus>,
    onBack: () -> Unit,
    onAddRouteClick: () -> Unit,
    onEditRouteClick: (Route) -> Unit,
    onManageStopsClick: (Route) -> Unit,
    onDeleteRouteClick: (Route) -> Unit,
    onUploadSchedulePdf: ((Route) -> Unit)? = null,
    onViewSchedulePdf: ((Route) -> Unit)? = null,
    onRemoveSchedulePdf: ((Route) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredRoutes = remember(routes, searchQuery) {
        routes.filter { route ->
            searchQuery.isBlank() ||
                    route.name.contains(searchQuery, ignoreCase = true) ||
                    route.routeId.contains(searchQuery, ignoreCase = true)
        }.sortedBy { it.name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
    ) {
        AdminSubScreenHeader(
            title = "Routes",
            subtitle = "${filteredRoutes.size} routes",
            onBack = onBack,
            actions = {
                Button(
                    onClick = onAddRouteClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Route", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AdminSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search routes..."
            )
        }

        if (filteredRoutes.isEmpty()) {
            AdminEmptyPlaceholder(
                icon = Icons.Default.Route,
                title = "No routes found",
                subtitle = if (searchQuery.isNotBlank()) "Try another search term" else "Add a route to get started"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredRoutes, key = { it.routeId }) { route ->
                    val assignedBuses = buses.filter { it.routeId.equals(route.routeId, ignoreCase = true) }
                    val busListStr = if (assignedBuses.isNotEmpty()) {
                        assignedBuses.joinToString(", ") { it.busNumber }
                    } else "No buses assigned"

                    RouteCompactRow(
                        route = route,
                        busListStr = busListStr,
                        stopsCount = route.stops.size,
                        onEdit = { onEditRouteClick(route) },
                        onManageStops = { onManageStopsClick(route) },
                        onDelete = { onDeleteRouteClick(route) },
                        onUploadPdf = { onUploadSchedulePdf?.invoke(route) },
                        onViewPdf = { onViewSchedulePdf?.invoke(route) },
                        onRemovePdf = { onRemoveSchedulePdf?.invoke(route) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RouteCompactRow(
    route: Route,
    busListStr: String,
    stopsCount: Int,
    onEdit: () -> Unit,
    onManageStops: () -> Unit,
    onDelete: () -> Unit,
    onUploadPdf: (() -> Unit)? = null,
    onViewPdf: (() -> Unit)? = null,
    onRemovePdf: (() -> Unit)? = null
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
                Text(
                    text = route.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "ID: ${route.routeId} • $stopsCount Stops",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )

                Text(
                    text = "Buses: $busListStr",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!route.schedulePdfUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEFF6FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = route.schedulePdfName ?: "Timetable PDF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D4ED8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Route Actions",
                        tint = Color(0xFF64748B)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Manage Stops ($stopsCount)") },
                        onClick = {
                            showMenu = false
                            onManageStops()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Route") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    if (!route.schedulePdfUrl.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("View Timetable (PDF)") },
                            onClick = {
                                showMenu = false
                                onViewPdf?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Replace Timetable (PDF)") },
                            onClick = {
                                showMenu = false
                                onUploadPdf?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove Timetable (PDF)", color = StatusDangerRed) },
                            onClick = {
                                showMenu = false
                                onRemovePdf?.invoke()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Upload Timetable (PDF)") },
                            onClick = {
                                showMenu = false
                                onUploadPdf?.invoke()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete Route", color = StatusDangerRed) },
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
 * Stops Management Sub-screen: Route selector, clean numbered vertical list, and reordering.
 */
@Composable
private fun StopsSubScreen(
    routes: List<Route>,
    selectedRoute: Route?,
    onSelectRoute: (Route?) -> Unit,
    onBack: () -> Unit,
    onAddStopClick: (Route) -> Unit,
    onOpenMapCreator: (Route) -> Unit,
    onEditStopClick: (Route, BusStop) -> Unit,
    onMoveStopUp: (Route, BusStop) -> Unit,
    onMoveStopDown: (Route, BusStop) -> Unit,
    onDeleteStopClick: (Route, BusStop) -> Unit
) {
    var showRoutePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CollegeSurface)
    ) {
        AdminSubScreenHeader(
            title = "Manage Stops",
            subtitle = selectedRoute?.name ?: "Select a route",
            onBack = onBack,
            actions = {
                if (selectedRoute != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { onOpenMapCreator(selectedRoute) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Map", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onAddStopClick(selectedRoute) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Add Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )

        // Route Selector dropdown
        Surface(
            color = Color.White,
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                    .clickable { showRoutePicker = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Selected Route",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = selectedRoute?.name ?: "Tap to choose route",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B)
                    )
                }

                DropdownMenu(
                    expanded = showRoutePicker,
                    onDismissRequest = { showRoutePicker = false }
                ) {
                    routes.forEach { route ->
                        DropdownMenuItem(
                            text = { Text("${route.name} (${route.stops.size} stops)") },
                            onClick = {
                                showRoutePicker = false
                                onSelectRoute(route)
                            }
                        )
                    }
                }
            }
        }

        if (selectedRoute == null || selectedRoute.stops.isEmpty()) {
            AdminEmptyPlaceholder(
                icon = Icons.Default.Place,
                title = "No stops in this route",
                subtitle = "Use '+ Add Stop' or 'Map' to create stops."
            )
        } else {
            val sortedStops = remember(selectedRoute.stops) {
                selectedRoute.stops.sortedBy { it.order }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(sortedStops, key = { _, stop -> stop.stopId }) { index, stop ->
                    StopCompactRow(
                        index = index,
                        totalStops = sortedStops.size,
                        stop = stop,
                        onEdit = { onEditStopClick(selectedRoute, stop) },
                        onMoveUp = { onMoveStopUp(selectedRoute, stop) },
                        onMoveDown = { onMoveStopDown(selectedRoute, stop) },
                        onDelete = { onDeleteStopClick(selectedRoute, stop) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun StopCompactRow(
    index: Int,
    totalStops: Int,
    stop: BusStop,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sequence Number Circle (01, 02...)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFF1F5F9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", index + 1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CollegePrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "%.5f, %.5f".format(stop.latitude, stop.longitude),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Quick reorder buttons
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "Move Up",
                    tint = if (index > 0) Color(0xFF64748B) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onMoveDown,
                enabled = index < totalStops - 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "Move Down",
                    tint = if (index < totalStops - 1) Color(0xFF64748B) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(16.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Stop Actions",
                        tint = Color(0xFF64748B)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Stop") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Stop", color = StatusDangerRed) },
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
