package com.generated.app
import android.content.Context
import android.os.Bundle
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowserApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE) }
    
    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var bookmarks by remember {
        val saved = prefs.getString("bookmarks", "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mutableStateOf(list)
    }

    fun saveBookmarks(list: List<String>) {
        val array = JSONArray(list)
        prefs.edit().putString("bookmarks", array.toString()).apply()
    }

    fun loadUrl(input: String) {
        var target = input.trim()
        if (target.isEmpty()) return
        
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            if (target.contains(".") && !target.contains(" ")) {
                target = "https://" + target
            } else {
                target = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
            }
        }
        currentUrl = target
        urlInput = target
        webView?.loadUrl(target)
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("輸入網址或搜尋...") },
                            trailingIcon = {
                                IconButton(onClick = { loadUrl(urlInput) }) {
                                    Icon(Icons.Filled.Search, contentDescription = "搜尋")
                                }
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {
                            if (!bookmarks.contains(currentUrl)) {
                                val newlist = bookmarks + currentUrl
                                bookmarks = newlist
                                saveBookmarks(newlist)
                            }
                        }) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "加入書籤",
                                tint = if (bookmarks.contains(currentUrl)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showBookmarksDialog = true }) {
                            Icon(Icons.Filled.Menu, contentDescription = "書籤選單")
                        }
                    }
                    
                    if (currentUrl.endsWith(".onion")) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                text = "提示: .onion 域名需要搭配 Tor Proxy (如 Orbot) 方可連線。",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(6.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "上一頁")
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新整理")
                    }
                    IconButton(onClick = { loadUrl("https://www.google.com") }) {
                        Icon(Icons.Filled.Home, contentDescription = "首頁")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let {
                                    currentUrl = it
                                    urlInput = it
                                }
                                canGoBack = view?.canGoBack() ?: false
                            }
                        }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                update = { wv ->
                    webView = wv
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("已儲存的書籤") },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("目前沒有任何書籤")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(bookmarks) { bUrl ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bUrl,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(onClick = {
                                    loadUrl(bUrl)
                                    showBookmarksDialog = false
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "前往")
                                }
                                IconButton(onClick = {
                                    val newList = bookmarks - bUrl
                                    bookmarks = newList
                                    saveBookmarks(newList)
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "刪除")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }
}
