package com.rui.photostudio.engine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offline detail and crop pipeline.
 * CPU reference implementation; designed to be replaceable by the GLES backend.
 */
data class DetailAdjustments(
    /** Fine surface micro-contrast, range -100..100. */
    val texture: Float = 0f,
    /** Mid-frequency local contrast, range -100..100. */
    val clarity: Float = 0f,
    /** High-frequency detail enhancement, range 0..150. */
    val detail: Float = 0f,
    /** Unsharp-mask amount, range 0..200. */
    val sharpening: Float = 0f,
    /** Luminance noise reduction, range 0..100. */
    val noiseReduction: Float = 0f,
    /** Chroma speckle reduction, range 0..100. */
    val colorNoiseReduction: Float = 0f,
    /** General isolated-pixel cleanup, range 0..100. */
    val speckleRemoval: Float = 0f
)

data class CropSpec(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) {
    fun normalized(): CropSpec {
        val l = left.coerceIn(0f, 0.999f)
        val t = top.coerceIn(0f, 0.999f)
        return copy(
            left = l,
            top = t,
            right = right.coerceIn(l + 0.001f, 1f),
            bottom = bottom.coerceIn(t + 0.001f, 1f)
        )
    }
}

object DetailEngine {
    /**
     * Applies spatial detail controls without modifying the source bitmap.
     * Processing is local and deterministic; no network or cloud service is used.
     */
    fun apply(source: Bitmap, settings: DetailAdjustments): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 3 || h < 3) return source.copy(Bitmap.Config.ARGB_8888, true)
        val input = IntArray(w * h)
        source.getPixels(input, 0, w, 0, 0, w, h)
        val blur = boxBlur(input, w, h)
        val amountTexture = settings.texture.coerceIn(-100f, 100f) / 100f
        val amountClarity = settings.clarity.coerceIn(-100f, 100f) / 100f
        val amountDetail = settings.detail.coerceIn(0f, 150f) / 100f
        val amountSharp = settings.sharpening.coerceIn(0f, 200f) / 100f
        val lumaDenoise = settings.noiseReduction.coerceIn(0f, 100f) / 100f
        val chromaDenoise = settings.colorNoiseReduction.coerceIn(0f, 100f) / 100f
        val speckle = settings.speckleRemoval.coerceIn(0f, 100f) / 100f
        val output = IntArray(input.size)

        for (i in input.indices) {
            val original = input[i]
            val base = blur[i]
            val r = (original ushr 16) and 255
            val g = (original ushr 8) and 255
            val b = original and 255
            val br = (base ushr 16) and 255
            val bg = (base ushr 8) and 255
            val bb = base and 255

            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val baseLum = 0.2126f * br + 0.7152f * bg + 0.0722f * bb
            val high = lum - baseLum
            val mid = (lum - baseLum) * (1f + amountClarity * 1.8f)
            val micro = high * amountTexture * 1.35f
            val sharp = high * amountSharp
            val enhanced = high * amountDetail

            // Luminance denoise preserves edges by reducing the blur blend near strong edges.
            val edge = (abs(high) / 42f).coerceIn(0f, 1f)
            val lumaBlend = (lumaDenoise * (1f - edge * 0.82f)).coerceIn(0f, 1f)
            val totalDetail = mid * amountClarity + micro + sharp + enhanced
            var rr = r + totalDetail - (r - br) * lumaBlend
            var gg = g + totalDetail - (g - bg) * lumaBlend
            var bb2 = b + totalDetail - (b - bb) * lumaBlend

            // Chroma-only smoothing: move channels toward the local neutral/luma relationship.
            val colorMix = (chromaDenoise * 0.72f + speckle * 0.48f).coerceIn(0f, 0.95f)
            if (colorMix > 0f) {
                val targetR = baseLum + (br - baseLum) * (1f - colorMix)
                val targetG = baseLum + (bg - baseLum) * (1f - colorMix)
                val targetB = baseLum + (bb - baseLum) * (1f - colorMix)
                rr = rr * (1f - colorMix) + targetR * colorMix
                gg = gg * (1f - colorMix) + targetG * colorMix
                bb2 = bb2 * (1f - colorMix) + targetB * colorMix
            }
            output[i] = (original and -0x1000000) or
                (rr.roundToInt().coerceIn(0, 255) shl 16) or
                (gg.roundToInt().coerceIn(0, 255) shl 8) or
                bb2.roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(output, 0, w, 0, 0, w, h)
        }
    }

    /** Crops, rotates and flips into a new bitmap; the original remains untouched. */
    fun crop(source: Bitmap, spec: CropSpec): Bitmap {
        val s = spec.normalized()
        val x = (s.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val y = (s.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val right = (s.right * source.width).roundToInt().coerceIn(x + 1, source.width)
        val bottom = (s.bottom * source.height).roundToInt().coerceIn(y + 1, source.height)
        val cropped = Bitmap.createBitmap(source, x, y, right - x, bottom - y)
        val matrix = android.graphics.Matrix().apply {
            postScale(if (s.flipHorizontal) -1f else 1f, if (s.flipVertical) -1f else 1f)
            if (s.rotationDegrees % 360f != 0f) postRotate(s.rotationDegrees)
        }
        return if (matrix.isIdentity) cropped else Bitmap.createBitmap(
            cropped, 0, 0, cropped.width, cropped.height, matrix, true
        )
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var ar = 0; var ag = 0; var ab = 0; var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val yy = (y + dy).coerceIn(0, h - 1)
                val c = src[yy * w + xx]
                ar += (c ushr 16) and 255
                ag += (c ushr 8) and 255
                ab += c and 255
                count++
            }
            out[y * w + x] = (src[y * w + x] and -0x1000000) or
                ((ar / count) shl 16) or ((ag / count) shl 8) or (ab / count)
        }
        return out
    }
}
