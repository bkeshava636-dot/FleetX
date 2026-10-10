package com.college.bustracker.pass.engine

import android.graphics.Bitmap
import com.college.bustracker.pass.model.CardQuad
import com.college.bustracker.pass.model.CardQualityResult
import com.college.bustracker.pass.model.Point2D
import com.college.bustracker.pass.model.QualityCheckIssue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Validates document scan quality, sharpness, illumination, glare, and geometry.
 * Pure algorithmic checks — zero cloud/network dependencies, fast and deterministic.
 */
object QualityChecker {

    const val MIN_WIDTH = 480
    const val MIN_HEIGHT = 320
    const val MIN_SHARPNESS_SCORE = 38.0f // Realistic handheld camera sharpness threshold
    const val MIN_MEAN_LUMINANCE = 35.0f
    const val MAX_MEAN_LUMINANCE = 238.0f
    const val MAX_GLARE_RATIO = 0.16f // Tolerates laminated bus pass reflections

    /**
     * Runs complete quality audit against a captured bitmap and its detected/specified corners.
     */
    fun evaluateQuality(
        bitmap: Bitmap,
        corners: CardQuad? = null,
        guideFrame: CardQuad? = null,
        rotationDegrees: Int = 0,
        isCardDetected: Boolean = corners != null,
        confidencePercent: Int = 75
    ): CardQualityResult {
        val w = bitmap.width
        val h = bitmap.height

        // 1. Resolution Check
        if (w < MIN_WIDTH || h < MIN_HEIGHT) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.RESOLUTION_LOW,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        // 2. Corner / Detection Existence Check
        val effectiveCorners = corners ?: CardQuad.createCentered(w.toFloat(), h.toFloat(), 0.85f)
        if (corners == null) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.CARD_NOT_DETECTED,
                detectedCorners = null,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = false
            )
        }

        // 3. Convexity & Distortion Check
        if (!effectiveCorners.isValidConvex()) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.PERSPECTIVE_DISTORTION,
                detectedCorners = effectiveCorners,
                isConvex = false,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        val aspect = effectiveCorners.aspectRatio()
        if (aspect < 1.05f || aspect > 2.35f) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.PERSPECTIVE_DISTORTION,
                detectedCorners = effectiveCorners,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        // 4. Boundary and Coverage Check
        val cardArea = effectiveCorners.area()
        val totalArea = (w * h).toFloat()
        // If guideFrame is supplied, compare relative to guide frame (scaled), otherwise relative to image
        val frameArea = guideFrame?.area() ?: (totalArea * 0.70f)
        val coverageRatio = if (frameArea > 1f) cardArea / frameArea else 0.75f

        // Reject only if extremely far away (< 20% of frame)
        if (coverageRatio < 0.22f) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.CARD_TOO_FAR,
                detectedCorners = effectiveCorners,
                coverageFraction = coverageRatio,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        // Reject if card extends outside the camera bitmap boundary
        if (!effectiveCorners.isWithinBounds(w.toFloat(), h.toFloat(), margin = 0f)) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.CARD_TOO_CLOSE,
                detectedCorners = effectiveCorners,
                coverageFraction = coverageRatio,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        // 5. Pixel Luminance, Glare, and Obstruction analysis
        val sampleW = min(320, w)
        val sampleH = (sampleW * h) / w
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        var totalLum = 0.0
        var glareCount = 0
        val totalPixels = pixels.size

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val color = pixels[y * sampleW + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                val lum = (77 * r + 150 * g + 29 * b) shr 8
                totalLum += lum

                if (lum >= 252) {
                    glareCount++
                }
            }
        }

        val meanLuminance = (totalLum / totalPixels).toFloat()
        val glareRatio = glareCount.toFloat() / totalPixels.toFloat()

        if (meanLuminance < MIN_MEAN_LUMINANCE) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.TOO_DARK,
                detectedCorners = effectiveCorners,
                meanBrightness = meanLuminance,
                glarePixelFraction = glareRatio,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        if (meanLuminance > MAX_MEAN_LUMINANCE) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.TOO_BRIGHT,
                detectedCorners = effectiveCorners,
                meanBrightness = meanLuminance,
                glarePixelFraction = glareRatio,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        if (glareRatio > MAX_GLARE_RATIO) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.TOO_MUCH_GLARE,
                detectedCorners = effectiveCorners,
                meanBrightness = meanLuminance,
                glarePixelFraction = glareRatio,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        // 6. Sharpness (Laplacian Energy on Luminance)
        val sharpness = computeLaplacianSharpness(pixels, sampleW, sampleH)
        if (sharpness < MIN_SHARPNESS_SCORE) {
            return CardQualityResult(
                isAccepted = false,
                primaryIssue = QualityCheckIssue.IMAGE_BLURRY,
                detectedCorners = effectiveCorners,
                meanBrightness = meanLuminance,
                sharpnessScore = sharpness,
                glarePixelFraction = glareRatio,
                imageWidth = w,
                imageHeight = h,
                rotationDegrees = rotationDegrees,
                confidencePercent = confidencePercent,
                isCardDetected = isCardDetected
            )
        }

        // All quality gates passed!
        return CardQualityResult(
            isAccepted = true,
            primaryIssue = null,
            detectedCorners = effectiveCorners,
            meanBrightness = meanLuminance,
            sharpnessScore = sharpness,
            glarePixelFraction = glareRatio,
            isConvex = true,
            coverageFraction = coverageRatio,
            imageWidth = w,
            imageHeight = h,
            rotationDegrees = rotationDegrees,
            confidencePercent = confidencePercent,
            isCardDetected = isCardDetected
        )
    }

    /**
     * Computes the variance of the Laplacian filter as a measure of high-frequency edge energy.
     */
    fun computeLaplacianSharpness(pixels: IntArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        // Subsample by stride of 2 for high performance
        val stride = 2
        for (y in 1 until (height - 1) step stride) {
            for (x in 1 until (width - 1) step stride) {
                val c = lum(pixels[y * width + x])
                val top = lum(pixels[(y - 1) * width + x])
                val bottom = lum(pixels[(y + 1) * width + x])
                val left = lum(pixels[y * width + (x - 1)])
                val right = lum(pixels[y * width + (x + 1)])

                val lap = (top + bottom + left + right) - (4 * c)
                val lapD = lap.toDouble()
                sum += lapD
                sumSq += lapD * lapD
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return variance.toFloat().coerceAtLeast(0f)
    }

    private inline fun lum(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (77 * r + 150 * g + 29 * b) shr 8
    }
}
