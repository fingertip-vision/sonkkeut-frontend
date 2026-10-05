package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
internal fun NativeHomeToolbar(running: Boolean, onSettings: () -> Unit, onEnd: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("homeToolbar"),verticalAlignment=Alignment.CenterVertically) {
        if(!running || LocalDensity.current.fontScale<=1.6f) {
            Text("손끝길",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f).semantics { heading() })
        } else Spacer(Modifier.weight(1f))
        TextButton(onClick=onSettings,modifier=Modifier.heightIn(min=56.dp)) { Text("설정") }
        if(running) Button(onClick=onEnd,modifier=Modifier.heightIn(min=56.dp),
            colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFE45C),contentColor=Color(0xFF111318))) { Text("종료") }
    }
}

@Composable
internal fun NativeHomeActions(running: Boolean, ready: Boolean, repeatEnabled: Boolean, onStart: () -> Unit, onRepeat: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("homeActions"),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(!running) NativeButton("손끝길 시작",Modifier.weight(1f),ready,onStart)
        else NativeButton("음성 재인식",Modifier.weight(1f).semantics { stateDescription="준비 중" },enabled=false) {}
        NativeButton("재안내",Modifier.weight(1f),repeatEnabled,onRepeat)
    }
}
