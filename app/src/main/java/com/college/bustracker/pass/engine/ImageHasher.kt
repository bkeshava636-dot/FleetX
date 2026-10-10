package com.college.bustracker.pass.engine

import android.graphics.Bitmap

/**
 * Calculates perceptual difference hashes (dHash) to detect duplicate front/back scans.
 */
object ImageHasher {

    /**
     * Computes a 64-bit perceptual gradient difference hash for an image.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, false)
        val pixels = IntArray(9 * 8)
        scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = lum(pixels[y * 9 + x])
                val right = lum(pixels[y * 9 + (x + 1)])
                if (left > right) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        return hash
    }

    /**
     * Calculates the Hamming distance (number of differing bits) between two hashes.
     * Distance in range [0, 64]. Values <= 9 indicate high visual similarity.
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Checks if two bitmaps appear to be the same image side.
     */
    fun areSimilar(bitmap1: Bitmap, bitmap2: Bitmap, thresholdDistance: Int = 9): Boolean {
        return try {
            val h1 = computeDHash(bitmap1)
            val h2 = computeDHash(bitmap2)
            hammingDistance(h1, h2) <= thresholdDistance
        } catch (e: Exception) {
            false
        }
    }

    private inline fun lum(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (77 * r + 150 * g + 29 * b) shr 8
    }
}
