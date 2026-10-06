package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class SpeechEndpointTest {
    private val voice=ShortArray(1600) { 1000 }
    private val silence=ShortArray(1600)
    @Test fun speechEndsAtExistingSilenceBoundAndPreservesCapturedAudio() {
        val endpoint=SpeechEndpoint()
        repeat(10) { assertFalse(endpoint.append(voice,1600,false)) }
        repeat(11) { assertFalse(endpoint.append(silence,1600,false)) }
        assertTrue(endpoint.append(silence,1600,false))
        assertEquals(35200,endpoint.samples)
        assertEquals(35200,endpoint.retainedSamples())
    }
    @Test fun intermediatePauseAndFollowingMenuAreNotTrimmed() {
        val endpoint=SpeechEndpoint()
        repeat(10) { endpoint.append(voice,1600,false) }
        repeat(8) { assertFalse(endpoint.append(silence,1600,false)) }
        repeat(10) { assertFalse(endpoint.append(voice,1600,false)) }
        repeat(12) { endpoint.append(silence,1600,false) }
        assertEquals(64000,endpoint.retainedSamples())
    }
    @Test fun manualFinishKeepsShortUtteranceAndMinimumAudio() {
        val endpoint=SpeechEndpoint()
        repeat(3) { assertFalse(endpoint.append(voice,1600,true)) }
        assertTrue(endpoint.append(voice,1600,true))
        assertEquals(6400,endpoint.retainedSamples())
    }
    @Test fun quietTrailingWordsAreNeverDiscardedByEnergyEndpointing() {
        val endpoint=SpeechEndpoint()
        repeat(10) { endpoint.append(voice,1600,false) }
        repeat(12) { endpoint.append(ShortArray(1600) { 100 },1600,false) }
        assertEquals(35200,endpoint.retainedSamples())
    }
    @Test fun quietSpeechIsStillSentToWhisperForItsOwnSilenceDecision() {
        val endpoint=SpeechEndpoint()
        val quiet=ShortArray(1600) { 100 }
        repeat(59) { assertFalse(endpoint.append(quiet,1600,false)) }
        assertTrue(endpoint.append(quiet,1600,false))
        assertFalse(endpoint.hasSpeech)
        assertEquals(96000,endpoint.retainedSamples())
    }
    @Test fun continuousNoiseOrSpeechStillHasThirtySecondLimit() {
        val endpoint=SpeechEndpoint()
        repeat(299) { assertFalse(endpoint.append(voice,1600,false)) }
        assertTrue(endpoint.append(voice,1600,false))
        assertEquals(480000,endpoint.retainedSamples())
    }
    @Test fun partialAudioChunksUseSamplesInsteadOfLoopCount() {
        val endpoint=SpeechEndpoint()
        repeat(20) { endpoint.append(voice,800,false) }
        repeat(23) { assertFalse(endpoint.append(silence,800,false)) }
        assertTrue(endpoint.append(silence,800,false))
        assertEquals(35200,endpoint.samples)
    }
    @Test fun microphonePreparationCaptureAndAnalysisHaveDistinctLabels() {
        val preparing=signalPresentation(SignalSnapshot(busy=true,preparingVoice=true))
        assertEquals("음성 모델 준비 중",preparing.label)
        val capturing=signalPresentation(SignalSnapshot(recording=true,busy=true))
        assertEquals("음성 인식 중",capturing.label)
        val analyzing=signalPresentation(SignalSnapshot(busy=true,voiceAnalysis=true))
        assertEquals("음성 분석 중",analyzing.label)
        assertTrue(analyzing.detail.contains("녹음은 끝났어요"))
        assertEquals("주문 처리 중",signalPresentation(SignalSnapshot(busy=true)).label)
    }
}
