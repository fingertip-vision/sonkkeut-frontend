package com.sonkkeut.app

// Prerecorded adapter results, not camera observations or executed AI calculations.
data class ReplayDecision(val action: String, val direction: String? = null, val reason: String = "", val recenter: Boolean = false, val reacquire: Boolean = false)
data class ReplayEvent(
    val session: Int, val keyframe: Int, val target: String, val sequence: Int,
    val decision: ReplayDecision? = null, val verifiedEffect: String? = null,
)
enum class ReplayState { GUIDING, AWAITING_RESULT, RECOVERY, RESULT, PAYMENT, PAUSED }
data class ReplayFeedback(val text: String, val direction: Boolean = false, val press: Boolean = false, val near: Boolean? = null)

class SyntheticReplay {
    var session = 0; private set
    var keyframe = 1; private set
    var target = "fixture-menu"; private set
    var state = ReplayState.PAUSED; private set
    private var sequence = -1
    private var pressIssued = false
    val expectedEffect = "option"

    fun start() { session++; keyframe = 1; target = "fixture-menu"; sequence = -1; pressIssued = false; state = ReplayState.GUIDING }
    fun pause() { session++; state = ReplayState.PAUSED }
    fun accept(event: ReplayEvent): ReplayFeedback? {
        if (state in listOf(ReplayState.PAUSED, ReplayState.PAYMENT, ReplayState.RECOVERY)) return null
        if (event.session != session || event.keyframe != keyframe || event.target != target || event.sequence <= sequence) return null
        sequence = event.sequence
        if (event.verifiedEffect != null) {
            if (state != ReplayState.AWAITING_RESULT) return null
            if (event.verifiedEffect == "payment") {
                state = ReplayState.PAYMENT
                return ReplayFeedback("합성 재생입니다. 결제 화면에서 안내를 종료합니다. 실제 결제는 진행하지 않습니다.")
            }
            if (event.verifiedEffect == expectedEffect) {
                state = ReplayState.RESULT
                return ReplayFeedback("합성 재생입니다. 고정된 옵션 화면 결과를 확인했습니다. 실제 주문 결과가 아닙니다.")
            }
            state = ReplayState.RECOVERY
            return ReplayFeedback("합성 재생입니다. 예상 화면과 다릅니다. 멈춘 뒤 재생을 다시 시작해 주세요.")
        }
        val decision = event.decision ?: return null
        if (decision.recenter || decision.reacquire) {
            state = ReplayState.RECOVERY
            return ReplayFeedback(if (decision.recenter) "합성 입력 안내입니다. 손을 멈추고 화면 중앙을 다시 맞춘 뒤 재시작해 주세요." else "합성 입력 안내입니다. 화면과 목표를 다시 확인한 뒤 재시작해 주세요.")
        }
        if (decision.action == "stop" || decision.reason == "missing_finger" || decision.reason == "low_finger_confidence") {
            state = ReplayState.RECOVERY
            return ReplayFeedback("합성 재생입니다. 입력이 불확실합니다. 손을 멈추고 재생을 다시 시작해 주세요.")
        }
        if (state == ReplayState.AWAITING_RESULT || state == ReplayState.RESULT) return null
        if (decision.reason == "awaiting_result") return null
        return when (decision.action) {
            "move", "near" -> {
                val near = decision.action == "near"
                val phrase = GuidancePhrases.movement(decision.direction ?: "", near) ?: return null
                ReplayFeedback("합성 재생입니다. $phrase", direction = true, near = near)
            }
            "hold" -> ReplayFeedback("합성 재생입니다. 위치를 유지하는 단계입니다. 아직 누름 안내가 아닙니다.")
            "press" -> if (!pressIssued && decision.reason == "continuous_inside") {
                pressIssued = true; state = ReplayState.AWAITING_RESULT
                ReplayFeedback("합성 재생입니다. 누름 안내 조건에 도달했습니다. 실제 화면은 누르지 마세요. 고정 결과를 기다립니다.", press = true)
            } else null
            else -> { state = ReplayState.RECOVERY; ReplayFeedback("합성 재생입니다. 알 수 없는 안내를 중지했습니다.") }
        }
    }
}

object ReplayFixtures {
    val normal = listOf(
        ReplayDecision("move", "up_right"), ReplayDecision("near", "up_right"),
        ReplayDecision("hold", reason = "dwell_pending"), ReplayDecision("press", reason = "continuous_inside"),
        ReplayDecision("hold", reason = "awaiting_result"),
    )
    fun events(session: Int, recovery: Boolean, payment: Boolean = false): List<ReplayEvent> {
        val decisions = if (recovery) listOf(normal[0], ReplayDecision("stop", reason = "missing_finger")) else normal
        return decisions.mapIndexed { index, decision -> ReplayEvent(session, 1, "fixture-menu", index, decision) } +
            if (recovery) emptyList() else listOf(ReplayEvent(session, 1, "fixture-menu", 5, verifiedEffect = if (payment) "payment" else "option"))
    }
}
