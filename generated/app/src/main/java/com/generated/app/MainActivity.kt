package com.generated.app
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        val savedApiKey = prefs.getString("api_key", "") ?: ""
        
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GeminiScreen(
                        initialApiKey = savedApiKey,
                        onSaveApiKey = { key ->
                            prefs.edit().putString("api_key", key).apply()
                        },
                        onSendApiRequest = { apiKey, prompt, callback ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val result = fetchGeminiResponse(apiKey, prompt)
                                withContext(Dispatchers.Main) {
                                    callback(result)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun fetchGeminiResponse(apiKey: String, promptText: String): String {
        if (apiKey.isBlank()) {
            return "錯誤：請先輸入您的 API Key。"
        }
        if (promptText.isBlank()) {
            return "錯誤：請輸入提示文字。"
        }

        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey"
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Build JSON Body: {"contents": [{"parts": [{"text": "..."}]}]}
            val jsonBody = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            
            partObj.put("text", promptText)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            jsonBody.put("contents", contentsArray)

            val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
            writer.write(jsonBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(response.toString())
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return parts.getJSONObject(0).optString("text", "無回應內容")
                        }
                    }
                }
                return "解析回應內容失敗。"
            } else {
                return "請求失敗 (HTTP $responseCode): ${response.toString()}"
            }
        } catch (e: Exception) {
            return "連線發生錯誤: ${e.localizedMessage ?: e.message}"
        } finally {
            connection?.disconnect()
        }
    }
}

@Composable
fun GeminiScreen(
    initialApiKey: String,
    onSaveApiKey: (String) -> Unit,
    onSendApiRequest: (apiKey: String, prompt: String, onResult: (String) -> Unit) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var prompt by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gemini API 測試器",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                onSaveApiKey(it)
            },
            label = { Text("API Key (YOUR_KEY_HERE)") },
            placeholder = { Text("請貼上您的 Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("提示詞 / Prompt") },
            placeholder = { Text("輸入想問 Gemini 的問題...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5
        )

        Button(
            onClick = {
                isLoading = true
                resultText = "連線中，請稍候..."
                onSendApiRequest(apiKey, prompt) { result ->
                    isLoading = false
                    resultText = result
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("發送 HTTP POST 請求")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "回應結果：",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (resultText.isEmpty()) "尚未發送請求" else resultText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}