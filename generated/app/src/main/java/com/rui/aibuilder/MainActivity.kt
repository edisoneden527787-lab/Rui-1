package com.rui.aibuilder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var scan: Button
    private var lastJson = "[]"
    private var loginAttempted = false
    private val navy = Color.rgb(18, 28, 48)
    private val blue = Color.rgb(45, 105, 210)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 10)
            setBackgroundColor(Color.rgb(245, 247, 251))
        }
        root.addView(TextView(this).apply {
            text = "DIST Grabber"
            textSize = 25f
            setTextColor(navy)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "臺中市中小學雲端校務系統 · 公開選單擷取"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 4, 0, 8)
        })
        status = TextView(this).apply {
            text = "正在載入網站…"
            textSize = 13f
            setTextColor(navy)
            setPadding(0, 6, 0, 8)
        }
        root.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val login = Button(this).apply {
            text = "尋找縣市帳號登入"
            setOnClickListener { openCountyLogin() }
        }
        scan = Button(this).apply {
            text = "掃描全部 DIST"
            isEnabled = false
            setOnClickListener { scanAll() }
        }
        actions.addView(login, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(scan, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        val actions2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions2.addView(Button(this).apply {
            text = "複製 JSON"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("DIST", lastJson))
                Toast.makeText(this@MainActivity, "已複製", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions2.addView(Button(this).apply {
            text = "匯出 / 分享"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("application/json")
                    .putExtra(Intent.EXTRA_TEXT, lastJson)
                startActivity(Intent.createChooser(send, "匯出 DIST 清單"))
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions2)
        result = TextView(this).apply {
            text = "等待掃描…"
            textSize = 13f
            setTextColor(Color.rgb(25, 32, 45))
            setPadding(10, 10, 10, 10)
        }
        root.addView(ScrollView(this).apply { addView(result) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "網站操作區：若登入入口未自動定位，可在此手動開啟選單"
            textSize = 11f
            setTextColor(Color.GRAY)
        })
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    status.text = "頁面已載入：${url ?: ""}"
                    scan.isEnabled = true
                    if (!loginAttempted) {
                        loginAttempted = true
                        web.postDelayed({ openCountyLogin(auto = true) }, 500)
                    }
                }
            }
            loadUrl("https://school.tc.edu.tw")
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 270))
        setContentView(root)
    }

    private fun openCountyLogin(auto: Boolean = false) {
        status.text = "正在尋找「縣市帳號登入」入口…"
        val js = """
          (function(){
            const nodes=[...document.querySelectorAll('a,button,[role=button],input[type=button],input[type=submit]')];
            const hit=nodes.find(e=>((e.innerText||e.value||e.getAttribute('aria-label')||'').replace(/\s/g,'')).includes('縣市帳號登入'));
            if(hit){hit.click();return '已點擊縣市帳號登入';}
            const text=[...document.querySelectorAll('body *')].find(e=>e.children.length===0 && ((e.innerText||'').replace(/\s/g,'')).includes('縣市帳號登入'));
            if(text){text.click();return '已點擊縣市帳號登入文字';}
            return '目前頁面未找到登入入口，請在下方網站區手動開啟';
          })();
        """.trimIndent()
        web.evaluateJavascript(js) { raw ->
            val message = raw?.trim('"') ?: "無法判斷登入入口"
            status.text = message
            if (auto) web.postDelayed({ chooseTaichungAndScan() }, 900)
        }
    }

    private fun chooseTaichungAndScan() {
        val js = """
          (function(){
            let changed=false;
            for(const s of document.querySelectorAll('select')){
              const opts=[...s.options];
              const o=opts.find(x=>/^(臺中市|台中市)$/.test((x.textContent||'').trim()));
              if(o && s.value!==o.value){s.value=o.value;s.dispatchEvent(new Event('input',{bubbles:true}));s.dispatchEvent(new Event('change',{bubbles:true}));changed=true;}
            }
            return changed?'已選擇臺中市，等待行政區選項載入':'未發現臺中市選項，直接掃描目前頁面';
          })();
        """.trimIndent()
        web.evaluateJavascript(js) { raw ->
            status.text = raw?.trim('"') ?: "正在掃描目前頁面"
            web.postDelayed({ scanAll() }, 1300)
        }
    }

    private fun scanAll() {
        status.text = "正在掃描目前頁面所有公開 DIST 選項…"
        val js = """
          (function(){
            const found=new Map();
            const add=(v,label,source)=>{
              if(v===undefined||v===null)return;
              v=String(v).trim(); label=String(label||'').trim();
              if(!v||v.length>80||/^(請選擇|選擇|全部|--.*--|null|undefined)$/i.test(v))return;
              const key=v+'|'+label;
              if(!found.has(key))found.set(key,{dist:v,label:label,source:source});
            };
            for(const [si,s] of [...document.querySelectorAll('select')].entries()){
              const hint=((s.id||'')+' '+(s.name||'')+' '+(s.getAttribute('onchange')||'')+' '+(s.getAttribute('aria-label')||'')).toLowerCase();
              const opts=[...s.options];
              const looksDistrict=/dist|district|area|town|city|county|region/.test(hint) ||
                opts.some(o=>/大里區|霧峰區|太平區|北屯區|西屯區|南屯區|烏日區/.test(o.textContent||''));
              if(looksDistrict) opts.forEach(o=>add(o.value,o.textContent,'select:'+hint.trim()));
            }
            document.querySelectorAll('[data-dist],[dist],input[name*=dist i]').forEach((e,i)=>
              add(e.getAttribute('data-dist')||e.getAttribute('dist')||e.value,e.textContent||e.getAttribute('aria-label'),'attribute'));
            const scripts=[...document.scripts].map(s=>s.textContent||'').join('\n');
            const patterns=[
              /(?:dist|district)\s*[:=]\s*['"]([^'"]{1,80})['"]/ig,
              /[?&]dist=([^&'"\s]+)/ig,
              /selectDistrict\s*\(\s*['"]([^'"]{1,80})['"]/ig
            ];
            for(const re of patterns){let m;while((m=re.exec(scripts))!==null)add(decodeURIComponent(m[1]),'','script-pattern');}
            const items=[...found.values()];
            const target=items.filter(x=>/大里區/.test(x.label));
            return JSON.stringify({url:location.href,title:document.title,count:items.length,target:target,items:items});
          })();
        """.trimIndent()
        web.evaluateJavascript(js) { raw ->
            try {
                val decoded = if (raw.isNullOrBlank() || raw == "null") "{}"
                    else org.json.JSONTokener(raw).nextValue().toString()
                val obj = JSONObject(decoded)
                val items = obj.optJSONArray("items") ?: JSONArray()
                val clean = JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    if (item.optString("dist").isNotBlank()) clean.put(item)
                }
                lastJson = clean.toString(2)
                val dali = (0 until clean.length()).map { clean.getJSONObject(it) }
                    .filter { it.optString("label").contains("大里區") }
                result.text = buildString {
                    append("找到 ${clean.length()} 個候選 DIST 值\n")
                    if (dali.isNotEmpty()) {
                        append("\n【大里區匹配】\n")
                        dali.forEach { append("DIST：${it.optString("dist")}  ·  ${it.optString("label")}\n") }
                    } else append("\n目前頁面尚未找到大里區選項。\n")
                    append("\n完整候選清單：\n")
                    for (i in 0 until clean.length()) {
                        val it = clean.getJSONObject(i)
                        append("${i + 1}. ${it.optString("dist")}  ${it.optString("label")}\n")
                    }
                    append("\n來源頁面：${obj.optString("url")}\n可複製或匯出 JSON。")
                }
                status.text = "掃描完成：${clean.length()} 個候選值；大里區匹配 ${dali.size} 個"
            } catch (e: Exception) {
                status.text = "解析失敗：${e.message}"
                result.text = "無法解析掃描結果，請重新掃描。"
            }
        }
    }
}
