package com.sonkkeut.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val SignalInk = Color(0xFF111717)
internal val SignalGold = Color(0xFFF3DC83)
internal val SignalPaper = Color(0xFFF4F2E9)
internal val SignalFont = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

/** Presentation only: readiness and actions belong to NativeAppModel / the permission launcher. */
internal data class WelcomeState(
    val ready: Boolean,
    val message: String,
    val cameraGranted: Boolean,
    val permissionDenied: Boolean = false,
    val permissionSettingsRequired: Boolean = false
) {
    val canRetry: Boolean get() = !ready && message.startsWith("AI 준비 실패")
    val status: String get() = when {
        !ready && message.startsWith("AI 준비 실패") -> message
        !ready -> "시작에 필요한 AI를 준비하고 있어요"
        !cameraGranted && permissionDenied -> if (permissionSettingsRequired)
            "휴대폰 설정에서 카메라 권한을 허용해 주세요"
            else "카메라 권한이 필요해요. 다시 허용해 주세요"
        !cameraGranted -> "시작하려면 카메라 권한을 허용해 주세요"
        else -> "시작할 준비가 됐어요"
    }
    val action: String get() = when {
        canRetry -> "AI 다시 준비"
        permissionSettingsRequired && !cameraGranted -> "카메라 권한 설정"
        permissionDenied && !cameraGranted -> "카메라 권한 허용"
        else -> "손끝길 시작"
    }
}

@Composable
internal fun SignalWelcome(
    state: WelcomeState,
    light: Boolean,
    repeatEnabled: Boolean,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onRepeat: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val background = if (light) SignalPaper else SignalInk
    val foreground = if (light) SignalInk else SignalPaper
    val muted = if (light) Color(0xFF465750) else Color(0xFFBAC5BF)
    val accent = if (light) Color(0xFF5D4A06) else SignalGold
    val edge = if (light) Color(0xFFB8C5BC) else Color(0xFF36433F)
    val large = LocalDensity.current.fontScale >= 1.6f
    val actionEnabled=state.ready || state.canRetry && onRetry!=null
    Surface(color = background, contentColor = foreground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("signalWelcome")
            .semantics { isTraversalGroup = true }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
                .heightIn(min = 52.dp).semantics { traversalIndex = 0f }, verticalAlignment = Alignment.CenterVertically) {
                if (!large) {
                    Canvas(Modifier.size(28.dp).clearAndSetSemantics {}) {
                        val path = Path().apply {
                            moveTo(size.width * .12f, size.height * .9f)
                            cubicTo(0f, size.height * .45f, size.width * .9f, size.height * .65f, size.width * .7f, size.height * .3f)
                        }
                        drawPath(path, accent, style = Stroke(size.width * .16f, cap = StrokeCap.Round))
                        drawCircle(accent, size.width * .1f, Offset(size.width * .7f, size.height * .1f))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                SignalText("손끝길", if (large) 19 else 23, foreground, Modifier.weight(1f), bold = true)
                TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 56.dp)) {
                    SignalText("설정", 16, foreground)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp).semantics { traversalIndex = 1f }) {
                Spacer(Modifier.height(if (large) 12.dp else 27.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                    SignalText("나의 속도로, 나의 주문을", 16, accent, bold = true)
                }
                Spacer(Modifier.height(14.dp))
                SignalText("손끝에서,\n막힘없이.", if (large) 28 else 49, foreground,
                    Modifier.semantics { heading() }, bold = true, lineHeight = if (large) 36 else 58)
                // This illustration is branding only, never a representation of live AI output.
                Surface(color = SignalInk, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(if (large) 140.dp else 240.dp)
                        .clearAndSetSemantics {}) {
                    Box {
                        Image(painterResource(R.drawable.signal_path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        if (!light) {
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to SignalInk, .12f to Color.Transparent, .86f to Color.Transparent, 1f to SignalInk)))
                            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to SignalInk, .23f to Color.Transparent, .77f to Color.Transparent, 1f to SignalInk)))
                        }
                    }
                }
                SignalText("화면을 비추고, 메뉴를 말하세요.\n손끝이 갈 곳을 함께 찾아드려요.", 19, muted, lineHeight = 28)
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = edge)
                if (large) Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("01  비추기", "02  말하기", "03  따라가기").forEach { SignalText(it, 17, foreground, bold = true) }
                } else Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("비추기", "말하기", "따라가기").forEachIndexed { index, label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SignalText("0${index + 1}", 12, accent, Modifier.clearAndSetSemantics {})
                            SignalText(label, 16, foreground, bold = true)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 10.dp)
                .semantics { traversalIndex = 2f }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalText(state.status, if (large) 14 else 16, muted,
                    Modifier.testTag("welcomeStatus").heightIn(max = if (large) 130.dp else 96.dp)
                        .verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite })
                Button(onClick = { if(state.canRetry) onRetry?.invoke() else onStart() }, enabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("welcomeStart"),
                    shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalGold, contentColor = SignalInk,
                        disabledContainerColor = if (light) Color(0xFFD8DFD9) else Color(0xFF36433F),
                        disabledContentColor = muted)) {
                    SignalText(state.action, if (large) 20 else 24, if (actionEnabled) SignalInk else muted,
                        Modifier.weight(1f), bold = true)
                    if (!large) SignalText("→", 30, if (actionEnabled) SignalInk else muted, Modifier.clearAndSetSemantics {})
                }
                if (large) Column(Modifier.fillMaxWidth()) {
                    SignalText("결제는 키오스크에서\n직접 진행해요", 12, muted, lineHeight = 17)
                    TextButton(onClick = onRepeat, enabled = repeatEnabled,
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                        SignalText("재안내", 14, if (repeatEnabled) accent else muted)
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SignalText("결제는 키오스크에서\n직접 진행해요", 12, muted, Modifier.weight(1f), lineHeight = 17)
                    TextButton(onClick = onRepeat, enabled = repeatEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                        SignalText("재안내", 14, if (repeatEnabled) accent else muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalText(text: String, size: Int, color: Color, modifier: Modifier = Modifier,
                       bold: Boolean = false, lineHeight: Int = (size * 1.35f).toInt()) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = lineHeight.sp,
        fontFamily = SignalFont, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = if (size >= 28) (-.9).sp else 0.sp, style = TextStyle(lineBreak = LineBreak.Heading))
}
