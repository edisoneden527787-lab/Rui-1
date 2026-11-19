package com.rui.aibuilder

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var address: EditText
    private lateinit var browser: WebView
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("cloud_android", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 10)
            setBackgroundColor(Color.rgb(245, 247, 251))
        }
        root.addView(TextView(this).apply {
            text = "Cloud Android 15"
            textSize = 25f
            setTextColor(Color.rgb(15, 23, 42))
            setPadding(0, 0, 0, 4)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "雲端 Android 遠端操作器 · 輸入已部署主機的 HTTPS 網址"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        })
        address = EditText(this).apply {
            hint = "https://your-android-host.example"
            textSize = 14f
            singleLine = true
            setText(prefs.getString("url", "") ?: "")
        }
        root.addView(address, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "連線"; setOnClickListener { connect() } }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply { text = "重新整理"; setOnClickListener { if (browser.url != null) browser.reload() else connect() } }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply { text = "返回"; setOnClickListener { if (browser.canGoBack()) browser.goBack() } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        status = TextView(this).apply {
            text = "尚未連線。此 APK 是遠端控制客戶端，需先有已部署的 Android 主機服務。"
            textSize = 12f
            setTextColor(Color.rgb(51, 65, 85))
            setPadding(2, 8, 2, 8)
        }
        root.addView(status)
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { status.text = "正在連線：${url ?: ""}" }
                override fun onPageFinished(view: WebView?, url: String?) { status.text = "已載入：${url ?: ""}" }
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) status.text = "連線失敗：${error?.description ?: "請檢查主機網址與服務狀態"}"
                }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun connect() {
        val url = address.text.toString().trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            Toast.makeText(this, "請輸入有效的 http:// 或 https:// 主機網址", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("url", url).apply()
        browser.loadUrl(url)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }
}
