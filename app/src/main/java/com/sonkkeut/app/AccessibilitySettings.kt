package com.sonkkeut.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight

internal class AccessibilityPreferences(context: Context) {
    private val storage = context.getSharedPreferences("accessibility", Context.MODE_PRIVATE)
    var textSize by mutableIntStateOf(storage.getInt("textSize", 1).coerceIn(0, 2)); private set
    var light by mutableStateOf(storage.getBoolean("light", false)); private set
    var voice by mutableStateOf(storage.getBoolean("voice", true)); private set
    var vibration by mutableStateOf(storage.getBoolean("vibration", true)); private set
    var speed by mutableIntStateOf(storage.getInt("speed", 1).coerceIn(0, 2)); private set
    fun text(value: Int) { textSize = value; storage.edit().putInt("textSize", value).apply() }
    fun theme(value: Boolean) { light = value; storage.edit().putBoolean("light", value).apply() }
    fun speech(value: Boolean) { voice = value; storage.edit().putBoolean("voice", value).apply() }
    fun haptic(value: Boolean) { vibration = value; storage.edit().putBoolean("vibration", value).apply() }
    fun rate(value: Int) { speed = value; storage.edit().putInt("speed", value).apply() }
}
internal val LocalAccessibilityPreferences = staticCompositionLocalOf<AccessibilityPreferences> { error("Missing accessibility preferences") }

@Composable
internal fun AccessibleApp(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { AccessibilityPreferences(context) }
    val density = LocalDensity.current
    SideEffect {
        (context as? android.app.Activity)?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = preferences.light
                isAppearanceLightNavigationBars = preferences.light
            }
        }
    }
    val colors = if (preferences.light) lightColorScheme(
        primary = Color(0xFFFFDF38), onPrimary = Color(0xFF111827),
        background = Color(0xFFF7F8FA), surface = Color(0xFFF7F8FA), onSurface = Color(0xFF171B26),
    ) else darkColorScheme(primary = Color(0xFFFFE45C), onPrimary = Color(0xFF111318),
        background = Color(0xFF171B26), surface = Color(0xFF171B26), onSurface = Color(0xFFF7F8FA))
    val typography = remember(preferences.textSize) {
        val base = Typography()
        if (preferences.textSize != 2) base else base.copy(
            displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
            displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
            displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
            bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Bold),
            bodyMedium = base.bodyMedium.copy(fontWeight = FontWeight.Bold),
            bodySmall = base.bodySmall.copy(fontWeight = FontWeight.Bold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
            labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold),
            labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
    CompositionLocalProvider(LocalAccessibilityPreferences provides preferences,
        LocalDensity provides Density(density.density, density.fontScale * listOf(1f, 1.15f, 1.3f)[preferences.textSize])) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

@Composable
internal fun AccessibilitySettings(onBack: () -> Unit) {
    val preferences = LocalAccessibilityPreferences.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text("‹  메인 화면으로") }
        Text("접근성 설정", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        HorizontalDivider()
        SettingChoices("글자 크기", listOf("기본", "크게", "더 크게"), preferences.textSize, preferences::text)
        SettingChoices("화면 색상", listOf("어둡게", "밝게"), if (preferences.light) 1 else 0) { preferences.theme(it == 1) }
        SettingToggle("음성 안내", preferences.voice, preferences::speech)
        SettingToggle("진동 안내", preferences.vibration, preferences::haptic)
        SettingChoices("안내 속도", listOf("느리게", "보통", "빠르게"), preferences.speed, preferences::rate)
        Text("휴대폰의 글자 크기 설정도 반영해요", style = MaterialTheme.typography.bodyLarge)
        Text("자동 음성을 꺼도 ‘다시 듣기’는 사용할 수 있어요.", style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        Text("카메라 화면은 전체 프레임을 표시합니다. 메인에는 카메라·안내 중지·재안내를 두고, 주문과 설정은 별도 화면에서 이용합니다.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingChoices(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        // Vertical choices avoid clipping with system font enlargement and narrow displays.
        val compact = LocalDensity.current.fontScale <= 1.4f
        @Composable fun Choice(index: Int, label: String, modifier: Modifier) {

            OutlinedButton(onClick = { onSelect(index) }, modifier = modifier.heightIn(min = 56.dp)
                .semantics { this.selected = index == selected },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (index == selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (index == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) {
                Text(if (index == selected) "$label  ✓" else label, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, label -> Choice(index, label, Modifier.weight(1f)) }
        } else options.forEachIndexed { index, label -> Choice(index, label, Modifier.fillMaxWidth()) }
        HorizontalDivider()
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = change, modifier = Modifier.semantics { contentDescription = label })
        }
        HorizontalDivider()
    }
}
