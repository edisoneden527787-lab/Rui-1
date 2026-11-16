package com.rui.aibuilder

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Bg = Color(0xFF101114)
private val Panel = Color(0xFF1B1D22)
private val Accent = Color(0xFF8B9CFF)
private val Muted = Color(0xFF9A9DA8)

data class ChatLine(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RuiBuilderApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuiBuilderApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rui_builder", Context.MODE_PRIVATE) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("尚未連接雲端 AI 服務") }
    val messages = remember {
        mutableStateListOf(
            ChatLine("你好！我是 Rui AI APK Builder。描述你想開發的 Android 應用程式，我可以透過已部署的雲端服務協助規劃與生成。", false)
        )
    }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Bg, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Rui AI APK Builder", fontWeight = FontWeight.Bold)
                            Text("雲端 Android 開發工作台", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showSettings = true }) { Text("服務設定") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = Color.White)
                )
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(status, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("描述你要開發的 Android App…", color = Muted) },
                        minLines = 2, maxLines = 5,
                        shape = RoundedCornerShape(18.dp),
                        enabled = !busy
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val text = prompt.trim()
                            if (text.isNotEmpty() && !busy) {
                                prompt = ""
                                messages.add(ChatLine(text, true))
                                busy = true
                                status = "正在連接雲端服務…"
                                scope.launch {
                                    val result = sendPrompt(endpoint, text)
                                    messages.add(ChatLine(result, false))
                                    status = if (result.startsWith("連線失敗：")) "服務未連接" else "已收到雲端回覆"
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && prompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("傳送開發需求")
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("專案工作區", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("輸入需求後，訊息會送到你設定的雲端 AI 服務。APK 編譯與下載功能需由後端編譯 Worker 提供。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.fromUser) Arrangement.End else Arrangement.Start) {
                            Surface(
                                color = if (item.fromUser) Color(0xFF303B68) else Panel,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Text(item.text, Modifier.padding(13.dp), color = Color.White)
                            }
                        }
                    }
                    if (busy) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 正在處理…", color = Muted)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        var draft by remember { mutableStateOf(endpoint) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("雲端服務設定") },
            text = {
                Column {
                    Text("輸入已部署的 Rui AI 後端網址。此欄位不是 API Key。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("服務網址") }, placeholder = { Text("https://your-server.example.com") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    endpoint = draft.trim().trimEnd('/')
                    prefs.edit().putString("endpoint", endpoint).apply()
                    status = if (endpoint.isBlank()) "尚未設定雲端服務網址" else "服務網址已儲存"
                    showSettings = false
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("取消") } }
        )
    }
}

private suspend fun sendPrompt(baseUrl: String, prompt: String): String = withContext(Dispatchers.IO) {
    if (baseUrl.isBlank()) return@withContext "連線失敗：尚未設定雲端服務網址。請先開啟「服務設定」。"
    try {
        val url = URL(baseUrl.trimEnd('/') + "/api/chat")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val body = JSONObject().put("message", prompt).toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) return@withContext "服務錯誤（HTTP $code）：$response"
        JSONObject(response).optString("reply", "雲端服務沒有回傳 reply 欄位。")
    } catch (e: Exception) {
        "連線失敗：${e.message ?: "未知錯誤"}"
    }
}
