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
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import android.hardware.camera2.CameraCharacteristics
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
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
    var cameraStatus by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted=it; if(it && !model.detailMode) model.start() }
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it && pendingMic && model.page=="home" && model.running && !model.paused) { model.listen() } else if(!it) model.announce("마이크 권한을 허용해 주세요."); pendingMic=false }
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
        if(outputSession[0]!=model.targetAttempt) { output.resetAttempt(); outputSession[0]=model.targetAttempt }
        if(outputSession[1]==model.announcementNumber) return@LaunchedEffect
        outputSession[1]=model.announcementNumber
        if(!model.recording && !model.speechBusy) output.resumeOutput()
        if(model.announcement.isNotBlank()) output.announce("native:${model.announcementNumber}",model.announcement,press=model.pressAnnouncement,
            vibration=if(model.pressAnnouncement) longArrayOf(0,70,60,70) else if(model.vibeHz>0) longArrayOf(0,25,(1000/model.vibeHz).toLong().coerceAtLeast(60),25) else null)
    }
    LaunchedEffect(model.speechRequested,model.speechBusy) {
        if(model.speechRequested && !model.speechBusy && model.running && !model.paused && model.page=="home") {
            model.consumeSpeechRequest()
            val needsModel=!model.modelInstalled
            if(needsModel) { model.toggleTextOrder(); model.announce("음성 모델을 준비하거나 주문을 직접 입력해 주세요.") }
            else {
                output.suspendOutput()
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { model.listen() }
                else { pendingMic=true; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
            }
        }
    }
    fun openSettings() { pendingMic=false; output.stop(); model.openEnvironmentSettings() }
    fun closeSettings() { pendingMic=false; output.stop(); model.closeEnvironmentSettings(cameraGranted) }
    BackHandler(model.page!="home" || model.textOrderOpen || model.running || model.detailMode) {
        pendingMic=false
        if(model.page=="accessibility") closeSettings()
        else if(model.detailMode) model.closeDetailRead()
        else if(model.textOrderOpen) model.toggleTextOrder()
        else if(model.page!="home") model.open("home")
        else openSettings()
    }
    val showOrderConfirmation=model.order!=null && !model.paused && !model.detailMode && !model.textOrderOpen && !model.recording && !model.speechBusy
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    LaunchedEffect(showOrderConfirmation) { if(showOrderConfirmation) { focus.clearFocus(); keyboard?.hide() } }
    Surface(Modifier.fillMaxSize()) {
        when(model.page) {
            "home" -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                NativeHomeToolbar(model.running,{openSettings()},{pendingMic=false; output.stop(); model.end()})

                if(model.running && !model.detailMode) {
                    Text(if(model.order!=null && model.flowState!="S4" && model.message!=model.order!!.confirmation()) model.message else model.visualGuidance(),maxLines=if(LocalDensity.current.fontScale>1.6f) 1 else 2,overflow=TextOverflow.Ellipsis,style=if(LocalDensity.current.fontScale>1.6f) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,modifier=Modifier.fillMaxWidth().semantics { liveRegion=LiveRegionMode.Polite })
                }
                Box(Modifier.fillMaxWidth().weight(if(showOrderConfirmation) { if(LocalDensity.current.fontScale>1.6f) .08f else .15f } else 1f).testTag("mainCamera").background(Color(0xFF080F1E))) {
                    if(cameraGranted && model.cameraActive) key(retry) { NativeCamera(Modifier.fillMaxSize(),model,{cameraStatus=it; if(it.startsWith("카메라 오류")) { model.pause(); output.suspendOutput() }}) }
                    else Column(Modifier.align(Alignment.Center).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(if(!cameraGranted) "카메라 권한을 허용해 주세요." else model.message,color=Color.White)
                        Button(onClick={if(cameraGranted) model.start() else cameraPermission.launch(Manifest.permission.CAMERA)},enabled=model.ready || !cameraGranted,modifier=Modifier.heightIn(min=56.dp)) { Text(if(!cameraGranted) "카메라 권한 허용" else if(model.running) "카메라 다시 시작" else "손끝길 시작") }
                        if(cameraStatus.startsWith("카메라 오류")) {
                            Text(cameraStatus,color=Color.White)
                            NativeButton("카메라 다시 연결") { retry++; cameraStatus=""; model.start() }
                        }
                    }
                    if(cameraGranted && model.cameraActive) {
                        CameraOverlay(model.frame,Modifier.fillMaxSize(),preferences.lowVision && model.flowState in listOf("S4","S5"))
                        if(!showOrderConfirmation) Text(if(model.detailMode) "글자 가까이 비추기 · 손끝 안내 중지" else if(model.found) "화면 인식 중" else "키오스크 전체 화면을 비춰 주세요",color=Color.White,modifier=Modifier.align(Alignment.TopCenter).background(Color(0xDD080F1E)).padding(8.dp))
                        if(cameraStatus.startsWith("카메라 오류")) TextButton(onClick={retry++},modifier=Modifier.align(Alignment.Center)) { Text("카메라 다시 연결") }
                    }
                }
                if(model.detailMode) {
                    Column(Modifier.fillMaxWidth().heightIn(max=280.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(model.detailStatus,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                        NativeButton("상세 읽기 시작",enabled=cameraGranted && !model.detailBusy) { output.stop(); model.requestDetailRead() }
                        if(model.detailBusy) NativeButton("상세 읽기 취소") { model.cancelDetailRead() }
                        NativeButton("읽은 내용 음성 안내",enabled=model.detailLines.isNotEmpty() && !model.detailBusy) { output.resumeOutput(); output.read(model.detailLines.joinToString(". ") { it.text }) }
                        model.detailLines.forEach { Text(it.text) }
                        NativeButton("상세 읽기 닫기") { model.closeDetailRead() }
                        Text("읽은 글자는 확인용입니다. 버튼 위치 안내에 사용하지 않습니다.")
                    }
                }
                if(model.running && !model.paused && model.flowState!="S6") {
                    if(model.textOrderOpen || model.recording || model.speechBusy || model.flowState=="S3") {
                        Column(Modifier.fillMaxWidth().heightIn(max=280.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {

                        Text("예: 따뜻한 아메리카노 두 잔하고 카페라떼 한 잔 포장해 주세요.")
                        Text(model.speechStatus)
                        if(!model.modelInstalled) NativeButton("자체 음성 모델 받기 · 약 485MB",enabled=!model.speechBusy) { model.downloadSpeech() }
                        else NativeButton("자체 모델로 말하기",enabled=!model.speechBusy && model.menu.isNotEmpty()) {
                            output.suspendOutput()
                            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.listen()
                            else { pendingMic=true; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                        }
                        if(model.recording) NativeButton("말하기 완료") { model.finishSpeech() }
                        if(model.speechBusy) NativeButton("음성 작업 취소") { model.cancelSpeech(); output.resumeOutput() }
                        if(model.textOrderOpen) {
                            OutlinedTextField(model.orderDraft,model::editOrderDraft,label={Text("주문 문장")},modifier=Modifier.fillMaxWidth(),minLines=2)
                            NativeButton("입력한 주문 확인",enabled=!model.speechBusy) { model.submit(model.orderDraft) }
                        } else NativeButton("직접 입력 주문") { model.toggleTextOrder() }
                        model.rag?.let { result ->
                            result.ambiguities.firstOrNull()?.let { ambiguity ->
                                Text("‘${ambiguity.original}’과 비슷한 메뉴를 선택해 주세요.")
                                ambiguity.candidates.forEach { candidate -> NativeButton(candidate.menu.name+if(candidate.menu.soldOut) " · 품절" else "",enabled=!candidate.menu.soldOut) { model.selectCandidate(candidate) } }
                            }
                        }
                        if(model.recommendations.isNotEmpty()) {
                            Text("등록된 메뉴 후보입니다. 재료·알레르기 정보는 보장하지 않습니다. 선택 후 수량·옵션을 다시 입력해 주세요.")
                            model.recommendations.forEach { candidate ->
                                NativeButton("후보 선택 · ${candidate.menu.name}",enabled=!model.speechBusy) { model.selectRecommendation(candidate) }
                            }
                        }
                        Text(model.message,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                        }
                    } else if(model.order==null) NativeButton("직접 입력 주문") { model.toggleTextOrder() }
                }
                if(showOrderConfirmation) {
                    NativeOrderConfirmation(model.order!!,model.progressText(),Modifier.fillMaxWidth().weight(if(LocalDensity.current.fontScale>1.6f) .92f else .85f))
                } else {
                    Text(model.message,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium,
                        modifier=Modifier.fillMaxWidth().semantics { liveRegion=LiveRegionMode.Polite; contentDescription=model.message })
                }
                NativeHomeActions(model.running,model.ready,output.lastText!=null,
                    { if(cameraGranted) model.start() else cameraPermission.launch(Manifest.permission.CAMERA) },
                    { output.resumeOutput(); output.repeat() })
            }
            "accessibility" -> AccessibilitySettings { closeSettings() }

        }
    }
}

@Composable
internal fun NativeButton(label: String,modifier: Modifier=Modifier.fillMaxWidth(),enabled: Boolean=true,action: () -> Unit) {
    Button(onClick=action,enabled=enabled,modifier=modifier.heightIn(min=64.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
        colors=ButtonDefaults.buttonColors(disabledContainerColor=Color(0xFF33455F),disabledContentColor=Color(0xFFF5F8FF))) {
        Text(label,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable
private fun CameraOverlay(frame: Map<String,Any?>,modifier: Modifier,showTarget: Boolean) {
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
        (frame["target_image_box"] as? List<*>)?.takeIf { showTarget }?.let { box -> if(box.size==4) {
            val b=box.map { (it as? Number)?.toFloat() ?: return@let }
            drawRect(Color(0xFFFFDF38),point(b[0],b[1]),androidx.compose.ui.geometry.Size((b[2]-b[0])*scale,(b[3]-b[1])*scale),style=Stroke(5.dp.toPx()))
        } }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class, ExperimentalCamera2Interop::class)
@Composable
private fun NativeCamera(modifier: Modifier,model: NativeAppModel,status: (String) -> Unit) {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current
    val wide=LocalAccessibilityPreferences.current.wideCamera
    val latestStatus by rememberUpdatedState(status)
    val previewView=remember { PreviewView(context).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE; scaleType=PreviewView.ScaleType.FIT_CENTER; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO } }
    AndroidView(factory={previewView},modifier=modifier.semantics { contentDescription="키오스크 촬영용 카메라" })
    DisposableEffect(owner,previewView,wide) {
        var disposed=false; var provider: ProcessCameraProvider?=null
        val resolution=ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(1280,960),ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val preview=Preview.Builder().setResolutionSelector(resolution).build()
        val analysis=ImageAnalysis.Builder().setResolutionSelector(resolution).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val executor=Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(executor) { image -> try { image.image?.let { model.process(it,image.imageInfo.rotationDegrees) } } finally { image.close() } }
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({ if(!disposed) try {
            provider=future.get(); preview.setSurfaceProvider(previewView.surfaceProvider)
            val cameraProvider=provider!!
            val back=CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraProvider.availableCameraInfos)
            val widest=if(wide) back.maxByOrNull { info -> runCatching {
                val metadata=Camera2CameraInfo.from(info)
                val sensor=metadata.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val focal=metadata.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
                if(sensor!=null && focal!=null && focal>0) sensor.width/focal else 0f
            }.getOrDefault(0f) } else null
            val selector=if(widest!=null) CameraSelector.Builder().addCameraFilter { infos -> infos.filter { it==widest } }.build() else CameraSelector.DEFAULT_BACK_CAMERA
            val camera=try { cameraProvider.bindToLifecycle(owner,selector,preview,analysis) }
                catch(error: Exception) { cameraProvider.unbind(preview,analysis); if(!wide) throw error; cameraProvider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis) }
            if(wide) camera.cameraInfo.zoomState.value?.minZoomRatio?.let { camera.cameraControl.setZoomRatio(it) }
            latestStatus("카메라 연결됨")
        } catch(e: Exception) { provider?.unbind(preview,analysis); latestStatus("카메라 오류: ${e.message}") } },ContextCompat.getMainExecutor(context))
        onDispose { disposed=true; analysis.clearAnalyzer(); provider?.unbind(preview,analysis); executor.shutdown() }
    }
}
