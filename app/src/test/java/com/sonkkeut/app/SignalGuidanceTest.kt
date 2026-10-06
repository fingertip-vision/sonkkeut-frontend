package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class SignalGuidanceTest {
    private val frame: Map<String,Any?> = mapOf("found" to true,"tip" to listOf(.5,.4),"tip_conf" to .9,"tip_pointing" to .9,
        "frame_size" to listOf(960,720),"target_image_box" to listOf(200,150,400,300),
        "event" to mapOf("type" to "direction","dir" to "right","target_id" to "coffee"))
    private val ready=SignalSnapshot(flow="S4",hasOrder=true,frame=frame,targetId="coffee",attempt=3,frameAttempt=3,frameAt=1000,now=1100)
    private fun event(vararg values: Pair<String,Any?>)=ready.copy(frame=frame+("event" to mapOf(*values)))
    private fun noArrow(s: SignalSnapshot) { val result=signalPresentation(s); assertNull(result.direction); assertNull(result.targetBox); assertNotEquals(SignalPhase.PRESS,result.phase) }

    @Test fun eightDirectionsComeOnlyFromMatchedNativeEvents() {
        listOf("right","up_right","up","up_left","left","down_left","down","down_right").forEach { dir ->
            val result=signalPresentation(event("type" to "direction","dir" to dir,"target_id" to "coffee"))
            assertEquals(dir,result.direction); assertEquals(SignalPhase.MOVE,result.phase); assertNotNull(result.targetBox)
        }
    }
    @Test fun staleFutureMissingAndDifferentAttemptOrTargetNeverDrawMovement() {
        listOf(ready.copy(now=2501),ready.copy(now=999),ready.copy(frameAt=0),ready.copy(frameAttempt=2),ready.copy(targetId="tea"),ready.copy(targetId=null),
            event("type" to "direction","dir" to "right"),ready.copy(frame=frame-("event")),ready.copy(frame=frame+("found" to false)),
            ready.copy(frame=frame+("target_missing" to true)),ready.copy(flow="S2")).forEach(::noArrow)
    }
    @Test fun invalidHandEvidenceCannotProduceMovementOrPress() {
        listOf("tip_conf","tip_pointing").forEach { key ->
            listOf(null,Double.NaN,Double.POSITIVE_INFINITY,-1,.49,1.1,"high").forEach { value ->
                noArrow(ready.copy(frame=frame+(key to value)))
                noArrow(ready.copy(frame=frame+(key to value)+("event" to mapOf("type" to "press","target_id" to "coffee"))))
            }
        }
        listOf(null,emptyList<Double>(),listOf(.2),listOf(Double.NaN,.4),listOf(1.1,.4)).forEach { noArrow(ready.copy(frame=frame+("tip" to it))) }
    }
    @Test fun foregroundActivitiesSuppressOldCameraGuidance() {
        listOf(ready.copy(paused=true),ready.copy(recording=true),ready.copy(busy=true),ready.copy(editing=true),ready.copy(flow="SE"),ready.copy(flow="S6")).forEach(::noArrow)
        assertEquals(SignalPhase.LISTENING,signalPresentation(ready.copy(recording=true,busy=true)).phase)
        assertEquals(SignalPhase.COMPLETE,signalPresentation(ready.copy(paused=true,flow="S6")).phase)
    }
    @Test fun pressRequiresExplicitEventAndResultIsNotCompletion() {
        assertEquals(SignalPhase.PRESS,signalPresentation(event("type" to "press","target_id" to "coffee")).phase)
        assertEquals(SignalPhase.RESULT,signalPresentation(ready.copy(flow="S5")).phase)
        assertNull(signalPresentation(ready.copy(flow="S5")).direction)
        assertEquals(SignalPhase.HOLD,signalPresentation(event("type" to "direction","distance" to "reach","target_id" to "coffee")).phase)
        noArrow(event("type" to "press","target_id" to "tea"))
    }
    @Test fun missingHandAndPointingHaveDistinctPrompts() {
        val noHand=signalPresentation(event("type" to "no_hand","target_id" to "coffee"))
        val point=signalPresentation(event("type" to "point","target_id" to "coffee"))
        assertNotEquals(noHand.title,point.title)
        assertEquals(SignalPhase.HAND,noHand.phase)
        assertNull(noHand.targetBox)
    }
    @Test fun geometryMustBeFiniteOrderedAndInsideProcessedFrame() {
        assertEquals(listOf(200f,150f,400f,300f),signalTargetBox(frame))
        listOf(listOf(400,150,200,300),listOf(-1,0,100,100),listOf(0,0,961,720),listOf(0,0,1,Double.NaN),listOf(0,0,1)).forEach {
            assertNull(signalTargetBox(frame+("target_image_box" to it)))
        }
        assertNull(signalTargetBox(frame+("frame_size" to listOf(Double.POSITIVE_INFINITY,720))))
        assertNull(signalTargetBox(frame+("frame_size" to listOf(0,720))))
    }
    @Test fun targetCoordinatesAloneNeverImplyADirection() {
        noArrow(event("type" to "direction","dir" to "unknown","target_id" to "coffee"))
        noArrow(event("type" to "hold","target_id" to "coffee"))
        noArrow(event("type" to "reset","target_id" to "coffee"))
    }
    @Test fun singleFramePressRemainsReadableOnlyWhileFreshHandStillReachesSameTarget() {
        val reach=event("type" to "direction","distance" to "reach","target_id" to "coffee").copy(flow="S5",pressAt=1000,pressAttempt=3)
        assertEquals(SignalPhase.PRESS,signalPresentation(reach).phase)
        assertEquals(SignalPhase.RESULT,signalPresentation(reach.copy(pressAttempt=2)).phase)
        assertEquals(SignalPhase.RESULT,signalPresentation(reach.copy(now=2201)).phase)
        assertEquals(SignalPhase.RESULT,signalPresentation(reach.copy(frame=frame)).phase)
        noArrow(reach.copy(now=2600))
        noArrow(reach.copy(frame=reach.frame+("tip_conf" to .2)))
        noArrow(reach.copy(paused=true))
    }
    @Test fun footerCannotLeakPreviousDirectionsButKeepsOrderErrors() {
        val expired=ready.copy(now=3000)
        assertEquals(signalPresentation(expired).detail,signalStatusMessage(expired,signalPresentation(expired),"오른쪽으로 이동하세요"))
        val failed=ready.copy(flow="SE")
        assertEquals("장바구니 수량을 확인해 주세요",signalStatusMessage(failed,signalPresentation(failed),"장바구니 수량을 확인해 주세요"))
    }
}
