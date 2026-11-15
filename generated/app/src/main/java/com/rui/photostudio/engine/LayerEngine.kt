package com.rui.photostudio.engine

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class LayerKind { IMAGE, ADJUSTMENT, GROUP }
enum class LayerBlendMode {
    NORMAL, MULTIPLY, SCREEN, OVERLAY, SOFT_LIGHT, HARD_LIGHT,
    DARKEN, LIGHTEN, COLOR, LUMINOSITY
}

/**
 * Non-destructive layer node. Image pixels are never modified by rendering.
 * Adjustment layers evaluate their settings against the accumulated image
 * below them; masks are full-resolution PixelMask coverage maps.
 */
data class LayerNode(
    val id: String,
    val name: String,
    val kind: LayerKind,
    val bitmap: Bitmap? = null,
    val adjustments: ProfessionalSettings? = null,
    val mask: PixelMask? = null,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val children: List<LayerNode> = emptyList()
) {
    fun renamed(value: String) = copy(name = value)
}

object LayerEngine {
    fun render(base: Bitmap, layers: List<LayerNode>): Bitmap {
        var composite = base.copy(Bitmap.Config.ARGB_8888, true)
        layers.forEach { layer ->
            if (!layer.visible || layer.opacity <= 0f) return@forEach
            composite = when (layer.kind) {
                LayerKind.IMAGE -> {
                    val image = layer.bitmap ?: return@forEach
                    blend(composite, image, layer.mask, layer.opacity, layer.blendMode)
                }
                LayerKind.ADJUSTMENT -> {
                    val settings = layer.adjustments ?: return@forEach
                    val changed = ProfessionalEngine.apply(composite, settings)
                    blend(composite, changed, layer.mask, layer.opacity, layer.blendMode)
                }
                LayerKind.GROUP -> {
                    if (layer.children.isEmpty()) return@forEach
                    val groupResult = renderTransparentGroup(composite, layer.children)
                    blend(composite, groupResult, layer.mask, layer.opacity, layer.blendMode)
                }
            }
        }
        return composite
    }

    private fun renderTransparentGroup(base: Bitmap, children: List<LayerNode>): Bitmap =
        render(base, children)

    private fun blend(
        base: Bitmap,
        source: Bitmap,
        mask: PixelMask?,
        opacity: Float,
        mode: LayerBlendMode
    ): Bitmap {
        val width = base.width
        val height = base.height
        val dst = IntArray(width * height)
        val srcBitmap = if (source.width == width && source.height == height) source
            else Bitmap.createScaledBitmap(source, width, height, true)
        val src = IntArray(width * height)
        base.getPixels(dst, 0, width, 0, 0, width, height)
        srcBitmap.getPixels(src, 0, width, 0, 0, width, height)
        val globalOpacity = opacity.coerceIn(0f, 1f)

        for (i in dst.indices) {
            val d = dst[i]
            val s = src[i]
            val maskWeight = if (mask == null) 1f else {
                val mx = ((i % width).toFloat() / width * mask.width).toInt().coerceIn(0, mask.width - 1)
                val my = ((i / width).toFloat() / height * mask.height).toInt().coerceIn(0, mask.height - 1)
                mask.valueAt(mx, my)
            }
            val sa = ((s ushr 24) and 255) / 255f * globalOpacity * maskWeight
            if (sa <= 0f) continue
            val da = ((d ushr 24) and 255) / 255f
            val dr = ((d ushr 16) and 255) / 255f
            val dg = ((d ushr 8) and 255) / 255f
            val db = (d and 255) / 255f
            val sr = ((s ushr 16) and 255) / 255f
            val sg = ((s ushr 8) and 255) / 255f
            val sb = (s and 255) / 255f

            val blended = blendRgb(dr, dg, db, sr, sg, sb, mode)
            val outA = sa + da * (1f - sa)
            fun channel(dstC: Float, srcC: Float, blendC: Float): Int {
                if (outA <= 0f) return 0
                val premultiplied = blendC * sa + dstC * da * (1f - sa)
                return (premultiplied / outA * 255f).roundToInt().coerceIn(0, 255)
            }
            val alpha = (outA * 255f).roundToInt().coerceIn(0, 255)
            dst[i] = (alpha shl 24) or
                (channel(dr, sr, blended[0]) shl 16) or
                (channel(dg, sg, blended[1]) shl 8) or
                channel(db, sb, blended[2])
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(dst, 0, width, 0, 0, width, height)
        }
    }

    private fun blendRgb(
        dr: Float, dg: Float, db: Float,
        sr: Float, sg: Float, sb: Float,
        mode: LayerBlendMode
    ): FloatArray {
        if (mode == LayerBlendMode.COLOR || mode == LayerBlendMode.LUMINOSITY) {
            val dl = luminance(dr, dg, db)
            val sl = luminance(sr, sg, sb)
            if (mode == LayerBlendMode.LUMINOSITY) {
                val delta = sl - dl
                return floatArrayOf((dr + delta).coerceIn(0f, 1f),
                    (dg + delta).coerceIn(0f, 1f), (db + delta).coerceIn(0f, 1f))
            }
            val sourceChroma = floatArrayOf(sr - sl, sg - sl, sb - sl)
            return floatArrayOf((dl + sourceChroma[0]).coerceIn(0f, 1f),
                (dl + sourceChroma[1]).coerceIn(0f, 1f),
                (dl + sourceChroma[2]).coerceIn(0f, 1f))
        }
        fun blend(d: Float, s: Float): Float = when (mode) {
            LayerBlendMode.NORMAL -> s
            LayerBlendMode.MULTIPLY -> d * s
            LayerBlendMode.SCREEN -> 1f - (1f - d) * (1f - s)
            LayerBlendMode.OVERLAY -> if (d < .5f) 2f * d * s else 1f - 2f * (1f - d) * (1f - s)
            LayerBlendMode.HARD_LIGHT -> if (s < .5f) 2f * d * s else 1f - 2f * (1f - d) * (1f - s)
            LayerBlendMode.SOFT_LIGHT -> if (s < .5f) d - (1f - 2f * s) * d * (1f - d)
                else d + (2f * s - 1f) * (softLightD(d) - d)
            LayerBlendMode.DARKEN -> min(d, s)
            LayerBlendMode.LIGHTEN -> max(d, s)
            LayerBlendMode.COLOR, LayerBlendMode.LUMINOSITY -> s
        }
        return floatArrayOf(blend(dr, sr).coerceIn(0f, 1f),
            blend(dg, sg).coerceIn(0f, 1f), blend(db, sb).coerceIn(0f, 1f))
    }

    private fun softLightD(value: Float): Float =
        if (value <= .25f) ((16f * value - 12f) * value + 4f) * value else kotlin.math.sqrt(value)

    private fun luminance(r: Float, g: Float, b: Float): Float =
        (0.3f * r + 0.59f * g + 0.11f * b).coerceIn(0f, 1f)
}
