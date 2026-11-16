// Build trigger: anonymous Rui APK Builder release candidate
package com.rui.aibuilder

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import java.io.DataOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Bg=Color(0xFF101114)
private val Panel=Color(0xFF1B1D22)
private val Accent=Color(0xFF8B9CFF)
private val Muted=Color(0xFF9A9DA8)
data class ChatLine(val text:String,val fromUser:Boolean)
data class HistoryItem(val id:Long,val prompt:String,val projectName:String,val jobId:String,val status:String,val createdAt:Long)
private fun readHistory(p:android.content.SharedPreferences):List<HistoryItem> = try {
 val j=org.json.JSONArray(p.getString("history","[]")?:"[]")
 (0 until j.length()).mapNotNull{i->j.optJSONObject(i)?.let{o->HistoryItem(o.optLong("id"),o.optString("prompt"),o.optString("projectName"),o.optString("jobId"),o.optString("status"),o.optLong("createdAt"))}}.sortedByDescending{it.createdAt}.take(30)
}catch(_:Exception){emptyList()}
private fun writeHistory(p:android.content.SharedPreferences,list:List<HistoryItem>){
 val j=org.json.JSONArray();list.take(30).forEach{h->j.put(JSONObject().put("id",h.id).put("prompt",h.prompt).put("projectName",h.projectName).put("jobId",h.jobId).put("status",h.status).put("createdAt",h.createdAt))};p.edit().putString("history",j.toString()).apply()
}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{RuiBuilderApp()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuiBuilderApp(){
 val context= LocalContext.current
 val prefs= remember{context.getSharedPreferences("rui_builder",Context.MODE_PRIVATE)}
 var endpoint by remember{mutableStateOf(prefs.getString("endpoint","https://api-production-3704ca67.up.railway.app")?:"https://api-production-3704ca67.up.railway.app")}
 var prompt by remember{mutableStateOf("")}
 var busy by remember{mutableStateOf(false)}
 var operationProgress by remember{mutableStateOf(0f)}
 var operationStep by remember{mutableStateOf("")}
 var progressIndeterminate by remember{mutableStateOf(false)}
 var showSettings by remember{mutableStateOf(false)}
 var showHistory by remember{mutableStateOf(false)}
 var reviewEnabled by remember{mutableStateOf(prefs.getBoolean("review_enabled",false))}
 var history by remember{mutableStateOf(readHistory(prefs))}
 var status by remember{mutableStateOf("免登入模式：可直接使用雲端 AI")}
 var projectFiles by remember{mutableStateOf<Map<String,String>>(emptyMap())}
 var jobId by remember{mutableStateOf("")}
 var projectName by remember{mutableStateOf("RuiGeneratedApp")}
 var modelStatus by remember{mutableStateOf("正在讀取模型清單…")}
 var builtinModels by remember{mutableStateOf(listOf<Triple<String,String,Boolean>>())}
 var activeModelName by remember{mutableStateOf("")}
 var modelDownloadProgress by remember{mutableStateOf(0f)}
 var modelDownloadMessage by remember{mutableStateOf("")}
 var modelDownloadBusy by remember{mutableStateOf(false)}
 val messages= remember{mutableStateListOf(ChatLine("你好！我是 Rui AI APK Builder。免登入模式已啟用，描述你的 App，我會產生 Android 專案並交給雲端 Worker 編譯 APK。",false))}
 val scope= rememberCoroutineScope()
 LaunchedEffect(endpoint){
  try{
   val catalog=api(endpoint,"/api/models/catalog",null,null)
   val arr=catalog.optJSONArray("models")?:org.json.JSONArray()
   builtinModels=(0 until arr.length()).map{val o=arr.getJSONObject(it);Triple(o.optString("name"),o.optString("description"),o.optBoolean("installed",false))}
   activeModelName=catalog.optString("active")
   modelStatus=if(activeModelName.isNotBlank())"目前使用：$activeModelName" else "尚未選擇模型"
  }catch(e:Exception){modelStatus="模型清單載入失敗：${e.message?: "API 無法連線"}"}
 }
 fun saveHistory(pText:String,name:String,job:String,state:String){
  val entry=HistoryItem(System.currentTimeMillis(),pText,name,job,state,System.currentTimeMillis())
  history=(listOf(entry)+history).take(30);writeHistory(prefs,history)
 }
 val ggufPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null)scope.launch{
   val name=queryDisplayName(context,uri)
   if(!name.lowercase().endsWith(".gguf")){status="檔案格式錯誤：請選擇 .gguf";return@launch}
   busy=true;operationProgress=0f;operationStep="GGUF 匯入：上傳檔案並建立雲端模型";progressIndeterminate=true;status=operationStep
   try{
    val result=uploadGguf(context,endpoint,uri,name)
    if(!result.optBoolean("verified",false))throw IllegalStateException("伺服器未完成模型驗證，請勿使用此模型")
    val catalog=api(endpoint,"/api/models/catalog",null,null)
    val arr=catalog.optJSONArray("models")?:org.json.JSONArray()
    builtinModels=(0 until arr.length()).map{val o=arr.getJSONObject(it);Triple(o.optString("name"),o.optString("description"),o.optBoolean("installed",false))}
    activeModelName=result.optString("active")
    modelStatus="目前使用："+activeModelName
    progressIndeterminate=false;operationProgress=1f;operationStep="完成：GGUF 已匯入並通過實際推理測試";status="GGUF 匯入成功，已通過雲端推理驗證並切換："+result.optString("name")
   }catch(e:Exception){progressIndeterminate=false;status="GGUF 匯入失敗："+(e.message?:"未知錯誤")}
   busy=false
  }
 }

 suspend fun generate(){
  val requestedPrompt=prompt.trim()
  if(requestedPrompt.isBlank())return
  messages.add(ChatLine(requestedPrompt,true))
  busy=true;operationProgress=0.08f;operationStep="步驟 1/3：連線雲端模型";status="步驟 1/3：正在連線雲端模型…"
  try{
   operationStep="步驟 2/3：模型正在理解需求並生成 Android 程式碼";progressIndeterminate=true;status=operationStep
   val result=api(endpoint,"/api/projects/generate",JSONObject().put("prompt",requestedPrompt).put("review_enabled",reviewEnabled),null)
   progressIndeterminate=false;operationProgress=0.88f;operationStep="步驟 3/3：驗證專案檔案與 Gradle 結構";status=operationStep
   val obj=result.getJSONObject("files");val map=mutableMapOf<String,String>()
   val keys=obj.keys();while(keys.hasNext()){val key=keys.next();map[key]=obj.getString(key)}
   projectFiles=map
   projectName=result.optString("project_name","RuiGeneratedApp")
   saveHistory(requestedPrompt,projectName,"","專案已生成")
   messages.add(ChatLine("專案已生成：${result.optString("project_name","RuiGeneratedApp")}，共 ${map.size} 個檔案。\n${result.optString("notes","")}\n確認後按「雲端編譯 APK」。",false))
   operationProgress=1f;operationStep="完成：專案已生成";status="完成：專案生成成功（${map.size} 個檔案）"
  }catch(e:Exception){status="生成失敗：${e.message?:e.javaClass.simpleName}";messages.add(ChatLine("生成失敗：${e.message?:e.javaClass.simpleName}",false))}
  finally{busy=false}
 }

 suspend fun build(){
  if(projectFiles.isEmpty()){status="請先生成 Android 專案";return}
  busy=true;operationProgress=0.03f;operationStep="步驟 1/6：提交專案到雲端 Worker";status=operationStep
  try{
   val files=JSONObject();projectFiles.forEach{(k,v)->files.put(k,v)}
   val queued=api(endpoint,"/api/build",JSONObject().put("files",files),null)
   jobId=queued.getString("job_id");operationProgress=0.05f;operationStep="步驟 2/6：雲端工作目錄建立中";status="已排入編譯佇列：$jobId"
   saveHistory(prompt,projectName,jobId,"排隊中")
   var finished=false
   repeat(190){
    if(!finished){
     delay(4000)
     val state=api(endpoint,"/api/build/$jobId",null,null)
     val stage=state.optString("stage")
     operationProgress=(state.optDouble("progress",35.0)/100.0).toFloat().coerceIn(0f,1f)
     operationStep=when(stage){
      "preparing"->"步驟 2/6：建立雲端工作目錄"
      "writing_files"->"步驟 3/6：寫入 Kotlin、Manifest 與 Gradle 專案檔"
      "validating"->"步驟 4/6：檢查專案結構與必要設定"
      "gradle"->"步驟 5/6：Gradle 解析依賴並編譯 APK（此階段可能較久）"
      "packaging"->"步驟 6/6：整理 APK 並準備下載"
      "complete"->"完成：APK 已可下載"
      else->state.optString("message","雲端 Worker 正在處理")
     }
     progressIndeterminate=(stage=="gradle")
     status=operationStep
     when(state.optString("status")){
      "success"->{finished=true;operationProgress=1f;operationStep="完成：APK 已編譯並封裝";status="APK 編譯完成！按「下載 APK」取得檔案";saveHistory(prompt,projectName,jobId,"編譯成功");messages.add(ChatLine("雲端編譯成功，APK 大小：${state.optLong("size")} bytes。",false))}
      "failed"->{finished=true;saveHistory(prompt,projectName,jobId,"編譯失敗");status="編譯失敗：${state.optString("error","請檢查專案")}"}
      else->status="雲端編譯中…（$jobId）"
     }
    }
   }
   if(!finished)status="編譯仍在執行，可稍後再次查詢：$jobId"
  }catch(e:Exception){status="提交/編譯失敗：${e.message?:e.javaClass.simpleName}";messages.add(ChatLine("編譯錯誤：${e.message?:e.javaClass.simpleName}",false))}
  finally{busy=false}
 }

 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Panel,onBackground=Color.White,onSurface=Color.White)){
  Scaffold(containerColor=Bg,topBar={TopAppBar(title={Column{Text("Rui AI Builder",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text("ANDROID STUDIO · CLOUD BUILD",color=Muted,style=MaterialTheme.typography.labelSmall)}},actions={Row{TextButton(onClick={showHistory=true}){Text("歷史")};TextButton(onClick={showSettings=true}){Text("設定")}}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg,titleContentColor=Color.White))},
  bottomBar={Column(Modifier.fillMaxWidth().background(Bg).padding(12.dp)){
   Text(status,color=Muted,style=MaterialTheme.typography.labelSmall)
   if(busy){Spacer(Modifier.height(5.dp));Text(operationStep,color=Accent,style=MaterialTheme.typography.labelSmall);if(progressIndeterminate)LinearProgressIndicator(modifier=Modifier.fillMaxWidth()) else LinearProgressIndicator(progress={operationProgress},modifier=Modifier.fillMaxWidth())}
   Spacer(Modifier.height(6.dp))
   OutlinedTextField(value=prompt,onValueChange={prompt=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("例如：製作一款有相機、相簿與濾鏡的圖片編輯 App…",color=Muted)},minLines=2,maxLines=5,shape=RoundedCornerShape(18.dp),enabled=!busy,colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,focusedContainerColor=Panel,unfocusedContainerColor=Panel))
   Spacer(Modifier.height(8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
    Button(onClick={scope.launch{generate()}},enabled=!busy&&prompt.isNotBlank(),modifier=Modifier.weight(1f)){Text("生成專案")}
    Button(onClick={scope.launch{build()}},enabled=!busy&&projectFiles.isNotEmpty(),modifier=Modifier.weight(1f)){Text("雲端編譯")}
   }
   if(jobId.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
    OutlinedButton(onClick={scope.launch{try{val s=api(endpoint,"/api/build/$jobId",null,null);status="編譯狀態：${s.optString("status")}"}catch(e:Exception){status=e.message?:"查詢失敗"}}},modifier=Modifier.weight(1f)){Text("查詢狀態")}
    OutlinedButton(onClick={ downloadApk(context,endpoint,jobId) },modifier=Modifier.weight(1f)){Text("下載 APK")}
   }
  }}){padding->
   Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=14.dp)){
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)){
     Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      Text("工作台",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
      Text("描述需求、匯入模型，然後編譯成可安裝的 APK。",color=Muted,style=MaterialTheme.typography.bodySmall)
      Text(if(projectFiles.isEmpty())"尚未生成專案" else "已載入 ${projectFiles.size} 個專案檔案",color=Muted,style=MaterialTheme.typography.bodySmall)
      Text(modelStatus,color=Accent,style=MaterialTheme.typography.bodySmall)
      Text("內建雲端模型",fontWeight=FontWeight.SemiBold)
      if(builtinModels.isEmpty()) Text("正在載入模型清單，請確認 API 已更新。",color=Muted,style=MaterialTheme.typography.bodySmall)
      builtinModels.forEach{(name,description,installed)->
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Column(Modifier.weight(1f)){Text(name,style=MaterialTheme.typography.bodyMedium);Text(description,color=Muted,style=MaterialTheme.typography.labelSmall)}
        TextButton(enabled=!busy&&!modelDownloadBusy,onClick={scope.launch{
         modelDownloadBusy=true;modelDownloadProgress=0f;modelDownloadMessage=if(installed)"正在啟用已安裝模型…" else "正在建立模型下載工作…"
         try{
          api(endpoint,"/api/models/pull/"+name,null,null)
          var state="queued"
          for(i in 0 until 1200){
           val p=api(endpoint,"/api/models/pull-status",null,null)
           state=p.optString("status")
           modelDownloadMessage=p.optString("message","下載中…")
           val total=p.optLong("total");val done=p.optLong("completed")
           modelDownloadProgress=if(total>0)(done.toFloat()/total).coerceIn(0f,1f) else 0f
           if(state=="success"||state=="failed")break
           delay(1500)
          }
          if(state=="success"){activeModelName=name;modelStatus="目前使用：$name";modelDownloadMessage="模型下載及載入完成"}
          else if(state=="failed")modelDownloadMessage="下載失敗："+modelDownloadMessage
          else modelDownloadMessage="下載仍在執行，請稍後重新整理狀態"
         }catch(e:Exception){modelDownloadMessage="模型下載失敗：${e.message}"}
         modelDownloadBusy=false
        }}){Text(if(activeModelName==name)"使用中" else if(installed)"啟用" else "下載 / 使用")}
       }
      }
      if(modelDownloadBusy||modelDownloadMessage.isNotBlank()){
       Text(modelDownloadMessage,color=Muted,style=MaterialTheme.typography.bodySmall)
       if(modelDownloadBusy){if(modelDownloadProgress>0f)LinearProgressIndicator(progress={modelDownloadProgress},modifier=Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())}
      }
      OutlinedButton(onClick={ggufPicker.launch(arrayOf("*/*"))},enabled=!busy&&!modelDownloadBusy,modifier=Modifier.fillMaxWidth()){Text("自行匯入 .GGUF 模型到雲端")}
     }
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(modifier=Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     items(messages){item->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(item.fromUser)Arrangement.End else Arrangement.Start){Surface(color=if(item.fromUser)Color(0xFF303B68) else Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.widthIn(max=320.dp)){Text(item.text,Modifier.padding(13.dp),color=Color.White)}}}
     if(busy)item{Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp));Text("AI / Worker 正在處理…",color=Muted)}}
    }
   }
  }
  if(showSettings){
   var draft by remember{mutableStateOf(endpoint)}
   AlertDialog(onDismissRequest={showSettings=false},title={Text("雲端服務設定")},text={Column{
    Text("設定雲端 API 網址；此版本不需要帳號或 OAuth。",style=MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp));OutlinedTextField(value=draft,onValueChange={draft=it},label={Text("API 服務網址")},placeholder={Text("https://your-domain.example")},singleLine=true)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text("額外內容審查");Text(if(reviewEnabled)"開啟：生成時要求模型額外檢查內容" else "關閉：不增加應用程式層審查提示",color=Muted,style=MaterialTheme.typography.bodySmall)}
     Switch(checked=reviewEnabled,onCheckedChange={reviewEnabled=it;prefs.edit().putBoolean("review_enabled",it).apply()})
    }
    Text("此開關只控制額外審查提示，不會關閉模型、平台或法律本身的安全限制。",color=Muted,style=MaterialTheme.typography.labelSmall)
   }},confirmButton={TextButton(onClick={endpoint=draft.trim().trimEnd('/');prefs.edit().putString("endpoint",endpoint).apply();status="設定已儲存";showSettings=false}){Text("儲存")}},dismissButton={TextButton(onClick={showSettings=false}){Text("取消")}})
  }
  if(showHistory){
   AlertDialog(onDismissRequest={showHistory=false},title={Text("歷史紀錄")},text={
    if(history.isEmpty())Text("目前沒有生成紀錄。")
    else LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     items(history,key={it.id}){h->
      Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()){
       Column(Modifier.padding(10.dp)){
        Text(h.projectName,fontWeight=FontWeight.SemiBold)
        Text(h.prompt,maxLines=2,color=Muted,style=MaterialTheme.typography.bodySmall)
        Text(h.status+(if(h.jobId.isNotBlank())" · 工作 ${h.jobId}" else ""),color=Accent,style=MaterialTheme.typography.labelSmall)
        TextButton(onClick={prompt=h.prompt;jobId=h.jobId;projectName=h.projectName;projectFiles=emptyMap();status="提示詞已載入；請重新生成專案以還原檔案";showHistory=false}){Text("載入")}
       }
      }
     }
    }
   },confirmButton={TextButton(onClick={showHistory=false}){Text("關閉")}},dismissButton={TextButton(onClick={history=emptyList();writeHistory(prefs,emptyList());status="歷史紀錄已清除"}){Text("清除紀錄")}})
  }
 }
}

private suspend fun api(base:String,path:String,body:JSONObject?,token:String?):JSONObject=withContext(Dispatchers.IO){
 if(base.isBlank())throw IllegalStateException("尚未設定 API 網址")
 val c=URL(base.trimEnd('/')+path).openConnection() as HttpURLConnection
 c.requestMethod=if(body==null)"GET" else "POST";c.connectTimeout=20000;c.readTimeout=120000
 c.setRequestProperty("Accept","application/json")
 if(token!=null)c.setRequestProperty("Authorization","Bearer $token")
 if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
 val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream
 val text=stream?.bufferedReader()?.use{it.readText()}?:""
 c.disconnect()
 if(code !in 200..299)throw IllegalStateException("HTTP $code: $text")
 JSONObject(text)
}


private fun queryDisplayName(context:Context,uri:Uri):String{
 var name="model.gguf"
 context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
  if(cursor.moveToFirst()){val i=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)name=cursor.getString(i)?:name}
 }
 return name
}

private suspend fun uploadGguf(context:Context,base:String,uri:Uri,filename:String):JSONObject=withContext(Dispatchers.IO){
 val boundary="RuiGguf"+System.currentTimeMillis()
 val connection=URL(base.trimEnd('/')+"/api/models/import").openConnection() as HttpURLConnection
 connection.requestMethod="POST";connection.connectTimeout=30000;connection.readTimeout=1800000
 connection.doOutput=true;connection.setChunkedStreamingMode(1024*1024)
 connection.setRequestProperty("Accept","application/json")
 connection.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
 DataOutputStream(connection.outputStream).use{out->
  out.writeBytes("--$boundary\r\n")
  out.writeBytes("Content-Disposition: form-data; name=\"model_name\"\r\n\r\n")
  out.writeBytes(filename.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_.-]"),"-")+"\r\n")
  out.writeBytes("--$boundary\r\n")
  out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n")
  out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
  context.contentResolver.openInputStream(uri)?.use{input->
   val buffer=ByteArray(1024*1024);while(true){val count=input.read(buffer);if(count<0)break;out.write(buffer,0,count)}
  }?:throw IllegalStateException("無法讀取選取的 GGUF 檔案")
  out.writeBytes("\r\n--$boundary--\r\n")
 }
 val code=connection.responseCode
 val stream=if(code in 200..299)connection.inputStream else connection.errorStream
 val body=stream?.bufferedReader()?.use{it.readText()}?:""
 connection.disconnect()
 if(code !in 200..299)throw IllegalStateException("HTTP $code: $body")
 JSONObject(body)
}

private fun downloadApk(context:Context,base:String,job:String){
 try{
  val request=DownloadManager.Request(Uri.parse(base.trimEnd('/')+"/api/build/$job/download"))
   .setTitle("Rui AI APK")
   .setDescription("下載雲端編譯完成的 APK")
   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"Rui-$job.apk")
  val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  manager.enqueue(request)
 }catch(_:Exception){}
}
