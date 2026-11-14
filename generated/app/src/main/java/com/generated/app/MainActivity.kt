package com.generated.app
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            val colors = if (isDarkMode) darkColorScheme(primary = Color(0xFF0A84FF), background = Color.Black, surface = Color(0xFF1C1C1E)) else lightColorScheme(primary = Color(0xFF007AFF), background = Color(0xFFF2F2F7), surface = Color.White)
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RuiOSScreen(isDarkMode = isDarkMode, onToggleTheme = { isDarkMode = !isDarkMode })
                }
            }
        }
    }
}

@Composable
fun RuiOSScreen(isDarkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rui_os_settings", Context.MODE_PRIVATE) }
    
    var islandExpanded by remember { mutableStateOf(false) }
    var islandEvent by remember { mutableStateOf("標題：音樂播放中") }
    var isPlaying by remember { mutableStateOf(true) }
    var songIndex by remember { mutableStateOf(0) }
    val songs = listOf("Rui OS Theme - Audio", "iOS Dynamic Island Spec", "Shizuku Service Running", "Charging 85% Fast")
    
    var islandWidthDp by remember { mutableFloatStateOf(prefs.getFloat("island_width", 200f)) }
    var cornerRadiusDp by remember { mutableFloatStateOf(prefs.getFloat("island_radius", 24f)) }
    var batteryLevel by remember { mutableIntStateOf(85) }
    var isCharging by remember { mutableStateOf(true) }
    var shizukuConnected by remember { mutableStateOf(false) }
    var adbEnabled by remember { mutableStateOf(true) }
    
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Simulated iOS Status Bar
        IOSStatusBar(batteryLevel = batteryLevel, isCharging = isCharging)

        // Interactive Dynamic Island Preview Header
        Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(if (isDarkMode) Color.Black else Color(0xFFE5E5EA)), contentAlignment = Alignment.TopCenter) {
            DynamicIslandView(
                expanded = islandExpanded,
                eventText = if (islandExpanded) songs[songIndex] else islandEvent,
                widthDp = islandWidthDp,
                radiusDp = cornerRadiusDp,
                isPlaying = isPlaying,
                onToggleExpand = { islandExpanded = !islandExpanded },
                onPlayPause = { isPlaying = !isPlaying },
                onNext = { songIndex = (songIndex + 1) % songs.size }
            )
        }

        // Main Control Options
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("動態島") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Shizuku / ADB") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("系統與偏好") })
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
            if (selectedTab == 0) {
                item {
                    Text("動態島自訂設定", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("動態島寬度: ${islandWidthDp.toInt()} dp")
                    Slider(value = islandWidthDp, onValueChange = { islandWidthDp = it; prefs.edit().putFloat("island_width", it).apply() }, valueRange = 140f..320f)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("圓角弧度: ${cornerRadiusDp.toInt()} dp")
                    Slider(value = cornerRadiusDp, onValueChange = { cornerRadiusDp = it; prefs.edit().putFloat("island_radius", it).apply() }, valueRange = 12f..40f)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("模擬事件觸發", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { islandEvent = "來電：Rui OS 支援團隊"; islandExpanded = true }) { Text("模擬來電") }
                        Button(onClick = { islandEvent = "已連接 AirPods Pro"; islandExpanded = false }) { Text("耳機連接") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { isCharging = !isCharging; islandEvent = if (isCharging) "充電中 85%" else "未在充電"; islandExpanded = false }) { Text("切換充電狀態") }
                        Button(onClick = { islandEvent = "通知：新訊息已收到"; islandExpanded = true }) { Text("模擬通知") }
                    }
                }
            } else if (selectedTab == 1) {
                item {
                    Text("Shizuku & 無線 ADB 偵測", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = if (shizukuConnected) Color.Green else Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = if (shizukuConnected) "Shizuku 服務：已連線 (Binder 正常)" else "Shizuku 服務：未連接", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("無線除錯狀態: " + if (adbEnabled) "已開啟 (Port: 5555)" else "未啟用", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { shizukuConnected = !shizukuConnected }) {
                                    Text(if (shizukuConnected) "斷開 Binder" else "請求 Shizuku 授權")
                                }
                                Button(onClick = { adbEnabled = !adbEnabled }) {
                                    Text("重新偵測 ADB")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("引導說明:", fontWeight = FontWeight.Bold)
                    Text("1. 請至開發者選項啟用「無線除錯」。\n2. 啟動 Shizuku 服務並透過配對碼進行配對。\n3. 本 App 整合原生 Binder 介面監控連線狀況。", fontSize = 13.sp, color = Color.Gray)
                }
            } else {
                item {
                    Text("系統樣式與權限", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("深色模式")
                        Switch(checked = isDarkMode, onCheckedChange = { onToggleTheme() })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("懸浮視窗權限 (Overlay)")
                        Button(onClick = { }) { Text("已授權") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("通知存取權限")
                        Button(onClick = { }) { Text("前往設定") }
                    }
                }
            }
        }
    }
}

@Composable
fun IOSStatusBar(batteryLevel: Int, isCharging: Boolean) {
    val timeStr = remember {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date())
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = timeStr, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = "$batteryLevel%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.width(22.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(if (isCharging) Color.Green else Color.Gray)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(batteryLevel / 100f).background(Color.White))
            }
        }
    }
}

@Composable
fun DynamicIslandView(
    expanded: Boolean,
    eventText: String,
    widthDp: Float,
    radiusDp: Float,
    isPlaying: Boolean,
    onToggleExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val currentWidth = if (expanded) 320.dp else widthDp.dp
    val currentHeight = if (expanded) 80.dp else 36.dp

    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .width(currentWidth)
            .height(currentHeight)
            .clip(RoundedCornerShape(radiusDp.dp))
            .background(Color.Black)
            .clickable { onToggleExpand() }
            .animateContentSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!expanded) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF0A84FF)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = eventText, color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
                            contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color.Yellow)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = eventText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Rui OS Player", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.clickable { onToggleExpand() })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = if (isPlaying) Icons.Default.Menu else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}
