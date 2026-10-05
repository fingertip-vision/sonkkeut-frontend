package com.sonkkeut.app

/** Uses the native Guide's .5 confidence/pointing thresholds for visual feedback only. */
internal object NativeHandPrompt {
    fun hint(frame: Map<String,Any?>): String? {
        val confidence=(frame["tip_conf"] as? Number)?.toDouble()
        val pointing=(frame["tip_pointing"] as? Number)?.toDouble()
        if(confidence!=null && (!confidence.isFinite() || confidence<.5)) return "☝ 손끝을 다시 보여 주세요"
        if(pointing!=null && (!pointing.isFinite() || pointing<.5)) return "☝ 검지만 펴 주세요"
        return null
    }
}
