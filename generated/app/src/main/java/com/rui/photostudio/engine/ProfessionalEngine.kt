package com.rui.photostudio.engine

import android.graphics.Bitmap
import kotlin.math.*

/**
 * Full offline CPU reference pipeline for professional global and masked local adjustments.
 * Values are normalized; this is a deterministic reference backend, not a GPU implementation.
 */
data class RgbCurves(
    val red: List<CurvePoint> = emptyList(),
    val green: List<CurvePoint> = emptyList(),
    val blue: List<CurvePoint> = emptyList(),
    val master: List<CurvePoint> = emptyList()
)

data class ColorWheel(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f
)

data class ProfessionalSettings(
    // Basic / light
    val exposure: Float = 0f, val contrast: Float = 1f,
    val highlights: Float = 0f, val shadows: Float = 0f,
    val whites: Float = 0f, val blacks: Float = 0f,
    val temperature: Float = 0f, val tint: Float = 0f,
    val vibrance: Float = 0f, val saturation: Float = 1f,
    // HSL / mixer (six hue bands: red, yellow, green, cyan, blue, magenta)
    val hsl: List<HslBand> = emptyList(),
    val curves: RgbCurves = RgbCurves(),
    // Color grading and split toning
    val shadowsGrade: ColorWheel = ColorWheel(),
    val midtonesGrade: ColorWheel = ColorWheel(),
    val highlightsGrade: ColorWheel = ColorWheel(),
    val gradingBalance: Float = 0f,
    val splitShadow: ColorWheel = ColorWheel(),
    val splitHighlight: ColorWheel = ColorWheel(),
    val splitBalance: Float = 0f,
    // Detail / effects
    val sharpening: Float = 0f, val luminanceNoiseReduction: Float = 0f,
    val colorNoiseReduction: Float = 0f, val clarity: Float = 0f,
    val texture: Float = 0f, val dehaze: Float = 0f,
    val vignette: Float = 0f, val grain: Float = 0f,
    val chromaticAberration: Float = 0f, val lensDistortion: Float = 0f
)

data class LocalAdjustment(
    val name: String,
    val mask: RadialMask,
    val settings: ProfessionalSettings,
    val enabled: Boolean = true,
    val opacity: Float = 1f
)

object ProfessionalEngine {
    /** Apply all supported tonal and color controls. A mask makes every control local. */
    fun apply(source: Bitmap, settings: ProfessionalSettings, mask: RadialMask? = null): Bitmap {
        val w = source.width
        val h = source.height
        val input = IntArray(w * h)
        source.getPixels(input, 0, w, 0, 0, w, h)
        val output = IntArray(input.size)
        val exp = 2.0.pow(settings.exposure.coerceIn(-5f, 5f).toDouble()).toFloat()
        val vignetteAmount = settings.vignette.coerceIn(-100f, 100f) / 100f
        val grainAmount = settings.grain.coerceIn(0f, 100f) / 100f
        val ca = settings.chromaticAberration.coerceIn(0f, 100f) / 100f
        val distortion = settings.lensDistortion.coerceIn(-100f, 100f) / 100f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val nx = (x + 0.5f) / w * 2f - 1f
            val ny = (y + 0.5f) / h * 2f - 1f
            val radius2 = nx * nx + ny * ny
            val localWeight = mask?.weight((x + .5f) / w, (y + .5f) / h) ?: 1f
            val original = input[i]
            val caOffset = (ca * radius2 * min(w, h) * 0.012f).roundToInt()
            val red = sampleChannel(input, w, h, x + caOffset, y, 16)
            val green = (original ushr 8) and 255
            val blue = sampleChannel(input, w, h, x - caOffset, y, 0)
            var r = red / 255f
            var g = green / 255f
            var b = blue / 255f
            val luma0 = .2126f*r + .7152f*g + .0722f*b
            val shadowW = (1f-luma0).pow(2)
            val highlightW = luma0.pow(2)
            val amount = localWeight
            val contrast = 1f + (settings.contrast.coerceIn(0f, 3f)-1f)*amount
            val lightShift = (settings.shadows.coerceIn(-100f,100f)*shadowW +
                settings.highlights.coerceIn(-100f,100f)*highlightW) * .0025f * amount
            val whiteBlack = (settings.whites.coerceIn(-100f,100f)*highlightW +
                settings.blacks.coerceIn(-100f,100f)*(1f-shadowW)) * .0018f * amount
            r = ((r*exp + lightShift + whiteBlack - .5f)*contrast + .5f)
            g = ((g*exp + lightShift + whiteBlack - .5f)*contrast + .5f)
            b = ((b*exp + lightShift + whiteBlack - .5f)*contrast + .5f)
            r += (settings.temperature*.0015f + settings.tint*.0005f)*amount
            g -= settings.tint*.001f*amount
            b += (-settings.temperature*.0015f + settings.tint*.0005f)*amount
            val lum = .2126f*r + .7152f*g + .0722f*b
            val satBoost = settings.vibrance.coerceIn(-100f,100f)/100f * (1f - abs(r-g).coerceAtMost(1f))
            val sat = (1f + (settings.saturation.coerceIn(0f,3f)-1f)*amount + satBoost*amount).coerceAtLeast(0f)
            r = lum + (r-lum)*sat; g = lum + (g-lum)*sat; b = lum + (b-lum)*sat

            if (settings.hsl.isNotEmpty()) {
                val hsv = rgbToHsv(r,g,b)
                var hh=hsv[0]; var ss=hsv[1]; var vv=hsv[2]
                settings.hsl.forEach { band ->
                    val delta = abs(hh-band.hueCenter).let { min(it,360f-it) }
                    val width=band.hueWidth.coerceAtLeast(1f)
                    val weight=(1f-delta/width).coerceIn(0f,1f)*amount
                    hh=(hh+band.hueShift*weight+360f)%360f
                    ss=(ss+band.saturationShift/100f*weight).coerceIn(0f,1f)
                    vv=(vv+band.luminanceShift/100f*weight).coerceIn(0f,1f)
                }
                val rgb=hsvToRgb(hh,ss,vv); r=rgb[0];g=rgb[1];b=rgb[2]
            }
            // Master and per-channel RGB curves / tone curve.
            r=curve(settings.curves.master,r);g=curve(settings.curves.master,g);b=curve(settings.curves.master,b)
            r=curve(settings.curves.red,r);g=curve(settings.curves.green,g);b=curve(settings.curves.blue,b)

            // Three-way grading plus split toning, weighted by tonal range.
            val luma=.2126f*r+.7152f*g+.0722f*b
            val sw=((1f-luma-settings.gradingBalance*.01f).coerceIn(0f,1f)).pow(2)
            val hw=((luma+settings.gradingBalance*.01f).coerceIn(0f,1f)).pow(2)
            val mw=(1f-sw-hw).coerceAtLeast(0f)
            val shadowColor=wheelRgb(settings.shadowsGrade)
            val midColor=wheelRgb(settings.midtonesGrade)
            val highColor=wheelRgb(settings.highlightsGrade)
            val splitS=wheelRgb(settings.splitShadow); val splitH=wheelRgb(settings.splitHighlight)
            val splitShadowWeight=(1f-luma-settings.splitBalance*.01f).coerceIn(0f,1f)
            val splitHighlightWeight=(luma+settings.splitBalance*.01f).coerceIn(0f,1f)
            r += amount*(sw*shadowColor[0]+mw*midColor[0]+hw*highColor[0]+
                splitShadowWeight*splitS[0]+splitHighlightWeight*splitH[0])*.12f
            g += amount*(sw*shadowColor[1]+mw*midColor[1]+hw*highColor[1]+
                splitShadowWeight*splitS[1]+splitHighlightWeight*splitH[1])*.12f
            b += amount*(sw*shadowColor[2]+mw*midColor[2]+hw*highColor[2]+
                splitShadowWeight*splitS[2]+splitHighlightWeight*splitH[2])*.12f

            // Dehaze and clarity are local-contrast approximations in this CPU reference.
            val haze=settings.dehaze.coerceIn(-100f,100f)/100f*amount
            val contrastBoost=1f+haze*.35f+settings.clarity.coerceIn(-100f,100f)/100f*.12f*amount
            r=(r-.5f)*contrastBoost+.5f;g=(g-.5f)*contrastBoost+.5f;b=(b-.5f)*contrastBoost+.5f
            val vignetteFactor=(1f-vignetteAmount*radius2*.55f*amount).coerceIn(.15f,2f)
            r*=vignetteFactor;g*=vignetteFactor;b*=vignetteFactor
            // Deterministic grain: stable across preview/export and device runs.
            val noise=((i*1103515245L+12345L) ushr 16).toInt().and(255)/255f-.5f
            val grain=noise*grainAmount*.16f*amount
            r+=grain;g+=grain;b+=grain
            // Radial lens distortion correction via inverse sampling of the source.
            val corrected = if (abs(distortion) > .0001f) {
                val factor=1f+distortion*radius2*.22f
                val sx=((nx*factor+1f)*.5f*w).roundToInt().coerceIn(0,w-1)
                val sy=((ny*factor+1f)*.5f*h).roundToInt().coerceIn(0,h-1)
                input[sy*w+sx]
            } else original
            // Keep tonal processing but use source remap only when lens correction is enabled.
            if (abs(distortion) > .0001f) {
                val cr=(corrected ushr 16) and 255; val cg=(corrected ushr 8) and 255; val cb=corrected and 255
                val rr=cr/255f; val gg=cg/255f; val bb=cb/255f
                r += (rr - ((original ushr 16 and 255)/255f)) * .35f
                g += (gg - ((original ushr 8 and 255)/255f)) * .35f
                b += (bb - ((original and 255)/255f)) * .35f
            }
            output[i]=(original and -0x1000000) or
                ((r.coerceIn(0f,1f)*255f).roundToInt() shl 16) or
                ((g.coerceIn(0f,1f)*255f).roundToInt() shl 8) or
                (b.coerceIn(0f,1f)*255f).roundToInt()
        }
        var result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { it.setPixels(output,0,w,0,0,w,h) }
        val detail=DetailAdjustments(
            texture=settings.texture, clarity=settings.clarity,
            sharpening=settings.sharpening,
            noiseReduction=settings.luminanceNoiseReduction,
            colorNoiseReduction=settings.colorNoiseReduction
        )
        if (settings.texture != 0f || settings.clarity != 0f || settings.sharpening != 0f ||
            settings.luminanceNoiseReduction != 0f || settings.colorNoiseReduction != 0f) {
            val detailed=DetailEngine.apply(result,detail)
            if (mask == null) result=detailed else result=blendMasked(result,detailed,mask)
        }
        return result
    }

    /** Apply multiple independently editable local adjustments in order. */
    fun applyLocalAdjustments(source: Bitmap, adjustments: List<LocalAdjustment>): Bitmap {
        var current=source
        adjustments.filter { it.enabled && it.opacity>0f }.forEach { item ->
            // Render at full strength, then blend the mask exactly once.
            val changed=apply(current,item.settings,null)
            current=blendMasked(current,changed,item.mask,item.opacity)
        }
        return current
    }

    private fun blendMasked(base: Bitmap, changed: Bitmap, mask: RadialMask, opacity: Float=1f): Bitmap {
        val w=base.width; val h=base.height
        val a=IntArray(w*h);val b=IntArray(w*h)
        base.getPixels(a,0,w,0,0,w,h);changed.getPixels(b,0,w,0,0,w,h)
        for(i in a.indices){
            val x=(i%w+.5f)/w;val y=(i/w+.5f)/h
            val t=(mask.weight(x,y)*opacity).coerceIn(0f,1f)
            val aa=a[i];val bb=b[i]
            fun mix(shift:Int)=((((aa ushr shift) and 255)*(1f-t)+((bb ushr shift) and 255)*t).roundToInt().coerceIn(0,255))
            a[i]=(aa and -0x1000000) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{it.setPixels(a,0,w,0,0,w,h)}
    }


    /** Pixel-accurate mask path used by brush/gradient/AI selections. */
    fun apply(source: Bitmap, settings: ProfessionalSettings, mask: PixelMask): Bitmap {
        require(mask.width > 0 && mask.height > 0)
        val adjusted = apply(source, settings)
        val w = source.width
        val h = source.height
        val originalPixels = IntArray(w * h)
        val adjustedPixels = IntArray(w * h)
        source.getPixels(originalPixels, 0, w, 0, 0, w, h)
        adjusted.getPixels(adjustedPixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val mx = ((x + .5f) / w * mask.width).toInt().coerceIn(0, mask.width - 1)
            val my = ((y + .5f) / h * mask.height).toInt().coerceIn(0, mask.height - 1)
            val amount = mask.valueAt(mx, my).coerceIn(0f, 1f)
            val a = originalPixels[i]
            val b = adjustedPixels[i]
            fun mix(shift: Int): Int {
                val av = (a ushr shift) and 255
                val bv = (b ushr shift) and 255
                return (av + (bv - av) * amount).toInt().coerceIn(0, 255)
            }
            out[i] = (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    private fun sampleChannel(p:IntArray,w:Int,h:Int,x:Int,y:Int,shift:Int)=
        ((p[y.coerceIn(0,h-1)*w+x.coerceIn(0,w-1)] ushr shift) and 255)

    private fun curve(points:List<CurvePoint>,v:Float):Float{
        if(points.size<2)return v
        val p=points.sortedBy{it.x}
        if(v<=p.first().x)return p.first().y
        if(v>=p.last().x)return p.last().y
        for(i in 0 until p.lastIndex){val a=p[i];val b=p[i+1];if(v>=a.x&&v<=b.x){
            val t=(v-a.x)/(b.x-a.x).coerceAtLeast(.0001f);return a.y+(b.y-a.y)*t
        }}
        return v
    }
    private fun wheelRgb(w:ColorWheel):FloatArray{
        val hsv=hsvToRgb(w.hue,w.saturation.coerceIn(0f,100f)/100f,1f)
        val lum=w.luminance.coerceIn(-100f,100f)/100f*.12f
        return floatArrayOf((hsv[0]-.5f)*w.saturation/100f+lum,(hsv[1]-.5f)*w.saturation/100f+lum,(hsv[2]-.5f)*w.saturation/100f+lum)
    }
    private fun rgbToHsv(r0:Float,g0:Float,b0:Float):FloatArray{
        val r=r0.coerceIn(0f,1f);val g=g0.coerceIn(0f,1f);val b=b0.coerceIn(0f,1f)
        val mx=max(r,max(g,b));val mn=min(r,min(g,b));val d=mx-mn
        var h=when{d==0f->0f;mx==r->60f*(((g-b)/d)%6f);mx==g->60f*((b-r)/d+2f);else->60f*((r-g)/d+4f)}
        if(h<0f)h+=360f
        return floatArrayOf(h,if(mx==0f)0f else d/mx,mx)
    }
    private fun hsvToRgb(h0:Float,s:Float,v:Float):FloatArray{
        val h=(h0%360f+360f)%360f;val c=v*s;val x=c*(1f-abs((h/60f)%2f-1f));val m=v-c
        val (r,g,b)=when{h<60f->Triple(c,x,0f);h<120f->Triple(x,c,0f);h<180f->Triple(0f,c,x);h<240f->Triple(0f,x,c);h<300f->Triple(x,0f,c);else->Triple(c,0f,x)}
        return floatArrayOf(r+m,g+m,b+m)
    }
}
