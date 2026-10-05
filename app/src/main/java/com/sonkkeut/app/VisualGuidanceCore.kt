package com.sonkkeut.app

import kotlin.math.*

// Port of sonkkeut-ai guidance.py at ba55be0248c709ab9222d40c100dcb9d61362a50.
// Input is an already-produced normalized observation, never a camera image.
data class CorePoint(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) }
    fun insidePlane() = x in 0.0..1.0 && y in 0.0..1.0
}
data class CoreBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init { require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 } && left < right && top < bottom) }
    val center get() = CorePoint((left + right) / 2, (top + bottom) / 2)
    fun contains(p: CorePoint) = p.x in left..right && p.y in top..bottom
}
data class CoreTarget(val id: String, val kind: String, val box: CoreBox, val confidence: Double) {
    init { require(id.isNotBlank() && kind in listOf("button", "menu", "price", "tab", "back") && confidence.isFinite() && confidence in 0.0..1.0) }
}
data class CoreDecision(
    val action: String, val direction: String? = null, val dx: Double? = null, val dy: Double? = null,
    val distance: Double? = null, val dwellSeconds: Double = 0.0, val reason: String = "",
    val recenter: Boolean = false, val reacquire: Boolean = false,
) {
    val distanceBand get() = if (reason == "awaiting_result") null else when (action) { "move" -> "far"; "near" -> "near"; "hold", "press" -> "reached"; else -> null }
    fun asReplayDecision() = ReplayDecision(action, direction, reason, recenter, reacquire)
}

class VisualGuidanceCore(
    private val nearDistance: Double = 0.05, private val minConfidence: Double = 0.7,
    private val dwellDuration: Double = 0.3, private val maxFrameGap: Double = 0.2,
    private val recenterDuration: Double = 3.0, private val increasingEpsilon: Double = 1e-6,
) {
    init {
        require(minConfidence.isFinite() && minConfidence in 0.0..1.0)
        require(listOf(nearDistance, dwellDuration, maxFrameGap, recenterDuration).all { it.isFinite() && it > 0 })
        require(increasingEpsilon.isFinite() && increasingEpsilon >= 0)
    }
    private data class TargetKey(val keyframe: Int?, val id: String, val kind: String, val box: CoreBox, val hand: String?)
    private var timestamp: Double? = null
    private var targetKey: TargetKey? = null
    private var insideSince: Double? = null
    private var pressed = false
    private var previousDistance: Double? = null
    private var previousValidAt: Double? = null
    private var risingSince: Double? = null
    private var recenterEmitted = false
    private fun resetProgress() { insideSince = null; previousDistance = null; previousValidAt = null; risingSince = null; recenterEmitted = false }
    fun resetAttempt() { pressed = false; resetProgress() }
    private fun stop(reason: String, reacquire: Boolean = false): CoreDecision { resetProgress(); return CoreDecision("stop", reason = reason, reacquire = reacquire) }
    private fun recenter(distance: Double, now: Double): Boolean {
        if (previousDistance?.let { distance - it > increasingEpsilon } == true) {
            if (risingSince == null) risingSince = previousValidAt
        } else { risingSince = null; recenterEmitted = false }
        previousDistance = distance; previousValidAt = now
        if (risingSince?.let { !recenterEmitted && now - it + 1e-9 >= recenterDuration } == true) { recenterEmitted = true; return true }
        return false
    }
    fun update(point: CorePoint?, target: CoreTarget?, now: Double, rawPoint: CorePoint? = null, fingerConfidence: Double = 1.0, keyframe: Int? = null, hand: String? = null): CoreDecision {
        if (!now.isFinite() || timestamp?.let { now <= it } == true) { resetProgress(); throw IllegalArgumentException("timestamps must be finite and strictly increase") }
        if (timestamp?.let { now - it > maxFrameGap + 1e-9 } == true) resetProgress()
        timestamp = now
        if (keyframe != null && keyframe < 0) return stop("invalid_keyframe", true)
        if (hand != null && hand.isBlank()) return stop("invalid_hand_id")
        if (target == null) { targetKey = null; return stop("missing_target", true) }
        if (target.kind == "price") return stop("noninteractive_target", true)
        val key = TargetKey(keyframe, target.id, target.kind, target.box, hand)
        if (key != targetKey) { resetProgress(); targetKey = key }
        if (target.confidence < minConfidence) return stop("low_target_confidence", true)
        if (!fingerConfidence.isFinite() || fingerConfidence !in 0.0..1.0) return stop("invalid_finger_confidence")
        if (fingerConfidence < minConfidence) return stop("low_finger_confidence")
        if (point == null || rawPoint == null) return stop("missing_finger")
        if (!point.insidePlane() || !rawPoint.insidePlane()) return stop("outside_plane")
        val dx = target.box.center.x - point.x; val dy = target.box.center.y - point.y
        val distance = hypot(dx, dy)
        if (pressed) return CoreDecision("hold", dx = dx, dy = dy, distance = distance, reason = "awaiting_result")
        if (target.box.contains(point) && target.box.contains(rawPoint)) {
            previousDistance = null; previousValidAt = null; risingSince = null; recenterEmitted = false
            if (insideSince == null) insideSince = now
            val dwell = now - insideSince!!
            if (dwell + 1e-9 >= dwellDuration) { pressed = true; return CoreDecision("press", dx = dx, dy = dy, distance = distance, dwellSeconds = dwell, reason = "continuous_inside") }
            return CoreDecision("hold", dx = dx, dy = dy, distance = distance, dwellSeconds = dwell, reason = "dwell_pending")
        }
        insideSince = null
        val recenter = recenter(distance, now)
        return CoreDecision(if (distance <= nearDistance) "near" else "move", direction(dx, dy), dx, dy, distance,
            reason = if (!target.box.contains(rawPoint)) "raw_outside_target" else "smoothed_outside_target", recenter = recenter)
    }
    companion object {
        fun direction(dx: Double, dy: Double): String? {
            require(dx.isFinite() && dy.isFinite())
            if (dx == 0.0 && dy == 0.0) return null
            val sector = floor((atan2(dy, dx) + PI / 8) / (PI / 4)).toInt()
            return listOf("right", "down_right", "down", "down_left", "left", "up_left", "up", "up_right")[((sector % 8) + 8) % 8]
        }
    }
}
