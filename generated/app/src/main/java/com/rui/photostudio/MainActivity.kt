package com.rui.photostudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rui.photostudio.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val Bg=Color(0xFF090A0C)
private val Accent=Color(0xFF80B7FF)
private data class Control(val title:String,val value:Float,val range:ClosedFloatingPointRange<Float>,val set:(Float)->Unit)
private data class HistoryEntry(val id:Long,val title:String,val time:Long,val imageUri:String,val settingsJson:String)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=android.graphics.Color.rgb(9,10,12)
  window.navigationBarColor=android.graphics.Color.rgb(9,10,12)
  setContent{Editor()}
 }
}

@Composable private fun Editor(){
 val context=LocalContext.current
 val scope= rememberCoroutineScope()
 val gson= remember{Gson()}
 val prefs= remember{context.getSharedPreferences("rui_photo_history",0)}
 var source by remember{mutableStateOf<Bitmap?>(null)}
 var sourceUri by remember{mutableStateOf("")}
 var preview by remember{mutableStateOf<Bitmap?>(null)}
 var settings by remember{mutableStateOf(ProfessionalSettings())}
 var undoStack by remember{mutableStateOf(listOf<ProfessionalSettings>())}
 var redoStack by remember{mutableStateOf(listOf<ProfessionalSettings>())}
 var lastSnapshot by remember{mutableStateOf(settings)}
 var historyAction by remember{mutableStateOf(false)}
 var tab by remember{mutableStateOf("基本")}
 var local by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)}
 var note by remember{mutableStateOf("從相簿選擇照片開始編輯")}
 var bandIndex by remember{mutableIntStateOf(0)}
 var curveChannel by remember{mutableStateOf("Master")}
 var showHistory by remember{mutableStateOf(false)}
 var showFullscreen by remember{mutableStateOf(false)}
 var history by remember{mutableStateOf(runCatching{
  val raw=prefs.getString("entries","[]")?: "[]"
  gson.fromJson<List<HistoryEntry>>(raw,object:TypeToken<List<HistoryEntry>>(){}.type)?: emptyList()
 }.getOrDefault(emptyList()))}

 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri?->
  if(uri!=null){
   busy=true
   scope.launch{
    val savedUri=withContext(Dispatchers.IO){copyToHistoryStorage(context,uri)}
    val loaded=if(savedUri!=null)withContext(Dispatchers.IO){decode(context,savedUri)}else null
    source=loaded
    sourceUri=savedUri?.toString().orEmpty()
    settings=ProfessionalSettings()
    note=if(loaded==null)"讀取照片失敗" else "已從相簿載入照片"
    busy=false
   }
  }
 }

 LaunchedEffect(source,settings,local){
  val img=source
  if(img==null){preview=null;return@LaunchedEffect}
  busy=true
  val result=withContext(Dispatchers.Default){
   val scale=minOf(1f,1100f/max(img.width,img.height))
   val small=if(scale<1f)Bitmap.createScaledBitmap(img,(img.width*scale).toInt().coerceAtLeast(1),(img.height*scale).toInt().coerceAtLeast(1),true)else img
   if(local)ProfessionalEngine.apply(small,settings,RadialMask(.5f,.5f,.42f,.42f))else ProfessionalEngine.apply(small,settings)
  }
  preview=result
  busy=false
 }

 // Debounced snapshots provide undo/redo without storing every slider frame.
 LaunchedEffect(settings){
  if(historyAction){lastSnapshot=settings;historyAction=false}
  else{
   delay(300)
   if(settings!=lastSnapshot){
    undoStack=(undoStack+lastSnapshot).takeLast(100)
    redoStack=emptyList()
    lastSnapshot=settings
   }
  }
 }

 // Persist a snapshot after the user pauses editing; history survives app restarts.
 LaunchedEffect(sourceUri,settings){
  if(sourceUri.isNotBlank()){
   delay(650)
   val json=gson.toJson(settings)
   if(history.lastOrNull()?.let{it.imageUri==sourceUri&&it.settingsJson==json}!=true){
    val entry=HistoryEntry(System.currentTimeMillis(),"編輯版本 ${history.size+1}",System.currentTimeMillis(),sourceUri,json)
    history=(history+entry).takeLast(80)
    prefs.edit().putString("entries",gson.toJson(history)).apply()
   }
  }
 }

 val bandHue=listOf(0f,60f,120f,180f,240f,300f)
 val controls=when(tab){
  "基本"->listOf(
   Control("曝光 Exposure",settings.exposure,-5f..5f){settings=settings.copy(exposure=it)},
   Control("對比 Contrast",settings.contrast,0f..2f){settings=settings.copy(contrast=it)},
   Control("高光 Highlights",settings.highlights,-100f..100f){settings=settings.copy(highlights=it)},
   Control("陰影 Shadows",settings.shadows,-100f..100f){settings=settings.copy(shadows=it)},
   Control("白色 Whites",settings.whites,-100f..100f){settings=settings.copy(whites=it)},
   Control("黑色 Blacks",settings.blacks,-100f..100f){settings=settings.copy(blacks=it)},
   Control("色溫 White Balance",settings.temperature,-100f..100f){settings=settings.copy(temperature=it)},
   Control("色調 Tint",settings.tint,-100f..100f){settings=settings.copy(tint=it)},
   Control("自然飽和度 Vibrance",settings.vibrance,-100f..100f){settings=settings.copy(vibrance=it)},
   Control("飽和度 Saturation",settings.saturation,0f..2f){settings=settings.copy(saturation=it)}
  )
  "色彩"->{
   val b=settings.hsl.getOrElse(bandIndex){HslBand(bandHue[bandIndex])}
   listOf(
    Control("HSL 色相",b.hueShift,-100f..100f){val a=settings.hsl.toMutableList();while(a.size<=bandIndex)a.add(HslBand(bandHue[a.size]));a[bandIndex]=b.copy(hueShift=it);settings=settings.copy(hsl=a)},
    Control("HSL 飽和度 / Color Mixer",b.saturationShift,-100f..100f){val a=settings.hsl.toMutableList();while(a.size<=bandIndex)a.add(HslBand(bandHue[a.size]));a[bandIndex]=b.copy(saturationShift=it);settings=settings.copy(hsl=a)},
    Control("HSL 明亮度",b.luminanceShift,-100f..100f){val a=settings.hsl.toMutableList();while(a.size<=bandIndex)a.add(HslBand(bandHue[a.size]));a[bandIndex]=b.copy(luminanceShift=it);settings=settings.copy(hsl=a)},

    Control("陰影色相",settings.shadowsGrade.hue,0f..360f){settings=settings.copy(shadowsGrade=settings.shadowsGrade.copy(hue=it))},
    Control("陰影飽和度",settings.shadowsGrade.saturation,0f..100f){settings=settings.copy(shadowsGrade=settings.shadowsGrade.copy(saturation=it))},
    Control("中間調色相",settings.midtonesGrade.hue,0f..360f){settings=settings.copy(midtonesGrade=settings.midtonesGrade.copy(hue=it))},
    Control("中間調飽和度",settings.midtonesGrade.saturation,0f..100f){settings=settings.copy(midtonesGrade=settings.midtonesGrade.copy(saturation=it))},
    Control("高光色相",settings.highlightsGrade.hue,0f..360f){settings=settings.copy(highlightsGrade=settings.highlightsGrade.copy(hue=it))},
    Control("高光飽和度",settings.highlightsGrade.saturation,0f..100f){settings=settings.copy(highlightsGrade=settings.highlightsGrade.copy(saturation=it))},
    Control("Split Toning 陰影色相",settings.splitShadow.hue,0f..360f){settings=settings.copy(splitShadow=settings.splitShadow.copy(hue=it))},
    Control("Split Toning 高光色相",settings.splitHighlight.hue,0f..360f){settings=settings.copy(splitHighlight=settings.splitHighlight.copy(hue=it))},
    Control("色彩分級平衡",settings.gradingBalance,-100f..100f){settings=settings.copy(gradingBalance=it)},
    Control("Split Toning 平衡",settings.splitBalance,-100f..100f){settings=settings.copy(splitBalance=it)}
   )
  }
  else->listOf(
   Control("銳化 Sharpening",settings.sharpening,0f..200f){settings=settings.copy(sharpening=it)},
   Control("明度降噪",settings.luminanceNoiseReduction,0f..100f){settings=settings.copy(luminanceNoiseReduction=it)},
   Control("色彩降噪",settings.colorNoiseReduction,0f..100f){settings=settings.copy(colorNoiseReduction=it)},
   Control("清晰度 Clarity",settings.clarity,-100f..100f){settings=settings.copy(clarity=it)},
   Control("紋理 Texture",settings.texture,-100f..100f){settings=settings.copy(texture=it)},
   Control("去霧 Dehaze",settings.dehaze,-100f..100f){settings=settings.copy(dehaze=it)},
   Control("暗角 Vignette",settings.vignette,-100f..100f){settings=settings.copy(vignette=it)},
   Control("顆粒 Grain",settings.grain,0f..100f){settings=settings.copy(grain=it)},
   Control("色差修正",settings.chromaticAberration,0f..100f){settings=settings.copy(chromaticAberration=it)},
   Control("鏡頭畸變修正",settings.lensDistortion,-100f..100f){settings=settings.copy(lensDistortion=it)}
  )
 }

 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Color(0xFF17191D))){
  Column(Modifier.fillMaxSize().background(Bg).padding(horizontal=14.dp)){
   Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text("RUI",color=Accent,fontSize=11.sp,fontWeight=FontWeight.Bold);Text("Photo Studio",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)}
    Text("OFFLINE PRO",color=Color.LightGray,fontSize=10.sp)
   }
   Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
    Button(onClick={picker.launch("image/*")},modifier=Modifier.weight(1f)){Text("開啟相簿")}
    OutlinedButton(onClick={showHistory=true}){Text("歷史")}
    OutlinedButton(enabled=undoStack.isNotEmpty(),onClick={
     if(undoStack.isNotEmpty()){
      val target=undoStack.last();redoStack=(redoStack+settings).takeLast(100)
      undoStack=undoStack.dropLast(1);historyAction=true;settings=target
     }
    }){Text("上一步")}
    OutlinedButton(enabled=redoStack.isNotEmpty(),onClick={
     if(redoStack.isNotEmpty()){
      val target=redoStack.last();undoStack=(undoStack+settings).takeLast(100)
      redoStack=redoStack.dropLast(1);historyAction=true;settings=target
     }
    }){Text("下一步")}
    OutlinedButton(onClick={settings=ProfessionalSettings()}){Text("還原全部")}
    Button(enabled=source!=null&&!busy,onClick={
     val img=source
     if(img!=null){busy=true;scope.launch{val ok=withContext(Dispatchers.IO){val out=if(local)ProfessionalEngine.apply(img,settings,RadialMask(.5f,.5f,.42f,.42f))else ProfessionalEngine.apply(img,settings);save(context,out)};note=if(ok)"匯出完成"else"匯出失敗";Toast.makeText(context,if(ok)"已成功匯出至相簿：Rui Photo Studio" else "匯出失敗，請檢查儲存空間後重試",Toast.LENGTH_LONG).show();busy=false}}
    }){Text("匯出")}
   }
   Box(Modifier.weight(1f).fillMaxWidth().padding(top=8.dp).background(Color(0xFF111317),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){
    val img=preview
    if(img==null)Text("從相簿選擇照片",color=Color.White)else Image(img.asImageBitmap(),null,Modifier.fillMaxSize().padding(5.dp).pointerInput(Unit){detectTapGestures(onTap={showFullscreen=true})},contentScale=ContentScale.Fit)
    if(busy)CircularProgressIndicator(color=Accent)
   }
   Text(note,color=Color.LightGray,fontSize=11.sp,modifier=Modifier.padding(vertical=5.dp))
   Row(verticalAlignment=Alignment.CenterVertically){Text("全域",color=if(!local)Accent else Color.LightGray);Switch(checked=local,onCheckedChange={local=it});Text("局部遮罩",color=if(local)Accent else Color.LightGray)}
   Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("基本","色彩","細節").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)})}}
   if(tab=="色彩")Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){listOf("紅","黃","綠","青","藍","洋紅").forEachIndexed{i,n->FilterChip(selected=bandIndex==i,onClick={bandIndex=i},label={Text(n)})}}
   if(tab=="色彩"){
    Text("曲線調色",color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp,bottom=4.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
     listOf("Master","Red","Green","Blue").forEach{ch->FilterChip(selected=curveChannel==ch,onClick={curveChannel=ch},label={Text(ch)})}
    }
    val curvePoints=when(curveChannel){
     "Red"->settings.curves.red
     "Green"->settings.curves.green
     "Blue"->settings.curves.blue
     else->settings.curves.master
    }.ifEmpty{listOf(CurvePoint(0f,0f),CurvePoint(.5f,.5f),CurvePoint(1f,1f))}
    CurveEditor(curvePoints,when(curveChannel){"Red"->Color(0xFFFF6666);"Green"->Color(0xFF66DD88);"Blue"->Color(0xFF6699FF);else->Color.White}){pts->
     val curves=when(curveChannel){
      "Red"->settings.curves.copy(red=pts)
      "Green"->settings.curves.copy(green=pts)
      "Blue"->settings.curves.copy(blue=pts)
      else->settings.curves.copy(master=pts)
     }
     settings=settings.copy(curves=curves)
    }
    Text("拖曳控制點調整曲線；點擊曲線附近以新增控制點。曲線也會套用於局部遮罩模式。",color=Color.LightGray,fontSize=10.sp,modifier=Modifier.padding(bottom=6.dp))
   }
   LazyColumn(Modifier.weight(1.05f),contentPadding=PaddingValues(bottom=14.dp)){
    items(controls){c->Column(Modifier.fillMaxWidth().padding(vertical=2.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){
      Text(c.title,color=Color.White,fontSize=12.sp,modifier=Modifier.weight(1f))
      OutlinedTextField(
       value=if(c.value.isFinite()) "%.2f".format(Locale.US,c.value) else "0",
       onValueChange={raw->raw.toFloatOrNull()?.let{v->c.set(v.coerceIn(c.range.start,c.range.endInclusive))}},
       modifier=Modifier.width(104.dp).height(54.dp),
       singleLine=true,
       textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=12.sp),
       keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
       shape=RoundedCornerShape(10.dp)
      )
     }
     Slider(value=c.value.coerceIn(c.range.start,c.range.endInclusive),onValueChange=c.set,valueRange=c.range)
    }}
   }
  }
  if(showFullscreen){
   androidx.compose.ui.window.Dialog(onDismissRequest={showFullscreen=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit){detectTapGestures(onTap={showFullscreen=false})},contentAlignment=Alignment.Center){
     preview?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
     Text("點擊照片返回編輯",color=Color.White,modifier=Modifier.align(Alignment.BottomCenter).padding(20.dp))
    }
   }
  }
  if(showHistory){
   AlertDialog(onDismissRequest={showHistory=false},title={Text("編輯歷史紀錄")},
    text={
     if(history.isEmpty())Text("目前還沒有歷史紀錄")
     else LazyColumn(Modifier.heightIn(max=440.dp)){
      items(history.reversed()){entry->
       TextButton(onClick={
        showHistory=false;busy=true
        scope.launch{
         val uri=Uri.parse(entry.imageUri)
         val loaded=withContext(Dispatchers.IO){decode(context,uri)}
         val restored=runCatching{gson.fromJson(entry.settingsJson,ProfessionalSettings::class.java)}.getOrDefault(ProfessionalSettings())
         source=loaded;sourceUri=entry.imageUri;settings=restored
         note=if(loaded==null)"歷史照片無法重新讀取，請確認原照片仍存在" else "已載入歷史版本，可繼續編輯"
         busy=false
        }
       },modifier=Modifier.fillMaxWidth()){
        Column(Modifier.fillMaxWidth()){
         Text(entry.title,color=Color.White)
         Text(SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.getDefault()).format(Date(entry.time)),color=Color.LightGray,fontSize=11.sp)
        }
       }
      }
     }
    },
    confirmButton={TextButton(onClick={showHistory=false}){Text("關閉")}},
    dismissButton={TextButton(onClick={history=emptyList();prefs.edit().remove("entries").apply()}){Text("清除紀錄")}}
   )
  }
 }
}


@Composable private fun CurveEditor(points:List<CurvePoint>,lineColor:Color,onChange:(List<CurvePoint>)->Unit){
 val currentPoints by rememberUpdatedState(points)
 val change by rememberUpdatedState(onChange)
 Canvas(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF14171B),RoundedCornerShape(12.dp)).pointerInput(Unit){
  var working=emptyList<CurvePoint>()
  var selected=-1
  detectDragGestures(
   onDragStart={p->
    working=currentPoints.toList()
    val w=size.width.toFloat().coerceAtLeast(1f);val h=size.height.toFloat().coerceAtLeast(1f)
    val px=p.x/w;val py=1f-p.y/h
    val nearest=working.indices.minByOrNull{i->
     val dx=(working[i].x-px)*w;val dy=(working[i].y-py)*h
     dx*dx+dy*dy
    }
    selected=if(nearest!=null){
     val dx=(working[nearest].x-px)*w;val dy=(working[nearest].y-py)*h
     if(dx*dx+dy*dy<1600f)nearest else -1
    }else -1
    if(selected<0){
     val inserted=CurvePoint(px.coerceIn(.01f,.99f),py.coerceIn(0f,1f))
     working=(working+inserted).sortedBy{it.x}
     selected=working.indexOf(inserted)
     change(working)
    }
   },
   onDrag={event,_->
    event.consume()
    if(selected>=0&&selected<working.size){
     val w=size.width.toFloat().coerceAtLeast(1f);val h=size.height.toFloat().coerceAtLeast(1f)
     val old=working[selected]
     val minX=if(selected==0)0f else working[selected-1].x+.005f
     val maxX=if(selected==working.lastIndex)1f else working[selected+1].x-.005f
     val nx=if(selected==0)0f else if(selected==working.lastIndex)1f else (old.x+(event.position.x-event.previousPosition.x)/w).coerceIn(minX,maxX)
     val ny=(old.y-(event.position.y-event.previousPosition.y)/h).coerceIn(0f,1f)
     working=working.toMutableList().also{it[selected]=CurvePoint(nx,ny)}
     change(working)
    }
   }
  )
 }){
  val w=size.width;val h=size.height
  for(i in 0..4){
   val p=i/4f
   drawLine(Color(0xFF343A42),Offset(w*p,0f),Offset(w*p,h),1f)
   drawLine(Color(0xFF343A42),Offset(0f,h*p),Offset(w,h*p),1f)
  }
  val sorted=points.sortedBy{it.x}
  if(sorted.size>=2){
   val path=Path()
   sorted.forEachIndexed{i,p->
    val x=p.x.coerceIn(0f,1f)*w;val y=(1f-p.y.coerceIn(0f,1f))*h
    if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
   }
   drawPath(path,lineColor,style=Stroke(width=3f))
   sorted.forEach{p->drawCircle(lineColor,7f,Offset(p.x.coerceIn(0f,1f)*w,(1f-p.y.coerceIn(0f,1f))*h));drawCircle(Color(0xFF101216),3f,Offset(p.x.coerceIn(0f,1f)*w,(1f-p.y.coerceIn(0f,1f))*h))}
  }
 }
}

private fun copyToHistoryStorage(context:android.content.Context,uri:Uri):Uri?{
 return try{
  val dir=File(context.filesDir,"history_images").apply{mkdirs()}
  val target=File(dir,"photo_"+System.currentTimeMillis()+".img")
  context.contentResolver.openInputStream(uri)?.use{input->target.outputStream().use{output->input.copyTo(output)}}
  if(target.exists()&&target.length()>0)Uri.fromFile(target)else null
 }catch(_:Exception){null}
}
private fun decode(context:android.content.Context,uri:Uri):Bitmap?{
 return try{
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
  if(bounds.outWidth<=0||bounds.outHeight<=0)null else{
   var sample=1
   while(max(bounds.outWidth/sample,bounds.outHeight/sample)>4096)sample*=2
   val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888}
   context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
  }
 }catch(_:Exception){null}
}
private fun save(context:android.content.Context,b:Bitmap):Boolean{
 return try{
  val values=android.content.ContentValues().apply{
   put(MediaStore.Images.Media.DISPLAY_NAME,"RuiPhotoStudio_"+System.currentTimeMillis()+".jpg")
   put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
   put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Rui Photo Studio")
  }
  val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:return false
  context.contentResolver.openOutputStream(uri)?.use{b.compress(Bitmap.CompressFormat.JPEG,96,it)}?:false
 }catch(_:Exception){false}
}
