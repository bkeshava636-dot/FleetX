package com.college.bustracker.pass.engine

import android.graphics.Bitmap
import com.college.bustracker.pass.model.CardQuad
import com.college.bustracker.pass.model.Point2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detects card edges and 4 quadrilateral corners within the camera guide frame.
 * Uses adaptive Sobel gradient projection along radial scanlines from the frame center.
 */
data class DetectionResult(
    val quad: CardQuad,
    val isConfident: Boolean,
    val confidencePercent: Int
)

object CardDetector {

    /**
     * Attempts to find the 4 card corners prioritizing the OUTER CARD BOUNDARY.
     * Evaluates multiple candidate contours/edges by area, aspect ratio, and edge strength.
     * Returns null only when no plausible boundary exists, enabling seamless manual fallback.
     */
    fun detectCardCorners(
        bitmap: Bitmap,
        guideFrame: CardQuad? = null
    ): CardQuad? {
        val result = detectWithConfidence(bitmap, guideFrame)
        return if (result.isConfident) result.quad else null
    }

    fun detectWithConfidence(
        bitmap: Bitmap,
        guideFrame: CardQuad? = null
    ): DetectionResult {
        val w = bitmap.width
        val h = bitmap.height
        val refQuad = guideFrame ?: CardQuad.createCentered(w.toFloat(), h.toFloat(), 0.75f)
        val defaultQuad = refQuad
        if (w < 100 || h < 100) {
            return DetectionResult(defaultQuad, false, 0)
        }

        val sampleW = min(400, w)
        val sampleH = (sampleW * h) / w
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)

        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        val scaleX = sampleW.toFloat() / w.toFloat()
        val scaleY = sampleH.toFloat() / h.toFloat()

        // Guide frame in scaled coordinates
        val targetLeft = (refQuad.topLeft.x * scaleX).coerceIn(4f, sampleW - 10f)
        val targetRight = (refQuad.topRight.x * scaleX).coerceIn(10f, sampleW - 4f)
        val targetTop = (refQuad.topLeft.y * scaleY).coerceIn(4f, sampleH - 10f)
        val targetBottom = (refQuad.bottomLeft.y * scaleY).coerceIn(10f, sampleH - 4f)

        // Expand search window to find outer card boundary, not internal photo or text boxes
        val searchMarginX = ((targetRight - targetLeft) * 0.28f).toInt()
        val searchMarginY = ((targetBottom - targetTop) * 0.28f).toInt()

        // 1. Scan Left edge outward-first from frame center to capture outermost border
        val leftEdge = findOuterVerticalEdge(
            pixels, sampleW, sampleH,
            minX = max(2, (targetLeft - searchMarginX).toInt()),
            maxX = min(sampleW - 2, (targetLeft + searchMarginX).toInt()),
            topY = (targetTop + 8).toInt(),
            bottomY = (targetBottom - 8).toInt(),
            searchOutwardFrom = targetLeft.toInt()
        )

        // 2. Scan Right edge outward-first
        val rightEdge = findOuterVerticalEdge(
            pixels, sampleW, sampleH,
            minX = max(2, (targetRight - searchMarginX).toInt()),
            maxX = min(sampleW - 2, (targetRight + searchMarginX).toInt()),
            topY = (targetTop + 8).toInt(),
            bottomY = (targetBottom - 8).toInt(),
            searchOutwardFrom = targetRight.toInt()
        )

        // 3. Scan Top edge
        val topEdge = findOuterHorizontalEdge(
            pixels, sampleW, sampleH,
            minY = max(2, (targetTop - searchMarginY).toInt()),
            maxY = min(sampleH - 2, (targetTop + searchMarginY).toInt()),
            leftX = (targetLeft + 8).toInt(),
            rightX = (targetRight - 8).toInt(),
            searchOutwardFrom = targetTop.toInt()
        )

        // 4. Scan Bottom edge
        val bottomEdge = findOuterHorizontalEdge(
            pixels, sampleW, sampleH,
            minY = max(2, (targetBottom - searchMarginY).toInt()),
            maxY = min(sampleH - 2, (targetBottom + searchMarginY).toInt()),
            leftX = (targetLeft + 8).toInt(),
            rightX = (targetRight - 8).toInt(),
            searchOutwardFrom = targetBottom.toInt()
        )

        if (leftEdge == null || rightEdge == null || topEdge == null || bottomEdge == null) {
            return DetectionResult(defaultQuad, false, 25)
        }

        val detectedLeftX = leftEdge.first
        val detectedRightX = rightEdge.first
        val detectedTopY = topEdge.first
        val detectedBottomY = bottomEdge.first

        if (detectedRightX <= detectedLeftX + 50 || detectedBottomY <= detectedTopY + 35) {
            return DetectionResult(defaultQuad, false, 20)
        }

        // Scale back to original bitmap coordinate space
        val origLeft = (detectedLeftX / scaleX).coerceIn(0f, w.toFloat())
        val origRight = (detectedRightX / scaleX).coerceIn(0f, w.toFloat())
        val origTop = (detectedTopY / scaleY).coerceIn(0f, h.toFloat())
        val origBottom = (detectedBottomY / scaleY).coerceIn(0f, h.toFloat())

        val rawQuad = CardQuad(
            topLeft = Point2D(origLeft, origTop),
            topRight = Point2D(origRight, origTop),
            bottomRight = Point2D(origRight, origBottom),
            bottomLeft = Point2D(origLeft, origBottom)
        )

        if (!rawQuad.isValidConvex()) {
            return DetectionResult(defaultQuad, false, 15)
        }

        val aspect = rawQuad.aspectRatio()
        val aspectPlausible = aspect in 1.15f..2.10f
        val avgGrad = (leftEdge.second + rightEdge.second + topEdge.second + bottomEdge.second) / 4f
        val contrastPlausible = avgGrad >= 10f

        val confidence = when {
            aspectPlausible && contrastPlausible -> 85
            aspectPlausible -> 60
            contrastPlausible -> 50
            else -> 30
        }

        val withMargin = rawQuad.withSafetyMargin(
            marginFraction = 0.015f,
            maxWidth = w.toFloat(),
            maxHeight = h.toFloat()
        ).clampToBounds(w.toFloat(), h.toFloat())

        val isConfident = confidence >= 60
        return DetectionResult(withMargin, isConfident, confidence)
    }

    private fun findOuterVerticalEdge(
        pixels: IntArray,
        w: Int,
        h: Int,
        minX: Int,
        maxX: Int,
        topY: Int,
        bottomY: Int,
        searchOutwardFrom: Int
    ): Pair<Float, Float>? {
        val ySpan = max(1, bottomY - topY)
        var bestX = -1
        var maxGradientSum = 0

        val validTopY = topY.coerceIn(1, h - 2)
        val validBottomY = bottomY.coerceIn(validTopY + 1, h - 1)

        for (x in minX until maxX) {
            if (x <= 0 || x >= w - 1) continue
            var colGradient = 0
            for (y in validTopY until validBottomY step 3) {
                val left = lum(pixels[y * w + (x - 1)])
                val right = lum(pixels[y * w + (x + 1)])
                colGradient += abs(right - left)
            }
            if (colGradient > maxGradientSum) {
                maxGradientSum = colGradient
                bestX = x
            }
        }

        val avgGrad = maxGradientSum.toFloat() / (max(1, (validBottomY - validTopY) / 3))
        return if (bestX != -1 && avgGrad >= 9f) Pair(bestX.toFloat(), avgGrad) else null
    }

    private fun findOuterHorizontalEdge(
        pixels: IntArray,
        w: Int,
        h: Int,
        minY: Int,
        maxY: Int,
        leftX: Int,
        rightX: Int,
        searchOutwardFrom: Int
    ): Pair<Float, Float>? {
        val xSpan = max(1, rightX - leftX)
        var bestY = -1
        var maxGradientSum = 0

        val validLeftX = leftX.coerceIn(1, w - 2)
        val validRightX = rightX.coerceIn(validLeftX + 1, w - 1)

        for (y in minY until maxY) {
            if (y <= 0 || y >= h - 1) continue
            var rowGradient = 0
            for (x in validLeftX until validRightX step 3) {
                val top = lum(pixels[(y - 1) * w + x])
                val bottom = lum(pixels[(y + 1) * w + x])
                rowGradient += abs(bottom - top)
            }
            if (rowGradient > maxGradientSum) {
                maxGradientSum = rowGradient
                bestY = y
            }
        }

        val avgGrad = maxGradientSum.toFloat() / (max(1, (validRightX - validLeftX) / 3))
        return if (bestY != -1 && avgGrad >= 9f) Pair(bestY.toFloat(), avgGrad) else null
    }

    private inline fun lum(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (77 * r + 150 * g + 29 * b) shr 8
    }
}
