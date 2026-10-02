package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class SyntheticReplayTest {
    private fun event(replay: SyntheticReplay, sequence: Int, action: String = "press", reason: String = "continuous_inside") =
        ReplayEvent(replay.session, replay.keyframe, replay.target, sequence, ReplayDecision(action, reason = reason))

    @Test fun normalTraceHasOnePressAndExplicitResult() {
        val replay = SyntheticReplay(); replay.start()
        val feedback = ReplayFixtures.events(replay.session, false).mapNotNull { replay.accept(it) }
        assertEquals(1, feedback.count { it.press })
        assertEquals(ReplayState.RESULT, replay.state)
        assertTrue(feedback.last().text.contains("실제 주문 결과가 아닙니다"))
    }
    @Test fun staleSessionKeyframeTargetAndDuplicateCannotPress() {
        val replay = SyntheticReplay(); replay.start()
        val valid = event(replay, 0)
        listOf(valid.copy(session = 0), valid.copy(keyframe = 2), valid.copy(target = "old")).forEach { assertNull(replay.accept(it)) }
        assertTrue(replay.accept(valid)!!.press)
        assertNull(replay.accept(valid))
        assertNull(replay.accept(event(replay, 1)))
    }
    @Test fun resultWithoutPressIsNotSuccess() {
        val replay = SyntheticReplay(); replay.start()
        assertNull(replay.accept(ReplayEvent(replay.session, 1, replay.target, 0, verifiedEffect = "option")))
        assertEquals(ReplayState.GUIDING, replay.state)
    }
    @Test fun unexpectedResultRequiresExplicitRestart() {
        val replay = SyntheticReplay(); replay.start(); replay.accept(event(replay, 0))
        replay.accept(ReplayEvent(replay.session, 1, replay.target, 1, verifiedEffect = "menu"))
        assertEquals(ReplayState.RECOVERY, replay.state)
        assertNull(replay.accept(event(replay, 2)))
        replay.start(); assertTrue(replay.accept(event(replay, 0))!!.press)
    }
    @Test fun missingFingerStopsAndRejectsSubsequentPress() {
        val replay = SyntheticReplay(); replay.start()
        ReplayFixtures.events(replay.session, true).forEach { replay.accept(it) }
        assertEquals(ReplayState.RECOVERY, replay.state)
        assertNull(replay.accept(event(replay, 2)))
    }
    @Test fun paymentEndsGuidanceAndDoesNotExecutePayment() {
        val replay = SyntheticReplay(); replay.start()
        val feedback = ReplayFixtures.events(replay.session, false, true).mapNotNull { replay.accept(it) }
        assertEquals(ReplayState.PAYMENT, replay.state)
        assertTrue(feedback.last().text.contains("실제 결제는 진행하지 않습니다"))
        assertNull(replay.accept(event(replay, 6)))
    }
    @Test fun pauseInvalidatesQueuedEventsAndResumeNeedsNewSession() {
        val replay = SyntheticReplay(); replay.start(); val queued = event(replay, 0)
        replay.pause(); assertNull(replay.accept(queued))
        replay.start(); assertNull(replay.accept(queued)); assertTrue(replay.accept(event(replay, 0))!!.press)
    }
    @Test fun dwellAndAwaitingResultsAreNotPressInstructions() {
        val replay = SyntheticReplay(); replay.start()
        assertFalse(replay.accept(event(replay, 0, "hold", "dwell_pending"))!!.press)
        assertNull(replay.accept(event(replay, 1, "hold", "awaiting_result")))
        assertNull(replay.accept(event(replay, 2, "press", "uncertain")))
    }
}
