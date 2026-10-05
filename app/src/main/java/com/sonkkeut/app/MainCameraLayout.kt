package com.sonkkeut.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MainCameraLayout(
    status: String, active: Boolean, paused: Boolean, cameraGranted: Boolean,
    repeatEnabled: Boolean, onSettings: () -> Unit,
    onRepeat: () -> Unit, onStop: () -> Unit, onStart: () -> Unit,
    onPermission: () -> Unit, onRetry: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
    val cameraHeight = (maxHeight - 320.dp).coerceAtLeast(180.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            SettingsButton(onSettings)
        }
        Box(Modifier.fillMaxWidth().height(cameraHeight).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D1118))) {
            if (active) preview(Modifier.fillMaxSize().padding(bottom = 56.dp))
            if (!active || status.startsWith("카메라를 사용할 수 없습니다")) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(status, color = Color(0xFFF7F8FA), textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    when {
                        !cameraGranted -> Button(onClick = onPermission) { Text("카메라 권한 허용") }
                        paused -> Button(onClick = onStart) { Text("카메라 다시 시작") }
                        else -> Button(onClick = onRetry) { Text("카메라 다시 연결") }
                    }
                }
            } else {
                Text(if (status.startsWith("카메라 미리보기")) "카메라 켜짐" else status, color = Color(0xFFF7F8FA), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0xFF0D1118)).padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        Text(if (active) "화면 쪽으로 비춰 주세요" else "카메라 안내", fontSize = 24.sp,
            fontWeight = if (LocalAccessibilityPreferences.current.textSize == 2) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp))
        Text("화면 인식과 주문 안내는 준비 중이에요", fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MainAction("다시 듣기", Modifier.weight(1f), repeatEnabled, onRepeat)
            MainAction("중지", Modifier.weight(1f), !paused, onStop)
        }
    }
    }
}

@Composable
private fun MainAction(label: String, modifier: Modifier, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 108.dp),
        shape = RoundedCornerShape(16.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), disabledContentColor = MaterialTheme.colorScheme.onPrimary)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionSymbol(label == "중지")
        Text(label, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = if (LocalAccessibilityPreferences.current.textSize == 2) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsSymbol() {
    val foreground = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(28.dp)) {
        val color = foreground
        val width = 2.dp.toPx()
        run {
            drawCircle(color, size.minDimension * 0.31f, style = Stroke(width))
            drawCircle(color, size.minDimension * 0.1f, style = Stroke(width))
            for (i in 0..7) {
                val angle = i * Math.PI / 4
                val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                drawLine(color, center + direction * (size.minDimension * 0.32f), center + direction * (size.minDimension * 0.46f), width, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SettingsButton(action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsSymbol()
            Text("설정", fontSize = 14.sp)
        }
    }
}

@Composable
private fun ActionSymbol(stop: Boolean) {
    val foreground = LocalContentColor.current
    Canvas(Modifier.size(28.dp)) {
        val color = foreground
        val stroke = 2.dp.toPx()
        if (stop) drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(stroke))
        else {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .12f, size.height * .35f)
                lineTo(size.width * .35f, size.height * .35f)
                lineTo(size.width * .58f, size.height * .12f)
                lineTo(size.width * .58f, size.height * .88f)
                lineTo(size.width * .35f, size.height * .65f)
                lineTo(size.width * .12f, size.height * .65f)
                close()
            }
            drawPath(path, color, style = Stroke(stroke))
            drawArc(color, -55f, 110f, false, Offset(size.width * .4f, size.height * .18f),
                androidx.compose.ui.geometry.Size(size.width * .5f, size.height * .64f), style = Stroke(stroke))
        }
    }
}
