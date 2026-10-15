package com.college.bustracker.ui.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.college.bustracker.util.GeocodingResultState
import com.college.bustracker.util.NominatimGeocoder
import com.college.bustracker.util.NominatimPlace
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.college.bustracker.data.model.BusStop
import com.college.bustracker.data.model.Route
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

@Composable
fun MapStopCreatorDialog(
    route: Route,
    onDismiss: () -> Unit,
    onSaveStops: (List<BusStop>) -> Unit
) {
    val context = LocalContext.current
    val workingStops = remember {
        mutableStateListOf<BusStop>().apply {
            addAll(route.stops.sortedBy { it.order })
        }
    }

    var pendingNewPoint by remember { mutableStateOf<LatLng?>(null) }
    var stopToEdit by remember { mutableStateOf<BusStop?>(null) }
    var stopToDelete by remember { mutableStateOf<BusStop?>(null) }
    var showPreviewList by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Location Search States (uses Google Geocoder with OSM fallback)
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<NominatimPlace>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var suggestedStopName by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 3) {
            kotlinx.coroutines.delay(1200L)
            isSearching = true
            searchError = null
            when (val res = NominatimGeocoder.search(trimmed, context)) {
                is GeocodingResultState.Success -> {
                    searchResults = res.places
                    searchError = null
                }
                is GeocodingResultState.NoResults -> {
                    searchResults = emptyList()
                    searchError = "No locations found for \"$trimmed\""
                }
                is GeocodingResultState.RateLimited -> {
                    searchResults = emptyList()
                    searchError = "Search rate limited. Please wait a moment."
                }
                is GeocodingResultState.NetworkError -> {
                    searchResults = emptyList()
                    searchError = "Network error: check internet connection."
                }
                is GeocodingResultState.Error -> {
                    searchResults = emptyList()
                    searchError = res.message
                }
            }
            isSearching = false
        } else if (trimmed.isEmpty()) {
            searchResults = emptyList()
            searchError = null
        }
    }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }

    val initialPos = remember {
        val avgLat = workingStops.map { it.latitude }.average().takeIf { !it.isNaN() && it != 0.0 } ?: 15.3435
        val avgLng = workingStops.map { it.longitude }.average().takeIf { !it.isNaN() && it != 0.0 } ?: 76.1554
        LatLng(avgLat, avgLng)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 14.5f)
    }

    Dialog(
        onDismissRequest = {
            if (workingStops != route.stops) {
                showExitConfirmation = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // 1. Google Map Engine
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = mapType),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = true,
                        mapToolbarEnabled = false
                    ),
                    onMapClick = { latLng ->
                        suggestedStopName = ""
                        pendingNewPoint = latLng
                    }
                ) {
                    // Route line connecting stops
                    if (workingStops.isNotEmpty()) {
                        Polyline(
                            points = workingStops.map { LatLng(it.latitude, it.longitude) },
                            color = Color(0xFF1A56DB),
                            width = 10f
                        )
                    }

                    // Numbered Pins for each stop
                    workingStops.forEachIndexed { index, stop ->
                        val markerState = rememberMarkerState(
                            key = "${stop.stopId}_${stop.order}",
                            position = LatLng(stop.latitude, stop.longitude)
                        )

                        LaunchedEffect(markerState.dragState) {
                            if (markerState.dragState == DragState.END) {
                                val pos = markerState.position
                                val idx = workingStops.indexOfFirst { it.stopId == stop.stopId }
                                if (idx >= 0) {
                                    workingStops[idx] = workingStops[idx].copy(
                                        latitude = pos.latitude,
                                        longitude = pos.longitude
                                    )
                                }
                            }
                        }

                        Marker(
                            state = markerState,
                            title = "${stop.order}. ${stop.name}",
                            snippet = "Lat: ${"%.4f".format(stop.latitude)}, Lng: ${"%.4f".format(stop.longitude)}",
                            icon = NumberedPinCache.getPin(context, stop.order),
                            draggable = true,
                            onClick = {
                                stopToEdit = stop
                                true
                            }
                        )
                    }
                }

                // 2. Top Controls (Navigation, Search Bar, Results Dropdown, Tips)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // 2a. Top Navigation Bar
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (workingStops != route.stops) {
                                            showExitConfirmation = true
                                        } else {
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color(0xFF1E293B)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Add Stops on Map",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${route.name} • ${workingStops.size} stops",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val validation = MapStopLogic.validateForSave(workingStops)
                                    if (validation.isSuccess) {
                                        onSaveStops(workingStops.toList())
                                        onDismiss()
                                    } else {
                                        errorMessage = validation.exceptionOrNull()?.message ?: "Validation failed"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE ALL", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2b. Location Search Bar
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF64748B),
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Search place/address on map…", fontSize = 13.sp, color = Color.Gray)
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                    val trimmed = searchQuery.trim()
                                    if (trimmed.length >= 2) {
                                        isSearching = true
                                        searchError = null
                                        coroutineScope.launch {
                                            when (val res = NominatimGeocoder.search(trimmed, context)) {
                                                is GeocodingResultState.Success -> {
                                                    searchResults = res.places
                                                    searchError = null
                                                }
                                                is GeocodingResultState.NoResults -> {
                                                    searchResults = emptyList()
                                                    searchError = "No locations found for \"$trimmed\""
                                                }
                                                is GeocodingResultState.RateLimited -> {
                                                    searchResults = emptyList()
                                                    searchError = "Search rate limited. Please wait a moment."
                                                }
                                                is GeocodingResultState.NetworkError -> {
                                                    searchResults = emptyList()
                                                    searchError = "Network error: check internet connection."
                                                }
                                                is GeocodingResultState.Error -> {
                                                    searchResults = emptyList()
                                                    searchError = res.message
                                                }
                                            }
                                            isSearching = false
                                        }
                                    }
                                })
                            )

                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .padding(end = 6.dp),
                                    strokeWidth = 2.dp,
                                    color = CollegePrimary
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        searchError = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2c. Search Results List Dropdown
                    if (searchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PLACES FOUND (${searchResults.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CollegePrimary
                                    )
                                    IconButton(
                                        onClick = { searchResults = emptyList() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close",
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.Gray
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                LazyColumn {
                                    items(searchResults) { place ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val pt = LatLng(place.latitude, place.longitude)
                                                    coroutineScope.launch {
                                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pt, 16.5f))
                                                    }
                                                    suggestedStopName = place.displayName.split(",").firstOrNull()?.trim() ?: place.displayName
                                                    pendingNewPoint = pt
                                                    searchResults = emptyList()
                                                    searchError = null
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = CollegePrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = place.displayName.split(",").firstOrNull()?.trim() ?: place.displayName,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = place.displayName,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFF8FAFC))
                                    }
                                }
                            }
                        }
                    }

                    // 2d. Search Error Banner
                    if (searchError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = searchError ?: "",
                                    fontSize = 12.sp,
                                    color = StatusDangerRed,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { searchError = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = StatusDangerRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2e. Instruction Tip Bar
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xEE1E293B)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "Tap map or search above to place stop • Drag pins to adjust",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // 3. Floating Map Controls (Satellite Layer & Zoom Controls)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Map Type Toggle (Normal / Hybrid Satellite)
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(
                            onClick = {
                                mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle Satellite",
                                tint = if (mapType == MapType.HYBRID) CollegePrimary else Color.DarkGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Zoom In
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Zoom Out
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 4. Bottom Controls: Preview List Toggle & Reorder Sheet
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FormatListNumbered,
                                    contentDescription = null,
                                    tint = CollegePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stop Sequence (${workingStops.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            TextButton(
                                onClick = { showPreviewList = !showPreviewList },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (showPreviewList) "Hide List" else "Preview & Reorder",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CollegePrimary
                                )
                            }
                        }

                        if (showPreviewList) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(8.dp))

                            if (workingStops.isEmpty()) {
                                Text(
                                    text = "No stops yet. Tap on the map to add Stop #1.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    itemsIndexed(workingStops) { index, stop ->
                                        StopPreviewRow(
                                            stop = stop,
                                            index = index,
                                            totalCount = workingStops.size,
                                            onMoveUp = {
                                                val reordered = MapStopLogic.moveStop(workingStops, index, index - 1)
                                                workingStops.clear()
                                                workingStops.addAll(reordered)
                                            },
                                            onMoveDown = {
                                                val reordered = MapStopLogic.moveStop(workingStops, index, index + 1)
                                                workingStops.clear()
                                                workingStops.addAll(reordered)
                                            },
                                            onEdit = { stopToEdit = stop },
                                            onDelete = { stopToDelete = stop }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Add Stop Dialog (Prompt for stop name after tapping map or selecting place)
            val pointToAdd = pendingNewPoint
            if (pointToAdd != null) {
                var newStopName by remember(pointToAdd) { mutableStateOf(suggestedStopName) }
                var nameError by remember { mutableStateOf<String?>(null) }
                val nextOrder = workingStops.size + 1

                AlertDialog(
                    onDismissRequest = {
                        pendingNewPoint = null
                        suggestedStopName = ""
                    },
                    title = {
                        Text("Add Stop #$nextOrder", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Column {
                            Text(
                                "Location: Lat ${"%.5f".format(pointToAdd.latitude)}, Lng ${"%.5f".format(pointToAdd.longitude)}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = newStopName,
                                onValueChange = {
                                    newStopName = it
                                    nameError = null
                                },
                                label = { Text("Enter stop name") },
                                placeholder = { Text("e.g. Main Gate, City Bus Stand") },
                                isError = nameError != null,
                                supportingText = nameError?.let { { Text(it, color = StatusDangerRed) } },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newStopName.trim().isBlank()) {
                                    nameError = "Please enter a stop name"
                                    return@Button
                                }
                                val updated = MapStopLogic.addStop(
                                    existingStops = workingStops,
                                    name = newStopName.trim(),
                                    latitude = pointToAdd.latitude,
                                    longitude = pointToAdd.longitude
                                )
                                workingStops.clear()
                                workingStops.addAll(updated)
                                pendingNewPoint = null
                                suggestedStopName = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                        ) {
                            Text("Add Stop")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            pendingNewPoint = null
                            suggestedStopName = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 6. Edit Stop Dialog (Tapped pin or clicked edit in list)
            val editingStop = stopToEdit
            if (editingStop != null) {
                var editName by remember(editingStop) { mutableStateOf(editingStop.name) }
                var editError by remember { mutableStateOf<String?>(null) }

                AlertDialog(
                    onDismissRequest = { stopToEdit = null },
                    title = {
                        Text("Edit Stop #${editingStop.order}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Column {
                            Text(
                                "Stop ID: ${editingStop.stopId}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "Coordinates: ${"%.5f".format(editingStop.latitude)}, ${"%.5f".format(editingStop.longitude)}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = editName,
                                onValueChange = {
                                    editName = it
                                    editError = null
                                },
                                label = { Text("Stop name") },
                                isError = editError != null,
                                supportingText = editError?.let { { Text(it, color = StatusDangerRed) } },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (editName.trim().isBlank()) {
                                    editError = "Stop name cannot be blank"
                                    return@Button
                                }
                                val updated = MapStopLogic.updateStop(
                                    existingStops = workingStops,
                                    stopId = editingStop.stopId,
                                    newName = editName
                                )
                                workingStops.clear()
                                workingStops.addAll(updated)
                                stopToEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                        ) {
                            Text("Save Changes")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    val deleted = MapStopLogic.deleteStop(workingStops, editingStop.stopId)
                                    workingStops.clear()
                                    workingStops.addAll(deleted)
                                    stopToEdit = null
                                }
                            ) {
                                Text("Delete", color = StatusDangerRed)
                            }
                            TextButton(onClick = { stopToEdit = null }) {
                                Text("Cancel")
                            }
                        }
                    }
                )
            }

            // 7. Delete Confirmation Dialog
            val deletingStop = stopToDelete
            if (deletingStop != null) {
                AlertDialog(
                    onDismissRequest = { stopToDelete = null },
                    title = { Text("Delete Stop #${deletingStop.order}?") },
                    text = {
                        Text("Are you sure you want to remove '${deletingStop.name}'? Remaining stops will be renumbered.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val remaining = MapStopLogic.deleteStop(workingStops, deletingStop.stopId)
                                workingStops.clear()
                                workingStops.addAll(remaining)
                                stopToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { stopToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 8. Exit Without Saving Confirmation
            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    title = { Text("Discard Unsaved Changes?") },
                    text = { Text("You have unsaved stop modifications. Are you sure you want to exit without saving?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExitConfirmation = false
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                        ) {
                            Text("Discard")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmation = false }) {
                            Text("Keep Editing")
                        }
                    }
                )
            }

            // 9. Error Message Alert
            if (errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { errorMessage = null },
                    title = { Text("Cannot Save Stops") },
                    text = { Text(errorMessage ?: "") },
                    confirmButton = {
                        Button(onClick = { errorMessage = null }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StopPreviewRow(
    stop: BusStop,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(CollegePrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stop.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Lat: ${"%.4f".format(stop.latitude)}, Lng: ${"%.4f".format(stop.longitude)}",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (index > 0) CollegePrimary else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (index < totalCount - 1) CollegePrimary else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = StatusDangerRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Cache for Canvas-rendered numbered pins (e.g. 1, 2, 3...)
 */
object NumberedPinCache {
    private val cache = mutableMapOf<Int, BitmapDescriptor>()

    fun getPin(context: Context, number: Int): BitmapDescriptor {
        return cache.getOrPut(number) {
            val density = context.resources.displayMetrics.density
            val width = (34 * density).toInt().coerceAtLeast(1)
            val height = (44 * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = width / 2f
            val circleY = radius

            // Pin triangle tip at bottom
            val path = Path().apply {
                moveTo(radius - (6 * density), circleY + (6 * density))
                lineTo(radius + (6 * density), circleY + (6 * density))
                lineTo(radius, height.toFloat() - (2 * density))
                close()
            }
            paint.color = android.graphics.Color.parseColor("#1A56DB")
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)

            // Pin circle
            paint.color = android.graphics.Color.parseColor("#1A56DB")
            canvas.drawCircle(radius, circleY, radius - (2 * density), paint)

            // White border
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * density
            canvas.drawCircle(radius, circleY, radius - (2 * density), paint)

            // Number text
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 12f * density
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER

            val text = number.toString()
            val textBounds = Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)
            val textY = circleY + (textBounds.height() / 2f)
            canvas.drawText(text, radius, textY, paint)

            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }
}
