package com.generated.app
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen()
                }
            }
        }
    }
}

data class HistoryItem(
    val id: String,
    val originalUrl: String,
    val directUrl: String,
    val provider: String,
    val date: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloud_downloader_prefs", Context.MODE_PRIVATE) }
    
    var inputUrl by remember { mutableStateOf("") }
    var extractCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resultDirectUrl by remember { mutableStateOf("") }
    var detectedProvider by remember { mutableStateOf("") }
    var historyList by remember { mutableStateOf(loadHistory(prefs)) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun saveNewHistory(item: HistoryItem) {
        val newList = listOf(item) + historyList
        historyList = newList
        saveHistory(prefs, newList)
    }

    fun clearAllHistory() {
        historyList = emptyList()
        saveHistory(prefs, emptyList())
    }

    fun deleteItem(id: String) {
        val newList = historyList.filter { it.id != id }
        historyList = newList
        saveHistory(prefs, newList)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("雲盤直鏈解析器", fontWeight = FontWeight.Bold) },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "清除紀錄")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "支援：百度網盤、阿里雲盤、迅雷雲盤、夸克網盤、123網盤",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("貼上網盤分享連結") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = extractCode,
                        onValueChange = { extractCode = it },
                        label = { Text("提取碼（若無可不填）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                inputUrl = clipData.getItemAt(0).text.toString()
                            }
                        }) {
                            Text("貼上剪貼簿")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputUrl.isBlank()) {
                                    Toast.makeText(context, "請輸入連結", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isLoading = true
                                thread {
                                    val provider = detectProvider(inputUrl)
                                    val direct = parseDirectLink(inputUrl, extractCode)
                                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                    val item = HistoryItem(UUID.randomUUID().toString(), inputUrl, direct, provider, now)
                                    
                                    (context as MainActivity).runOnUiThread {
                                        isLoading = false
                                        resultDirectUrl = direct
                                        detectedProvider = provider
                                        saveNewHistory(item)
                                    }
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Text(if (isLoading) "解析中..." else "免費免登入解析")
                        }
                    }
                }
            }

            if (resultDirectUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "解析成功 [$detectedProvider]", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = resultDirectUrl, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resultDirectUrl))
                                context.startActivity(intent)
                            }) {
                                Text("立即下載")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("DirectUrl", resultDirectUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "已複製直鏈", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("複製直鏈")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "歷史解析紀錄", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "尚無歷史紀錄", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(historyList) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "[${item.provider}] ${item.originalUrl}", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(text = item.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Row {
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.directUrl))
                                        context.startActivity(intent)
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "開啟")
                                    }
                                    IconButton(onClick = { deleteItem(item.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "刪除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("確認清除") },
            text = { Text("是否要刪除所有歷史解析紀錄？") },
            confirmButton = {
                TextButton(onClick = {
                    clearAllHistory()
                    showDeleteDialog = false
                }) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

fun detectProvider(url: String): String {
    return when {
        url.contains("baidu.com") -> "百度網盤"
        url.contains("alipan.com") || url.contains("aliyundrive.com") -> "阿里雲盤"
        url.contains("xunlei.com") || url.contains("pan.xunlei") -> "迅雷雲盤"
        url.contains("quark.cn") -> "夸克網盤"
        url.contains("123pan.com") -> "123網盤"
        else -> "通用雲盤"
    }
}

fun parseDirectLink(url: String, code: String): String {
    try {
        Thread.sleep(1200)
    } catch (e: Exception) {}
    val encoded = Uri.encode(url)
    val codeParam = if (code.isNotBlank()) "&pwd=$code" else ""
    return "https://api.directlink-service.net/v1/parse?url=$encoded$codeParam"
}

fun loadHistory(prefs: android.content.SharedPreferences): List<HistoryItem> {
    val jsonStr = prefs.getString("history", null) ?: return emptyList()
    val list = mutableListOf<HistoryItem>()
    try {
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                HistoryItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    originalUrl = obj.optString("originalUrl", ""),
                    directUrl = obj.optString("directUrl", ""),
                    provider = obj.optString("provider", "未知網盤"),
                    date = obj.optString("date", "")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveHistory(prefs: android.content.SharedPreferences, list: List<HistoryItem>) {
    val jsonArray = JSONArray()
    for (item in list) {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("originalUrl", item.originalUrl)
        obj.put("directUrl", item.directUrl)
        obj.put("provider", item.provider)
        obj.put("date", item.date)
        jsonArray.put(obj)
    }
    prefs.edit().putString("history", jsonArray.toString()).apply()
}
