package com.college.bustracker.pass.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.R
import com.college.bustracker.pass.model.BusPass
import com.college.bustracker.pass.storage.BusPassStorage
import com.college.bustracker.ui.theme.CollegePrimary
import com.college.bustracker.ui.theme.CollegeSurface
import com.college.bustracker.ui.theme.StatusActiveGreen
import com.college.bustracker.ui.theme.StatusDangerRed
import com.college.bustracker.ui.theme.StatusWarningOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main "My Bus Pass" Screen.
 * Routes between IDLE (Saved Pass or Empty State), Camera Scanner, Corner Adjustment, and Review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusPassScreen(
    viewModel: BusPassViewModel,
    onNavigateBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showReplaceConfirmDialog by remember { mutableStateOf(false) }

    // General error toast
    LaunchedEffect(uiState.generalError) {
        uiState.generalError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearGeneralError()
        }
    }

    when (uiState.currentStep) {
        ScanStep.IDLE, ScanStep.SAVED -> {
            BusPassIdleContent(
                uiState = uiState,
                onStartScan = { viewModel.startScanning(isReplacement = false) },
                onRequestReplace = { showReplaceConfirmDialog = true },
                onRequestDelete = { showDeleteConfirmDialog = true },
                onResumeDraft = { viewModel.resumeDraft() },
                onDiscardDraft = { viewModel.discardDraft() },
                onAttachReceipt = { uri -> viewModel.attachFeeReceipt(context, uri) },
                onDeleteReceipt = { viewModel.deleteFeeReceipt() },
                onNavigateBack = onNavigateBack,
                showBackButton = showBackButton
            )
        }

        ScanStep.SCANNING_FRONT, ScanStep.SCANNING_BACK -> {
            val isFront = uiState.currentStep == ScanStep.SCANNING_FRONT
            BusPassScannerScreen(
                viewModel = viewModel,
                isFrontSide = isFront,
                onNavigateBack = {
                    viewModel.cancelScanning()
                }
            )
        }

        ScanStep.ADJUST_FRONT, ScanStep.ADJUST_BACK -> {
            val rawBitmap = uiState.pendingRawBitmap
            val corners = uiState.pendingCorners
            if (rawBitmap != null && corners != null) {
                CornerAdjustmentScreen(
                    rawBitmap = rawBitmap,
                    initialCorners = corners,
                    onConfirmed = { adjusted ->
                        viewModel.confirmManualCorners(adjusted)
                    },
                    onReset = {
                        viewModel.openManualCornerAdjustment()
                    },
                    onCancel = {
                        if (uiState.currentStep == ScanStep.ADJUST_FRONT && uiState.draftFrontBitmap != null) {
                            viewModel.cancelCornerAdjustmentToStep(ScanStep.FRONT_CONFIRMED)
                        } else if (uiState.currentStep == ScanStep.ADJUST_BACK && uiState.draftBackBitmap != null) {
                            viewModel.cancelCornerAdjustmentToStep(ScanStep.REVIEW)
                        } else {
                            viewModel.cancelScanning()
                        }
                    }
                )
            } else {
                viewModel.cancelScanning()
            }
        }

        ScanStep.FRONT_CONFIRMED -> {
            // Screen transitioning between front confirmed and back scan
            FrontScanConfirmedPrompt(
                frontBitmap = uiState.draftFrontBitmap,
                onProceedToBack = { viewModel.proceedToBackScan() },
                onAdjustCrop = { viewModel.openAdjustFrontCrop() },
                onRetakeFront = { viewModel.retakeFront() },
                onCancel = { viewModel.cancelScanning() }
            )
        }

        ScanStep.REVIEW, ScanStep.SAVING -> {
            BusPassReviewScreen(
                frontBitmap = uiState.draftFrontBitmap,
                backBitmap = uiState.draftBackBitmap,
                isDuplicateWarning = uiState.isDuplicateWarning,
                isSaving = uiState.isSaving,
                onAdjustFrontCrop = { viewModel.openAdjustFrontCrop() },
                onAdjustBackCrop = { viewModel.openAdjustBackCrop() },
                onRetakeFront = { viewModel.retakeFront() },
                onRetakeBack = { viewModel.retakeBack() },
                onConfirmSave = { viewModel.savePass() },
                onCancel = { viewModel.cancelScanning() }
            )
        }

        ScanStep.ERROR -> {
            // Return to IDLE
            viewModel.cancelScanning()
        }
    }

    // Replace confirmation dialog (leaves existing pass completely intact if cancelled)
    if (showReplaceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmDialog = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = CollegePrimary) },
            title = { Text(stringResource(R.string.replace_bus_pass_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.replace_bus_pass_desc),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceConfirmDialog = false
                        viewModel.startScanning(isReplacement = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                ) {
                    Text(stringResource(R.string.continue_to_scan))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusDangerRed) },
            title = { Text(stringResource(R.string.delete_bus_pass_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.delete_bus_pass_desc),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deletePass()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                ) {
                    Text(stringResource(R.string.delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Idle state displaying either the saved Digital Bus Pass with flip view or empty state prompt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusPassIdleContent(
    uiState: BusPassUiState,
    onStartScan: () -> Unit,
    onRequestReplace: () -> Unit,
    onRequestDelete: () -> Unit,
    onResumeDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onAttachReceipt: (android.net.Uri) -> Unit,
    onDeleteReceipt: () -> Unit,
    onNavigateBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val savedPass = uiState.savedPass
    val context = LocalContext.current
    val storage = remember { BusPassStorage(context) }

    var selectedSideTab by remember { mutableIntStateOf(0) } // 0 = Front, 1 = Back, 2 = Receipt
    var inspectBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDeleteReceiptDialog by remember { mutableStateOf(false) }

    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onAttachReceipt(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_bus_pass), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Draft In-Progress Prompt (if previously interrupted)
            if (uiState.hasDraftFront && savedPass == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = CollegePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.unfinished_scan_detected), fontWeight = FontWeight.Bold, color = CollegePrimary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.unfinished_scan_desc),
                            fontSize = 13.sp,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDiscardDraft) {
                                Text(stringResource(R.string.discard_draft), color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onResumeDraft,
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                Text(stringResource(R.string.continue_action))
                            }
                        }
                    }
                }
            }

            if (savedPass != null) {
                // SAVED PASS VIEWER - Modern College Card Wallet Experience
                val frontBmp = remember(savedPass.frontImagePath) {
                    storage.loadBitmap(savedPass.frontImagePath)
                }
                val backBmp = remember(savedPass.backImagePath) {
                    storage.loadBitmap(savedPass.backImagePath)
                }
                val receiptBmp = remember(savedPass.receiptImagePath) {
                    savedPass.receiptImagePath?.let { storage.loadBitmap(it) }
                }

                // If receipt was deleted while selected, fallback to front
                if (selectedSideTab == 2 && receiptBmp == null) {
                    selectedSideTab = 0
                }

                val currentBmp = when (selectedSideTab) {
                    0 -> frontBmp
                    1 -> backBmp
                    2 -> receiptBmp
                    else -> frontBmp
                }

                // Modern Side Pill Selector & Quick Flip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segmented pill tab
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedSideTab == 0) CollegePrimary else Color.Transparent,
                                modifier = Modifier.clickable { selectedSideTab = 0 }
                            ) {
                                Text(
                                    text = stringResource(R.string.pass_front_side),
                                    color = if (selectedSideTab == 0) Color.White else Color(0xFF475569),
                                    fontWeight = if (selectedSideTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedSideTab == 1) CollegePrimary else Color.Transparent,
                                modifier = Modifier.clickable { selectedSideTab = 1 }
                            ) {
                                Text(
                                    text = stringResource(R.string.pass_back_side),
                                    color = if (selectedSideTab == 1) Color.White else Color(0xFF475569),
                                    fontWeight = if (selectedSideTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                            if (receiptBmp != null) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selectedSideTab == 2) CollegePrimary else Color.Transparent,
                                    modifier = Modifier.clickable { selectedSideTab = 2 }
                                ) {
                                    Text(
                                        text = stringResource(R.string.pass_fee_receipt),
                                        color = if (selectedSideTab == 2) Color.White else Color(0xFF475569),
                                        fontWeight = if (selectedSideTab == 2) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Flip Button (only flips front/back)
                    IconButton(
                        onClick = { selectedSideTab = if (selectedSideTab == 0) 1 else 0 },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = stringResource(R.string.pass_flip_card),
                            tint = Color(0xFF1E293B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Premium Card Display with subtle gradient framing & shadow
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F1D)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(265.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { inspectBitmap = currentBmp },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentBmp != null) {
                            Image(
                                bitmap = currentBmp.asImageBitmap(),
                                contentDescription = when (selectedSideTab) {
                                    0 -> stringResource(R.string.front_pass)
                                    1 -> stringResource(R.string.back_pass)
                                    else -> stringResource(R.string.pass_fee_receipt)
                                },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            )

                            // Tap to Zoom & Fullscreen Pill Badge
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.70f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        stringResource(R.string.pass_tap_to_zoom),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Text(stringResource(R.string.image_unavailable), color = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // OPTIONAL FEE RECEIPT SECTION
                if (receiptBmp != null) {
                    // Receipt attached summary card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSideTab = 2 }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(CollegePrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = CollegePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.fee_receipt_attached),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = stringResource(R.string.fee_receipt_subtext),
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { receiptPickerLauncher.launch("image/*") },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.replace_receipt),
                                        tint = CollegePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showDeleteReceiptDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete_receipt),
                                        tint = StatusDangerRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Option to add fee receipt (Clean dashed or outlined card)
                    OutlinedButton(
                        onClick = { receiptPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, CollegePrimary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CollegePrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CollegePrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.attach_fee_receipt),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CollegePrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Replace or Delete (Clean modern outline buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRequestReplace,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, CollegePrimary.copy(alpha = 0.4f))
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp), tint = CollegePrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.rescan_pass), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CollegePrimary)
                    }

                    OutlinedButton(
                        onClick = onRequestDelete,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, StatusDangerRed.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(17.dp), tint = StatusDangerRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.delete_pass), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StatusDangerRed)
                    }
                }

                // Delete Receipt Confirmation Dialog
                if (showDeleteReceiptDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteReceiptDialog = false },
                        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusDangerRed) },
                        title = { Text(stringResource(R.string.remove_fee_receipt_title), fontWeight = FontWeight.Bold) },
                        text = {
                            Text(stringResource(R.string.remove_fee_receipt_desc), fontSize = 14.sp)
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteReceiptDialog = false
                                    onDeleteReceipt()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                            ) {
                                Text(stringResource(R.string.remove_action))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteReceiptDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

            } else {
                // PREMIUM EMPTY STATE
                Spacer(modifier = Modifier.height(24.dp))

                // Illustrated Card graphic
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(CollegePrimary.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = CollegePrimary,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.add_pass_title),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.add_pass_desc),
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onStartScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.scan_pass_now), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    // Inspect Zoom Dialog
    inspectBitmap?.let { bmp ->
        InspectZoomDialog(
            bitmap = bmp,
            title = if (selectedSideTab == 0) stringResource(R.string.front_pass) else stringResource(R.string.back_pass),
            onDismiss = { inspectBitmap = null }
        )
    }
}

/**
 * Mid-scan prompt shown when front side has been confirmed, instructing student to turn card over.
 */
@Composable
private fun FrontScanConfirmedPrompt(
    frontBitmap: Bitmap?,
    onProceedToBack: () -> Unit,
    onAdjustCrop: () -> Unit,
    onRetakeFront: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(StatusActiveGreen.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusActiveGreen,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.front_side_captured),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.flip_pass_to_scan_back),
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Preview of cropped front pass
            if (frontBitmap != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(190.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = frontBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.front_pass),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option to refine/adjust crop manually
            OutlinedButton(
                onClick = onAdjustCrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8))
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.adjust_crop_corners),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onProceedToBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
            ) {
                Icon(imageVector = Icons.Default.FlipCameraAndroid, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.use_photo_and_scan_back), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onRetakeFront) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.retake_front), color = Color(0xFF94A3B8))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel), color = Color(0xFFEF4444))
                }
            }
        }
    }
}
