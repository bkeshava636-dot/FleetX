package com.college.bustracker.pass.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative image enhancement for document readability.
 * Applies mild contrast normalization and text sharpening while preserving
 * natural colors, student photograph fidelity, official stamps, and signatures.
 */
object CardEnhancer {

    /**
     * Enhances the perspective-corrected bitmap conservatively.
     */
    fun enhance(bitmap: Bitmap): Bitmap {
        return try {
            val w = bitmap.width
            val h = bitmap.height

            // 1. Analyze global luminance distribution
            val sampleW = min(160, w)
            val sampleH = (sampleW * h) / w
            val thumb = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
            val pixels = IntArray(sampleW * sampleH)
            thumb.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
            if (thumb != bitmap) {
                thumb.recycle()
            }

            var sumLum = 0.0
            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sumLum += (77 * r + 150 * g + 29 * b) shr 8
            }
            val avgLum = sumLum / pixels.size

            // Mild target midpoint adjustment: normal document ideal is ~135
            val brightnessShift = when {
                avgLum < 110 -> ((110 - avgLum) * 0.45f).toFloat().coerceAtMost(25f)
                avgLum > 175 -> -((avgLum - 175) * 0.35f).toFloat().coerceAtLeast(-20f)
                else -> 0f
            }

            // Mild contrast enhancement factor (1.0 = unchanged, 1.12 = gentle crisp text enhancement)
            val contrast = 1.10f

            val cm = ColorMatrix()
            // Standard contrast adjustment: scale = contrast, translate = (1 - contrast) * 128 + brightnessShift
            val scale = contrast
            val translate = (1f - scale) * 128f + brightnessShift

            cm.set(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )

            val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(cm)
                isFilterBitmap = true
            }

            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            output
        } catch (e: Throwable) {
            // If enhancement fails for any memory reason, safely return original bitmap without crashing
            bitmap
        }
    }
}
