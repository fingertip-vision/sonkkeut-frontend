package com.sonkkeut.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF164C40), background = Color(0xFFF7FAF8),
                surface = Color(0xFFF7FAF8), onSurface = Color(0xFF17201D),
            )) { CameraScreen() }
        }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var micGranted by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var cameraRequested by rememberSaveable { mutableStateOf(false) }
    var micRequested by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var cameraStatus by remember { mutableStateOf("카메라를 준비하고 있습니다") }
    var frameStatus by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    cameraGranted = granted(Manifest.permission.CAMERA)
                    micGranted = granted(Manifest.permission.RECORD_AUDIO)
                    foreground = true
                }
                Lifecycle.Event.ON_STOP -> { foreground = false; paused = true }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val active = cameraGranted && foreground && !paused
    val status = when {
        !cameraGranted && cameraRequested -> "카메라 권한이 없어 촬영을 시작할 수 없습니다. 다시 허용하거나 앱 설정에서 변경해 주세요."
        !cameraGranted -> "화면을 촬영하려면 카메라 권한이 필요합니다."
        paused -> "일시 정지 상태입니다. 카메라 입력을 멈췄습니다."
        !foreground -> "앱이 화면에 표시될 때 카메라를 사용할 수 있습니다."
        else -> cameraStatus
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("손끝길", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text("카메라 준비", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (active) {
                key(retry) {
                    CameraInput(
                        Modifier.fillMaxWidth().height(240.dp),
                        onStatus = { cameraStatus = it }, onFrame = { frameStatus = it },
                    )
                }
                if (frameStatus.isNotEmpty()) Text(frameStatus, style = MaterialTheme.typography.bodySmall)
            }
            if (!cameraGranted) {
                Button(onClick = { cameraRequested = true; cameraPermission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (cameraRequested) "카메라 권한 다시 요청" else "카메라 권한 허용")
                }
            } else {
                Button(onClick = { paused = !paused }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (paused) "카메라 다시 시작" else "일시 정지")
                }
                if (active && cameraStatus.startsWith("카메라를 사용할 수 없습니다")) {
                    OutlinedButton(onClick = { retry++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("카메라 다시 연결") }
                }
            }
            HorizontalDivider()
            Text(if (micGranted) "마이크 권한이 허용되었습니다. 음성 주문은 아직 준비 중입니다." else if (micRequested) "마이크 권한이 거부되었습니다. 카메라는 계속 사용할 수 있습니다." else "음성 주문을 위한 마이크 권한을 미리 준비할 수 있습니다.")
            if (!micGranted) {
                OutlinedButton(onClick = { micRequested = true; micPermission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("마이크 권한 허용") }
            }
            if ((!cameraGranted && cameraRequested) || (!micGranted && micRequested)) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("앱 권한 설정 열기") }
            }
            HorizontalDivider()
            BackendSettings()
            Text("현재는 카메라 미리보기 단계입니다. 화면 인식과 주문 안내는 아직 제공하지 않습니다.")
            Text("영상은 저장하거나 서버에 보내지 않습니다. 마이크 녹음도 시작하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CameraInput(modifier: Modifier, onStatus: (String) -> Unit, onFrame: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestStatus by rememberUpdatedState(onStatus)
    val latestFrame by rememberUpdatedState(onFrame)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier.semantics { contentDescription = "키오스크 촬영용 카메라 미리보기" })
    DisposableEffect(lifecycleOwner, previewView) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var lastUpdate = 0L
        latestStatus("카메라를 준비하고 있습니다")
        analysis.setAnalyzer(executor) { image ->
            try {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 1000) {
                    lastUpdate = now
                    val description = "카메라 입력 정상 · ${image.width} × ${image.height} · 회전 ${image.imageInfo.rotationDegrees}°"
                    mainExecutor.execute { if (!disposed) latestFrame(description) }
                }
            } finally { image.close() }
        }
        val streamObserver = androidx.lifecycle.Observer<PreviewView.StreamState> { stream ->
            if (!disposed && stream == PreviewView.StreamState.STREAMING) latestStatus("카메라 미리보기가 실행 중입니다. 휴대폰을 화면 쪽으로 들어 주세요.")
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) {
                try {
                    provider = future.get()
                    val cameraProvider = requireNotNull(provider)
                    if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) error("No rear camera")
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    provider?.unbind(preview, analysis)
                    latestStatus("카메라를 사용할 수 없습니다. 권한과 기기의 카메라 상태를 확인해 주세요.")
                }
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            previewView.previewStreamState.removeObserver(streamObserver)
            executor.shutdown()
        }
    }
}
