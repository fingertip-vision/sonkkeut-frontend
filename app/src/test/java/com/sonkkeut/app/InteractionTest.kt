package com.sonkkeut.app

import kr.sonkkeut.android.*
import org.junit.Assert.*
import org.junit.Test

class InteractionTest {
    private val menu=listOf(MenuDocument("아메리카노"),MenuDocument("카페라떼"),MenuDocument("까르보나라"),MenuDocument("홍차",soldOut=true))
    private fun dialog()=VoiceDialogManager().also { it.begin(VoiceSlot.TEMPERATURE,"온도는요?",mapOf("아이스" to "ice","따뜻하게" to "hot"),true) }
    private fun element(name: String,kind: String="menu",readable: Boolean=true,id: String=name)=RecognizedElement(id,kind,name,listOf(.2,.3,.8,.6),readable)
    private fun screen(k: Int,vararg elements: RecognizedElement,type: String="menu")=RecognizedScreen(type,k,elements.toList(),0,null,null)
    @Test fun unknownConfidenceRequiresExplicitConfirmation() {
        val d=dialog(); val answer=d.input("아이스")
        assertNull(answer.accepted); assertEquals(DialogState.VERIFYING,d.state)
        assertEquals("ice",d.input("네").accepted)
    }
    @Test fun highConfidenceCanImplicitlyConfirmOnlyExactContextChoice() {
        assertEquals("ice",dialog().input("아이스",SpeechEvidence(probability=.96)).accepted)
        assertNull(dialog().input("피자",SpeechEvidence(probability=.99)).accepted)
        assertNull(dialog().input("아이스",SpeechEvidence(probability=.99,noSpeech=.9)).accepted)
    }
    @Test fun whisperScoreIsNotConfusedWithProbability() {
        assertEquals("ice",dialog().input("아이스",SpeechEvidence(decoderScore=-.1)).accepted)
        assertNull(dialog().input("아이스",SpeechEvidence(decoderScore=-2.0)).accepted)
        assertNull(dialog().input("아이스",SpeechEvidence(probability=-1.0)).accepted)
        assertNull(dialog().input("아이스",SpeechEvidence(probability=Double.NaN)).accepted)
    }
    @Test fun rejectionAndImmediateReplacementNeverAcceptPreviousChoice() {
        val d=dialog(); d.input("아이스"); assertNull(d.input("아니요").accepted)
        d.input("아이스"); assertNull(d.input("따뜻하게").accepted); assertEquals("hot",d.input("맞아요").accepted)
    }
    @Test fun wordContainingYesDoesNotAccidentallyConfirm() { val d=dialog(); d.input("아이스"); assertNull(d.input("네개 주세요").accepted) }
    @Test fun staleTtsCompletionDoesNotOpenMicrophoneAfterScreenTransition() {
        val d=dialog(); val turn=d.input("아이스"); d.begin(VoiceSlot.SIZE,"크기는요?",mapOf("라지" to "라지"))
        assertFalse(d.afterPrompt(turn.generation)); assertNull(d.input("아이스").accepted)
    }
    @Test fun retriesOfferChoicesAndCanRestartOrCancelWithVoice() {
        val d=dialog(); repeat(3) { d.input("") }; assertEquals(DialogState.FALLBACK,d.state)
        assertTrue(d.prompt.contains("다시 또는 취소")); d.input("다시"); assertEquals(0,d.retries)
        val cancelled=d.input("취소"); assertTrue(cancelled.stopped); assertFalse(d.afterPrompt(cancelled.generation))
    }
    @Test fun recommendationDecisionNeedsOneExplicitYesEvenWithNoScore() {
        val d=VoiceDialogManager(); d.begin(VoiceSlot.RECOMMENDATION,"라떼로 바꿀까요?",mapOf("네" to "네","아니요" to "아니요"))
        assertEquals("네",d.input("네").accepted)
    }
    @Test fun unknownMenuIsConfirmedBeforeItCanTriggerFallback() {
        val d=VoiceDialogManager(); d.begin(VoiceSlot.MENU,"메뉴 이름은요?",mapOf("아메리카노" to "아메리카노"),true)
        assertNull(d.input("크림 파스타",SpeechEvidence(probability=.99)).accepted)
        assertEquals("크림파스타",d.input("네").accepted)
    }
    @Test fun domainConceptRecommendationIsExplicitAndExcludesSoldOutMenus() {
        val matcher=MenuMatcher(menu)
        assertEquals("까르보나라",matcher.recommend("크림 파스타").first().menu.name)
        assertTrue(matcher.exact("크림 파스타").isEmpty()); assertTrue(matcher.recommend("홍차").none { it.menu.soldOut })
    }
    @Test fun duplicateAliasDoesNotReturnOneCertainMatch() {
        val matcher=MenuMatcher(listOf(MenuDocument("라떼A",listOf("라떼")),MenuDocument("라떼B",listOf("라떼"))))
        assertEquals(2,matcher.exact("라떼").size)
    }
    @Test fun freshViewportIsRequiredAfterScroll() {
        val nav=ScreenNavigator(); assertEquals(DetectionStatus.SCROLL,nav.findMenuItem("라떼",screen(1,element("아메리카노")),0).status)
        assertEquals(DetectionStatus.WAITING_CHANGE,nav.findMenuItem("라떼",screen(2,element("아메리카노")),1000).status)
        assertEquals(DetectionStatus.FOUND,nav.findMenuItem("라떼",screen(3,element("라떼")),2000).status)
    }
    @Test fun categoryOnlyViewportSwitchesTabWithoutInventingScrollArea() {
        val nav=ScreenNavigator()
        val result=nav.findMenuItem("라떼",screen(1,element("음료","tab"),type="category"),0)
        assertEquals(DetectionStatus.SWITCH_TAB,result.status); assertEquals("음료",result.target!!.text)
    }
    @Test fun scrollThenTabsStopAfterBoundedExploration() {
        val nav=ScreenNavigator(1,1); val els=arrayOf(element("아메리카노"),element("음료","tab"))
        assertEquals(DetectionStatus.SCROLL,nav.findMenuItem("피자",screen(1,*els),0).status)
        assertEquals(DetectionStatus.SWITCH_TAB,nav.findMenuItem("피자",screen(2,*els),16000).status)
        nav.findMenuItem("피자",screen(3,*els),32000)
        assertEquals(DetectionStatus.NOT_FOUND_IN_VIEWPORT,nav.findMenuItem("피자",screen(4,*els),48000).status)
    }
    @Test fun uncertainOrOverlappingDuplicateMenuCannotBeSelected() {
        val nav=ScreenNavigator()
        assertEquals(DetectionStatus.UNREADABLE,nav.findMenuItem("라떼",screen(1,element("라떼",readable=false)),0).status)
        assertNotEquals(DetectionStatus.FOUND,nav.findMenuItem("라떼",screen(2,element("라떼",id="a"),element("라떼",id="b")),1000).status)
    }
    @Test fun swipeCompletionNeedsFingerMotionAndStillRequiresVisualChange() {
        val nav=ScreenNavigator(); nav.findMenuItem("라떼",screen(1,element("아메리카노")),0)
        assertTrue(nav.swipeHint(listOf(.5,.54),1000)!!.contains("터치"))
        assertTrue(nav.swipeHint(listOf(.5,.35),2000)!!.contains("손을 떼어"))
        assertNotEquals(DetectionStatus.FOUND,nav.findMenuItem("라떼",screen(2,element("아메리카노")),3000).status)
    }
    @Test fun closeupCanResolveFailureButDoesNotProvideActionCoordinates() {
        val v=VisionVerifier(); v.begin("아메리카노",0)
        val scan=DetailScan(listOf(DetailLine("아메리카노 4,500원",.99,listOf(.1,.2,.3,.4),0)),1,20,false)
        assertEquals(VisionFinding.RESOLVED_BY_CLOSEUP,v.accept(scan,100)); assertFalse(v.active)
    }
    @Test fun missingOrUnreadableCloseupIsNotProofOfAbsence() {
        val v=VisionVerifier(); v.begin("아메리카노",0)
        repeat(3) { v.accept(DetailScan(emptyList(),0,10,true),100) }
        assertEquals(VisionFinding.NOT_OBSERVED,v.finding); assertEquals(0,v.reads)
        assertEquals(VisionFinding.CATALOG_NOT_LISTED,v.absenceInCatalog("피자",setOf("라떼")))
    }
    @Test fun timeoutAndCancellationAreExplicitScanOutcomes() {
        val v=VisionVerifier(1000); v.begin("라떼",0); assertTrue(v.expire(1000)); assertEquals(VisionFinding.TIMED_OUT,v.finding)
        v.begin("라떼",0); v.cancel(); assertEquals(VisionFinding.CANCELLED,v.finding)
    }
    @Test fun stepOptionsUpdateCurrentItemWithoutRestartingOrAddingAnotherItem() {
        val f=NativeOrderFlow(); f.submit(NativeOrder(listOf(NativeOrderItem("라떼",2,5000)),null)); f.confirm()
        f.updateChoice("dine","포장"); f.updateChoice("temperature","ice"); f.updateChoice("size","라지")
        assertEquals(2,f.order!!.items.single().qty); assertEquals("ice",f.currentItem()!!.temperature); assertEquals("포장",f.order!!.dine); assertFalse(f.allAdded())
    }
}
