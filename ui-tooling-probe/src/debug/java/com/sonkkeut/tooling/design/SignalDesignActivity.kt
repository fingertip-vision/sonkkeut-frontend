package com.sonkkeut.tooling.design

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.sonkkeut.tooling.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

private val Ink = Color(0xFF111717)
private val Panel = Color(0xFF1C2624)
private val Paper = Color(0xFFF4F2E9)
private val Muted = Color(0xFFBAC5BF)
private val Signal = Color(0xFFF3DC83)
private val Mint = Color(0xFFB7DCC4)
private val Edge = Color(0xFF36433F)
private val Warn = Color(0xFFF1BDA1)
private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

enum class Scene(val title: String, val headline: String, val detail: String, val cue: String) {
    HOME("시작", "", "", ""),
    READY("촬영 준비", "화면 전체를\n비춰 주세요", "키오스크의 네 모서리가 보이도록\n휴대폰을 천천히 맞춰 주세요.", "화면을 정면으로 비춰 주세요"),
    ANALYZING("화면 분석 중", "메뉴를\n살펴보고 있어요", "휴대폰을 잠시 그대로 유지해 주세요.\n준비되면 소리로 알려드릴게요.", "잠시만 그대로 유지해 주세요"),
    GUIDANCE("손끝 안내", "오른쪽으로\n조금만 이동해요", "카페라테 버튼을 향해\n손끝을 천천히 움직여 주세요.", "손끝을 오른쪽으로 이동하세요"),
    RECOVERY("안내 중지", "괜찮아요,\n다시 이어가요", "화면을 다시 비춘 뒤\n카메라를 다시 시작해 주세요.", "현재 안내가 중지되어 있어요")
}

class SignalDesignActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val requested = intent.getStringExtra("scene") ?: intent.data?.getQueryParameter("scene") ?: "home"
        val initial = Scene.entries.firstOrNull { it.name.equals(requested, true) } ?: Scene.HOME
        val scale = intent.getFloatExtra("fontScale", 0f)
        setContent { SignalStudio(initial, scale, intent.getBooleanExtra("reduceMotion", false)) }
    }
}

@Composable
fun SignalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Signal, onPrimary = Ink, background = Ink,
            surface = Panel, onSurface = Paper, onBackground = Paper, outline = Edge),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, lineHeight = 27.sp),
            bodyMedium = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, lineHeight = 24.sp),
            titleLarge = TextStyle(fontFamily = Pretendard, fontSize = 23.sp, fontWeight = FontWeight.SemiBold),
            labelLarge = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        ), content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalStudio(initial: Scene = Scene.HOME, initialFontScale: Float = 0f, initialReducedMotion: Boolean = false) {
    var scene by rememberSaveable { mutableStateOf(initial) }
    var inspector by rememberSaveable { mutableStateOf(false) }
    var order by rememberSaveable { mutableStateOf(false) }
    var large by rememberSaveable { mutableStateOf(initialFontScale >= 2f) }
    var reduceMotion by rememberSaveable { mutableStateOf(initialReducedMotion) }
    val density = LocalDensity.current
    val sampleScale = if (large) maxOf(2f, initialFontScale, density.fontScale) else if (initialFontScale > 0f) initialFontScale else density.fontScale
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val currentScene by rememberUpdatedState(scene)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && currentScene != Scene.HOME) scene = Scene.RECOVERY
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(scene != Scene.HOME && !inspector && !order) { scene = Scene.HOME }
    CompositionLocalProvider(LocalDensity provides Density(density.density, sampleScale)) {
        SignalTheme {
            Scaffold(containerColor = Ink, contentColor = Paper,
                snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { inset ->
                Column(Modifier.fillMaxSize().padding(inset).safeDrawingPadding().semantics { isTraversalGroup = true }) {
                    BrandHeader(scene != Scene.HOME, onSettings = { inspector = true }, onExit = { scene = Scene.HOME })
                    if (scene == Scene.HOME) {
                        Welcome(Modifier.weight(1f), reduceMotion)
                        WelcomeAction { scene = Scene.READY }
                    } else {
                        Guidance(scene, reduceMotion, Modifier.weight(1f)) { order = true }
                        GuidanceActions(scene, onPrimary = {
                            if (scene == Scene.RECOVERY) scene = Scene.READY
                            else {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                view.announceForAccessibility(scene.cue)
                                scope.launch { snackbar.showSnackbar(scene.cue, duration = SnackbarDuration.Short) }
                            }
                        })
                    }
                }
            }
            if (inspector) ModalBottomSheet(onDismissRequest = { inspector = false }, containerColor = Panel,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Type("시안 둘러보기", 27, FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Type("촬영과 주문은 예시입니다.\n아래에서 화면 상태를 바꿔 볼 수 있어요.", 16, color = Muted)
                    Scene.entries.forEach { candidate ->
                        Surface(onClick = { scene = candidate; inspector = false }, color = if (scene == candidate) Signal else Ink,
                            contentColor = if (scene == candidate) Ink else Paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Type(candidate.title, 20, FontWeight.SemiBold, color = if (scene == candidate) Ink else Paper)
                                Glyph(if (candidate == scene) "check" else "right", 22.dp, if (scene == candidate) Ink else Muted)
                            }
                        }
                    }
                    HorizontalDivider(color = Edge)
                    SettingSwitch("큰 글씨 · 2배", large) { large = it }
                    SettingSwitch("움직임 줄이기", reduceMotion) { reduceMotion = it }
                    TextButton(onClick = { inspector = false }, modifier = Modifier.fillMaxWidth()) { Type("닫기", 20, color = Signal) }
                }
            }
            if (order) ModalBottomSheet(onDismissRequest = { order = false }, containerColor = Panel,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(Modifier.padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Type("주문 확인", 28, FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Type("카페라테 1잔", 28, FontWeight.SemiBold)
                    Type("따뜻하게 · 포장\n시안에 사용하는 주문 예시입니다.", 19, color = Muted)
                    PrimaryButton("닫기", onClick = { order = false })
                }
            }
        }
    }
}

@Composable
private fun Type(text: String, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Paper,
                 modifier: Modifier = Modifier, lineHeight: Int = (size * 1.35f).toInt()) {
    Text(text, modifier, color = color, fontFamily = Pretendard, fontSize = size.sp, fontWeight = weight,
        lineHeight = lineHeight.sp, letterSpacing = if (size >= 28) (-0.9).sp else 0.sp,
        style = TextStyle(lineBreak = LineBreak.Heading))
}

@Composable
private fun BrandHeader(camera: Boolean, onSettings: () -> Unit, onExit: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.6f
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).heightIn(min = 52.dp)
        .semantics { traversalIndex = 0f }, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!large) BrandSymbol(Modifier.size(28.dp))
            Type("손끝길", if (large) 19 else 23, FontWeight.Bold)
        }
        TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            if (!large) { Glyph("settings", 18.dp, Muted); Spacer(Modifier.width(6.dp)) }
            Type("설정", 16, color = Paper)
        }
        if (camera) {
            Spacer(Modifier.width(4.dp))
            Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = Ink),
                shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Type("종료", 17, FontWeight.SemiBold, Ink)
            }
        }
    }
}

@Composable
private fun Welcome(modifier: Modifier, reduced: Boolean) {
    val large = LocalDensity.current.fontScale >= 1.6f
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val reveal by animateFloatAsState(if (appeared) 1f else 0f, tween(if (reduced) 0 else 650), label = "pathEntrance")
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)
        .semantics { traversalIndex = 1f }) {
        Spacer(Modifier.height(27.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(Signal, CircleShape))
            Type("나의 속도로, 나의 주문을", 16, FontWeight.SemiBold, Signal)
        }
        Spacer(Modifier.height(14.dp))
        Type("손끝에서,\n막힘없이.", if (large) 36 else 49, FontWeight.Bold, lineHeight = if (large) 44 else 58,
            modifier = Modifier.semantics { heading() })
        Box(Modifier.fillMaxWidth().height(if (large) 160.dp else 260.dp).clearAndSetSemantics {}) {
            Image(painterResource(R.drawable.signal_path), null, Modifier.fillMaxSize().graphicsLayer {
                alpha = reveal; scaleX = .96f + .04f * reveal; scaleY = scaleX
            }, contentScale = ContentScale.Fit)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Ink, .12f to Color.Transparent, .86f to Color.Transparent, 1f to Ink)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ink, .23f to Color.Transparent, .77f to Color.Transparent, 1f to Ink)))
        }
        Type("화면을 비추고, 메뉴를 말하세요.\n손끝이 갈 곳을 함께 찾아드려요.", 19, color = Muted, lineHeight = 28)
        Spacer(Modifier.height(25.dp))
        HorizontalDivider(color = Edge)
        if (large) Column(Modifier.padding(vertical=19.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf("01  비추기", "02  말하기", "03  따라가기").forEach { Type(it,17,FontWeight.SemiBold) }
        } else Row(Modifier.fillMaxWidth().padding(vertical = 19.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("비추기", "말하기", "따라가기").forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Type("0${i + 1}", 12, FontWeight.SemiBold, Signal, modifier = Modifier.clearAndSetSemantics {})
                    Type(label, 17, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WelcomeAction(onStart: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp)
        .semantics { traversalIndex = 2f }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph("check", 17.dp, Mint)
            Type("시작할 준비가 됐어요", 16, color = Muted)
        }
        PrimaryButton("손끝길 시작", onClick = onStart)
        Type("결제는 키오스크에서 직접 진행해요", 13, color = Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun Guidance(scene: Scene, reduced: Boolean, modifier: Modifier, onOrder: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.6f
    val extraLarge = LocalDensity.current.fontScale > 2.1f
    val tint by animateColorAsState(if (scene == Scene.RECOVERY) Warn else if (scene == Scene.ANALYZING) Mint else Signal,
        tween(if (reduced) 0 else 320), label = "signalState")
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .semantics { traversalIndex = 1f }, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).background(tint, CircleShape))
                Type(scene.title, 15, FontWeight.SemiBold, tint)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val headline = if (extraLarge && scene == Scene.GUIDANCE) "오른쪽으로\n조금만\n이동해요" else scene.headline
                Type(headline, if (extraLarge) 24 else 34, FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() }, lineHeight = if (extraLarge) 31 else 42)
                if (!large) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = tint, shape = RoundedCornerShape(22.dp), modifier = Modifier.size(68.dp)) {
                        Box(contentAlignment = Alignment.Center) { Glyph(when(scene) {
                            Scene.GUIDANCE -> "right"
                            Scene.RECOVERY -> "refresh"
                            Scene.ANALYZING -> "scan"
                            else -> "frame"
                        }, 35.dp, Ink) }
                    }
                }
            }
        }
        CameraScene(scene, tint, if (large) 190.dp else 326.dp, reduced)
        Type(scene.detail, 18, color = Muted, lineHeight = 26)
        if (scene == Scene.GUIDANCE) {
            Surface(onClick = onOrder, color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                if (extraLarge) Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Type("카페라테 1잔",19,FontWeight.SemiBold)
                    Type("따뜻하게 · 포장",14,color=Muted)
                    Type("주문 보기",16,FontWeight.SemiBold,Signal,Modifier.align(Alignment.End))
                } else Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("cup", 26.dp, Signal)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Type("카페라테 1잔", 19, FontWeight.SemiBold)
                        Type("따뜻하게 · 포장", 14, color = Muted)
                    }
                    Type("주문 보기", 14, color = Signal)
                    Spacer(Modifier.width(6.dp))
                    Glyph("right", 16.dp, Signal)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(if (scene == Scene.RECOVERY) "pause" else "sound", 18.dp, tint)
                Type(if (scene == Scene.RECOVERY) "다시 시작하기 전까지 안내를 멈춰요" else "음성 안내로 다음 행동을 알려드려요", 14, color = Muted)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CameraScene(scene: Scene, tint: Color, height: Dp, reduced: Boolean) {
    val focus by animateFloatAsState(if (scene == Scene.GUIDANCE) 1f else 0f,
        tween(if (reduced) 0 else 420), label="targetArrival")
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(26.dp)).background(Panel)
        .border(1.dp, Edge, RoundedCornerShape(26.dp)).clearAndSetSemantics {
            contentDescription = "카메라 예시 이미지. 실제 촬영 또는 인식 결과가 아닙니다."
        }) {
        Image(painterResource(R.drawable.kiosk_sample), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            alpha = if (scene == Scene.RECOVERY) .32f else .82f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = .38f), Color.Transparent, Ink.copy(alpha = .55f)))))
        Row(Modifier.align(Alignment.TopStart).padding(16.dp).background(Ink.copy(alpha = .93f), RoundedCornerShape(30.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(5.dp).background(tint, CircleShape))
            Type("카메라 예시", 12, FontWeight.SemiBold)
        }
        if (scene != Scene.RECOVERY) Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val margin = 30.dp.toPx()
            val length = 23.dp.toPx()
            val stroke = 2.5.dp.toPx()
            val left = margin; val top = margin + 36.dp.toPx(); val right = size.width - margin; val bottom = size.height - margin
            if (scene != Scene.GUIDANCE) {
                listOf(Offset(left,top) to Offset(1f,1f),Offset(right,top) to Offset(-1f,1f),
                    Offset(left,bottom) to Offset(1f,-1f),Offset(right,bottom) to Offset(-1f,-1f)).forEach { (o,d) ->
                    drawLine(tint,o,o+Offset(length*d.x,0f),stroke,StrokeCap.Round)
                    drawLine(tint,o,o+Offset(0f,length*d.y),stroke,StrokeCap.Round)
                }
                if (scene == Scene.ANALYZING) {
                    drawLine(tint.copy(alpha=.25f),Offset(left,size.height*.51f),Offset(right,size.height*.51f),16.dp.toPx())
                    drawLine(tint,Offset(left,size.height*.51f),Offset(right,size.height*.51f),1.5.dp.toPx())
                }
            } else {
                val target = Offset(size.width*.67f,size.height*.64f)
                val origin = Offset(size.width*.42f,size.height*.81f)
                val route = Path().apply { moveTo(origin.x,origin.y); cubicTo(origin.x+30.dp.toPx(),origin.y,target.x-42.dp.toPx(),target.y,target.x,target.y) }
                drawPath(route,Ink.copy(alpha=.7f),style=Stroke(8.dp.toPx(),cap=StrokeCap.Round))
                drawPath(route,Signal.copy(alpha=focus),style=Stroke(3.dp.toPx(),cap=StrokeCap.Round,pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),7.dp.toPx()))))
                drawCircle(Signal.copy(alpha=.14f*focus),44.dp.toPx(),target)
                drawCircle(Signal.copy(alpha=focus),29.dp.toPx()*(.85f+.15f*focus),target,style=Stroke(2.dp.toPx()))
                drawCircle(Ink,9.dp.toPx(),target)
                drawCircle(Signal,5.dp.toPx(),target)
                drawCircle(Paper,6.dp.toPx(),origin)
            }
        }
        if (scene == Scene.RECOVERY) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = Ink.copy(alpha=.9f), shape = CircleShape, modifier = Modifier.size(60.dp)) {
                Box(contentAlignment=Alignment.Center) { Glyph("pause", 26.dp, Warn) }
            }
            Type("안내가 멈춰 있어요", 20, FontWeight.SemiBold)
        }
        if (scene == Scene.GUIDANCE) Surface(color=Ink.copy(alpha=.95f), shape=RoundedCornerShape(50),
            modifier=Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Row(Modifier.padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Glyph("target",16.dp,Signal); Type("목표 · 카페라테",14,FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GuidanceActions(scene: Scene, onPrimary: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.6f
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal=24.dp).padding(top=12.dp,bottom=16.dp)
        .semantics { traversalIndex = 2f }, verticalArrangement=Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color=Edge)
        if (large || scene == Scene.RECOVERY) {
            PrimaryButton(if(scene == Scene.RECOVERY) "카메라 다시 시작" else "재안내", onClick=onPrimary,
                icon=if(scene == Scene.RECOVERY) "frame" else "sound")
            if (scene != Scene.RECOVERY) Type("음성 재인식 · 준비 중",15,color=Muted,modifier=Modifier.align(Alignment.CenterHorizontally)
                .semantics { disabled() })
        } else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={}, enabled=false, modifier=Modifier.weight(1f).heightIn(min=76.dp), shape=RoundedCornerShape(22.dp),
                border=BorderStroke(1.dp,Edge),colors=ButtonDefaults.outlinedButtonColors(disabledContentColor=Muted)) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Type("음성 재인식",16,color=Muted);Type("준비 중",12,color=Muted)
                }
            }
            Button(onClick=onPrimary,modifier=Modifier.weight(1f).heightIn(min=76.dp),shape=RoundedCornerShape(22.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Signal,contentColor=Ink),contentPadding=PaddingValues(12.dp)) {
                Glyph("sound",21.dp,Ink);Spacer(Modifier.width(8.dp));Type("재안내",23,FontWeight.Bold,Ink)
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, icon: String = "right") {
    Button(onClick=onClick, modifier=Modifier.fillMaxWidth().heightIn(min=80.dp),shape=RoundedCornerShape(24.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Signal,contentColor=Ink),contentPadding=PaddingValues(horizontal=22.dp,vertical=18.dp)) {
        Type(label,24,FontWeight.Bold,Ink,Modifier.weight(1f),lineHeight=31)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(38.dp).background(Ink,CircleShape),contentAlignment=Alignment.Center) { Glyph(icon,22.dp,Signal) }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked,role=Role.Switch,onValueChange=onChange).padding(vertical=4.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Type(label,19,modifier=Modifier.weight(1f))
        Switch(checked,onCheckedChange=null)
    }
}

@Composable
private fun BrandSymbol(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val p=Path().apply { moveTo(size.width*.13f,size.height*.82f); cubicTo(size.width*.13f,size.height*.5f,size.width*.7f,size.height*.65f,size.width*.7f,size.height*.32f) }
        drawPath(p,Signal,style=Stroke(size.width*.15f,cap=StrokeCap.Round))
        drawCircle(Signal,size.width*.11f,Offset(size.width*.73f,size.height*.1f))
    }
}

@Composable
private fun Glyph(kind: String, sizeDp: Dp, tint: Color) {
    Canvas(Modifier.size(sizeDp).clearAndSetSemantics {}) {
        val w=size.width; val h=size.height; val s=w*.075f
        fun line(x:Float,y:Float,a:Float,b:Float)=drawLine(tint,Offset(w*x,h*y),Offset(w*a,h*b),s,StrokeCap.Round)
        when(kind) {
            "right" -> { line(.18f,.5f,.82f,.5f);line(.56f,.24f,.82f,.5f);line(.56f,.76f,.82f,.5f) }
            "check" -> { line(.18f,.53f,.4f,.75f);line(.4f,.75f,.84f,.25f) }
            "pause" -> { line(.36f,.23f,.36f,.77f);line(.64f,.23f,.64f,.77f) }
            "sound" -> {
                val p=Path().apply { moveTo(w*.15f,h*.4f);lineTo(w*.32f,h*.4f);lineTo(w*.52f,h*.22f);lineTo(w*.52f,h*.78f);lineTo(w*.32f,h*.6f);lineTo(w*.15f,h*.6f);close() }
                drawPath(p,tint,style=Stroke(s,join=StrokeJoin.Round))
                drawArc(tint,-55f,110f,false,Offset(w*.39f,h*.18f),Size(w*.51f,h*.64f),style=Stroke(s,cap=StrokeCap.Round))
            }
            "settings" -> { line(.18f,.3f,.82f,.3f);line(.18f,.7f,.82f,.7f);drawCircle(Ink,w*.1f,Offset(w*.38f,h*.3f));drawCircle(tint,w*.1f,Offset(w*.38f,h*.3f),style=Stroke(s));drawCircle(Ink,w*.1f,Offset(w*.64f,h*.7f));drawCircle(tint,w*.1f,Offset(w*.64f,h*.7f),style=Stroke(s)) }
            "refresh" -> { drawArc(tint,-70f,290f,false,Offset(w*.2f,h*.2f),Size(w*.6f,h*.6f),style=Stroke(s,cap=StrokeCap.Round));line(.62f,.13f,.65f,.36f);line(.65f,.36f,.86f,.28f) }
            "target" -> { drawCircle(tint,w*.35f,center,style=Stroke(s));drawCircle(tint,w*.09f,center) }
            "cup" -> { drawRoundRect(tint,Offset(w*.13f,h*.24f),Size(w*.57f,h*.52f),androidx.compose.ui.geometry.CornerRadius(w*.08f),style=Stroke(s));drawArc(tint,-90f,180f,false,Offset(w*.57f,h*.31f),Size(w*.33f,h*.3f),style=Stroke(s));line(.15f,.9f,.77f,.9f) }
            else -> { line(.15f,.37f,.15f,.15f);line(.15f,.15f,.37f,.15f);line(.63f,.15f,.85f,.15f);line(.85f,.15f,.85f,.37f);line(.15f,.63f,.15f,.85f);line(.15f,.85f,.37f,.85f);line(.63f,.85f,.85f,.85f);line(.85f,.85f,.85f,.63f);if(kind=="scan") line(.12f,.5f,.88f,.5f) }
        }
    }
}

@Preview(name="Signal · 시작",widthDp=393,heightDp=873,showBackground=true)
@Composable private fun HomePreview() { SignalStudio() }
@Preview(name="Signal · 손끝 안내",widthDp=393,heightDp=873,showBackground=true)
@Composable private fun CameraPreview() { SignalStudio(Scene.GUIDANCE) }
@Preview(name="Signal · 큰 글씨",widthDp=393,heightDp=873,fontScale=2f,showBackground=true)
@Composable private fun LargePreview() { SignalStudio(Scene.GUIDANCE,2f,true) }
