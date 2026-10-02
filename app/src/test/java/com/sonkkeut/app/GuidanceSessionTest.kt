package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class GuidanceSessionTest {
    private fun run(scenario: IntegratedScenario): Pair<GuidanceSession,List<SessionOutput>> {
        val session=GuidanceSession(); val source=SyntheticObservationAdapter(scenario); session.start(source.selection)
        val results=generateSequence { source.next(session.token) }.map { session.accept(it) }.toList()
        return session to results
    }
    @Test fun fullPipelineComputesOnePressAndExpectedScreen() {
        val (session,rows)=run(IntegratedScenario.NORMAL)
        assertEquals(GuidanceSessionState.VERIFIED,session.state)
        assertEquals(1,rows.count { it.decision?.action=="press" })
        assertTrue(rows.any { it.decision?.action=="move" })
        assertTrue(rows.any { it.decision?.action=="near" })
        assertEquals("expected_screen",rows.first { it.result?.terminal==true }.result!!.reason)
    }
    @Test fun cartEvidenceAndPaymentHaveDifferentTerminalStates() {
        val (cart,rows)=run(IntegratedScenario.CART)
        assertEquals(GuidanceSessionState.VERIFIED,cart.state)
        assertEquals("expected_cart_quantity",rows.first { it.result?.terminal==true }.result!!.reason)
        assertEquals(GuidanceSessionState.PAYMENT,run(IntegratedScenario.PAYMENT).first.state)
    }
    @Test fun missingHandPlaneAndTargetChangeStopBeforePress() {
        listOf(IntegratedScenario.HAND_LOSS,IntegratedScenario.PLANE_LOSS,IntegratedScenario.TARGET_CHANGE).forEach { scenario ->
            val (session,rows)=run(scenario); assertEquals(scenario.name,GuidanceSessionState.RECOVERY,session.state)
            assertEquals(0,rows.count { it.decision?.action=="press" })
        }
    }
    @Test fun wrongResultAndTimeoutCannotBecomeSuccess() {
        val wrong=run(IntegratedScenario.WRONG_RESULT)
        assertEquals(GuidanceSessionState.RECOVERY,wrong.first.state)
        assertTrue(wrong.second.any { it.result?.reason=="unexpected_screen" })
        val timeout=run(IntegratedScenario.TIMEOUT)
        assertEquals(GuidanceSessionState.RECOVERY,timeout.first.state)
        assertTrue(timeout.second.any { it.result?.reason=="no_screen_change" })
    }
    @Test fun stopRestartRejectsQueuedOldSessionAndDuplicate() {
        val session=GuidanceSession(); val source=SyntheticObservationAdapter(IntegratedScenario.NORMAL)
        session.start(source.selection); val queued=source.next(session.token)!!
        session.stop(); assertTrue(session.accept(queued).ignored)
        session.start(source.selection); assertTrue(session.accept(queued).ignored)
        val current=queued.copy(session=session.token); assertFalse(session.accept(current).ignored)
        assertTrue(session.accept(current).ignored)
        assertEquals(GuidanceSessionState.GUIDING,session.state)
    }
    @Test fun staleFutureDelayedAndMalformedObservationCannotPress() {
        listOf<(SessionObservation)->SessionObservation>(
            { it.copy(processedAt=it.capturedAt-.1) },{ it.copy(processedAt=it.capturedAt+.3) },
            { it.copy(capturedAt=Double.NaN) },{ it.copy(screenType="invalid") },{ it.copy(cornerConfidence=.1) }
        ).forEach { corrupt ->
            val source=SyntheticObservationAdapter(IntegratedScenario.NORMAL); val session=GuidanceSession(); session.start(source.selection)
            val result=session.accept(corrupt(source.next(session.token)!!))
            assertEquals(GuidanceSessionState.RECOVERY,result.state); assertNull(result.decision)
        }
    }
}
