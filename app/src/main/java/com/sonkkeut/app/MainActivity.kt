package com.sonkkeut.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AccessibleApp { NativeApp() } }
    }
}

@Composable
private fun NativeApp(model: NativeAppModel = viewModel()) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val preferences=LocalAccessibilityPreferences.current
    val output=remember { GuidanceOutput(context) }
    val outputSession=remember { intArrayOf(-1,-1) }
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    var pendingMic by remember { mutableStateOf(false) }
    var pendingSystem by remember { mutableStateOf(false) }
    var cameraStatus by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted=it; if(it) model.start() }
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it && pendingMic && model.page=="order") { if(pendingSystem) model.listenSystem() else model.listen() } else if(!it) model.announce("마이크 권한을 허용해 주세요."); pendingMic=false; pendingSystem=false }
    val conversationPermissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        if(cameraGranted && ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.startConversation()
        else model.announce("음성 주문에는 카메라와 마이크 권한이 필요합니다.")
    }
    SideEffect { output.configure(preferences.voice,preferences.vibration,listOf(.75f,1f,1.25f)[preferences.speed]) }
    DisposableEffect(output) { onDispose { output.close() } }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP) { model.stopForBackground(); output.suspendOutput() }
            if(event==Lifecycle.Event.ON_RESUME) { cameraGranted=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED; output.resumeOutput() }
        }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model.targetAttempt,model.announcementNumber) {
        if(model.conversation.active && !model.conversation.guidanceAllowed) return@LaunchedEffect
        if(outputSession[0]!=model.targetAttempt) { output.resetAttempt(); outputSession[0]=model.targetAttempt }
        if(outputSession[1]==model.announcementNumber) return@LaunchedEffect
        outputSession[1]=model.announcementNumber
        if(!model.recording && !model.speechBusy) output.resumeOutput()
        if(model.announcement.isNotBlank()) output.announce("native:${model.announcementNumber}",model.announcement,press=model.pressAnnouncement,
            vibration=if(model.pressAnnouncement) longArrayOf(0,70,60,70) else if(model.vibeHz>0) longArrayOf(0,25,(1000/model.vibeHz).toLong().coerceAtLeast(60),25) else null)
    }
    LaunchedEffect(model.dialogTurn?.generation) {
        val turn=model.dialogTurn
        if(turn!=null && model.conversation.active) output.dialog(turn.prompt) { okay -> model.dialogPromptCompleted(turn.generation,okay) }
        else if(model.conversation.active) output.stop()
    }
    BackHandler(model.page!="home") { pendingMic=false; model.open("home") }
    fun go(page: String) { pendingMic=false; output.stop(); model.open(page) }
    Surface(Modifier.fillMaxSize()) {
        when(model.page) {
            "home" -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("손끝길",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick={go("menu")},modifier=Modifier.heightIn(min=56.dp)) { Text("메뉴·설정") }
                }
                Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF080F1E))) {
                    if(cameraGranted && !model.paused && model.ready) key(retry,model.conversation.closeup) { NativeCamera(Modifier.fillMaxSize(),model,{cameraStatus=it}) }
                    else Column(Modifier.align(Alignment.Center).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(if(!cameraGranted) "카메라 권한을 허용해 주세요." else model.message,color=Color.White)
                        Button(onClick={if(cameraGranted) model.start() else cameraPermission.launch(Manifest.permission.CAMERA)},enabled=model.ready || !cameraGranted,modifier=Modifier.heightIn(min=56.dp)) { Text(if(cameraGranted) "손끝길 시작" else "카메라 권한 허용") }
                    }
                    if(cameraGranted && !model.paused && model.ready) {
                        CameraOverlay(model.frame,Modifier.fillMaxSize())
                        Text(if(model.found) "화면 인식 중" else "키오스크 전체 화면을 비춰 주세요",color=Color.White,modifier=Modifier.align(Alignment.TopCenter).background(Color(0xDD080F1E)).padding(8.dp))
                        if(cameraStatus.startsWith("카메라 오류")) TextButton(onClick={retry++},modifier=Modifier.align(Alignment.Center)) { Text("카메라 다시 연결") }
                    }
                }
                Text(model.message,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium,
                    modifier=Modifier.fillMaxWidth().semantics { liveRegion=LiveRegionMode.Polite; contentDescription=model.message })
                if(model.conversation.active) Text(if(model.recording) "듣고 있습니다 · ‘취소’로 종료" else if(model.speechBusy) "음성을 분석하고 있습니다" else if(model.conversation.closeup) model.conversation.status else "음성으로 단계별 주문 중",style=MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    NativeButton(if(model.paused) "안내 계속" else "안내 중지",Modifier.weight(1f)) { if(model.paused) { if(cameraGranted) model.start() else cameraPermission.launch(Manifest.permission.CAMERA) } else { model.pause(); output.suspendOutput() } }
                    NativeButton("재안내",Modifier.weight(1f),output.lastText!=null) { output.resumeOutput(); output.repeat() }
                }
            }
            "menu" -> NativePage("메뉴·설정",{go("home")}) {
                Text(model.connectionMessage,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                NativeButton("음성만으로 단계별 주문",enabled=model.ready && model.menu.isNotEmpty()) { conversationPermissions.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)) }
                NativeButton("음성·직접 입력 주문") { go("order") }
                NativeButton("화면 읽기·버튼 선택") { go("screen") }
                NativeButton("화면 다시 인식·안내 복구") { model.recoverScreen() }
                NativeButton("화면·카메라·음성 설정") { go("accessibility") }
                NativeButton("서버·매장 설정") { go("connection") }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("익명 사용 통계 전송",modifier=Modifier.weight(1f))
                    Switch(model.usageConsent,{model.changeUsageConsent(it)},modifier=Modifier.semantics { contentDescription="익명 사용 통계 전송" })
                }
                Text("동의한 경우 안내 결과만 전송합니다. 영상·음성·주문 문장은 전송하지 않습니다.",style=MaterialTheme.typography.bodyMedium)
                Text("Kotlin 앱 ${BuildConfig.VERSION_NAME} · 휴대폰에서 OCR·손끝·음성 추론",style=MaterialTheme.typography.bodyMedium)
            }
            "accessibility" -> AccessibilitySettings { go("menu") }
            "connection" -> NativePage("서버·매장 설정",{go("menu")}) {
                var base by rememberSaveable { mutableStateOf(model.server) }
                var code by rememberSaveable { mutableStateOf(model.storeCode) }
                Text("인터넷 연결 시 자동으로 메뉴를 연결합니다. 연결이 끊기면 저장된 매장 메뉴를 사용합니다.")
                OutlinedTextField(base,{base=it},label={Text("서버 HTTPS 주소")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(code,{code=it},label={Text("매장 코드")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                NativeButton("설정 저장·연결") { model.saveConnection(base,code) }
                Text(model.connectionMessage,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                model.menu.forEach { Text("${it.name} · ${it.price?.let { p -> "${p}원" } ?: "가격 미확인"}${if(it.soldOut) " · 품절" else ""}") }
            }
            "screen" -> NativePage("화면 읽기",{go("menu")}) {
                NativeButton("읽은 내용 음성 안내",enabled=model.screen!=null) { output.resumeOutput(); output.read(model.screen?.reading() ?: "아직 화면을 읽지 못했습니다.") }
                Text(if(model.found) "읽은 버튼을 선택하면 손끝으로 위치를 안내합니다." else "메인 화면에서 키오스크를 인식한 뒤 확인해 주세요.")
                model.screen?.elements?.forEach { element ->
                    NativeButton(if(element.readable) element.text.ifBlank { "글자 없음" } else "읽기 불확실",enabled=model.found && element.readable && element.kind in listOf("menu","button","tab","back")) { model.guide(element) }
                }
            }
            "order" -> NativePage("주문하기",{go("menu")}) {
                var text by rememberSaveable { mutableStateOf("") }
                Text("예: 따뜻한 아메리카노 두 잔하고 카페라떼 한 잔 포장해 주세요.")
                Text(model.speechStatus)
                if(!model.modelInstalled) NativeButton("자체 음성 모델 받기 · 약 485MB",enabled=!model.speechBusy) { model.downloadSpeech() }
                else NativeButton("자체 모델로 말하기",enabled=!model.speechBusy && model.menu.isNotEmpty()) {
                    output.suspendOutput()
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.listen()
                    else { pendingMic=true; pendingSystem=false; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                }
                if(model.recording) NativeButton("말하기 완료") { model.finishSpeech() }
                if(model.speechBusy) NativeButton("음성 작업 취소") { model.cancelSpeech(); output.resumeOutput() }
                NativeButton("기기 음성 인식으로 말하기",enabled=!model.speechBusy && model.systemSpeechAvailable && model.menu.isNotEmpty()) {
                    output.suspendOutput()
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.listenSystem()
                    else { pendingMic=true; pendingSystem=true; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                }
                if(!model.systemSpeechAvailable) Text("기기 온디바이스 음성 인식이 없습니다. 자체 모델이나 직접 입력을 이용해 주세요.")
                OutlinedTextField(text,{text=it},label={Text("주문 문장")},modifier=Modifier.fillMaxWidth(),minLines=2)
                NativeButton("입력한 주문 확인",enabled=!model.speechBusy) { model.submit(text) }
                if(model.rawSpeech.isNotBlank()) Text("들은 문장: ${model.rawSpeech}")
                model.rag?.let { result ->
                    Text("보정 문장: ${result.text}")
                    result.ambiguities.firstOrNull()?.let { ambiguity ->
                        Text("‘${ambiguity.original}’과 비슷한 메뉴를 선택해 주세요.")
                        ambiguity.candidates.forEach { candidate -> NativeButton(candidate.menu.name+if(candidate.menu.soldOut) " · 품절" else "",enabled=!candidate.menu.soldOut) { model.selectCandidate(candidate) } }
                    }
                }
                Text(model.message,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                if(model.order!=null && !model.speechBusy && model.rag?.ambiguities.isNullOrEmpty()) NativeButton("네, 이 주문으로 안내 시작") { output.resumeOutput(); model.confirm() }
            }
        }
    }
}

@Composable
internal fun NativeButton(label: String,modifier: Modifier=Modifier.fillMaxWidth(),enabled: Boolean=true,action: () -> Unit) {
    Button(onClick=action,enabled=enabled,modifier=modifier.heightIn(min=64.dp),
        colors=ButtonDefaults.buttonColors(disabledContainerColor=Color(0xFF33455F),disabledContentColor=Color(0xFFF5F8FF))) {
        Text(label,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable
private fun NativePage(title: String,back: () -> Unit,body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=back,modifier=Modifier.heightIn(min=56.dp)) { Text("‹ 뒤로") }
        Text(title,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.semantics { heading() }); body()
    }
}

@Composable
private fun CameraOverlay(frame: Map<String,Any?>,modifier: Modifier) {
    Canvas(modifier) {
        val dimensions=frame["frame_size"] as? List<*> ?: return@Canvas
        val width=(dimensions.getOrNull(0) as? Number)?.toFloat() ?: return@Canvas
        val height=(dimensions.getOrNull(1) as? Number)?.toFloat() ?: return@Canvas
        if(width<=0 || height<=0) return@Canvas
        val scale=minOf(size.width/width,size.height/height); val dx=(size.width-width*scale)/2; val dy=(size.height-height*scale)/2
        fun point(x: Float,y: Float)=Offset(dx+x*scale,dy+y*scale)
        (frame["corners"] as? List<*>)?.let { values -> if(values.size==8) for(i in 0..3) {
            val next=(i+1)%4
            val x=(values[i*2] as? Number)?.toFloat() ?: continue; val y=(values[i*2+1] as? Number)?.toFloat() ?: continue
            val nx=(values[next*2] as? Number)?.toFloat() ?: continue; val ny=(values[next*2+1] as? Number)?.toFloat() ?: continue
            drawLine(Color(0xFFFFDF38),point(x,y),point(nx,ny),3.dp.toPx())
        } }
        (frame["target_image_box"] as? List<*>)?.let { box -> if(box.size==4) {
            val b=box.map { (it as? Number)?.toFloat() ?: return@let }
            drawRect(Color(0xFFFFDF38),point(b[0],b[1]),androidx.compose.ui.geometry.Size((b[2]-b[0])*scale,(b[3]-b[1])*scale),style=Stroke(5.dp.toPx()))
        } }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun NativeCamera(modifier: Modifier,model: NativeAppModel,status: (String) -> Unit) {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current
    val latestStatus by rememberUpdatedState(status)
    val previewView=remember { PreviewView(context).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE; scaleType=PreviewView.ScaleType.FIT_CENTER; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO } }
    AndroidView(factory={previewView},modifier=modifier.semantics { contentDescription="키오스크 촬영용 카메라" })
    DisposableEffect(owner,previewView) {
        var disposed=false; var provider: ProcessCameraProvider?=null
        val resolution=ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(if(model.conversation.closeup) Size(1920,1440) else Size(1280,960),ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val preview=Preview.Builder().setResolutionSelector(resolution).build()
        val analysis=ImageAnalysis.Builder().setResolutionSelector(resolution).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val executor=Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(executor) { image -> try { image.image?.let { model.process(it,image.imageInfo.rotationDegrees) } } finally { image.close() } }
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({ if(!disposed) try {
            provider=future.get(); preview.setSurfaceProvider(previewView.surfaceProvider)
            provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
            latestStatus("카메라 연결됨")
        } catch(e: Exception) { provider?.unbind(preview,analysis); latestStatus("카메라 오류: ${e.message}") } },ContextCompat.getMainExecutor(context))
        onDispose { disposed=true; analysis.clearAnalyzer(); provider?.unbind(preview,analysis); executor.shutdown() }
    }
}
