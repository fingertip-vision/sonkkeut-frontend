package com.sonkkeut.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
            AccessibleApp { CameraScreen() }
        }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val output = remember { GuidanceOutput(context) }
    val preferences = LocalAccessibilityPreferences.current
    SideEffect { output.configure(preferences.voice, preferences.vibration, listOf(0.75f, 1f, 1.25f)[preferences.speed]) }
    DisposableEffect(output) { onDispose { output.close() } }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var cameraRequested by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var cameraStatus by remember { mutableStateOf("카메라를 준비하고 있습니다") }
    var retry by remember { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(showSettings) { showSettings = false }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    cameraGranted = granted(Manifest.permission.CAMERA)
                    foreground = true
                }
                Lifecycle.Event.ON_STOP -> { foreground = false; paused = true; output.suspendOutput() }
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
    LaunchedEffect(foreground, paused, status, output.ready) {
        if (!foreground || paused) {
            output.suspendOutput()
        } else {
            output.resumeOutput()
            val screenReader = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java).isTouchExplorationEnabled
            // TalkBack already announces live-region state; avoid competing automatic speech.
            if (!screenReader) output.announce("state:$status", "손끝길 안내입니다. $status")
        }
    }
    Surface(Modifier.fillMaxSize()) {
        if (showSettings) {
            AccessibilitySettings(onBack = { showSettings = false })
        } else {
            MainCameraLayout(
                status = status, active = active, paused = paused, cameraGranted = cameraGranted,
                repeatEnabled = foreground && !paused && output.lastText != null,
                onSettings = { showSettings = true },
                onRepeat = { output.repeat() }, onStop = { output.suspendOutput(); paused = true },
                onStart = { paused = false },
                onPermission = { cameraRequested = true; cameraPermission.launch(Manifest.permission.CAMERA) },
                onRetry = { retry++ },
                preview = { modifier -> key(retry) { CameraInput(modifier, onStatus = { cameraStatus = it }, onFrame = {}) } },
            )
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
