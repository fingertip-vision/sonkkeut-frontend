package com.sonkkeut.app

// Python tracking.py, revision ba55be0. Input landmarks are camera pixels.
data class CoreHand(val id: String,val landmarks: List<CorePoint>,val confidence: Double,val planeDistance: Double?=null) {
    init { require(id.isNotBlank() && landmarks.size==21 && confidence.isFinite() && confidence in 0.0..1.0); require(planeDistance==null || planeDistance.isFinite() && planeDistance>=0) }
}
data class CoreFingertip(val rawPoint: CorePoint?,val point: CorePoint?,val confidence: Double,val handId: String?,val reason: String,val selectionBasis: String?=null) {
    val valid get()=reason=="tracking" && rawPoint != null && point != null
}
class CoreFingertipTracker(private val minConfidence: Double=.7,private val smoothingFrames: Int=5,private val maxFrameGap: Double=.2,private val missingTimeout: Double=1.0) {
    init { require(minConfidence.isFinite() && minConfidence in 0.0..1.0 && smoothingFrames>0); require(listOf(maxFrameGap,missingTimeout).all { it.isFinite() && it>0 }) }
    private val history=ArrayDeque<CorePoint>()
    private var handId: String?=null
    private var timestamp: Double?=null
    private var lastHandAt: Double?=null
    private var missingSince: Double?=null
    private var keyframeId: Int?=null
    private fun discard(identity: Boolean=false) { history.clear(); if(identity) handId=null }
    private fun stop(reason: String,hand: CoreHand?=null,basis: String?=null): CoreFingertip { discard(hand==null); return CoreFingertip(null,null,hand?.confidence ?: 0.0,hand?.id,reason,basis) }
    private fun choose(hands: List<CoreHand>): Pair<CoreHand?,String> {
        if(hands.size==1) return hands.first() to "single_hand"
        if(hands.all { it.planeDistance != null }) {
            val nearest=hands.minOf { it.planeDistance!! }; val candidates=hands.filter { it.planeDistance==nearest }
            if(candidates.size==1) return candidates.first() to "measured_plane_distance"
            val retained=candidates.find { it.id==handId }; return retained to if(retained != null) "retained_identity" else "ambiguous_hand"
        }
        val retained=hands.find { it.id==handId }; return retained to if(retained != null) "retained_identity" else "ambiguous_hand"
    }
    fun update(hands: List<CoreHand>,transform: CoreHomography?,now: Double,keyframe: Int?=null): CoreFingertip {
        if(!now.isFinite() || timestamp?.let { now<=it }==true) { discard(true); throw IllegalArgumentException("timestamps must strictly increase") }
        if(timestamp?.let { now-it>maxFrameGap+1e-9 }==true) discard()
        timestamp=now
        if(keyframe != null && keyframe<0) return stop("invalid_keyframe")
        if(keyframe != keyframeId) discard()
        keyframeId=keyframe
        if(hands.map { it.id }.toSet().size != hands.size) return stop("ambiguous_hand")
        if(hands.isEmpty()) {
            if(missingSince==null) missingSince=lastHandAt ?: now
            return stop(if(now-missingSince!!+1e-9>=missingTimeout) "lost_hand" else "missing_hand")
        }
        lastHandAt=now; missingSince=null
        val (hand,basis)=choose(hands)
        if(hand==null) return stop("ambiguous_hand")
        if(hand.id != handId) discard()
        handId=hand.id
        if(hand.confidence<minConfidence) return stop("low_hand_confidence",hand,basis)
        if(transform==null) return stop("invalid_plane",hand,basis)
        val point=try { transform.cameraToScreen(hand.landmarks[8]) } catch(_: IllegalArgumentException) { return stop("invalid_plane",hand,basis) }
        if(!point.insidePlane()) return stop("outside_plane",hand,basis)
        history.addLast(point); while(history.size>smoothingFrames) history.removeFirst()
        val smoothed=CorePoint(history.sumOf { it.x }/history.size,history.sumOf { it.y }/history.size)
        return CoreFingertip(point,smoothed,hand.confidence,hand.id,"tracking",basis)
    }
}
