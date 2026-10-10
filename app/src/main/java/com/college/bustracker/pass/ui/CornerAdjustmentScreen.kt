package com.college.bustracker.pass.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bustracker.pass.model.CardQuad
import com.college.bustracker.pass.model.Point2D
import com.college.bustracker.ui.theme.CollegePrimary

@Composable
fun CornerAdjustmentScreen(
    rawBitmap: Bitmap,
    initialCorners: CardQuad,
    onConfirmed: (CardQuad) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    var corners by remember { mutableStateOf(initialCorners) }
    val cornersState = rememberUpdatedState(corners)
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }

    val isValid = remember(corners) { corners.isValidConvex() }

    val bmpW = rawBitmap.width.toFloat()
    val bmpH = rawBitmap.height.toFloat()

    // Create a memory/render-efficient display bitmap once to keep Canvas/Image rendering at 60fps
    val displayBitmap = remember(rawBitmap) {
        val maxDim = 1280
        if (rawBitmap.width > maxDim || rawBitmap.height > maxDim) {
            val scaleFactor = minOf(maxDim.toFloat() / rawBitmap.width, maxDim.toFloat() / rawBitmap.height)
            val dw = (rawBitmap.width * scaleFactor).toInt()
            val dh = (rawBitmap.height * scaleFactor).toInt()
            Bitmap.createScaledBitmap(rawBitmap, dw, dh, true)
        } else {
            rawBitmap
        }
    }
    val displayImageBitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Adjust Card Corners",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = if (isValid) Color(0xFF065F46) else Color(0xFF7F1D1D),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isValid) "Valid Shape ✓" else "Invalid Shape ✕",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Drag the 4 corner circles to match the edges of your card.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Display Image with interactive polygon and corner handles
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                val boxWidthPx = constraints.maxWidth.toFloat()
                val boxHeightPx = constraints.maxHeight.toFloat()

                // Calculate exact letterboxed image placement inside this viewport
                val scale = minOf(boxWidthPx / bmpW, boxHeightPx / bmpH)
                val displayW = bmpW * scale
                val displayH = bmpH * scale
                val offsetX = (boxWidthPx - displayW) / 2f
                val offsetY = (boxHeightPx - displayH) / 2f

                // Convert between Bitmap coordinates and Box coordinates
                fun toScreen(p: Point2D): Offset {
                    return Offset(offsetX + p.x * scale, offsetY + p.y * scale)
                }

                fun toBitmap(offset: Offset): Point2D {
                    val bx = ((offset.x - offsetX) / scale).coerceIn(0f, bmpW)
                    val by = ((offset.y - offsetY) / scale).coerceIn(0f, bmpH)
                    return Point2D(bx, by)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(scale, offsetX, offsetY, bmpW, bmpH) {
                            val touchThresholdPx = 54.dp.toPx()
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val currentCorners = cornersState.value
                                    val cornerPoints = listOf(
                                        toScreen(currentCorners.topLeft),
                                        toScreen(currentCorners.topRight),
                                        toScreen(currentCorners.bottomRight),
                                        toScreen(currentCorners.bottomLeft)
                                    )

                                    var closestIdx: Int? = null
                                    var closestDist = Float.MAX_VALUE
                                    cornerPoints.forEachIndexed { index, pt ->
                                        val dist = (pt - startOffset).getDistance()
                                        if (dist < touchThresholdPx && dist < closestDist) {
                                            closestDist = dist
                                            closestIdx = index
                                        }
                                    }
                                    activeCornerIndex = closestIdx
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val idx = activeCornerIndex ?: return@detectDragGestures
                                    val newBitmapPt = toBitmap(change.position)

                                    val current = cornersState.value
                                    corners = when (idx) {
                                        0 -> current.copy(topLeft = newBitmapPt)
                                        1 -> current.copy(topRight = newBitmapPt)
                                        2 -> current.copy(bottomRight = newBitmapPt)
                                        else -> current.copy(bottomLeft = newBitmapPt)
                                    }
                                },
                                onDragEnd = {
                                    activeCornerIndex = null
                                },
                                onDragCancel = {
                                    activeCornerIndex = null
                                }
                            )
                        }
                ) {
                    // Background Card Photo centered exactly at offsetX, offsetY
                    Image(
                        bitmap = displayImageBitmap,
                        contentDescription = "Card raw capture",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                    )

                    // Canvas drawing Polygon and 4 Corner Pins
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pTL = toScreen(corners.topLeft)
                        val pTR = toScreen(corners.topRight)
                        val pBR = toScreen(corners.bottomRight)
                        val pBL = toScreen(corners.bottomLeft)

                        val strokeColor = if (isValid) Color(0xFF10B981) else Color(0xFFEF4444)

                        // Draw Quadrilateral boundary
                        val path = Path().apply {
                            moveTo(pTL.x, pTL.y)
                            lineTo(pTR.x, pTR.y)
                            lineTo(pBR.x, pBR.y)
                            lineTo(pBL.x, pBL.y)
                            close()
                        }

                        drawPath(
                            path = path,
                            color = strokeColor.copy(alpha = 0.25f)
                        )
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Draw 4 interactive corner pins with crosshairs for precision alignment
                        listOf(
                            pTL to "TL",
                            pTR to "TR",
                            pBR to "BR",
                            pBL to "BL"
                        ).forEachIndexed { index, (pt, label) ->
                            val isCurrentActive = (index == activeCornerIndex)
                            val pinRadius = if (isCurrentActive) 24.dp.toPx() else 18.dp.toPx()

                            // If active, draw precision crosshairs extending from handle
                            if (isCurrentActive) {
                                val crossLen = 36.dp.toPx()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.7f),
                                    start = Offset(pt.x - crossLen, pt.y),
                                    end = Offset(pt.x + crossLen, pt.y),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.7f),
                                    start = Offset(pt.x, pt.y - crossLen),
                                    end = Offset(pt.x, pt.y + crossLen),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }

                            // Outer contrast glow ring
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.45f),
                                radius = pinRadius + 4.dp.toPx(),
                                center = pt
                            )
                            // Outer white ring
                            drawCircle(
                                color = Color.White,
                                radius = pinRadius,
                                center = pt
                            )
                            // Inner colored ring
                            drawCircle(
                                color = if (isCurrentActive) Color(0xFF38BDF8) else strokeColor,
                                radius = pinRadius - 3.5.dp.toPx(),
                                center = pt
                            )
                            // Center bullseye
                            drawCircle(
                                color = Color.White,
                                radius = if (isCurrentActive) 5.dp.toPx() else 4.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            // Bottom Buttons
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retake", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            corners = initialCorners
                            onReset()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (isValid) {
                                onConfirmed(corners)
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CollegePrimary)
                    ) {
                        Text("Confirm Crop")
                    }
                }
            }
        }
    }
}
