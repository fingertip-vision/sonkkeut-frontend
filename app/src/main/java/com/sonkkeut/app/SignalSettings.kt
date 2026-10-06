package com.sonkkeut.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AccessibilitySettings(onBack: () -> Unit) {
    val preferences=LocalAccessibilityPreferences.current
    SignalSessionTheme(preferences.light) { SignalSettings(preferences,onBack) }
}

@Composable
internal fun SignalSettings(preferences: AccessibilityPreferences, onBack: () -> Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(color=colors.background,contentColor=colors.onBackground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("signalSettings").semantics { isTraversalGroup=true; paneTitle="환경 설정" }) {
            TextButton(onClick=onBack,modifier=Modifier.padding(horizontal=16.dp).heightIn(min=60.dp).semantics { traversalIndex=0f }) {
                Text("‹  메인 화면으로",style=MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp)
                .semantics { traversalIndex=1f },verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("나에게 맞는 손끝길",color=colors.primary,style=MaterialTheme.typography.labelLarge)
                    Text("환경 설정",fontFamily=SignalFont,fontWeight=FontWeight.Bold,fontSize=32.sp,lineHeight=40.sp,modifier=Modifier.semantics { heading() })
                    Text("편한 크기와 안내 방식으로.\n변경한 설정은 바로 저장돼요.",color=colors.onSurfaceVariant)
                }
                SettingsSection("01","화면 설정") {
                    SettingsChoices("글자 크기",listOf("기본","크게","더 크게"),preferences.textSize,preferences::text)
                    Text("휴대폰의 글자 크기 설정도 반영해요",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    SettingsChoices("화면 색상",listOf("어둡게","밝게"),if(preferences.light) 1 else 0) { preferences.theme(it==1) }
                    SettingsToggle("목표 버튼 크게 강조",preferences.lowVision,preferences::target)
                }
                SettingsSection("02","카메라 설정") {
                    SettingsToggle("가까이서 화면 전체 담기",preferences.wideCamera,preferences::camera)
                    Text("지원 기기는 넓은 후면 렌즈·최소 줌을 사용해요. 지원하지 않거나 연결되지 않으면 기본 렌즈로 전환해요.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                }
                SettingsSection("03","음성 설정") {
                    SettingsToggle("음성 안내",preferences.voice,preferences::speech)
                    SettingsToggle("진동 안내",preferences.vibration,preferences::haptic)
                    SettingsChoices("안내 속도",listOf("느리게","보통","빠르게"),preferences.speed,preferences::rate)
                    Text("자동 음성을 꺼도 ‘재안내’는 사용할 수 있어요.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                }
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("안내로 돌아갈 때",style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics { heading() })
                    Text("설정을 열면 안내가 잠시 멈춰요. 안내 중이었다면 메인 화면에서 새 화면을 확인하고 이어갑니다. 다른 앱을 다녀왔다면 카메라를 다시 시작해 주세요.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    Text("손끝길 ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(number,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge,modifier=Modifier.clearAndSetSemantics {})
            Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics { heading() })
        }
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
        }
    }
}

@Composable
private fun SettingsChoices(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics { heading() })
        @Composable fun Choice(index: Int, modifier: Modifier) {
            val active=index==selected
            Box(modifier.heightIn(min=56.dp).clip(RoundedCornerShape(16.dp))
                .background(if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                .selectable(active,role=Role.RadioButton,onClick={onSelect(index)})
                .semantics { contentDescription="$title, ${options[index]}" }.padding(horizontal=8.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                Text(options[index],style=MaterialTheme.typography.labelLarge,
                    color=if(active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier=Modifier.clearAndSetSemantics {})
            }
        }
        if(LocalDensity.current.fontScale<=1.4f) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            options.indices.forEach { Choice(it,Modifier.weight(1f)) }
        } else options.indices.forEach { Choice(it,Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).toggleable(value=checked,role=Role.Switch,onValueChange=onChange)
        .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
        Switch(checked,onCheckedChange=null,modifier=Modifier.clearAndSetSemantics {})
    }
}
