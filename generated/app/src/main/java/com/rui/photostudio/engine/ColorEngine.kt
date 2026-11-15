package com.rui.photostudio.engine

import android.graphics.Bitmap
import kotlin.math.*

/**
 * Offline, deterministic pixel pipeline. All values are normalized unless noted.
 * This CPU implementation is the correctness reference for the future GLES backend.
 */
data class PhotoAdjustments(
    val exposureEv: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f
)

data class HslBand(
    val hueCenter: Float,
    val hueWidth: Float = 35f,
    val hueShift: Float = 0f,
    val saturationShift: Float = 0f,
    val luminanceShift: Float = 0f
)

data class CurvePoint(val x: Float, val y: Float)

data class RadialMask(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val feather: Float = 0.35f,
    val invert: Boolean = false
) {
    fun weight(x: Float, y: Float): Float {
        val dx = (x - centerX) / radiusX.coerceAtLeast(0.001f)
        val dy = (y - centerY) / radiusY.coerceAtLeast(0.001f)
        val d = sqrt(dx * dx + dy * dy)
        val edge = (1f - feather).coerceIn(0f, 0.99f)
        val t = ((1f - d) / (1f - edge)).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        return if (invert) 1f - smooth else smooth
    }
}

enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, ADD, DARKEN, LIGHTEN }

data class PixelLayer(
    val bitmap: Bitmap,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val mask: RadialMask? = null,
    val visible: Boolean = true
)

data class Histogram(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val luminance: IntArray
)

object ColorEngine {
    fun apply(
        source: Bitmap,
        adjustments: PhotoAdjustments,
        hslBands: List<HslBand> = emptyList(),
        curve: List<CurvePoint> = emptyList(),
        mask: RadialMask? = null
    ): Bitmap {
        val w = source.width
        val h = source.height
        val input = IntArray(w * h)
        source.getPixels(input, 0, w, 0, 0, w, h)
        val output = IntArray(input.size)
        val exposure = 2.0.pow(adjustments.exposureEv.toDouble()).toFloat()
        for (i in input.indices) {
            val c = input[i]
            val x = (i % w + 0.5f) / w
            val y = (i / w + 0.5f) / h
            val local = mask?.weight(x, y) ?: 1f
            var r = ((c ushr 16) and 255) / 255f
            var g = ((c ushr 8) and 255) / 255f
            var b = (c and 255) / 255f
            val originalLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val shadowWeight = (1f - originalLuma).pow(2)
            val highlightWeight = originalLuma.pow(2)
            val gain = 1f + (adjustments.contrast - 1f) * local
            r = (r * exposure * (1f + adjustments.whites * highlightWeight * local) +
                adjustments.shadows * shadowWeight * 0.22f * local +
                adjustments.blacks * (1f - shadowWeight) * 0.12f * local)
            g = (g * exposure * (1f + adjustments.whites * highlightWeight * local) +
                adjustments.shadows * shadowWeight * 0.22f * local +
                adjustments.blacks * (1f - shadowWeight) * 0.12f * local)
            b = (b * exposure * (1f + adjustments.whites * highlightWeight * local) +
                adjustments.shadows * shadowWeight * 0.22f * local +
                adjustments.blacks * (1f - shadowWeight) * 0.12f * local)
            r = ((r - 0.5f) * gain + 0.5f + adjustments.temperature * 0.10f * local + adjustments.tint * 0.035f * local)
            g = ((g - 0.5f) * gain + 0.5f - adjustments.tint * 0.035f * local)
            b = ((b - 0.5f) * gain + 0.5f - adjustments.temperature * 0.10f * local + adjustments.tint * 0.035f * local)
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val sat = (adjustments.saturation + adjustments.vibrance * (1f - abs(r - g) - abs(g - b)).coerceIn(0f, 1f)).coerceAtLeast(0f)
            r = luma + (r - luma) * sat
            g = luma + (g - luma) * sat
            b = luma + (b - luma) * sat
            if (hslBands.isNotEmpty()) {
                val hsv = rgbToHsv(r, g, b)
                var hh = hsv[0]
                var ss = hsv[1]
                var vv = hsv[2]
                hslBands.forEach { band ->
                    val distance = min(abs(hh - band.hueCenter), 360f - abs(hh - band.hueCenter))
                    val influence = (1f - distance / band.hueWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    hh = (hh + band.hueShift * influence + 360f) % 360f
                    ss = (ss + band.saturationShift / 100f * influence).coerceIn(0f, 1f)
                    vv = (vv + band.luminanceShift / 100f * influence).coerceIn(0f, 1f)
                }
                val rgb = hsvToRgb(hh, ss, vv)
                r = rgb[0]; g = rgb[1]; b = rgb[2]
            }
            if (curve.size >= 2) {
                r = sampleCurve(curve, r)
                g = sampleCurve(curve, g)
                b = sampleCurve(curve, b)
            }
            output[i] = (c and -0x1000000) or
                ((r.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
                ((g.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or
                (b.coerceIn(0f, 1f) * 255f).roundToInt()
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(output, 0, w, 0, 0, w, h)
        }
    }

    fun composite(base: Bitmap, layers: List<PixelLayer>): Bitmap {
        val w = base.width
        val h = base.height
        val dst = IntArray(w * h)
        base.getPixels(dst, 0, w, 0, 0, w, h)
        layers.filter { it.visible }.forEach { layer ->
            val scaled = if (layer.bitmap.width == w && layer.bitmap.height == h) layer.bitmap else
                Bitmap.createScaledBitmap(layer.bitmap, w, h, true)
            val src = IntArray(w * h)
            scaled.getPixels(src, 0, w, 0, 0, w, h)
            for (i in dst.indices) {
                val x = (i % w + 0.5f) / w
                val y = (i / w + 0.5f) / h
                val mask = layer.mask?.weight(x, y) ?: 1f
                val alpha = (((src[i] ushr 24) and 255) / 255f * layer.opacity * mask).coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                val dr = ((dst[i] ushr 16) and 255) / 255f
                val dg = ((dst[i] ushr 8) and 255) / 255f
                val db = (dst[i] and 255) / 255f
                val sr = ((src[i] ushr 16) and 255) / 255f
                val sg = ((src[i] ushr 8) and 255) / 255f
                val sb = (src[i] and 255) / 255f
                val rr = blend(dr, sr, layer.blendMode) * alpha + dr * (1f - alpha)
                val rg = blend(dg, sg, layer.blendMode) * alpha + dg * (1f - alpha)
                val rb = blend(db, sb, layer.blendMode) * alpha + db * (1f - alpha)
                dst[i] = -0x1000000 or ((rr * 255).roundToInt().coerceIn(0,255) shl 16) or
                    ((rg * 255).roundToInt().coerceIn(0,255) shl 8) or (rb * 255).roundToInt().coerceIn(0,255)
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(dst, 0, w, 0, 0, w, h) }
    }

    fun histogram(bitmap: Bitmap): Histogram {
        val binsR = IntArray(256); val binsG = IntArray(256); val binsB = IntArray(256); val binsL = IntArray(256)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEach { c ->
            val r = (c ushr 16) and 255; val g = (c ushr 8) and 255; val b = c and 255
            binsR[r]++; binsG[g]++; binsB[b]++
            binsL[(0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0,255)]++
        }
        return Histogram(binsR, binsG, binsB, binsL)
    }

    private fun blend(dst: Float, src: Float, mode: BlendMode): Float = when (mode) {
        BlendMode.NORMAL -> src
        BlendMode.MULTIPLY -> dst * src
        BlendMode.SCREEN -> 1f - (1f - dst) * (1f - src)
        BlendMode.OVERLAY -> if (dst < 0.5f) 2f * dst * src else 1f - 2f * (1f - dst) * (1f - src)
        BlendMode.ADD -> (dst + src).coerceAtMost(1f)
        BlendMode.DARKEN -> min(dst, src)
        BlendMode.LIGHTEN -> max(dst, src)
    }

    private fun sampleCurve(points: List<CurvePoint>, value: Float): Float {
        val sorted = points.sortedBy { it.x }
        if (value <= sorted.first().x) return sorted.first().y
        if (value >= sorted.last().x) return sorted.last().y
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]; val b = sorted[i + 1]
            if (value in a.x..b.x) {
                val t = (value - a.x) / (b.x - a.x).coerceAtLeast(0.0001f)
                return a.y + (b.y - a.y) * t
            }
        }
        return value
    }

    private fun rgbToHsv(r0: Float, g0: Float, b0: Float): FloatArray {
        val r = r0.coerceIn(0f,1f); val g = g0.coerceIn(0f,1f); val b = b0.coerceIn(0f,1f)
        val mx = max(r, max(g,b)); val mn = min(r, min(g,b)); val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }

    private fun hsvToRgb(h0: Float, s: Float, v: Float): FloatArray {
        val h = (h0 % 360f + 360f) % 360f
        val c = v * s; val x = c * (1f - abs((h / 60f) % 2f - 1f)); val m = v - c
        val (r,g,b) = when (h) {
            in 0f..<60f -> Triple(c,x,0f)
            in 60f..<120f -> Triple(x,c,0f)
            in 120f..<180f -> Triple(0f,c,x)
            in 180f..<240f -> Triple(0f,x,c)
            in 240f..<300f -> Triple(x,0f,c)
            else -> Triple(c,0f,x)
        }
        return floatArrayOf(r+m,g+m,b+m)
    }
}
