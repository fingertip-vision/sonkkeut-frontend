package com.sonkkeut.app

/** Output policy only: never detects a target or verifies a physical press. */
class AnnouncementGate {
    var lastText: String? = null
        private set
    private var lastKey: String? = null
    private var lastDirectionAt = Long.MIN_VALUE
    private var pressIssued = false

    fun accept(key: String, text: String, nowMs: Long, direction: Boolean = false, press: Boolean = false): Boolean {
        if (key == lastKey || (press && pressIssued)) return false
        if (direction && lastDirectionAt != Long.MIN_VALUE && nowMs - lastDirectionAt < 800) return false
        lastKey = key
        lastText = text
        if (direction) lastDirectionAt = nowMs
        if (press) pressIssued = true
        return true
    }
    fun resetAttempt() { pressIssued = false; lastKey = null; lastDirectionAt = Long.MIN_VALUE }
    fun clearDeduplication() { lastKey = null; lastDirectionAt = Long.MIN_VALUE }
}

object GuidancePhrases {
    private val directions = mapOf("up" to "위로", "up_right" to "오른쪽 위로", "right" to "오른쪽으로", "down_right" to "오른쪽 아래로", "down" to "아래로", "down_left" to "왼쪽 아래로", "left" to "왼쪽으로", "up_left" to "왼쪽 위로")
    fun movement(direction: String, near: Boolean): String? = directions[direction]?.let { "$it${if (near) " 조금" else " 이동해 주세요"}" }
    // Timings are pulse patterns, not a hardware actuator frequency.
    fun vibration(near: Boolean): LongArray = if (near) longArrayOf(0, 60, 190, 60, 190, 60) else longArrayOf(0, 80, 520, 80)
}
