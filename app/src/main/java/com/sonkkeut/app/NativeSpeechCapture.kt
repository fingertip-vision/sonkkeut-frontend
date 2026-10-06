package com.sonkkeut.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Frontend microphone lifecycle; recognition still uses the existing CT2 Whisper model. */
internal class NativeSpeechCapture(private val context: Context): AutoCloseable {
    data class Capture(val pcm: ShortArray,val captureMs: Long,val originalSamples: Int)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val generation=AtomicLong()
    private val closed=AtomicBoolean()
    private val finish=AtomicBoolean()
    private val recorder=AtomicReference<AudioRecord?>()
    fun start(onStarted: ()->Unit,onCaptured: (Capture)->Unit,onError: (Throwable)->Unit) {
        val token=generation.incrementAndGet(); finish.set(false)
        fun current()=!closed.get() && generation.get()==token
        fun deliver(block: ()->Unit) { main.post { if(current()) block() } }
        worker.execute {
            var audio: AudioRecord?=null
            try {
                check(current())
                check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { "마이크 권한을 허용해 주세요." }
                val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0) { "이 기기에서 16kHz 음성을 녹음할 수 없습니다." }
                audio=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum*2,6400))
                check(audio.state==AudioRecord.STATE_INITIALIZED) { "마이크를 준비하지 못했습니다." }
                recorder.set(audio); check(current()); audio.startRecording(); deliver(onStarted)
                val started=SystemClock.elapsedRealtime()
                val pcm=ShortArray(480000); val chunk=ShortArray(1600); val endpoint=SpeechEndpoint()
                while(current() && endpoint.samples<pcm.size) {
                    val count=audio.read(chunk,0,minOf(chunk.size,pcm.size-endpoint.samples),AudioRecord.READ_BLOCKING)
                    check(count>0) { "마이크 녹음이 중단됐습니다." }
                    chunk.copyInto(pcm,endpoint.samples,0,count)
                    if(endpoint.append(chunk,count,finish.get())) break
                }
                val result=Capture(pcm.copyOf(endpoint.retainedSamples()),SystemClock.elapsedRealtime()-started,endpoint.samples)
                // Release the mic before reporting analysis; no TTS can leak into the recording.
                val completedAudio=audio
                recorder.compareAndSet(completedAudio,null); runCatching { completedAudio.stop() }; completedAudio.release(); audio=null
                deliver { onCaptured(result) }
            } catch(error: Throwable) { deliver { onError(error) } }
            finally { audio?.let { recorder.compareAndSet(it,null); runCatching { it.stop() }; it.release() } }
        }
    }
    fun finishCapture() { finish.set(true) }
    fun cancel() { generation.incrementAndGet(); recorder.get()?.let { runCatching { it.stop() } } }
    override fun close() { if(closed.compareAndSet(false,true)) { cancel(); worker.shutdown() } }
}
