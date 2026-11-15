package com.rui.photostudio.engine

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full-resolution, single-channel mask data.
 *
 * Values are normalized coverage (0 = excluded, 1 = fully selected). This is
 * deliberately independent of UI so brush, gradient and AI masks share the
 * same pixel representation and can be serialized by the project layer.
 */
class PixelMask private constructor(
    val width: Int,
    val height: Int,
    internal val coverage: FloatArray
) {
    init {
        require(width > 0 && height > 0)
        require(coverage.size == width * height)
    }

    fun valueAt(x: Int, y: Int): Float =
        coverage[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]

    fun inverted(): PixelMask = create(width, height) { i -> 1f - coverage[i] }

    fun copy(): PixelMask = PixelMask(width, height, coverage.copyOf())

    fun combine(other: PixelMask, operation: MaskOperation): PixelMask {
        require(width == other.width && height == other.height) {
            "Masks must have identical dimensions"
        }
        return create(width, height) { i ->
            val a = coverage[i].coerceIn(0f, 1f)
            val b = other.coverage[i].coerceIn(0f, 1f)
            when (operation) {
                MaskOperation.ADD -> max(a, b)
                MaskOperation.INTERSECT -> min(a, b)
                MaskOperation.SUBTRACT -> a * (1f - b)
                MaskOperation.XOR -> a * (1f - b) + b * (1f - a)
            }.coerceIn(0f, 1f)
        }
    }

    /** Gaussian feather. Radius is in source-image pixels. */
    fun feather(radius: Float): PixelMask {
        val sigma = radius.coerceIn(0f, 128f)
        if (sigma < 0.5f) return copy()
        val kernelRadius = (sigma * 3f).toInt().coerceIn(1, 32)
        val kernel = FloatArray(kernelRadius * 2 + 1)
        var total = 0f
        for (i in -kernelRadius..kernelRadius) {
            val weight = exp(-(i * i) / (2f * sigma * sigma))
            kernel[i + kernelRadius] = weight
            total += weight
        }
        for (i in kernel.indices) kernel[i] /= total

        val temp = FloatArray(coverage.size)
        val output = FloatArray(coverage.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            for (k in -kernelRadius..kernelRadius) {
                sum += coverage[y * width + (x + k).coerceIn(0, width - 1)] * kernel[k + kernelRadius]
            }
            temp[y * width + x] = sum
        }
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            for (k in -kernelRadius..kernelRadius) {
                sum += temp[(y + k).coerceIn(0, height - 1) * width + x] * kernel[k + kernelRadius]
            }
            output[y * width + x] = sum.coerceIn(0f, 1f)
        }
        return PixelMask(width, height, output)
    }

    /** Export a red overlay whose alpha is the actual selection coverage. */
    fun toOverlayBitmap(): Bitmap {
        val pixels = IntArray(coverage.size)
        for (i in coverage.indices) {
            val alpha = (coverage[i].coerceIn(0f, 1f) * 170f).toInt()
            pixels[i] = (alpha shl 24) or 0x00FF3B30
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    fun toGrayscaleBitmap(): Bitmap {
        val pixels = IntArray(coverage.size)
        for (i in coverage.indices) {
            val v = (coverage[i].coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    companion object {
        fun empty(width: Int, height: Int): PixelMask =
            PixelMask(width, height, FloatArray(width * height))

        fun full(width: Int, height: Int): PixelMask =
            PixelMask(width, height, FloatArray(width * height) { 1f })

        fun fromBitmap(bitmap: Bitmap): PixelMask {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val values = FloatArray(pixels.size) { i ->
                val p = pixels[i]
                (((p ushr 24) and 255) / 255f) *
                    (0.2126f * ((p ushr 16) and 255) / 255f +
                     0.7152f * ((p ushr 8) and 255) / 255f +
                     0.0722f * (p and 255) / 255f)
            }
            return PixelMask(bitmap.width, bitmap.height, values)
        }

        fun brush(
            width: Int,
            height: Int,
            points: List<BrushPoint>,
            diameter: Float,
            hardness: Float = 0.8f,
            add: Boolean = true
        ): PixelMask {
            val result = FloatArray(width * height)
            val radius = diameter.coerceAtLeast(1f) / 2f
            val hard = hardness.coerceIn(0f, 1f)
            if (points.isEmpty()) return PixelMask(width, height, result)
            fun dab(cx: Float, cy: Float) {
                val left = (cx - radius).toInt().coerceAtLeast(0)
                val right = (cx + radius).toInt().coerceAtMost(width - 1)
                val top = (cy - radius).toInt().coerceAtLeast(0)
                val bottom = (cy + radius).toInt().coerceAtMost(height - 1)
                for (y in top..bottom) for (x in left..right) {
                    val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radius
                    if (d > 1f) continue
                    val edge = ((1f - d) / (1f - hard).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                    val amount = if (d <= hard) 1f else edge * edge * (3f - 2f * edge)
                    val index = y * width + x
                    result[index] = if (add) max(result[index], amount) else result[index] * (1f - amount)
                }
            }
            if (points.size == 1) dab(points[0].x, points[0].y)
            else for (i in 0 until points.lastIndex) {
                val a = points[i]
                val b = points[i + 1]
                val distance = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
                val steps = (distance / (radius * 0.22f).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
                for (step in 0..steps) {
                    val t = step.toFloat() / steps
                    dab(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
            }
            return PixelMask(width, height, result)
        }

        fun linearGradient(
            width: Int, height: Int,
            startX: Float, startY: Float, endX: Float, endY: Float,
            reverse: Boolean = false
        ): PixelMask {
            val dx = endX - startX
            val dy = endY - startY
            val length2 = (dx * dx + dy * dy).coerceAtLeast(0.000001f)
            return create(width, height) { i ->
                val x = (i % width + 0.5f) / width
                val y = (i / width + 0.5f) / height
                val t = (((x - startX) * dx + (y - startY) * dy) / length2).coerceIn(0f, 1f)
                val smooth = t * t * (3f - 2f * t)
                if (reverse) smooth else 1f - smooth
            }
        }

        fun radialGradient(
            width: Int, height: Int,
            centerX: Float, centerY: Float,
            radiusX: Float, radiusY: Float,
            feather: Float = 0.35f,
            invert: Boolean = false
        ): PixelMask = create(width, height) { i ->
            val x = (i % width + 0.5f) / width
            val y = (i / width + 0.5f) / height
            val nx = (x - centerX) / radiusX.coerceAtLeast(0.001f)
            val ny = (y - centerY) / radiusY.coerceAtLeast(0.001f)
            val d = sqrt(nx * nx + ny * ny)
            val edge = (1f - feather).coerceIn(0f, 0.99f)
            val t = ((1f - d) / (1f - edge)).coerceIn(0f, 1f)
            val smooth = t * t * (3f - 2f * t)
            if (invert) 1f - smooth else smooth
        }

        private inline fun create(width: Int, height: Int, value: (Int) -> Float): PixelMask {
            require(width > 0 && height > 0)
            return PixelMask(width, height, FloatArray(width * height) { value(it).coerceIn(0f, 1f) })
        }
    }
}

data class BrushPoint(val x: Float, val y: Float)

enum class MaskOperation { ADD, INTERSECT, SUBTRACT, XOR }
