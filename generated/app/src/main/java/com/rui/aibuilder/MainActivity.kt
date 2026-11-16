package com.rui.aibuilder

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{RuiBuilderApp()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuiBuilderApp(){
 val context= LocalContext.current
 val prefs= remember{context.getSharedPreferences("rui_builder",Context.MODE_PRIVATE)}
 var endpoint by remember{mutableStateOf(prefs.getString("endpoint","")?:"")}
 var webClientId by remember{mutableStateOf(prefs.getString("web_client_id","")?:"")}
 var idToken by remember{mutableStateOf(prefs.getString("id_token","")?:"")}
 var userEmail by remember{mutableStateOf(prefs.getString("user_email","")?:"")}
 var prompt by remember{mutableStateOf("")}
 var busy by remember{mutableStateOf(false)}
 var showSettings by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("尚未連接雲端 AI 服務")}
 var projectFiles by remember{mutableStateOf<Map<String,String>>(emptyMap())}
 var jobId by remember{mutableStateOf("")}
 val messages= remember{mutableStateListOf(ChatLine("你好！我是 Rui AI APK Builder。登入 Google 後，描述你的 App，我會產生 Android 專案並交給雲端 Worker 編譯 APK。",false))}
 val scope= rememberCoroutineScope()

 suspend fun login(){
  if(webClientId.isBlank()){status="請先在服務設定填入 Google OAuth Web Client ID";return}
  try{
   val option=GetGoogleIdOption.Builder().setServerClientId(webClientId.trim()).setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
   val request=GetCredentialRequest.Builder().addCredentialOption(option).build()
   val credential=CredentialManager.create(context).getCredential(context,request).credential
   if(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){
    val google=GoogleIdTokenCredential.createFrom(credential.data)
    val token=google.idToken
    val result=api(endpoint,"/api/auth/google",JSONObject().put("id_token",token),null)
    idToken=token;userEmail=result.optString("email",google.id);prefs.edit().putString("id_token",token).putString("user_email",userEmail).apply()
    status="Google 已登入：$userEmail"
   }else status="Google 登入未取得有效憑證"
  }catch(e:Exception){status="Google 登入失敗：${e.message?: "請檢查 OAuth 設定"}"}
 }

 suspend fun generate(){
  if(idToken.isBlank()){status="請先使用 Google 登入";return}
  if(prompt.isBlank())return
  busy=true;status="AI 正在生成 Android 專案…"
  try{
   val result=api(endpoint,"/api/projects/generate",JSONObject().put("prompt",prompt),idToken)
   val obj=result.getJSONObject("files");val map=mutableMapOf<String,String>()
   val keys=obj.keys();while(keys.hasNext()){val key=keys.next();map[key]=obj.getString(key)}
   projectFiles=map
   messages.add(ChatLine("專案已生成：${result.optString("project_name","RuiGeneratedApp")}，共 ${map.size} 個檔案。\n${result.optString("notes","")}\n確認後按「雲端編譯 APK」。",false))
   status="專案生成完成（${map.size} 個檔案）"
  }catch(e:Exception){status="生成失敗：${e.message}"}
  busy=false
 }

 suspend fun build(){
  if(idToken.isBlank()){status="請先使用 Google 登入";return}
  if(projectFiles.isEmpty()){status="請先生成 Android 專案";return}
  busy=true;status="正在提交雲端 APK Worker…"
  try{
   val files=JSONObject();projectFiles.forEach{(k,v)->files.put(k,v)}
   val queued=api(endpoint,"/api/build",JSONObject().put("files",files),idToken)
   jobId=queued.getString("job_id");status="已排入編譯佇列：$jobId"
   var finished=false
   repeat(190){
    if(!finished){
     delay(4000)
     val state=api(endpoint,"/api/build/$jobId",null,idToken)
     when(state.optString("status")){
      "success"->{finished=true;status="APK 編譯完成！按「下載 APK」取得檔案";messages.add(ChatLine("雲端編譯成功，APK 大小：${state.optLong("size")} bytes。",false))}
      "failed"->{finished=true;status="編譯失敗：${state.optString("error","請檢查專案")}"}
      else->status="雲端編譯中…（$jobId）"
     }
    }
   }
   if(!finished)status="編譯仍在執行，可稍後再次查詢：$jobId"
  }catch(e:Exception){status="提交/編譯失敗：${e.message}"}
  busy=false
 }

 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Panel,onBackground=Color.White,onSurface=Color.White)){
  Scaffold(containerColor=Bg,topBar={TopAppBar(title={Column{Text("Rui AI APK Builder",fontWeight=FontWeight.Bold);Text(if(userEmail.isBlank())"雲端 Android 開發工作台" else userEmail,color=Muted,style=MaterialTheme.typography.labelSmall)}},actions={TextButton(onClick={showSettings=true}){Text("設定")};TextButton(enabled=!busy,onClick={ scope.launch{login()} }){Text(if(idToken.isBlank())"Google 登入" else "已登入")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg,titleContentColor=Color.White))},
  bottomBar={Column(Modifier.fillMaxWidth().background(Bg).padding(12.dp)){
   Text(status,color=Muted,style=MaterialTheme.typography.labelSmall);Spacer(Modifier.height(6.dp))
   OutlinedTextField(value=prompt,onValueChange={prompt=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("描述你要開發的 Android App…",color=Muted)},minLines=2,maxLines=5,shape=RoundedCornerShape(16.dp),enabled=!busy)
   Spacer(Modifier.height(8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
    Button(onClick={scope.launch{generate()}},enabled=!busy&&prompt.isNotBlank(),modifier=Modifier.weight(1f)){Text("生成專案")}
    Button(onClick={scope.launch{build()}},enabled=!busy&&projectFiles.isNotEmpty(),modifier=Modifier.weight(1f)){Text("雲端編譯")}
   }
   if(jobId.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
    OutlinedButton(onClick={scope.launch{try{val s=api(endpoint,"/api/build/$jobId",null,idToken);status="編譯狀態：${s.optString("status")}"}catch(e:Exception){status=e.message?:"查詢失敗"}}},modifier=Modifier.weight(1f)){Text("查詢狀態")}
    OutlinedButton(onClick={ downloadApk(context,endpoint,jobId,idToken) },modifier=Modifier.weight(1f)){Text("下載 APK")}
   }
  }}){padding->
   Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=14.dp)){
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)){
     Column(Modifier.padding(12.dp)){Text("專案工作區",fontWeight=FontWeight.SemiBold);Text(if(projectFiles.isEmpty())"尚未生成專案" else "已載入 ${projectFiles.size} 個專案檔案",color=Muted,style=MaterialTheme.typography.bodySmall)}
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(modifier=Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     items(messages){item->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(item.fromUser)Arrangement.End else Arrangement.Start){Surface(color=if(item.fromUser)Color(0xFF303B68) else Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.widthIn(max=320.dp)){Text(item.text,Modifier.padding(13.dp),color=Color.White)}}}
     if(busy)item{Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp));Text("AI / Worker 正在處理…",color=Muted)}}
    }
   }
  }
  if(showSettings){
   var draft by remember{mutableStateOf(endpoint)};var clientDraft by remember{mutableStateOf(webClientId)}
   AlertDialog(onDismissRequest={showSettings=false},title={Text("雲端服務設定")},text={Column{
    Text("API 網址與 Google OAuth Web Client ID（不是 API Key）。",style=MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp));OutlinedTextField(value=draft,onValueChange={draft=it},label={Text("API 服務網址")},placeholder={Text("https://your-domain.example")},singleLine=true)
    Spacer(Modifier.height(8.dp));OutlinedTextField(value=clientDraft,onValueChange={clientDraft=it},label={Text("Google OAuth Web Client ID")},singleLine=true)
   }},confirmButton={TextButton(onClick={endpoint=draft.trim().trimEnd('/');webClientId=clientDraft.trim();prefs.edit().putString("endpoint",endpoint).putString("web_client_id",webClientId).apply();status="設定已儲存";showSettings=false}){Text("儲存")}},dismissButton={TextButton(onClick={showSettings=false}){Text("取消")}})
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

private fun downloadApk(context:Context,base:String,job:String,token:String){
 try{
  val request=DownloadManager.Request(Uri.parse(base.trimEnd('/')+"/api/build/$job/download"))
   .setTitle("Rui AI APK")
   .setDescription("下載雲端編譯完成的 APK")
   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"Rui-$job.apk")
   .addRequestHeader("Authorization","Bearer $token")
  val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  manager.enqueue(request)
 }catch(_:Exception){}
}
