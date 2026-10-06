package com.sonkkeut.app

import kr.sonkkeut.android.*
import org.junit.Assert.*
import org.junit.Test

class KnowledgeCheckoutTest {
    private fun screen(id: Int,vararg text: String)=RecognizedScreen("cart",id,text.mapIndexed { i,s -> RecognizedElement("$i","button",s,listOf(.1,.1,.8,.2),true) },2,6000,null)
    private val order=NativeOrder(listOf(NativeOrderItem("커피",2,3000)),null)
    @Test fun relatedTermRetrievesActualMenuWithoutBecomingExactAlias() {
        val m=MenuMatcher(listOf(MenuDocument("까르보나라",relatedTerms=listOf("고소한 면 요리"))))
        assertTrue(m.exact("고소한 면 요리").isEmpty())
        val hit=m.recommend("고소한 면 요리").single()
        assertEquals("까르보나라",hit.menu.name); assertEquals("store_knowledge",hit.basis)
    }
    @Test fun metadataNeverIntroducesUnavailableOrInventedMenu() {
        assertTrue(MenuMatcher(listOf(MenuDocument("까르보나라",soldOut=true,relatedTerms=listOf("고소한 면 요리")))).recommend("고소한 면 요리").isEmpty())
        assertTrue(MenuMatcher(emptyList()).recommend("크림 파스타").isEmpty())
    }
    @Test fun descriptionsAndCategoriesAreSearchableButNeverExactMatches() {
        val m=MenuMatcher(listOf(MenuDocument("오늘의 추천",category="면요리",description="고소한 버섯 소스로 만든 메뉴")))
        assertEquals("오늘의 추천",m.recommend("면요리").single().menu.name)
        assertEquals("오늘의 추천",m.recommend("버섯 소스").single().menu.name)
        assertTrue(m.exact("면요리").isEmpty())
    }
    @Test fun readableCartItemQuantityIsCompared() {
        val okay=CartReader.inspect(order,screen(1,"커피 2개 6,000원"))
        assertTrue(okay.complete); assertFalse(okay.mismatch)
        assertTrue(CartReader.inspect(order,screen(2,"커피 1개 3,000원")).mismatch)
    }
    @Test fun separatedNumbersAndRepeatedRowsAreNotGuessed() {
        assertFalse(CartReader.inspect(order,screen(1,"커피","2개","6,000원")).complete)
        val duplicate=CartReader.inspect(order,screen(2,"커피 1개","커피 1개"))
        assertFalse(duplicate.complete); assertFalse(duplicate.mismatch)
    }
    @Test fun optionsAndUncertainRowsPreventFullVerificationClaim() {
        assertFalse(CartReader.inspect(order.copy(items=listOf(order.items.single().copy(temperature="ice"))),screen(1,"커피 2개")).complete)
        val uncertain=screen(1,"커피 2개").let { it.copy(elements=it.elements.map { e -> e.copy(readable=false) }) }
        assertFalse(CartReader.inspect(order,uncertain).complete)
    }
    @Test fun anotherMenusSubstringIsNotAnOrderLine() {
        val result=CartReader.inspect(order,screen(1,"바닐라커피 1개"))
        assertFalse(result.complete); assertFalse(result.mismatch)
    }
    @Test fun receiptNeedsStableNewFramesAndPreservesLeadingZeroes() {
        val r=ReceiptReader(); val s=screen(1,"결제 완료","주문 번호: 0012","수령장소: 2번 창구")
        assertNull(r.observe(s)); assertNull(r.observe(s)); val result=r.observe(s.copy(keyframe=2))!!
        assertEquals("0012",result.orderNumber); assertTrue(result.speech().contains("0 0 1 2")); assertNotNull(result.pickupText)
    }
    @Test fun paymentInstructionsAndFailuresAreNotCompletion() {
        for(text in listOf("결제 완료 후 영수증을 받으세요","결제 진행 중","카드 삽입")) {
            val r=ReceiptReader(); assertNull(r.observe(screen(1,text))); assertNull(r.observe(screen(2,text)))
        }
        val r=ReceiptReader(); assertNull(r.observe(screen(1,"결제 완료","결제 취소"))); assertNull(r.observe(screen(2,"결제 완료","결제 취소")))
    }
    @Test fun ConflictingNumbersAreNeverChosenAndMissingNumberIsDisclosed() {
        val r=ReceiptReader(); val s=screen(1,"주문 완료","주문번호:12","주문번호:13")
        r.observe(s); val result=r.observe(s.copy(keyframe=2))!!
        assertNull(result.orderNumber); assertTrue(result.speech().contains("주문 번호는 확인하지 못했습니다"))
    }
    @Test fun ChangedNumberAndResetNeedNewEvidence() {
        val r=ReceiptReader(); r.observe(screen(1,"결제 완료","주문번호:1"))
        assertNull(r.observe(screen(2,"결제 완료","주문번호:2")))
        assertEquals("2",r.observe(screen(3,"결제 완료","주문번호:2"))!!.orderNumber)
        r.reset(); assertNull(r.observe(screen(4,"결제 완료","주문번호:2")))
    }
    @Test fun completedGuidanceCannotResumeKioskActionsAfterReading() {
        val flow=NativeOrderFlow(); flow.submit(order); flow.confirm(); flow.finishGuidance(); flow.paused=false
        assertNull(flow.accept(screen(10,"커피 2개","결제하기"))); assertNull(flow.plan()); assertNull(flow.confirm())
        assertEquals("S6",flow.state)
    }
}
