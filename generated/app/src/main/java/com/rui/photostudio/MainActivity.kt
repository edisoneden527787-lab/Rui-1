package com.rui.photostudio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Color as Pixel
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

private val bg = Color(0xFF101112)
private val panel = Color(0xFF1B1D1F)
private val gold = Color(0xFFC5AD82)
private val ink = Color(0xFFECEBE8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16,17,18)
        window.navigationBarColor = android.graphics.Color.rgb(16,17,18)
        setContent { Studio() }
    }
}

@Composable
private fun Studio() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var temperature by remember { mutableFloatStateOf(0f) }
    var highlights by remember { mutableFloatStateOf(0f) }
    var shadows by remember { mutableFloatStateOf(0f) }
    var whites by remember { mutableFloatStateOf(0f) }
    var blacks by remember { mutableFloatStateOf(0f) }
    var sharpening by remember { mutableFloatStateOf(0f) }
    var before by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("匯入照片以開始編輯") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }
                original = bitmap
                exposure = 0f; contrast = 0f; saturation = 0f; temperature = 0f
                highlights = 0f; shadows = 0f; whites = 0f; blacks = 0f; sharpening = 0f
                preview = bitmap
                note = "已載入照片 · ${bitmap.width} × ${bitmap.height}"
            } catch (e: Exception) { note = "圖片載入失敗：${e.localizedMessage}" }
            busy = false
        }
    }
    LaunchedEffect(original, exposure, contrast, saturation, temperature, highlights, shadows, whites, blacks, sharpening) {
        val src = original ?: return@LaunchedEffect
        busy = true
        preview = withContext(Dispatchers.Default) { render(src, exposure, contrast, saturation, temperature, highlights, shadows, whites, blacks, sharpening) }
        busy = false
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = gold, background = bg, surface = panel, onSurface = ink)) {
        Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RUI", color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("PHOTO STUDIO", color = ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                if (original != null) {
                    TextButton(onClick = { before = !before }) { Text(if (before) "SHOW EDITED" else "BEFORE", color = gold) }
                    TextButton(onClick = {
                        exposure = 0f; contrast = 0f; saturation = 0f; temperature = 0f
                        highlights = 0f; shadows = 0f; whites = 0f; blacks = 0f; sharpening = 0f
                    }) { Text("RESET", color = ink) }
                    TextButton(onClick = {
                        val bitmap = preview ?: return@TextButton
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, "RuiStudio_${System.currentTimeMillis()}.jpg")
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Rui Photo Studio")
                                }
                                val out = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                out?.let { context.contentResolver.openOutputStream(it)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream) } } ?: false
                            }
                            note = if (ok) "匯出完成：Pictures/Rui Photo Studio" else "匯出失敗"
                        }
                    }) { Text("EXPORT", color = gold) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF080909), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                val shown = if (before) original else preview
                if (shown != null) Image(shown!!.asImageBitmap(), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("專業影像工作區", color = ink, fontSize = 22.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("支援相簿匯入、即時調色與 JPG 匯出", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("＋ 匯入照片") }
                }
                if (busy) CircularProgressIndicator(color = gold, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
            Text(note, color = gold, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            Column(Modifier.heightIn(max = 270.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                SliderRow("曝光", exposure, -2f, 2f) { exposure = it }
                SliderRow("對比", contrast, -100f, 100f) { contrast = it }
                SliderRow("飽和度", saturation, -100f, 100f) { saturation = it }
                SliderRow("色溫", temperature, -100f, 100f) { temperature = it }
                SliderRow("高光", highlights, -100f, 100f) { highlights = it }
                SliderRow("陰影", shadows, -100f, 100f) { shadows = it }
                SliderRow("白色色階", whites, -100f, 100f) { whites = it }
                SliderRow("黑色色階", blacks, -100f, 100f) { blacks = it }
                SliderRow("銳化", sharpening, 0f, 100f) { sharpening = it }
                Text("以上調整會直接作用於像素並套用於匯出。AI 遮罩、HSL、曲線與圖層仍在開發中。", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ink, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text("%.1f".format(value), color = gold, fontSize = 12.sp)
            TextButton(onClick = { onChange(0f) }) { Text("重設", color = Color.Gray) }
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}

private fun render(
    src: Bitmap,
    exposure: Float,
    contrast: Float,
    saturation: Float,
    temperature: Float,
    highlights: Float,
    shadows: Float,
    whites: Float,
    blacks: Float,
    sharpening: Float
): Bitmap {
    val w = src.width
    val h = src.height
    val source = IntArray(w * h)
    src.getPixels(source, 0, w, 0, 0, w, h)
    val pixels = IntArray(source.size)
    val gain = 2.0.pow(exposure.toDouble()).toFloat()
    val c = 1f + contrast / 100f
    val sat = 1f + saturation / 100f
    val temp = temperature * 0.35f
    val hi = highlights / 100f
    val sh = shadows / 100f
    val white = whites / 100f
    val black = blacks / 100f

    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            val p = source[i]
            var r = (Pixel.red(p) * gain + temp).coerceIn(0f, 255f)
            var g = (Pixel.green(p) * gain).coerceIn(0f, 255f)
            var b = (Pixel.blue(p) * gain - temp).coerceIn(0f, 255f)
            var lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val shadowWeight = (1f - lum / 255f).coerceIn(0f, 1f)
            val highlightWeight = (lum / 255f).coerceIn(0f, 1f)
            val tone = sh * shadowWeight * 0.65f + hi * highlightWeight * 0.65f +
                black * (1f - shadowWeight) * 0.35f + white * highlightWeight * 0.35f
            r = (r + tone * 90f).coerceIn(0f, 255f)
            g = (g + tone * 90f).coerceIn(0f, 255f)
            b = (b + tone * 90f).coerceIn(0f, 255f)
            r = ((r - 128f) * c + 128f).coerceIn(0f, 255f)
            g = ((g - 128f) * c + 128f).coerceIn(0f, 255f)
            b = ((b - 128f) * c + 128f).coerceIn(0f, 255f)
            lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = (lum + (r - lum) * sat).coerceIn(0f, 255f)
            g = (lum + (g - lum) * sat).coerceIn(0f, 255f)
            b = (lum + (b - lum) * sat).coerceIn(0f, 255f)

            if (sharpening > 0f && x in 1 until w - 1 && y in 1 until h - 1) {
                val center = Pixel.red(source[i]) + Pixel.green(source[i]) + Pixel.blue(source[i])
                var neighbors = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx != 0 || dy != 0) {
                        val q = source[(y + dy) * w + x + dx]
                        neighbors += Pixel.red(q) + Pixel.green(q) + Pixel.blue(q)
                    }
                }
                val edge = (center - neighbors / 8f) / 3f
                val amount = sharpening / 100f * 0.8f
                r = (r + edge * amount).coerceIn(0f, 255f)
                g = (g + edge * amount).coerceIn(0f, 255f)
                b = (b + edge * amount).coerceIn(0f, 255f)
            }
            pixels[i] = Pixel.argb(Pixel.alpha(p), r.toInt(), g.toInt(), b.toInt())
        }
    }
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
