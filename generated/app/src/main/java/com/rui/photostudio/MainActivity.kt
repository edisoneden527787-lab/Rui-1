package com.rui.photostudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow

private val Bg = Color(0xFF090A0C)
private val Panel = Color(0xFF17191D)
private val Accent = Color(0xFF80B7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9,10,12)
        window.navigationBarColor = android.graphics.Color.rgb(9,10,12)
        setContent { RuiPhotoStudio() }
    }
}

@Composable
private fun RuiPhotoStudio() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("匯入一張照片，開始你的作品。") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
                bitmap = loaded
                exposure = 0f; contrast = 1f; saturation = 1f; warmth = 0f
                message = if (loaded != null) "已載入照片 · ${loaded.width} × ${loaded.height}" else "照片讀取失敗，請換一張圖片。"
                busy = false
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RUI", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Photo Studio", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text("LOCAL EDITOR", color = Color(0xFF9BA3AE), fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("匯入照片") }
                OutlinedButton(onClick = {
                    exposure = 0f; contrast = 1f; saturation = 1f; warmth = 0f
                    message = "調整參數已重設"
                }, shape = RoundedCornerShape(14.dp)) { Text("重設") }
                OutlinedButton(enabled = bitmap != null && !busy, onClick = {
                    val source = bitmap ?: return@OutlinedButton
                    busy = true
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { exportPhoto(context, source, exposure, contrast, saturation, warmth) }
                        message = if (saved) "已匯出至相簿 / Rui Photo Studio" else "匯出失敗，請確認儲存空間。"
                        busy = false
                    }
                }, shape = RoundedCornerShape(14.dp)) { Text("匯出") }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF111317), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                val photo = bitmap
                if (photo == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("＋", color = Accent, fontSize = 42.sp)
                        Text("選擇照片以開始編輯", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("本機處理 · 不需網路 · 不上傳照片", color = Color(0xFF858D98), fontSize = 12.sp)
                    }
                } else {
                    Image(bitmap = photo.asImageBitmap(), contentDescription = "編輯中的照片", modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(adjustmentMatrix(exposure, contrast, saturation, warmth)))
                }
                if (busy) CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
            }
            Text(message, color = Color(0xFF9BA3AE), fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            Text("LIGHT", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            AdjustmentSlider("曝光 Exposure", exposure, -3f..3f, { exposure = it }, "%.2f EV".format(exposure))
            AdjustmentSlider("對比 Contrast", contrast, 0f..2f, { contrast = it }, "%.2f".format(contrast))
            AdjustmentSlider("飽和度 Saturation", saturation, 0f..2f, { saturation = it }, "%.2f".format(saturation))
            AdjustmentSlider("色溫 Warmth", warmth, -1f..1f, { warmth = it }, "%.2f".format(warmth))
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                items(listOf("光線", "色彩", "細節", "遮罩", "圖層", "AI", "汽車")) { name ->
                    Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                        Text(name, color = if (name == "光線") Accent else Color.LightGray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, valueLabel: String) {
    Column(Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color(0xFFE8EAF0), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = Color(0xFF9BA3AE), fontSize = 11.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.height(30.dp))
    }
}

private fun adjustmentMatrix(exposure: Float, contrast: Float, saturation: Float, warmth: Float): ColorMatrix {
    val sat = ColorMatrix().apply { setToSaturation(saturation) }
    val a = sat.values.copyOf()
    val gain = 2f.pow(exposure) * contrast
    val offsets = 255f * (0.5f * (1f - contrast))
    val r = gain * (1f + warmth * 0.12f)
    val g = gain
    val b = gain * (1f - warmth * 0.12f)
    for (c in 0..4) a[c] *= r
    for (c in 5..9) a[c] *= g
    for (c in 10..14) a[c] *= b
    a[4] += offsets; a[9] += offsets; a[14] += offsets
    return ColorMatrix(a)
}

private fun decodePhoto(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 4096) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) { null }
}

private fun exportPhoto(context: android.content.Context, source: Bitmap, exposure: Float, contrast: Float, saturation: Float, warmth: Float): Boolean {
    return try {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(adjustmentMatrix(exposure, contrast, saturation, warmth).values)
        }
        AndroidCanvas(output).drawBitmap(source, 0f, 0f, paint)
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "RuiPhotoStudio_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Rui Photo Studio")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { output.compress(Bitmap.CompressFormat.JPEG, 96, it) } ?: false
    } catch (_: Exception) { false }
}
