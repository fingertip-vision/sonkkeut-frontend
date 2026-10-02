package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun IntegratedSessionControls(output: GuidanceOutput,enabled: Boolean,onActive: (Boolean)->Unit) {
    val controller=remember { GuidanceSession() }
    var adapter by remember { mutableStateOf<SyntheticObservationAdapter?>(null) }
    var state by remember { mutableStateOf(GuidanceSessionState.PAUSED) }
    var message by remember { mutableStateOf("통합 시나리오를 선택해 주세요.") }
    var count by remember { mutableIntStateOf(0) }
    var trace by remember { mutableStateOf(listOf<String>()) }
    fun show(result: SessionOutput) {
        state=result.state; message=result.message
        trace=(trace+"$count ${result.state}: ${result.decision?.action ?: result.result?.verdict ?: "상태"} · ${result.result?.reason ?: result.message}").takeLast(18)
        if(!result.ignored) {
            if(result.state in listOf(GuidanceSessionState.RECOVERY,GuidanceSessionState.PAYMENT,GuidanceSessionState.VERIFIED,GuidanceSessionState.PAUSED)) output.stop()
            val text="합성 관측 계산입니다. ${result.message}"+if(result.decision?.action=="press") " 실제 키오스크는 누르지 마세요." else ""
            output.announce("integrated:${controller.token}:${result.message}",text,
                direction=result.decision?.action in listOf("move","near"),press=result.decision?.action=="press",
                vibration=if(result.decision?.action in listOf("move","near")) GuidancePhrases.vibration(result.decision?.action=="near") else null)
        }
    }
    LaunchedEffect(enabled) { if(!enabled) { controller.stop(); output.clearRepeat(); adapter=null; state=controller.state; message="중지되었습니다. 복귀 후 새 시나리오를 선택해 주세요."; onActive(false) } }
    DisposableEffect(Unit) { onDispose { controller.stop(); onActive(false) } }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("통합 계산·복구 · B",style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics { heading() })
        Text("합성 모서리·검출 박스·손 관절로 실제 계산 코어 전체를 실행합니다. 카메라 인식·OCR·음성 주문·실제 결제는 실행하지 않습니다.")
        Text("세션 상태: ${when(state) {
            GuidanceSessionState.PAUSED -> "중지"; GuidanceSessionState.GUIDING -> "손끝 안내"
            GuidanceSessionState.VERIFYING -> "결과 비교 대기"; GuidanceSessionState.VERIFIED -> "합성 관측 결과 확인"
            GuidanceSessionState.RECOVERY -> "복구 필요"; GuidanceSessionState.PAYMENT -> "결제 안내 종료"
        }}")
        Text(message,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        IntegratedScenario.entries.forEach { scenario ->
            OutlinedButton(onClick={
                val source=SyntheticObservationAdapter(scenario); adapter=source; output.resetAttempt(); count=0; trace=emptyList()
                onActive(true); show(controller.start(source.selection))
            },enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("통합: ${scenario.label}") }
        }
        Button(onClick={
            val observation=adapter?.next(controller.token)
            if(observation != null) { count++; show(controller.accept(observation)) } else { show(controller.stop()); adapter=null; onActive(false) }
        },enabled=enabled && adapter != null && state in listOf(GuidanceSessionState.GUIDING,GuidanceSessionState.VERIFYING),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("다음 통합 관측 ($count)") }
        OutlinedButton(onClick={
            val stale=SyntheticObservationAdapter(IntegratedScenario.NORMAL).next(controller.token-1)!!
            show(controller.accept(stale))
        },enabled=enabled && adapter != null,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("통합 이전 세션 거부 확인") }
        OutlinedButton(onClick={ show(controller.stop()); adapter=null; onActive(false) },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("통합 세션 중지") }
        if(trace.isNotEmpty()) Text("통합 계산 기록\n"+trace.joinToString("\n"))
    }
}
