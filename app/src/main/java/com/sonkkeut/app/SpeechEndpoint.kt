package com.sonkkeut.app

import kotlin.math.sqrt

/** Same 1.2s endpoint as the deployed recorder; intermediate pauses remain in the PCM. */
internal class SpeechEndpoint {
    var samples=0; private set
    var hasSpeech=false; private set
    private var lastSpeechEnd=0
    fun append(chunk: ShortArray,count: Int,finished: Boolean): Boolean {
        require(count in 1..chunk.size)
        samples+=count
        var energy=0.0
        for(i in 0 until count) { val value=chunk[i]/32768.0; energy+=value*value }
        if(sqrt(energy/count)>=.012) { hasSpeech=true; lastSpeechEnd=samples }
        return (finished && samples>=6400) || (hasSpeech && samples>=16000 && samples-lastSpeechEnd>=19200) ||
            (!hasSpeech && samples>=96000) || samples>=480000
    }
    // Energy endpointing cannot safely distinguish quiet trailing words from silence.
    fun retainedSamples()=samples
}
