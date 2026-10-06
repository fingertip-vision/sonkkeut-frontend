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

internal class AccessibilityPreferences(context: Context, storageName: String = "accessibility") {
    private val storage = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    var textSize by mutableIntStateOf(storage.getInt("textSize", 1).coerceIn(0, 2)); private set
    var light by mutableStateOf(storage.getBoolean("light", false)); private set
    var voice by mutableStateOf(storage.getBoolean("voice", true)); private set
    var vibration by mutableStateOf(storage.getBoolean("vibration", true)); private set
    var speed by mutableIntStateOf(storage.getInt("speed", 1).coerceIn(0, 2)); private set
    var lowVision by mutableStateOf(storage.getBoolean("lowVision", true)); private set
    var wideCamera by mutableStateOf(storage.getBoolean("wideCamera", true)); private set
    fun target(value: Boolean) { lowVision=value; storage.edit().putBoolean("lowVision",value).apply() }
    fun camera(value: Boolean) { wideCamera=value; storage.edit().putBoolean("wideCamera",value).apply() }
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
