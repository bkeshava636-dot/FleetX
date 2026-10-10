package com.college.bustracker.pass.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.college.bustracker.pass.model.CardQuad
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Performs perspective transformation (homography) to un-warp tilted/perspective-distorted
 * card images into an aligned, upright rectangular card bitmap.
 */
object PerspectiveCorrector {

    // Standard ID-1 card aspect ratio (ISO/IEC 7810: 85.60 mm x 53.98 mm = 1.5857)
    const val STANDARD_CARD_ASPECT_RATIO = 1.586f

    /**
     * Corrects perspective distortion and crops the quadrilateral area into a straight card bitmap.
     */
    fun correctPerspective(
        sourceBitmap: Bitmap,
        quad: CardQuad,
        targetWidth: Int? = null,
        targetHeight: Int? = null
    ): Result<Bitmap> {
        return try {
            if (!quad.isValidConvex()) {
                return Result.failure(IllegalArgumentException("Quadrilateral must be convex for perspective transformation."))
            }

            val measuredW = quad.maxWidth()
            val measuredH = quad.maxHeight()

            // Calculate output dimensions
            val outW = targetWidth ?: run {
                val baseW = measuredW.roundToInt().coerceIn(700, 1800)
                baseW
            }

            val outH = targetHeight ?: run {
                // If measured aspect ratio is reasonably close to standard ID card, clamp to standard height
                val rawAspect = if (measuredH > 1f) measuredW / measuredH else STANDARD_CARD_ASPECT_RATIO
                if (rawAspect in 1.35f..1.85f) {
                    (outW / STANDARD_CARD_ASPECT_RATIO).roundToInt()
                } else {
                    measuredH.roundToInt().coerceIn(440, 1200)
                }
            }

            val safeW = max(100, min(outW, 2400))
            val safeH = max(100, min(outH, 1800))

            val srcPoints = quad.toFloatArray()
            val dstPoints = floatArrayOf(
                0f, 0f,
                safeW.toFloat(), 0f,
                safeW.toFloat(), safeH.toFloat(),
                0f, safeH.toFloat()
            )

            // Matrix.setPolyToPoly computes 3x3 perspective homography
            val matrix = Matrix()
            val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
            if (!success) {
                return Result.failure(IllegalStateException("Perspective matrix calculation failed for points."))
            }

            val outputBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            val paint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
                isDither = true
            }

            // Draw transformed bitmap onto canvas
            canvas.drawBitmap(sourceBitmap, matrix, paint)

            Result.success(outputBitmap)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
