package com.college.bustracker.diagnostics.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.college.bustracker.diagnostics.ContinuousRootCauseSummary
import com.college.bustracker.diagnostics.DiagnosticEvent
import com.college.bustracker.diagnostics.ExportRangeType
import com.college.bustracker.diagnostics.FailureType
import com.college.bustracker.diagnostics.GpsDiagnosticManager
import com.college.bustracker.diagnostics.GpsDiagnosticReport
import com.college.bustracker.diagnostics.GpsDiagnosticStorage
import com.college.bustracker.diagnostics.PipelineStepStatus
import com.college.bustracker.diagnostics.SessionStatus
import com.college.bustracker.service.TrackingServiceHelper
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen displaying the Continuous Background GPS Diagnostic System, persistent recording controls,
 * real-time observation HUD, root cause synthesis, and export tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsDiagnosticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { GpsDiagnosticStorage(context) }
    val scope = rememberCoroutineScope()

    val recordingState by GpsDiagnosticManager.recordingState.collectAsState()
    val storageStats by GpsDiagnosticManager.storageStats.collectAsState()
    val latestEvent by GpsDiagnosticManager.latestEvent.collectAsState()
    val currentReport by GpsDiagnosticManager.currentReport.collectAsState()
    val isSessionActive by GpsDiagnosticManager.isSessionActive.collectAsState()

    val isTrackingLive by TrackingServiceHelper.isTracking.collectAsState()
    val gpsStatusText by TrackingServiceHelper.gpsStatus.collectAsState()

    var showTimelineDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showFullTextReportDialog by remember { mutableStateOf(false) }
    var fullReportText by remember { mutableStateOf("") }
    var timelineEvents by remember { mutableStateOf<List<DiagnosticEvent>>(emptyList()) }
    var summaryData by remember { mutableStateOf<ContinuousRootCauseSummary?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val romInfo = remember { com.college.bustracker.diagnostics.DeviceRomDetector.getRomInfo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Diagnostics", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        GpsDiagnosticManager.refreshStorageStats()
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CollegeSurface,
                    titleContentColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = CollegeSurface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            // 0. Device OEM & Background Killer Diagnostics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(
                    1.2.dp,
                    when (romInfo.killRisk) {
                        "Extreme" -> StatusDangerRed
                        "High" -> StatusWarningOrange
                        else -> CollegePrimary
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DEVICE SYSTEM & ROM RECOGNITION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = romInfo.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "${romInfo.romVersion} • ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        // Risk Badge
                        Box(
                            modifier = Modifier
                                .background(
                                    when (romInfo.killRisk) {
                                        "Extreme" -> StatusDangerRed.copy(alpha = 0.12f)
                                        "High" -> StatusWarningOrange.copy(alpha = 0.12f)
                                        else -> StatusActiveGreen.copy(alpha = 0.12f)
                                    },
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${romInfo.killRisk} Kill Risk",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (romInfo.killRisk) {
                                    "Extreme" -> StatusDangerRed
                                    "High" -> StatusWarningOrange
                                    else -> StatusActiveGreen
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = romInfo.advice,
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val opened = com.college.bustracker.diagnostics.DeviceRomDetector.openOemBatteryOptimizationSettings(context)
                            if (!opened) {
                                Toast.makeText(context, "Please check Battery & Autostart settings in device Settings.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configure ${romInfo.displayName} Protection", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Continuous Recording Control & State Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.2.dp, if (recordingState.isRecording) StatusActiveGreen else Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "WINDOW-AWARE DIAGNOSTICS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isWindowRecording = recordingState.isRecording && recordingState.recordingSessionId.startsWith("win_")
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (recordingState.isRecording) StatusActiveGreen else Color(0xFF94A3B8),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isWindowRecording -> "RECORDING: ON (Shift Window)"
                                        recordingState.isRecording -> "RECORDING: ON (Manual)"
                                        else -> "RECORDING: STANDBY"
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = if (recordingState.isRecording) StatusActiveGreen else Color(0xFF475569)
                                )
                            }
                        }

                        if (recordingState.isRecording) {
                            Button(
                                onClick = { GpsDiagnosticManager.stopRecording(context) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop Recording", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { GpsDiagnosticManager.startRecording(context) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Manual", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (recordingState.isRecording) {
                            "Started: ${recordingState.startedAtFormatted.ifBlank { "Active" }} (Recording window events)"
                        } else {
                            "Auto-Window: Starts 2 min prior to shift (e.g. 06:58 / 15:58) and stops when shift ends to conserve storage."
                        },
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusMetric("Today's Events", storageStats.todayEventsRecorded.toString())
                        StatusMetric("Total Stored", storageStats.totalEventsRecorded.toString())
                        StatusMetric("Storage Used", storageStats.storageSizeFormatted)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real-time HUD states
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoRow(
                                label = "Last Event",
                                value = if (latestEvent != null) "${latestEvent?.timeFormatted} ${latestEvent?.eventName}" else recordingState.lastEventName.ifBlank { "None recorded" }
                            )
                            InfoRow(
                                label = "Current GPS State",
                                value = if (isTrackingLive) "Tracking Active ($gpsStatusText)" else "Tracking Inactive"
                            )
                            InfoRow(
                                label = "Foreground Service",
                                value = if (isTrackingLive) "RUNNING" else "STOPPED"
                            )
                            InfoRow(
                                label = "Last RTDB Write",
                                value = if (currentReport?.heartbeats?.isNotEmpty() == true) {
                                    val lastHb = currentReport?.heartbeats?.lastOrNull()
                                    "${lastHb?.timeFormatted} (${lastHb?.lastRtdbLatencyMs}ms)"
                                } else "None recorded"
                            )
                            InfoRow(
                                label = "Last Detected Issue",
                                value = if (currentReport?.primaryFailure != null && currentReport?.primaryFailure != FailureType.NONE) {
                                    "${currentReport?.primaryFailure?.name} (${currentReport?.primaryFailure?.userReason})"
                                } else "None detected",
                                isHighlight = currentReport?.primaryFailure != null && currentReport?.primaryFailure != FailureType.NONE
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Action Buttons Section
            Text(
                text = "DIAGNOSTIC ACTIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val events = withContext(Dispatchers.IO) { storage.getTodayEvents() }
                            timelineEvents = events
                            showTimelineDialog = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Timeline", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        scope.launch {
                            val sum = withContext(Dispatchers.IO) { GpsDiagnosticManager.generateRootCauseSummary() }
                            summaryData = sum
                            showSummaryDialog = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Root Cause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Export & Download Tools Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            try {
                                val savedFile = withContext(Dispatchers.IO) { storage.saveReportToDownloads(ExportRangeType.ALL) }
                                Toast.makeText(context, "Saved to Downloads: ${savedFile.name}", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    enabled = !isExporting
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { storage.getReportPlainText(ExportRangeType.ALL) }
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("FleetX Diagnostics", text))
                            Toast.makeText(context, "Diagnostic report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.2.dp, CollegePrimary),
                    enabled = !isExporting
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = CollegePrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Report", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CollegePrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Export & Download Tools Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { storage.getReportPlainText(ExportRangeType.ALL) }
                            fullReportText = text
                            showFullTextReportDialog = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B))
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF475569))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Report", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            try {
                                val zip = withContext(Dispatchers.IO) { storage.createExportZip(ExportRangeType.ALL) }
                                shareZipFile(context, zip)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                    enabled = !isExporting
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF475569))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share ZIP", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StatusDangerRed.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusDangerRed)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete All Records", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StatusDangerRed)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Operating Window Pipeline Breakdown
            val report = currentReport
            if (report != null) {
                Text(
                    text = "LAST OPERATING WINDOW PIPELINE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OperatingWindowSummaryCard(report = report)
                Spacer(modifier = Modifier.height(12.dp))
                PipelineStatusCard(pipeline = report.pipeline)
                Spacer(modifier = Modifier.height(12.dp))
                if (report.deviceState != null) {
                    DeviceStateCard(state = report.deviceState)
                }
            }
        }
    }

    // ==========================================
    // DIALOGS
    // ==========================================

    // Root-Cause Summary Modal Dialog
    if (showSummaryDialog && summaryData != null) {
        val sum = summaryData!!
        AlertDialog(
            onDismissRequest = { showSummaryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = CollegePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Root-Cause Synthesis", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    SummarySection("PRIMARY OBSERVATION", sum.primaryObservation, Color(0xFF0F172A))
                    SummarySection("LAST CONFIRMED HEALTHY EVENT", sum.lastConfirmedHealthyEvent, StatusActiveGreen)
                    SummarySection("LAST SUCCESSFUL RTDB WRITE", sum.lastSuccessfulRtdbWrite, StatusActiveGreen)
                    SummarySection("FIRST OBSERVED FAILURE", sum.firstObservedFailure, StatusDangerRed)
                    SummarySection("SERVICE STATE", sum.serviceState, Color(0xFF334155))
                    SummarySection("POSSIBLE CAUSES", sum.possibleCauses, Color(0xFFB45309))
                    SummarySection("NEXT EVIDENCE NEEDED", sum.nextEvidenceNeeded, Color(0xFF2563EB))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val text = "FleetX GPS Root-Cause Summary\n\n" +
                                "Observation: ${sum.primaryObservation}\n" +
                                "Last Healthy: ${sum.lastConfirmedHealthyEvent}\n" +
                                "Last RTDB: ${sum.lastSuccessfulRtdbWrite}\n" +
                                "First Failure: ${sum.firstObservedFailure}\n" +
                                "Service State: ${sum.serviceState}\n" +
                                "Possible Causes: ${sum.possibleCauses}\n" +
                                "Next Evidence: ${sum.nextEvidenceNeeded}"
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("FleetX Root Cause", text))
                        Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Summary")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSummaryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Timeline Modal Dialog
    if (showTimelineDialog) {
        AlertDialog(
            onDismissRequest = { showTimelineDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Today's Events (${timelineEvents.size})", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    IconButton(onClick = { showTimelineDialog = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                if (timelineEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No events recorded today yet.", color = Color(0xFF64748B))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(timelineEvents.reversed()) { ev ->
                            TimelineEventItem(ev)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimelineDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete All Diagnostic Records?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete all daily JSONL event logs, operating window sessions, and zip exports from device storage. This action cannot be undone.",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { storage.deleteAllRecords() }
                            GpsDiagnosticManager.refreshStorageStats()
                            showDeleteConfirmDialog = false
                            Toast.makeText(context, "All records deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text("Delete Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Text Report Viewer Dialog
    if (showFullTextReportDialog) {
        AlertDialog(
            onDismissRequest = { showFullTextReportDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Diagnostic Report", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { showFullTextReportDialog = false }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = fullReportText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0F172A),
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("FleetX Diagnostic Report", fullReportText))
                        Toast.makeText(context, "Full report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullTextReportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun StatusMetric(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}

@Composable
private fun InfoRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isHighlight) StatusDangerRed else Color(0xFF1E293B),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun SummarySection(title: String, content: String, titleColor: Color) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = titleColor, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = content, fontSize = 12.sp, color = Color(0xFF334155), lineHeight = 16.sp)
    }
}

@Composable
private fun TimelineEventItem(event: DiagnosticEvent) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (event.isFailure) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (event.isFailure) StatusDangerRed.copy(alpha = 0.4f) else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.eventName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (event.isFailure) StatusDangerRed else Color(0xFF0F172A)
                )
                Text(
                    text = event.timeFormatted,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "[${event.stage.displayName}]  App: ${event.appVisibility}  Scr: ${if (event.screenInteractive) "ON" else "OFF"}",
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
            if (event.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val detailStr = event.details.entries.joinToString("  ") { "${it.key}=${it.value}" }
                Text(
                    text = detailStr,
                    fontSize = 11.sp,
                    color = Color(0xFF334155),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun OperatingWindowSummaryCard(report: GpsDiagnosticReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${report.windowName} Window (${report.windowStart} - ${report.windowEnd})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = report.sessionStatus.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = when (report.sessionStatus) {
                        SessionStatus.COMPLETED -> StatusActiveGreen
                        SessionStatus.RUNNING -> CollegePrimary
                        SessionStatus.PARTIAL -> StatusWarningOrange
                        else -> StatusDangerRed
                    }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Bus: ${report.busId}  •  Driver: ${report.driverIdMasked}  •  Session: ${report.sessionId}",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = report.summaryMessage,
                fontSize = 12.sp,
                color = Color(0xFF334155),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun PipelineStatusCard(pipeline: com.college.bustracker.diagnostics.PipelineStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pipeline Stages",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(10.dp))
            PipelineRow("Alarm Scheduled & Received", pipeline.alarm)
            PipelineRow("Permissions (Fine / Background)", pipeline.permissions)
            PipelineRow("GPS Provider Enabled", pipeline.gpsProvider)
            PipelineRow("Foreground Service Started", pipeline.serviceStart)
            PipelineRow("Location Callback Received", pipeline.locationCallback)
            PipelineRow("Firebase Initialized", pipeline.firebaseInit)
            PipelineRow("RTDB Live Location Write", pipeline.rtdbWrite)
        }
    }
}

@Composable
private fun PipelineRow(title: String, status: PipelineStepStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 12.sp, color = Color(0xFF334155))
        when (status) {
            PipelineStepStatus.PASS -> Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Pass", tint = StatusActiveGreen, modifier = Modifier.size(16.dp))
            PipelineStepStatus.FAIL -> Icon(imageVector = Icons.Default.Close, contentDescription = "Fail", tint = StatusDangerRed, modifier = Modifier.size(16.dp))
            PipelineStepStatus.WARNING -> Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = StatusWarningOrange, modifier = Modifier.size(16.dp))
            PipelineStepStatus.NOT_REACHED -> Text("—", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeviceStateCard(state: com.college.bustracker.diagnostics.DeviceStateSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Device Environment (${state.deviceManufacturer} ${state.deviceModel})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow("Android Version", "API ${state.androidVersion}")
            InfoRow("Screen State", if (state.isInteractive) "Interactive (ON)" else "Locked / Idle")
            InfoRow("Battery Optimization", if (state.isIgnoringBatteryOptimizations) "EXEMPTED (Unrestricted)" else "RESTRICTED (Optimized)")
            InfoRow("Battery Saver Mode", if (state.isPowerSaveMode) "ENABLED" else "OFF")
            InfoRow("Doze Idle State", if (state.isDeviceIdleMode) "ACTIVE" else "INACTIVE")
            InfoRow("Background Location", if (state.backgroundLocationGranted) "GRANTED" else "MISSING")
            InfoRow("GPS Hardware", if (state.locationServicesEnabled) "ENABLED" else "DISABLED")
            InfoRow("Internet Connectivity", if (state.networkConnected) "CONNECTED" else "OFFLINE")
        }
    }
}

private fun shareZipFile(context: Context, zipFile: java.io.File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.diagnostic.provider",
            zipFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("FleetX Diagnostic Export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export FleetX GPS Diagnostics").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open share: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
