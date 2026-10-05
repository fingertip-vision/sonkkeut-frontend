package com.sonkkeut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class UiToolingProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { UiToolingProbe() } }
    }
}

@Composable
fun UiToolingProbe() {
    var active by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (active) Color(0xFF153E35) else Color(0xFF17253B),
        animationSpec = tween(400), label = "probeStateColor"
    )
    Surface(color = background, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(28.dp).semantics { isTraversalGroup = true }) {
            Text("UI 도구 검증", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading(); traversalIndex = 0f })
            Spacer(Modifier.height(24.dp))
            AnimatedContent(targetState = active, label = "probeStatus", modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                traversalIndex = 1f
            }) { running ->
                Text(if (running) "전환 완료" else "준비 완료", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(24.dp))
            Text("Compose · Material 3 · Animation\n제품 기능과 연결되지 않은 검증 화면",
                style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = { active = !active }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .semantics { traversalIndex = 2f }) {
                Text(if (active) "준비 상태로 복귀" else "상태 전환 확인", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Preview(name = "Tooling / default", showBackground = true, locale = "ko", widthDp = 360, heightDp = 800)
@Preview(name = "Tooling / large text", showBackground = true, locale = "ko", fontScale = 2f, widthDp = 360, heightDp = 800)
@Composable
private fun UiToolingProbePreview() { MaterialTheme { UiToolingProbe() } }
