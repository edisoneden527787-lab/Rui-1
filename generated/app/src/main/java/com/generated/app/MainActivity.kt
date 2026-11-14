package com.generated.app
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    var isExpanded by remember { mutableStateOf(false) }
    var activeMode by remember { mutableStateOf("music") } // music, timer, call, battery
    
    // Dynamic Island Customization Settings
    var islandWidth by remember { mutableStateOf(200) }
    var islandHeight by remember { mutableStateOf(36) }
    var isStatusBarEnabled by remember { mutableStateOf(true) }
    var shizukuConnected by remember { mutableStateOf(false) }
    var adbConnected by remember { mutableStateOf(false) }

    // Battery level state
    val context = LocalContext.current
    var batteryLevel by remember { mutableStateOf(85) }
    var isCharging by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level != -1 && scale != -1) {
            batteryLevel = (level * 100 / scale.toFloat()).toInt()
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("iOS 動態島 & 系統整合", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首頁") },
                    label = { Text("控制中心") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "設定") },
                    label = { Text("動態島設定") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "系統") },
                    label = { Text("Shizuku & ADB") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFA0A0A0))
        ) {
            // Simulated Screen Header with iOS Status Bar & Dynamic Island
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.Black)
            ) {
                // iOS Status Bar Simulation
                if (isStatusBarEnabled) {
                    SimulatedIOSStatusBar(batteryLevel = batteryLevel, isCharging = isCharging)
                }

                // Dynamic Island Container
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    DynamicIslandView(
                        isExpanded = isExpanded,
                        onToggleExpand = { isExpanded = !isExpanded },
                        activeMode = activeMode,
                        customWidth = islandWidth,
                        customHeight = islandHeight,
                        batteryLevel = batteryLevel
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    0 -> HomeScreenContent(
                        activeMode = activeMode,
                        onModeChange = { activeMode = it; isExpanded = false },
                        isExpanded = isExpanded,
                        onToggleExpand = { isExpanded = !isExpanded }
                    )
                    1 -> SettingsScreenContent(
                        islandWidth = islandWidth,
                        onWidthChange = { islandWidth = it },
                        islandHeight = islandHeight,
                        onHeightChange = { islandHeight = it },
                        isStatusBarEnabled = isStatusBarEnabled,
                        onStatusBarToggle = { isStatusBarEnabled = it }
                    )
                    2 -> SystemIntegrationContent(
                        shizukuConnected = shizukuConnected,
                        onToggleShizuku = { shizukuConnected = !shizukuConnected },
                        adbConnected = adbConnected,
                        onToggleAdb = { adbConnected = !adbConnected }
                    )
                }
            }
        }
    }
}

@Composable
fun SimulatedIOSStatusBar(batteryLevel: Int, isCharging: Boolean) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val currentTime = remember { mutableStateOf(timeFormat.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = timeFormat.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Time
        Text(
            text = currentTime.value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Right: Status Icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Signal",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(text = "5G", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$batteryLevel%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                if (isCharging) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Charging",
                        tint = Color.Green,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Battery",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicIslandView(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    activeMode: String,
    customWidth: Int,
    customHeight: Int,
    batteryLevel: Int
) {
    val widthAnimated by animateDpAsState(
        targetValue = if (isExpanded) 320.dp else customWidth.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "WidthAnimation"
    )
    val heightAnimated by animateDpAsState(
        targetValue = if (isExpanded) 90.dp else customHeight.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "HeightAnimation"
    )

    Box(
        modifier = Modifier
            .width(widthAnimated)
            .height(heightAnimated)
            .clip(RoundedCornerShape(heightAnimated / 2))
            .background(Color.Black)
            .clickable { onToggleExpand() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isExpanded) {
            // Compact Dynamic Island View
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (activeMode) {
                    "music" -> {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF2D55), modifier = Modifier.size(18.dp))
                        Text("播放中 - Dynamic", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Green, modifier = Modifier.size(14.dp))
                    }
                    "timer" -> {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
                        Text("倒數 04:59", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    "call" -> {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color.Green, modifier = Modifier.size(18.dp))
                        Text("通話中 01:23", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    "battery" -> {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Green, modifier = Modifier.size(18.dp))
                        Text("充電中 $batteryLevel%", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Expanded Dynamic Island View
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (activeMode == "music") "Apple Music" else if (activeMode == "call") "來電：張小明" else "系統提醒",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (activeMode == "music") "Jay Chou - 晴天" else if (activeMode == "call") "00:01:23" else "倒數計時中...",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "關閉",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp).clickable { onToggleExpand() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun HomeScreenContent(
    activeMode: String,
    onModeChange: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("動態島卡片切換", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onModeChange("music") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeMode == "music") MaterialTheme.colorScheme.primary else Color.Gray)
            ) {
                Text("音樂播放")
            }
            Button(
                onClick = { onModeChange("timer") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeMode == "timer") MaterialTheme.colorScheme.primary else Color.Gray)
            ) {
                Text("倒數計時")
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onModeChange("call") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeMode == "call") MaterialTheme.colorScheme.primary else Color.Gray)
            ) {
                Text("來電狀態")
            }
            Button(
                onClick = { onModeChange("battery") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (activeMode == "battery") MaterialTheme.colorScheme.primary else Color.Gray)
            ) {
                Text("充電顯示")
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("互動動作測試", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("點擊下方按鈕以展開或縮小膠囊，或直接點擊上方黑色動態島。")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onToggleExpand() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isExpanded) "縮小動態島" else "展開動態島")
                }
            }
        }
    }
}

@Composable
fun SettingsScreenContent(
    islandWidth: Int,
    onWidthChange: (Int) -> Unit,
    islandHeight: Int,
    onHeightChange: (Int) -> Unit,
    isStatusBarEnabled: Boolean,
    onStatusBarToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("外觀與尺寸設定", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("顯示仿 iOS 狀態列", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("開啓左側時間與右側電量圖示", fontSize = 12.sp, color = Color.Gray)
                    Switch(
                        checked = isStatusBarEnabled,
                        onCheckedChange = { onStatusBarToggle(it) }
                    )
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("預設寬度: ${islandWidth}dp", fontWeight = FontWeight.Bold)
                Slider(
                    value = islandWidth.toFloat(),
                    onValueChange = { onWidthChange(it.toInt()) },
                    valueRange = 140f..260f
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("預設高度: ${islandHeight}dp", fontWeight = FontWeight.Bold)
                Slider(
                    value = islandHeight.toFloat(),
                    onValueChange = { onHeightChange(it.toInt()) },
                    valueRange = 28f..50f
                )
            }
        }
    }
}

@Composable
fun SystemIntegrationContent(
    shizukuConnected: Boolean,
    onToggleShizuku: () -> Unit,
    adbConnected: Boolean,
    onToggleAdb: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Shizuku & ADB 系統授權管理", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // Shizuku Integration Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Shizuku 服務權限", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (shizukuConnected) "已授權" else "未連線",
                        color = if (shizukuConnected) Color.Green else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (shizukuConnected) "Shizuku UserService 運行中，已取得系統級控制權權利。" else "請確認 Shizuku App 已在背景執行並授權本應用程式。",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onToggleShizuku() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (shizukuConnected) "斷開 Shizuku 連線" else "請求 Shizuku 授權")
                }
            }
        }

        // Wireless ADB Debugging Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ADB 無線偵錯", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (adbConnected) "連線成功" else "離線",
                        color = if (adbConnected) Color.Green else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (adbConnected) "無線偵錯連線已建立 (埠: 5555)。" else "請至 [開發者選項] 開啟 [無線偵錯] 並輸入配對碼進行連接。",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onToggleAdb() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (adbConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (adbConnected) "關閉 ADB 無線連線" else "開始 ADB 配對連線")
                }
            }
        }

        // Operation Logs
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("系統操作記錄日誌", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Black)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "[INFO] System initialized.\n" +
                                (if (shizukuConnected) "[OK] Shizuku Binder connected.\n" else "[WARN] Shizuku service unavailable.\n") +
                                (if (adbConnected) "[OK] ADB daemon active on port 5555.\n" else "[INFO] ADB connection offline.\n") +
                                "[INFO] Dynamic Island surface rendered.",
                        color = Color.Green,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
