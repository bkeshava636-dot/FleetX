package com.college.bustracker.pass.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.college.bustracker.R
import com.college.bustracker.pass.engine.CardDetector
import com.college.bustracker.pass.engine.QualityChecker
import com.college.bustracker.pass.model.CardQuad
import com.college.bustracker.pass.model.Point2D
import com.college.bustracker.ui.theme.CollegePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun BusPassScannerScreen(
    viewModel: BusPassViewModel,
    isFrontSide: Boolean,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionPermanentlyDenied = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onGalleryImagePicked(context, uri)
        }
    }

    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isCapturing by remember { mutableStateOf(false) }
    var detectedGuidance by remember { mutableStateOf("Place your bus pass inside the frame") }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value

        // Standard ID Card Frame bounds (width ~85%, aspect 1.586)
        val frameWidthPx = (screenWidth * 0.88f)
        val frameHeightPx = (frameWidthPx / 1.586f)
        val frameLeftPx = (screenWidth - frameWidthPx) / 2f
        val frameTopPx = (screenHeight - frameHeightPx) / 2f - 40f

        val guideQuad = remember(frameLeftPx, frameTopPx, frameWidthPx, frameHeightPx) {
            CardQuad(
                topLeft = Point2D(frameLeftPx, frameTopPx),
                topRight = Point2D(frameLeftPx + frameWidthPx, frameTopPx),
                bottomRight = Point2D(frameLeftPx + frameWidthPx, frameTopPx + frameHeightPx),
                bottomLeft = Point2D(frameLeftPx, frameTopPx + frameHeightPx)
            )
        }

        if (hasCameraPermission && uiState.cameraError == null) {
            // CameraX PreviewView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .setFlashMode(flashMode)
                                .build()
                            imageCapture = capture

                            // Real-time card frame analyzer for live framing guidance only
                            val analyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                                processLiveFrame(
                                    imageProxy = imageProxy,
                                    guideQuad = guideQuad,
                                    onGuidance = { text, _ ->
                                        detectedGuidance = text
                                    }
                                )
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                analyzer
                            )
                        } catch (e: Exception) {
                            Log.e("BusPassScanner", "Camera init failed: ${e.message}")
                            viewModel.onCameraError("Camera unavailable: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Darkened Card Cut-Out Overlay with Professional Viewfinder Corner Brackets
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cutOutLeft = frameLeftPx * density
                val cutOutTop = frameTopPx * density
                val cutOutRight = (frameLeftPx + frameWidthPx) * density
                val cutOutBottom = (frameTopPx + frameHeightPx) * density
                val cornerRadiusPx = 16.dp.toPx()

                val cutOut = RoundRect(
                    rect = ComposeRect(
                        left = cutOutLeft,
                        top = cutOutTop,
                        right = cutOutRight,
                        bottom = cutOutBottom
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
                val path = Path().apply {
                    addRoundRect(cutOut)
                }

                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = 0.65f))
                }

                // Draw subtle card guide border
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.40f),
                    topLeft = Offset(cutOutLeft, cutOutTop),
                    size = androidx.compose.ui.geometry.Size(cutOutRight - cutOutLeft, cutOutBottom - cutOutTop),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )

                // Draw high-visibility golden corner brackets
                val bracketLen = 28.dp.toPx()
                val bracketStroke = 3.5.dp.toPx()
                val bracketColor = Color(0xFFFBBF24)

                // Top-Left bracket
                drawLine(bracketColor, Offset(cutOutLeft, cutOutTop + cornerRadiusPx), Offset(cutOutLeft, cutOutTop + cornerRadiusPx + bracketLen), bracketStroke)
                drawLine(bracketColor, Offset(cutOutLeft + cornerRadiusPx, cutOutTop), Offset(cutOutLeft + cornerRadiusPx + bracketLen, cutOutTop), bracketStroke)

                // Top-Right bracket
                drawLine(bracketColor, Offset(cutOutRight, cutOutTop + cornerRadiusPx), Offset(cutOutRight, cutOutTop + cornerRadiusPx + bracketLen), bracketStroke)
                drawLine(bracketColor, Offset(cutOutRight - cornerRadiusPx, cutOutTop), Offset(cutOutRight - cornerRadiusPx - bracketLen, cutOutTop), bracketStroke)

                // Bottom-Left bracket
                drawLine(bracketColor, Offset(cutOutLeft, cutOutBottom - cornerRadiusPx), Offset(cutOutLeft, cutOutBottom - cornerRadiusPx - bracketLen), bracketStroke)
                drawLine(bracketColor, Offset(cutOutLeft + cornerRadiusPx, cutOutBottom), Offset(cutOutLeft + cornerRadiusPx + bracketLen, cutOutBottom), bracketStroke)

                // Bottom-Right bracket
                drawLine(bracketColor, Offset(cutOutRight, cutOutBottom - cornerRadiusPx), Offset(cutOutRight, cutOutBottom - cornerRadiusPx - bracketLen), bracketStroke)
                drawLine(bracketColor, Offset(cutOutRight - cornerRadiusPx, cutOutBottom), Offset(cutOutRight - cornerRadiusPx - bracketLen, cutOutBottom), bracketStroke)
            }

            // Top Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }

                Text(
                    text = if (isFrontSide) stringResource(R.string.scan_front_pass) else stringResource(R.string.scan_back_pass),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {


                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                            imageCapture?.flashMode = flashMode
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                                else -> Icons.Default.FlashAuto
                            },
                            contentDescription = "Flash mode",
                            tint = if (flashMode == ImageCapture.FLASH_MODE_ON) Color(0xFFFBBF24) else Color.White
                        )
                    }
                }
            }

            // Real-time Guidance Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.70f),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = detectedGuidance,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom Shutter & Gallery Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery Fallback Button
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.choose_from_gallery),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Primary Shutter Button (Manual Capture)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .background(
                                if (isCapturing) Color.LightGray else Color.White,
                                CircleShape
                            )
                            .clickable(enabled = !isCapturing && imageCapture != null) {
                                isCapturing = true
                                captureImage(
                                    context = context,
                                    imageCapture = imageCapture,
                                    cameraExecutor = cameraExecutor,
                                    onSuccess = { bmp, rot ->
                                        isCapturing = false
                                        val mappedGuide = mapScreenGuideToBitmap(
                                            screenWidthDp = screenWidth,
                                            screenHeightDp = screenHeight,
                                            guideLeftDp = frameLeftPx,
                                            guideTopDp = frameTopPx,
                                            guideWidthDp = frameWidthPx,
                                            guideHeightDp = frameHeightPx,
                                            bitmapWidth = bmp.width,
                                            bitmapHeight = bmp.height
                                        )
                                        viewModel.processCapturedImage(bmp, mappedGuide, rot)
                                    },
                                    onError = {
                                        isCapturing = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = CollegePrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Capture Manually",
                                tint = CollegePrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Spacer for balanced centering
                    Spacer(modifier = Modifier.size(52.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Position pass inside frame and tap button to capture",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        } else {
            // Permission Denied or Camera Error Recovery Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (permissionPermanentlyDenied) "Camera Permission Disabled" else "Camera Unavailable",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (permissionPermanentlyDenied) {
                                "Camera permission was disabled in Settings. Please grant camera permission to scan your physical bus pass."
                            } else {
                                uiState.cameraError ?: "Camera couldn't be initialized on this device."
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (permissionPermanentlyDenied) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                Text("Open Settings")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.clearCameraError()
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                Text("Try Again")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose from Gallery Instead")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // Quality Validation Failure Modal
        AnimatedVisibility(
            visible = uiState.validationIssue != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scan Quality Alert",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.validationMessage ?: "Quality issue detected.",
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.startScanning(uiState.isReplacing)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retake")
                            }

                            Button(
                                onClick = {
                                    viewModel.openManualCornerAdjustment()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                            ) {
                                Text("Adjust Corners")
                            }
                        }
                    }
                }
            }
        }


    }
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture?,
    cameraExecutor: java.util.concurrent.Executor,
    onSuccess: (Bitmap, Int) -> Unit,
    onError: (Exception) -> Unit
) {
    if (imageCapture == null) {
        onError(IllegalStateException("ImageCapture is not initialized"))
        return
    }

    imageCapture.takePicture(
        cameraExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val bitmap = imageProxyToBitmap(image)
                image.close()
                ContextCompat.getMainExecutor(context).execute {
                    if (bitmap != null) onSuccess(bitmap, rot)
                    else onError(IllegalStateException("Failed to decode captured camera buffer"))
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(context).execute {
                    onError(exception)
                }
            }
        }
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate bitmap if needed according to image rotation degrees
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0 && raw != null) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } else {
            raw
        }
    } catch (e: Exception) {
        Log.e("BusPassScanner", "Error converting image proxy: ${e.message}")
        null
    }
}

private fun processLiveFrame(
    imageProxy: ImageProxy,
    guideQuad: CardQuad,
    onGuidance: (String, Boolean) -> Unit
) {
    try {
        val planeY = imageProxy.planes[0]
        val bufferY = planeY.buffer
        val rowStride = planeY.rowStride
        val width = imageProxy.width
        val height = imageProxy.height

        // Downsample luminance for instant ~15ms live frame analysis
        var sumY = 0L
        var glareCount = 0
        var sampled = 0

        val step = 8
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val lum = bufferY.get(y * rowStride + x).toInt() and 0xFF
                sumY += lum
                if (lum >= 250) glareCount++
                sampled++
            }
        }

        val avgLum = if (sampled > 0) sumY / sampled else 128
        val glareRatio = if (sampled > 0) glareCount.toFloat() / sampled else 0f

        when {
            avgLum < 38 -> onGuidance("Too dark — Move to a brighter area", false)
            avgLum > 230 -> onGuidance("Too bright — Avoid direct glare", false)
            glareRatio > 0.08f -> onGuidance("Glare detected — Tilt card slightly", false)
            else -> onGuidance("Card aligned — Tap button to capture", true)
        }
    } catch (e: Exception) {
        // Safe analysis failure recovery
    } finally {
        imageProxy.close()
    }
}

internal fun mapScreenGuideToBitmap(
    screenWidthDp: Float,
    screenHeightDp: Float,
    guideLeftDp: Float,
    guideTopDp: Float,
    guideWidthDp: Float,
    guideHeightDp: Float,
    bitmapWidth: Int,
    bitmapHeight: Int
): CardQuad {
    val screenAspect = screenWidthDp / screenHeightDp
    val bmpW = bitmapWidth.toFloat()
    val bmpH = bitmapHeight.toFloat()
    val bmpAspect = bmpW / bmpH

    val visibleBmpW: Float
    val visibleBmpH: Float
    val bmpOffsetX: Float
    val bmpOffsetY: Float

    if (screenAspect < bmpAspect) {
        visibleBmpH = bmpH
        visibleBmpW = bmpH * screenAspect
        bmpOffsetX = (bmpW - visibleBmpW) / 2f
        bmpOffsetY = 0f
    } else {
        visibleBmpW = bmpW
        visibleBmpH = bmpW / screenAspect
        bmpOffsetX = 0f
        bmpOffsetY = (bmpH - visibleBmpH) / 2f
    }

    val leftNorm = (guideLeftDp / screenWidthDp).coerceIn(0f, 1f)
    val rightNorm = ((guideLeftDp + guideWidthDp) / screenWidthDp).coerceIn(0f, 1f)
    val topNorm = (guideTopDp / screenHeightDp).coerceIn(0f, 1f)
    val bottomNorm = ((guideTopDp + guideHeightDp) / screenHeightDp).coerceIn(0f, 1f)

    val bmpLeft = (bmpOffsetX + leftNorm * visibleBmpW).coerceIn(0f, bmpW)
    val bmpRight = (bmpOffsetX + rightNorm * visibleBmpW).coerceIn(0f, bmpW)
    val bmpTop = (bmpOffsetY + topNorm * visibleBmpH).coerceIn(0f, bmpH)
    val bmpBottom = (bmpOffsetY + bottomNorm * visibleBmpH).coerceIn(0f, bmpH)

    return CardQuad(
        topLeft = Point2D(bmpLeft, bmpTop),
        topRight = Point2D(bmpRight, bmpTop),
        bottomRight = Point2D(bmpRight, bmpBottom),
        bottomLeft = Point2D(bmpLeft, bmpBottom)
    )
}
