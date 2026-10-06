package com.sonkkeut.app

import kr.sonkkeut.core.Pt
import kr.sonkkeut.core.Mat3
import kr.sonkkeut.core.UNIT_SQUARE
import org.junit.Assert.*
import org.junit.Test

class OcrRegionPresentationTest {
    private val screen=RecognizedScreen("unknown",3,listOf(
        RecognizedElement("read","menu","아메리카노",listOf(.2,.3,.6,.5),true),
        RecognizedElement("uncertain","button","담기",listOf(.2,.6,.5,.8),false),
        RecognizedElement("blank","button","",listOf(.1,.1,.2,.2),true)
    ),null,null,null)
    private val frame=mapOf<String,Any?>("found" to true,"keyframe_id" to 3,"frame_size" to listOf(1000,800),
        "corners" to listOf(100,100,900,100,900,700,100,700))
    private fun state(s: RecognizedScreen?=screen,f: Map<String,Any?> = frame,now: Long=1100,until: Long=6000,
                      active: Boolean=true,screenAt: Long=1000,frameAt: Long=1000) =
        OcrRegionPresentation.state(s,screenAt,f,frameAt,now,until,active)
    @Test fun showsOnlyTextBearingRegionsAndPreservesConfidenceStyle() {
        val result=state()
        assertTrue(result.requested); assertEquals(2,result.regions.size)
        assertTrue(result.regions.first().readable); assertFalse(result.regions.last().readable)
        assertEquals(listOf(Pt(260.0,280.0),Pt(580.0,280.0),Pt(580.0,400.0),Pt(260.0,400.0)),result.regions.first().points)
        assertTrue(result.status.contains("2개"))
    }
    @Test fun perspectiveFollowsCurrentTrackedCornersRatherThanAxisAlignedEnclosingBox() {
        val quad=listOf(Pt(100.0,100.0),Pt(800.0,150.0),Pt(900.0,700.0),Pt(200.0,650.0))
        val moved=frame+("corners" to quad.flatMap { listOf(it.x,it.y) })
        val actual=state(f=moved).regions.first().points
        val expected=Mat3.perspective(UNIT_SQUARE,quad).apply(Pt(.2,.3))
        assertEquals(expected.x,actual.first().x,.0001); assertEquals(expected.y,actual.first().y,.0001)
        assertNotEquals(actual[0].y,actual[1].y)
    }
    @Test fun displayIsOptInAndExpiresWithoutFrames() {
        assertFalse(state(until=0).requested)
        assertFalse(state(now=6000).requested)
        assertFalse(state(active=false).requested)
        assertTrue(state(now=5999,frameAt=5999,screenAt=5999).requested)
    }
    @Test fun lostPausedMismatchedOrStaleEvidenceCannotDraw() {
        assertTrue(state(s=null).regions.isEmpty())
        assertTrue(state(f=frame+("found" to false)).regions.isEmpty())
        assertTrue(state(f=frame+("keyframe_id" to 4)).regions.isEmpty())
        assertTrue(state(f=frame-("keyframe_id")).regions.isEmpty())
        assertTrue(state(now=2501).regions.isEmpty())
        assertTrue(state(now=5501,frameAt=5501,screenAt=1).regions.isEmpty())
        assertTrue(state(screenAt=0).regions.isEmpty())
        assertTrue(state(now=999).regions.isEmpty())
        assertTrue(state(active=false).regions.isEmpty())
    }
    @Test fun invalidPlaneDimensionsAndElementBoundsAreRejected() {
        for(corners in listOf(emptyList(),listOf(0,0,0,0,0,0,0,0),listOf(100,100,900,700,900,100,100,700),
            listOf(-1,100,900,100,900,700,100,700),listOf(Double.NaN,100,900,100,900,700,100,700)))
            assertTrue(state(f=frame+("corners" to corners)).regions.isEmpty())
        assertTrue(state(f=frame+("frame_size" to listOf(0,800))).regions.isEmpty())
        for(box in listOf(emptyList(),listOf(.5,.2,.1,.4),listOf(-.1,.2,.5,.4),listOf(.1,.2,Double.NaN,.4)))
            assertTrue(state(s=screen.copy(elements=listOf(screen.elements.first().copy(box=box)))).regions.isEmpty())
    }
    @Test fun emptyResultExplainsMissingEvidenceInsteadOfClaimingSuccessfulOcr() {
        val result=state(s=screen.copy(elements=emptyList()))
        assertTrue(result.requested); assertTrue(result.regions.isEmpty())
        assertTrue(result.status.contains("없습니다"))
    }
}
