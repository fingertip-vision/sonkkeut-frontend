package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun BackendSettings(onAnnouncement: (String) -> Unit = {}) {
    val currentAnnouncement by rememberUpdatedState(onAnnouncement)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("backend", android.content.Context.MODE_PRIVATE) }
    var address by rememberSaveable { mutableStateOf(prefs.getString("base_url", "") ?: "") }
    var message by remember { mutableStateOf("서버 주소를 입력한 뒤 연결 상태를 확인해 주세요.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val client = remember { BackendHealthClient() }
    var job by remember { mutableStateOf<Job?>(null) }
    fun cancel() {
        job?.cancel(); job = null
        if (busy) message = "연결 확인을 취소했습니다. 다시 확인할 수 있습니다."
        busy = false
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); job?.cancel() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("서버 연결", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("서버 연결은 카메라 미리보기와 별도로 확인합니다.")
        OutlinedTextField(
            value = address,
            onValueChange = { cancel(); address = it; message = "주소가 변경되었습니다. 연결 상태를 다시 확인해 주세요." },
            label = { Text("서버 기본 주소") }, placeholder = { Text("https://서버주소") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            val normalized = try { BackendAddress.normalize(address, BuildConfig.DEBUG) } catch (e: IllegalArgumentException) {
                message = e.message ?: "서버 주소를 확인해 주세요."
                currentAnnouncement(message)
                return@Button
            }
            address = normalized
            prefs.edit().putString("base_url", normalized).apply()
            busy = true; message = "서버 연결을 확인하고 있습니다."
            job = scope.launch {
                val result = client.check(normalized)
                message = when (result) {
                    HealthResult.Ready -> "서버 응답 정상 · 서비스 준비 완료(UP). 주문 기능 연결 여부는 별도 확인이 필요합니다."
                    is HealthResult.NotReady -> "서버에 연결됐지만 서비스가 준비되지 않았습니다(${result.status}). 데이터베이스 등 서버 상태를 확인해 주세요."
                    is HealthResult.HttpError -> "서버가 HTTP ${result.code}로 응답했습니다. 주소와 접근 권한을 확인해 주세요."
                    HealthResult.InvalidResponse -> "서버 응답을 받았지만 상태 확인 형식이 올바르지 않습니다."
                    HealthResult.Timeout -> "서버 응답 시간이 초과됐습니다. 잠시 후 다시 확인해 주세요."
                    HealthResult.ConnectionFailed -> "서버에 연결할 수 없습니다. 주소, 네트워크와 인증서를 확인해 주세요."
                }
                busy = false; job = null
                currentAnnouncement(message)
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("주소 저장 및 연결 확인") }
        if (busy) TextButton(onClick = { cancel() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("연결 확인 취소") }
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text("상태 확인 성공은 주문 API 연결을 의미하지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }
}
