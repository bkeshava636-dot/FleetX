package com.college.bustracker.ui.student

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.college.bustracker.util.AutomaticRideState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.college.bustracker.R
import com.college.bustracker.data.model.TrackingDisplayState
import com.college.bustracker.ui.common.StatusBadge
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

@Composable
fun StudentMapScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bus by viewModel.selectedBus.collectAsState()
    val route by viewModel.currentRoute.collectAsState()
    val selectedStop by viewModel.selectedStop.collectAsState()
    val liveLoc by viewModel.liveLocation.collectAsState()
    val trackingState by viewModel.trackingState.collectAsState()
    val swapAlert by viewModel.activeSwapForStudent.collectAsState()
    val lastUpdateText by viewModel.lastUpdateText.collectAsState()
    val nextStopEta by viewModel.nextStopEta.collectAsState()
    val upcomingStops by viewModel.upcomingStopsEta.collectAsState()
    val isHolidayToday by viewModel.isHolidayToday.collectAsState()
    val todayHolidayName by viewModel.todayHolidayName.collectAsState()
    val routeRoadPolyline by viewModel.routeRoadPolyline.collectAsState()
    val activeDetourPolyline by viewModel.activeDetourPolyline.collectAsState()
    val isOffRoute by viewModel.isOffRoute.collectAsState()

    var showUpcomingStops by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    val passedStopIds = remember(upcomingStops) {
        upcomingStops.filter { it.isPassed }.map { it.stop.stopId }.toSet()
    }

    var hasCenteredCameraOnBus by remember { mutableStateOf(false) }
    var hasCenteredFallback by remember { mutableStateOf(false) }

    val initialPos = remember {
        val initialLoc = viewModel.liveLocation.value
        val initLat = initialLoc?.latitude ?: 0.0
        val initLng = initialLoc?.longitude ?: 0.0
        if (initLat != 0.0 && initLng != 0.0) {
            hasCenteredCameraOnBus = true
            LatLng(initLat, initLng)
        } else {
            val stops = viewModel.currentRoute.value?.stops ?: emptyList()
            if (stops.isNotEmpty()) {
                hasCenteredFallback = true
                LatLng(stops.first().latitude, stops.first().longitude)
            } else {
                LatLng(15.3435, 76.1554)
            }
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 15.5f)
    }

    var isIdlePaused by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-pause live tracking if inactive for 15 minutes (preserves RTDB 10 GB free bandwidth)
    LaunchedEffect(lastInteractionTime, isIdlePaused) {
        if (!isIdlePaused) {
            kotlinx.coroutines.delay(15 * 60 * 1000L) // 15 minutes
            isIdlePaused = true
            viewModel.pauseLiveTracking()
        }
    }

    // Reset inactivity timer when user pans or zooms the map
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) {
            lastInteractionTime = System.currentTimeMillis()
            if (isIdlePaused) {
                isIdlePaused = false
                viewModel.resumeLiveTracking()
            }
        }
    }

    // Lifecycle observer to immediately detach RTDB listener on ON_STOP
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    viewModel.pauseLiveTracking()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (!isIdlePaused) {
                        viewModel.resumeLiveTracking()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Reaction to live bus coordinates arrival (if not already centered on creation)
    LaunchedEffect(liveLoc) {
        val lat = liveLoc?.latitude ?: 0.0
        val lng = liveLoc?.longitude ?: 0.0
        if (!hasCenteredCameraOnBus && lat != 0.0 && lng != 0.0) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.0f))
            hasCenteredCameraOnBus = true
        }
    }

    // Fallback reaction to route arrival when live bus location is absent/invalid
    LaunchedEffect(route) {
        if (!hasCenteredCameraOnBus && !hasCenteredFallback) {
            val lat = liveLoc?.latitude ?: 0.0
            val lng = liveLoc?.longitude ?: 0.0
            if (lat == 0.0 && lng == 0.0) {
                val stops = route?.stops ?: emptyList()
                if (stops.isNotEmpty()) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(stops.first().latitude, stops.first().longitude), 14.5f))
                    hasCenteredFallback = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map Engine
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = mapType),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false
            )
        ) {
            val stops = route?.stops ?: emptyList()
            val nextStopId = nextStopEta?.stop?.stopId
            val targetSelectedStopId = selectedStop?.stopId

            // 1. Scheduled Road-Snapped Route Line
            val roadPoints = if (routeRoadPolyline.isNotEmpty()) {
                routeRoadPolyline
            } else {
                stops.map { LatLng(it.latitude, it.longitude) }
            }

            if (roadPoints.isNotEmpty()) {
                Polyline(
                    points = roadPoints,
                    color = Color(0xFF1A56DB),
                    width = 8f
                )
            }

            // 2. Active Detour Route Line (when bus deviates from planned route)
            val detourPoints = activeDetourPolyline
            if (isOffRoute && detourPoints != null && detourPoints.isNotEmpty()) {
                Polyline(
                    points = detourPoints,
                    color = Color(0xFFD97706), // Distinct Amber for live detour
                    width = 10f
                )
            }

            // Route Stop Markers
            stops.forEach { stop ->
                val state = when {
                    stop.stopId == nextStopId -> StopMarkerIconCache.StopState.NEXT_ETA
                    stop.stopId == targetSelectedStopId -> StopMarkerIconCache.StopState.SELECTED
                    passedStopIds.contains(stop.stopId) -> StopMarkerIconCache.StopState.PASSED
                    else -> StopMarkerIconCache.StopState.NORMAL
                }
                Marker(
                    state = rememberMarkerState(
                        key = "stop_${stop.stopId}",
                        position = LatLng(stop.latitude, stop.longitude)
                    ),
                    title = "${stop.order}. ${stop.name}",
                    icon = StopMarkerIconCache.getIcon(context, stop.order, state),
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    onClick = {
                        viewModel.selectStop(stop)
                        false
                    }
                )
            }

            // Live Bus Marker
            val lat = liveLoc?.latitude ?: 0.0
            val lng = liveLoc?.longitude ?: 0.0
            val speedMps = liveLoc?.speed ?: 0.0f
            val bearing = liveLoc?.bearing ?: 0.0f
            val canShowMarker = lat != 0.0 && lng != 0.0

            if (canShowMarker) {
                val statusText = when (trackingState) {
                    TrackingDisplayState.ACTIVE -> context.getString(R.string.tracking_live)
                    TrackingDisplayState.WAITING_FOR_GPS -> context.getString(R.string.awaiting_gps_signal)
                    TrackingDisplayState.STALE -> context.getString(R.string.gps_signal_stale)
                    TrackingDisplayState.INACTIVE -> context.getString(R.string.bus_inactive)
                }
                val busLabel = bus?.busNumber ?: "BUS-25"
                val snippetText = if (trackingState == TrackingDisplayState.ACTIVE) {
                    val speedKmH = (speedMps * 3.6).toInt()
                    context.getString(R.string.speed_format, speedKmH, lastUpdateText)
                } else {
                    lastUpdateText
                }

                Marker(
                    state = rememberMarkerState(
                        key = "live_bus",
                        position = LatLng(lat, lng)
                    ).apply {
                        position = LatLng(lat, lng)
                    },
                    title = "$busLabel • $statusText",
                    snippet = snippetText,
                    rotation = if (speedMps > 1.0f) bearing else 0.0f,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    flat = true,
                    icon = BusMarkerIconCache.getIcon(context, trackingState, speedMps > 1.0f)
                )
            }
        }

        // Compact Floating Top Bar
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color(0xFF1E293B)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bus?.busNumber ?: "Bus",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (swapAlert != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "→ ${swapAlert?.replacementBusId}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusWarningOrange,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        text = route?.name ?: stringResource(R.string.no_route_selected),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isOffRoute) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Text(
                            text = "DETOUR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                StatusBadge(state = trackingState)
            }
        }

        // Floating Bottom Controls & Compact ETA Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Floating Action Buttons above the card (Satellite Toggle & Recenter)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Satellite / Normal Toggle
                FloatingActionButton(
                    onClick = {
                        mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                    },
                    modifier = Modifier.size(44.dp),
                    containerColor = Color.White,
                    contentColor = if (mapType == MapType.HYBRID) CollegePrimary else Color.DarkGray,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Toggle Satellite",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Recenter on Bus
                FloatingActionButton(
                    onClick = {
                        val lat = liveLoc?.latitude ?: 0.0
                        val lng = liveLoc?.longitude ?: 0.0
                        coroutineScope.launch {
                            if (lat != 0.0 && lng != 0.0) {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.0f))
                            } else {
                                route?.stops?.firstOrNull()?.let {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15.0f))
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(46.dp),
                    containerColor = CollegePrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.map_center_bus),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Bottom ETA Card or Holiday Banner
            if (isHolidayToday) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFDBEAFE), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.no_bus_service_today),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                            Text(
                                text = stringResource(R.string.holiday_prefix, todayHolidayName ?: stringResource(R.string.college_holiday)),
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Main Compact ETA Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showUpcomingStops = !showUpcomingStops },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF1F5F9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = CollegePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val stopTitle = nextStopEta?.stop?.name ?: (route?.stops?.firstOrNull()?.name ?: stringResource(R.string.no_route))
                            Text(
                                text = stopTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1
                            )
                            val speedKmH = ((liveLoc?.speed ?: 0f) * 3.6).toInt()
                            val detailSubtext = when (trackingState) {
                                TrackingDisplayState.ACTIVE -> {
                                    val dist = nextStopEta?.distanceFormatted ?: ""
                                    val isAtStop = (nextStopEta?.distanceMeters ?: Double.MAX_VALUE) <= 50.0
                                    val motionText = when {
                                        isAtStop -> stringResource(R.string.speed_at_stop)
                                        speedKmH > 2 -> "$speedKmH km/h"
                                        else -> "Stationary"
                                    }
                                    val cleanDist = if (dist.endsWith(" away")) dist else if (dist.isNotBlank()) "$dist away" else ""
                                    val backupTag = if (liveLoc?.sourceType == "STUDENT_ASSISTED") " • 👥 Backup GPS" else ""
                                    val baseText = if (cleanDist.isNotBlank()) "$cleanDist • $motionText" else motionText
                                    baseText + backupTag
                                }
                                TrackingDisplayState.WAITING_FOR_GPS -> stringResource(R.string.connecting_gps_pending)
                                TrackingDisplayState.STALE -> stringResource(R.string.signal_outdated, lastUpdateText)
                                TrackingDisplayState.INACTIVE -> stringResource(R.string.bus_not_in_service)
                            }
                            Text(
                                text = detailSubtext,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // ETA Badge
                        if (trackingState == TrackingDisplayState.ACTIVE && nextStopEta != null) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = nextStopEta?.etaFormatted ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        } else {
                            Text(
                                text = when (trackingState) {
                                    TrackingDisplayState.WAITING_FOR_GPS -> stringResource(R.string.status_pending)
                                    TrackingDisplayState.STALE -> stringResource(R.string.status_stale)
                                    else -> stringResource(R.string.status_off_duty)
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = if (showUpcomingStops) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand stops",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Collapsible detailed section (stops list + contributor)
                    if (showUpcomingStops) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Route Stops List
                        if (upcomingStops.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.route_stops_and_etas),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CollegePrimary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                upcomingStops.forEach { stopEta ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (stopEta.isPassed) "✓" else if (stopEta.isNext) "▶" else "•",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (stopEta.isPassed) Color(0xFF94A3B8) else if (stopEta.isNext) CollegePrimary else Color(0xFF64748B)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stopEta.stop.name,
                                                fontSize = 12.sp,
                                                fontWeight = if (stopEta.isNext) FontWeight.Bold else FontWeight.Normal,
                                                color = if (stopEta.isPassed) Color(0xFF94A3B8) else Color(0xFF1E293B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (trackingState == TrackingDisplayState.ACTIVE) stopEta.etaFormatted else if (stopEta.isPassed) stringResource(R.string.stop_passed) else "—",
                                            fontSize = 11.sp,
                                            fontWeight = if (stopEta.isNext) FontWeight.Bold else FontWeight.Medium,
                                            color = if (stopEta.isNext) Color(0xFF15803D) else if (stopEta.isPassed) Color(0xFF94A3B8) else Color(0xFF64748B),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Student Ride Assist Contributor
                        val isSharing by viewModel.isSharingLocation.collectAsState()
                        val isAutoEnabled by viewModel.isAutoSharingEnabled.collectAsState()
                        val autoState by viewModel.automaticRideState.collectAsState()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val headerText = when {
                                    isSharing -> stringResource(R.string.backup_gps_active)
                                    isAutoEnabled -> stringResource(R.string.auto_backup_on)
                                    else -> stringResource(R.string.student_transit_backup)
                                }
                                val subText = when {
                                    isSharing -> stringResource(R.string.assisting_live_map)
                                    isAutoEnabled -> stringResource(R.string.contributes_automatically)
                                    else -> stringResource(R.string.share_location_when_on_bus)
                                }
                                Text(
                                    text = headerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSharing || isAutoEnabled) StatusActiveGreen else Color(0xFF0F172A)
                                )
                                Text(
                                    text = subText,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isSharing || isAutoEnabled) {
                                Button(
                                    onClick = { viewModel.stopSharingLocation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.stop_sharing_btn), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val busId = bus?.busId
                                        if (!busId.isNullOrBlank()) {
                                            viewModel.enableAutoSharing(busId)
                                        } else {
                                            viewModel.startSharingLocation(context)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.auto_share_btn), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isIdlePaused) {
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Live tracking paused (15 min idle)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        isIdlePaused = false
                        viewModel.resumeLiveTracking()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
}

private object BusMarkerIconCache {
    private val cache = mutableMapOf<String, BitmapDescriptor>()

    fun getIcon(context: android.content.Context, state: TrackingDisplayState, isMoving: Boolean): BitmapDescriptor {
        val key = "${state}_$isMoving"
        return cache.getOrPut(key) {
            val density = context.resources.displayMetrics.density
            val sizePx = (48 * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val cx = sizePx / 2f
            val cy = sizePx / 2f
            val radius = sizePx * 0.38f

            // 1. Subtle Outer Shadow
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#33000000")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy + 2 * density, radius + 2 * density, shadowPaint)

            // 2. White Outer Border Ring
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius + 2.5f * density, borderPaint)

            // 3. Status-Colored Center Disc
            val fillColor = when (state) {
                TrackingDisplayState.ACTIVE -> android.graphics.Color.parseColor("#16A34A") // Emerald Green
                TrackingDisplayState.WAITING_FOR_GPS -> android.graphics.Color.parseColor("#D97706") // Amber
                TrackingDisplayState.STALE -> android.graphics.Color.parseColor("#EA580C") // Orange-Red
                TrackingDisplayState.INACTIVE -> android.graphics.Color.parseColor("#64748B") // Slate Gray
            }
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, discPaint)

            // 4. Directional Heading Beacon (triangle pointer on top edge when moving)
            if (isMoving) {
                val beaconBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    style = Paint.Style.FILL
                }
                val beaconPath = Path().apply {
                    moveTo(cx, cy - radius - 4f * density)
                    lineTo(cx - 5f * density, cy - radius + 3.5f * density)
                    lineTo(cx + 5f * density, cy - radius + 3.5f * density)
                    close()
                }
                canvas.drawPath(beaconPath, beaconBorderPaint)

                val innerBeaconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fillColor
                    style = Paint.Style.FILL
                }
                val innerBeaconPath = Path().apply {
                    moveTo(cx, cy - radius - 2.5f * density)
                    lineTo(cx - 3.5f * density, cy - radius + 3f * density)
                    lineTo(cx + 3.5f * density, cy - radius + 3f * density)
                    close()
                }
                canvas.drawPath(innerBeaconPath, innerBeaconPaint)
            }

            // 5. Crisp Material Bus Silhouette centered in pure white
            val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            val busW = 15f * density
            val busH = 19f * density
            val busLeft = cx - busW / 2f
            val busTop = cy - busH / 2f + (if (isMoving) 1f * density else 0f)
            val busRect = RectF(busLeft, busTop, busLeft + busW, busTop + busH)
            canvas.drawRoundRect(busRect, 3.5f * density, 3.5f * density, busPaint)

            // Front Windshield Cutout
            val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
            val windshieldRect = RectF(
                busLeft + 2f * density,
                busTop + 2.5f * density,
                busLeft + busW - 2f * density,
                busTop + 8.5f * density
            )
            canvas.drawRoundRect(windshieldRect, 1.5f * density, 1.5f * density, cutoutPaint)

            // Headlights
            val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(busLeft + 3.5f * density, busTop + busH - 3.5f * density, 1.3f * density, lightPaint)
            canvas.drawCircle(busLeft + busW - 3.5f * density, busTop + busH - 3.5f * density, 1.3f * density, lightPaint)

            // Front Grille Line
            val grillePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                strokeWidth = 1.2f * density
                style = Paint.Style.STROKE
            }
            canvas.drawLine(
                cx - 2f * density, busTop + busH - 3.5f * density,
                cx + 2f * density, busTop + busH - 3.5f * density,
                grillePaint
            )

            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }
}

private object StopMarkerIconCache {
    private val cache = mutableMapOf<String, BitmapDescriptor>()

    enum class StopState {
        NORMAL,
        NEXT_ETA,
        PASSED,
        SELECTED
    }

    fun getIcon(context: android.content.Context, order: Int, state: StopState): BitmapDescriptor {
        val key = "${order}_$state"
        return cache.getOrPut(key) {
            val density = context.resources.displayMetrics.density
            val sizeDp = when (state) {
                StopState.NEXT_ETA -> 32
                StopState.SELECTED -> 30
                StopState.NORMAL -> 24
                StopState.PASSED -> 20
            }
            val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val cx = sizePx / 2f
            val cy = sizePx / 2f
            val radius = (sizePx / 2f) - (2.5f * density)

            val (fillColor, borderColor, textColor) = when (state) {
                StopState.NEXT_ETA -> Triple(
                    android.graphics.Color.parseColor("#2563EB"), // Royal Blue
                    android.graphics.Color.WHITE,
                    android.graphics.Color.WHITE
                )
                StopState.SELECTED -> Triple(
                    android.graphics.Color.parseColor("#0F172A"), // Dark Indigo
                    android.graphics.Color.parseColor("#F59E0B"), // Amber Ring
                    android.graphics.Color.WHITE
                )
                StopState.PASSED -> Triple(
                    android.graphics.Color.parseColor("#F1F5F9"), // Light Muted Gray
                    android.graphics.Color.parseColor("#94A3B8"), // Border Gray
                    android.graphics.Color.parseColor("#64748B")  // Muted Text
                )
                StopState.NORMAL -> Triple(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.parseColor("#334155"), // Deep Slate Border
                    android.graphics.Color.parseColor("#0F172A")  // Dark Slate Text
                )
            }

            // Outer Shadow for prominent stops
            if (state == StopState.NEXT_ETA || state == StopState.SELECTED) {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#40000000")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy + 1.5f * density, radius + 2f * density, shadowPaint)
            }

            // Outer Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.FILL
            }
            val borderWidth = if (state == StopState.NEXT_ETA || state == StopState.SELECTED) 3f * density else 2.5f * density
            canvas.drawCircle(cx, cy, radius + borderWidth, borderPaint)

            // Center Disc Fill
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, discPaint)

            // Centered Stop Number Text
            val text = if (order > 0) order.toString() else "•"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = (sizeDp * 0.45f) * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = textPaint.fontMetrics
            val yOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
            canvas.drawText(text, cx, cy - yOffset, textPaint)

            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }
}
