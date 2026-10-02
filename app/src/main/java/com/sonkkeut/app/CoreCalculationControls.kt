package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun CoreCalculationControls(output: GuidanceOutput, enabled: Boolean) {
    var core by remember { mutableStateOf(VisualGuidanceCore()) }
    var session by remember { mutableStateOf(SyntheticReplay()) }
    var index by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("계산 입력을 시작해 주세요.") }
    var trace by remember { mutableStateOf(listOf<String>()) }
    val target = remember { CoreTarget("fixture-menu", "button", CoreBox(.48,.48,.52,.52), .95) }
    LaunchedEffect(enabled) { if (!enabled) { running = false; session.pause(); text = "계산을 중지했습니다. 새 입력으로 시작해 주세요." } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("실제 방향 계산 · 합성 좌표 입력 · T08", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("Python에서 이식한 계산을 휴대폰에서 실행합니다. 좌표는 미리 만든 값이며 카메라 인식·손끝 추적·실제 주문 결과 확인은 실행하지 않습니다.")
        Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        OutlinedButton(onClick = {
            output.resetAttempt(); core = VisualGuidanceCore(); session = SyntheticReplay().apply { start() }
            index = 0; running = true; trace = emptyList(); text = "합성 좌표 계산을 시작했습니다."
        }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("좌표 계산 새로 시작") }
        Button(onClick = {
            val point = when (index) { 0 -> CorePoint(.15,.7); 1 -> CorePoint(.46,.5); else -> CorePoint(.5,.5) }
            // Fixed monotonic capture times: manual wall-clock delays do not imply continuous dwell.
            val now = index / 10.0
            val decision = core.update(point, target, now, point, .95, 1, "fixture-hand")
            val feedback = session.accept(ReplayEvent(session.session, 1, target.id, index, decision.asReplayDecision()))
            if (feedback != null) { output.stop(); output.announce("core:$index", feedback.text, feedback.direction, feedback.press, feedback.near?.let { GuidancePhrases.vibration(it) }) }
            text = "입력 ${index + 1}: t=$now 초 · (${point.x}, ${point.y})\n계산: ${decision.action} / ${decision.reason}\n거리: ${decision.distance} · 유지: ${decision.dwellSeconds} 초\n" +
                if (decision.reason == "awaiting_result") "결과 대기 중입니다. 실제 화면 비교는 미구현입니다." else "합성 좌표에 대한 실제 계산 결과입니다."
            trace = trace + "t=$now ${decision.action} ${decision.reason}"
            index++
        }, enabled = enabled && running && index < 8, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("다음 좌표 계산 ($index/8)") }
        OutlinedButton(onClick = { running = false; session.pause(); output.stop(); text = "계산과 출력을 중지했습니다." }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("좌표 계산 중지") }
        if (trace.isNotEmpty()) Text("계산 기록\n" + trace.joinToString("\n"))
    }
}
