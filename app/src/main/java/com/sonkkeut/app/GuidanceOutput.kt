package com.sonkkeut.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class GuidanceOutput(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val accessibility = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val gate = AnnouncementGate()
    private var tts: TextToSpeech? = null
    private var closed = false
    private var initializationFinished = false
    private var suspended = false
    private var currentId: String? = null
    private var completion: (() -> Unit)? = null
    private var sequence = 0
    private var voiceEnabled = true
    private var vibrationEnabled = true
    private var speechRate = 1f
    fun configure(voice: Boolean, vibration: Boolean, rate: Float) {
        if (voiceEnabled && !voice) { tts?.stop(); currentId = null; audio.abandonAudioFocusRequest(focus) }
        if (vibrationEnabled && !vibration) vibrator?.cancel()
        voiceEnabled = voice; vibrationEnabled = vibration; speechRate = rate
    }
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change -> if (change < 0) stop() }, main).build()
    var ready by mutableStateOf(false)
        private set
    var speechStatus by mutableStateOf("한국어 음성 안내를 준비하고 있습니다.")
        private set
    var outputStatus by mutableStateOf("아직 재생한 안내가 없습니다.")
        private set
    val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true
    var lastText by mutableStateOf<String?>(null)
        private set

    init {
        main.postDelayed({ if (!closed && !initializationFinished) speechStatus = "음성 출력 준비가 지연되고 있습니다. 기기 음성 출력 설정을 확인해 주세요." }, 10000)
        tts = TextToSpeech(context.applicationContext) { result ->
            main.post {
                if (!closed) {
                    initializationFinished = true
                    val engine = tts
                    try {
                        val voice = if (result == TextToSpeech.SUCCESS) engine?.voices?.filter {
                            it.locale.language == "ko" && !it.isNetworkConnectionRequired &&
                                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                        }?.sortedBy { it.name }?.firstOrNull() else null
                        if (voice == null || engine?.setVoice(voice) != TextToSpeech.SUCCESS) {
                            speechStatus = "설치된 오프라인 한국어 음성이 없습니다. 기기의 음성 출력 설정에서 한국어 음성을 준비한 뒤 앱을 다시 실행해 주세요."
                        } else {
                            engine.setAudioAttributes(attributes)
                            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(id: String?) { main.post { if (currentId == id && !closed) outputStatus = "음성 안내 재생 중" } }
                                override fun onDone(id: String?) { main.post { if (currentId == id && !closed) { currentId = null; audio.abandonAudioFocusRequest(focus); outputStatus = "음성 안내 재생 완료"; val done=completion; completion=null; done?.invoke() } } }
                                @Deprecated("Android callback") override fun onError(id: String?) { main.post { if (currentId == id && !closed) { stop(); outputStatus = "음성 재생에 실패했습니다. 다시 듣기로 재시도해 주세요." } } }
                            })
                            ready = true
                            speechStatus = "오프라인 한국어 음성 안내 사용 가능"
                        }
                    } catch (_: Exception) {
                        speechStatus = "음성 출력기를 준비하지 못했습니다. 기기의 음성 출력 설정을 확인한 뒤 앱을 다시 실행해 주세요."
                    }
                }
            }
        }
    }

    fun announce(key: String, text: String, direction: Boolean = false, press: Boolean = false, vibration: LongArray? = null) {
        if (closed || suspended || !gate.accept(key, text, SystemClock.elapsedRealtime(), direction, press)) return
        lastText = gate.lastText
        stopDevices()
        if (vibrationEnabled && vibration != null && hasVibrator) {
            try { vibrator.vibrate(VibrationEffect.createWaveform(vibration, -1)) } catch (_: RuntimeException) { outputStatus = "이 기기에서 진동을 실행하지 못했습니다." }
        }
        speak(text)
    }
    fun readOrder(text: String, done: () -> Unit) {
        if(closed || suspended) return
        stopDevices(); lastText=text
        completion=done
        speak(text)
        if(currentId==null) { completion=null; done() }
    }
    fun repeat() { if (!closed && !suspended) lastText?.let { stopDevices(); speak(it, explicit = true) } }
    fun read(text: String) {
        if (closed || suspended) return
        gate.accept("explicit:${SystemClock.elapsedRealtime()}", text, SystemClock.elapsedRealtime(), false, false)
        lastText = text; stopDevices(); speak(text, explicit = true)
    }
    private fun speak(text: String, explicit: Boolean = false) {
        if (!voiceEnabled && !explicit) { outputStatus = "화면 안내: $text"; return }
        if (!explicit && accessibility.isTouchExplorationEnabled) { outputStatus = "화면 읽기 안내: $text"; return }
        if (!ready) { outputStatus = "화면 안내: $text"; return }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            outputStatus = "다른 소리가 재생 중입니다. 다시 듣기를 눌러 주세요."
            return
        }
        tts?.setSpeechRate(speechRate)
        val id = "guidance-${++sequence}"
        currentId = id
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            stopDevices(); outputStatus = "음성 재생에 실패했습니다. 다시 듣기로 재시도해 주세요."
        }
    }
    private fun stopDevices() { completion=null; currentId = null; tts?.stop(); vibrator?.cancel(); audio.abandonAudioFocusRequest(focus) }
    fun stop() { val done=completion; stopDevices(); gate.clearDeduplication(); outputStatus = "음성과 진동을 멈췄습니다."; done?.invoke() }
    fun suspendOutput() { suspended = true; stop() }
    fun resumeOutput() { suspended = false; gate.clearDeduplication() }
    fun resetAttempt() { stop(); gate.resetAttempt() }
    fun clearRepeat() { stopDevices(); gate.clearLastGuidance(); lastText = null; outputStatus = "이전 세션 안내를 지웠습니다." }
    fun close() { closed = true; stopDevices(); tts?.shutdown(); tts = null; main.removeCallbacksAndMessages(null) }
}
