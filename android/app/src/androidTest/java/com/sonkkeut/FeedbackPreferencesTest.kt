package com.sonkkeut

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.bridge.BridgeReactContext
import kr.sonkkeut.android.FeedbackPolicy
import kr.sonkkeut.rn.SonkkeutModule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackPreferencesTest {
    @Test fun disabledAutomaticSpeechStillAllowsExplicitRepeat() {
        val policy = FeedbackPolicy(false, false, 0.75f)
        assertFalse(policy.allowsSpeech(false, false))
        assertTrue(policy.allowsSpeech(true, false))
        assertFalse(policy.vibrationEnabled)
        assertEquals(0.75f, policy.speechRate, 0f)
    }
    @Test fun talkBackSuppressesAutomaticSpeechButNotExplicitRepeat() {
        val policy = FeedbackPolicy()
        assertFalse(policy.allowsSpeech(false, true))
        assertTrue(policy.allowsSpeech(true, true))
        assertTrue(policy.allowsSpeech(false, false))
    }
    @Test fun badRatesAreFiniteAndClamped() {
        assertEquals(1f, FeedbackPolicy.safeRate(Float.NaN), 0f)
        assertEquals(1f, FeedbackPolicy.safeRate(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0.75f, FeedbackPolicy.safeRate(-1f), 0f)
        assertEquals(1.25f, FeedbackPolicy.safeRate(3f), 0f)
        val policy = FeedbackPolicy()
        policy.configure(false, false, 1.25f)
        assertFalse(policy.voiceEnabled); assertFalse(policy.vibrationEnabled)
        assertEquals(1.25f, policy.speechRate, 0f)
    }
    @Test fun bridgeConfigurationPersistsWithoutNeedingModelInitialization() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val storage = context.getSharedPreferences("sonkkeut_feedback", Context.MODE_PRIVATE)
        val original = storage.all
        val module = SonkkeutModule(BridgeReactContext(context))
        try {
            module.configureFeedback(false, false, 0.75)
            instrumentation.waitForIdleSync()
            assertFalse(storage.getBoolean("voice", true))
            assertFalse(storage.getBoolean("vibration", true))
            assertEquals(0.75f, storage.getFloat("rate", 1f), 0f)
        } finally {
            module.configureFeedback(original["voice"] as? Boolean ?: true, original["vibration"] as? Boolean ?: true,
                (original["rate"] as? Float ?: 1f).toDouble())
            instrumentation.waitForIdleSync()
            val edit = storage.edit()
            listOf("voice", "vibration", "rate").forEach { key -> when (val value = original[key]) {
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                else -> edit.remove(key)
            } }
            edit.commit()
        }
    }
}
