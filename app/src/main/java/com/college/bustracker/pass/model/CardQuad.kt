package com.college.bustracker.pass.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Lightweight 2D point representation for cross-platform/unit-testable calculations.
 */
data class Point2D(val x: Float, val y: Float) {
    fun distanceTo(other: Point2D): Float = hypot(x - other.x, y - other.y)

    operator fun plus(other: Point2D): Point2D = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D): Point2D = Point2D(x - other.x, y - other.y)
}

/**
 * Four corners of a detected or manually adjusted card in clockwise order:
 * Top-Left -> Top-Right -> Bottom-Right -> Bottom-Left.
 */
data class CardQuad(
    val topLeft: Point2D,
    val topRight: Point2D,
    val bottomRight: Point2D,
    val bottomLeft: Point2D
) {
    /**
     * Checks whether the 4 points form a strictly convex quadrilateral without self-intersection.
     */
    fun isValidConvex(): Boolean {
        val points = listOf(topLeft, topRight, bottomRight, bottomLeft)
        var positive = 0
        var negative = 0

        for (i in 0 until 4) {
            val p0 = points[i]
            val p1 = points[(i + 1) % 4]
            val p2 = points[(i + 2) % 4]

            val cross = (p1.x - p0.x) * (p2.y - p1.y) - (p1.y - p0.y) * (p2.x - p1.x)
            if (cross > 0.001f) positive++
            else if (cross < -0.001f) negative++
        }

        // Strictly convex if all cross products have the same sign (either all > 0 or all < 0)
        return (positive == 4 && negative == 0) || (negative == 4 && positive == 0)
    }

    fun topWidth(): Float = topLeft.distanceTo(topRight)
    fun bottomWidth(): Float = bottomLeft.distanceTo(bottomRight)
    fun leftHeight(): Float = topLeft.distanceTo(bottomLeft)
    fun rightHeight(): Float = topRight.distanceTo(bottomRight)

    fun maxWidth(): Float = max(topWidth(), bottomWidth())
    fun maxHeight(): Float = max(leftHeight(), rightHeight())

    fun aspectRatio(): Float {
        val h = maxHeight()
        return if (h > 0.001f) maxWidth() / h else 0f
    }

    /**
     * Calculates the area using the Shoelace formula.
     */
    fun area(): Float {
        val x0 = topLeft.x; val y0 = topLeft.y
        val x1 = topRight.x; val y1 = topRight.y
        val x2 = bottomRight.x; val y2 = bottomRight.y
        val x3 = bottomLeft.x; val y3 = bottomLeft.y

        val shoelace = (x0 * y1 + x1 * y2 + x2 * y3 + x3 * y0) -
                (y0 * x1 + y1 * x2 + y2 * x3 + y3 * x0)
        return abs(shoelace) / 2f
    }

    /**
     * Checks if all corners are within given bounds [0, width] x [0, height].
     */
    fun isWithinBounds(width: Float, height: Float, margin: Float = 0f): Boolean {
        val corners = listOf(topLeft, topRight, bottomRight, bottomLeft)
        return corners.all { p ->
            p.x >= margin && p.x <= (width - margin) &&
            p.y >= margin && p.y <= (height - margin)
        }
    }

    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }

    /**
     * Expands the quadrilateral outwards from its centroid by a safety margin percentage (e.g. 0.04 = 4%),
     * clamping within the image boundary [0, maxWidth] x [0, maxHeight].
     * Guarantees that card edges and text are not accidentally cut off.
     */
    fun withSafetyMargin(marginFraction: Float = 0.04f, maxWidth: Float, maxHeight: Float): CardQuad {
        val centerX = (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4f
        val centerY = (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4f

        fun expand(p: Point2D): Point2D {
            val dx = p.x - centerX
            val dy = p.y - centerY
            val newX = (centerX + dx * (1f + marginFraction)).coerceIn(0f, maxWidth)
            val newY = (centerY + dy * (1f + marginFraction)).coerceIn(0f, maxHeight)
            return Point2D(newX, newY)
        }

        return CardQuad(
            topLeft = expand(topLeft),
            topRight = expand(topRight),
            bottomRight = expand(bottomRight),
            bottomLeft = expand(bottomLeft)
        )
    }

    fun centroid(): Point2D {
        val cx = (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4f
        val cy = (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4f
        return Point2D(cx, cy)
    }

    /**
     * Clamps all 4 points inside [0, maxWidth] x [0, maxHeight].
     */
    fun clampToBounds(maxWidth: Float, maxHeight: Float): CardQuad {
        return CardQuad(
            topLeft = Point2D(topLeft.x.coerceIn(0f, maxWidth), topLeft.y.coerceIn(0f, maxHeight)),
            topRight = Point2D(topRight.x.coerceIn(0f, maxWidth), topRight.y.coerceIn(0f, maxHeight)),
            bottomRight = Point2D(bottomRight.x.coerceIn(0f, maxWidth), bottomRight.y.coerceIn(0f, maxHeight)),
            bottomLeft = Point2D(bottomLeft.x.coerceIn(0f, maxWidth), bottomLeft.y.coerceIn(0f, maxHeight))
        )
    }

    fun clampToBounds(maxWidth: Int, maxHeight: Int): CardQuad =
        clampToBounds(maxWidth.toFloat(), maxHeight.toFloat())

    companion object {
        fun fromFloatArray(arr: FloatArray): CardQuad {
            require(arr.size >= 8) { "FloatArray must contain at least 8 elements" }
            return CardQuad(
                topLeft = Point2D(arr[0], arr[1]),
                topRight = Point2D(arr[2], arr[3]),
                bottomRight = Point2D(arr[4], arr[5]),
                bottomLeft = Point2D(arr[6], arr[7])
            )
        }

        /**
         * Creates a centered default card quad inside a given viewport (standard 1.586 ID card ratio).
         */
        fun createCentered(viewportWidth: Float, viewportHeight: Float, targetWidthFraction: Float = 0.85f): CardQuad {
            val targetW = viewportWidth * targetWidthFraction
            val targetH = targetW / 1.586f

            val left = (viewportWidth - targetW) / 2f
            val top = (viewportHeight - targetH) / 2f
            val right = left + targetW
            val bottom = top + targetH

            return CardQuad(
                topLeft = Point2D(left, top),
                topRight = Point2D(right, top),
                bottomRight = Point2D(right, bottom),
                bottomLeft = Point2D(left, bottom)
            )
        }
    }
}
