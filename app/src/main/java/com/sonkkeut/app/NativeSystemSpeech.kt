package com.sonkkeut.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Explicit on-device service; never silently falls back to a network recognizer. */
class NativeSystemSpeech(private val context: Context) {
    private var recognizer: SpeechRecognizer?=null
    private val main=Handler(Looper.getMainLooper())
    private var generation=0L
    val available get()=Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    fun listen(names: List<String>,result: (String)->Unit,error: (String)->Unit) = listenScored(names,{ text,_ -> result(text) },error)
    fun listenScored(names: List<String>,result: (String,Double?)->Unit,error: (String)->Unit) {
        cancel()
        if(!available) { error("기기 안의 한국어 음성 인식을 사용할 수 없습니다. 자체 모델이나 직접 입력을 이용해 주세요."); return }
        val token=++generation
        val engine=try { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) } catch(e: Exception) { error("기기 음성 인식을 준비하지 못했습니다."); return }; recognizer=engine
        engine.setRecognitionListener(object: RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?)=Unit
            override fun onBeginningOfSpeech()=Unit
            override fun onRmsChanged(rmsdB: Float)=Unit
            override fun onBufferReceived(buffer: ByteArray?)=Unit
            override fun onEndOfSpeech()=Unit
            override fun onPartialResults(partialResults: Bundle?)=Unit
            override fun onEvent(eventType: Int,params: Bundle?)=Unit
            override fun onError(code: Int) { if(token==generation) { release(); error("기기 음성을 인식하지 못했습니다 ($code). 다시 말씀하거나 직접 입력해 주세요.") } }
            override fun onResults(results: Bundle?) { if(token==generation) {
                val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                val confidence=results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()?.toDouble()?.takeIf { it.isFinite() && it in 0.0..1.0 }
                release(); if(text.isNullOrBlank()) error("음성을 듣지 못했습니다. 다시 말씀해 주세요.") else result(text,confidence)
            } }
        })
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
        if(Build.VERSION.SDK_INT>=33) intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS,ArrayList(names.take(24)))
        try { engine.startListening(intent); main.postDelayed({ if(token==generation && recognizer!=null) finish() },30000) }
        catch(e: Exception) { release(); error(e.message ?: "기기 음성 인식을 시작하지 못했습니다.") }
    }
    fun finish() { recognizer?.stopListening() }
    fun cancel() { generation++; recognizer?.cancel(); release() }
    private fun release() { main.removeCallbacksAndMessages(null); recognizer?.destroy(); recognizer=null }
}
