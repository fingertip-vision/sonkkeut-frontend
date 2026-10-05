package com.sonkkeut.app

import kr.sonkkeut.android.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeOrderTest {
    private val menu=listOf(MenuDocument("아메리카노",listOf("아아"),price=4500),MenuDocument("카페라떼",listOf("라떼"),price=5000),MenuDocument("바닐라라떼",price=5500))
    private fun rejects(text: String) { assertThrows(IllegalArgumentException::class.java) { NativeOrderParser.parse(text,menu) } }
    @Test fun multiItemQuantitiesAndTemperaturesStayWithTheirMenu() {
        val value=NativeOrderParser.parse("따뜻한 아메리카노 두 잔하고 아이스 카페라떼 한 잔 포장해 주세요",menu)
        assertEquals(listOf(2,1),value.items.map { it.qty }); assertEquals(listOf("hot","ice"),value.items.map { it.temperature }); assertEquals("포장",value.dine)
        assertTrue(value.confirmation().contains("맞나요"))
    }
    @Test fun exactAliasRetainsItsTemperature() { assertEquals("ice",NativeOrderParser.parse("아아 한 잔 주세요",menu).items.single().temperature) }
    @Test fun unsupportedFoodsAndOptionsAreNotSilentlyRemoved() { listOf("피자 한 개","디카페인 아메리카노 한 잔","아메리카노 두 잔하고 초콜릿 한 개").forEach(::rejects) }
    @Test fun quantitiesAndContradictoryOptionsAreRejected() { listOf("아메리카노 0잔","아메리카노 11잔","아메리카노 1.5잔","아이스 따뜻한 아메리카노 한 잔","아메리카노 한 잔 매장 포장","아메리카노 한 잔 두 잔","아메리카노 라지 스몰").forEach(::rejects) }
    @Test fun soldOutAndAmbiguousAliasRequireAnotherSelection() {
        assertThrows(IllegalArgumentException::class.java) { NativeOrderParser.parse("라떼",listOf(MenuDocument("카페라떼",listOf("라떼")),MenuDocument("바닐라라떼",listOf("라떼")))) }
        assertThrows(IllegalArgumentException::class.java) { NativeOrderParser.parse("아메리카노",listOf(menu.first().copy(soldOut=true))) }
    }
    @Test fun speechRetrievalCorrectsOnlyBoundedMenuAndCounterErrors() {
        val result=MenuSpeechIndex(menu).correct("바닐라떼 한 찬 포장해 주세요")
        assertEquals("바닐라라떼 한 잔 포장해 주세요",result.text)
        assertTrue(result.ambiguities.isEmpty()); assertEquals(1,NativeOrderParser.parse(result.text,menu).items.single().qty)
    }
    @Test fun speechRetrievalPreservesUnregisteredFoodAndUnsupportedOption() {
        val index=MenuSpeechIndex(menu)
        val raw="디카페인 아메리카노 한 잔 피자 하나"
        assertEquals(raw,index.correct(raw).text)
        assertTrue(index.correct("크림 파스타 하나").corrections.isEmpty())
    }
    @Test fun duplicateAliasReturnsCandidatesIncludingSoldOutStatus() {
        val result=MenuSpeechIndex(listOf(MenuDocument("카페라떼",listOf("라떼")),MenuDocument("바닐라라떼",listOf("라떼"),true))).correct("라떼 한 잔")
        assertEquals(2,result.ambiguities.single().candidates.size); assertTrue(result.ambiguities.single().candidates.any { it.menu.soldOut })
    }
    @Test fun quantityWordInsideMenuIsProtectedAndInputBounded() {
        assertEquals("두찬 한 개",MenuSpeechIndex(listOf(MenuDocument("두찬"))).correct("두찬 한 개").text)
        assertThrows(IllegalArgumentException::class.java) { MenuSpeechIndex(menu).correct("가".repeat(501)) }
    }
    @Test fun exactAliasDoesNotSwallowUnknownPrefix() { assertEquals("디카페인라떼",MenuSpeechIndex(menu).correct("디카페인라떼").text) }
    private fun element(id: String,text: String,kind: String="button")=RecognizedElement(id,kind,text,listOf(.1,.1,.5,.5),true)
    private fun screen(type: String,id: Int,vararg e: RecognizedElement,count: Int?=null,total: Int?=null,selected: List<String>?=null)=RecognizedScreen(type,id,e.toList(),count,total,selected)
    @Test fun noTargetIsPlannedUntilExplicitConfirmation() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노 한 잔 포장",menu))
        assertNull(flow.accept(screen("menu",1,element("m1","아메리카노","menu"))))
        assertNull(flow.action); assertEquals("S3",flow.state); assertEquals("m1",flow.confirm()?.target?.id)
    }
    @Test fun optionsAndSingleAddReachVerifiedCartBeforePayment() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아이스 아메리카노 한 잔 포장",menu))
        flow.accept(screen("menu",1,element("m","아메리카노","menu"))); flow.confirm(); flow.press(); flow.verdict("success","changed","성공")
        assertEquals("ice",flow.accept(screen("option",2,element("title","아메리카노","menu"),element("ice","ICE"),element("add","담기"),count=0,selected=emptyList()))?.value)
        flow.press(); flow.verdict("success","changed","성공")
        assertEquals("add",flow.accept(screen("option",3,element("title","아메리카노","menu"),element("ice","ICE"),element("add","담기"),count=0,selected=listOf("ice")))?.role)
        flow.press(); flow.verdict("success","cart_changed","담겼습니다")
        assertEquals("checkout",flow.accept(screen("cart",4,element("pay","결제하기"),count=1,total=4500))?.role)
        flow.press(); flow.verdict("success","changed","성공"); flow.accept(screen("payment",5)); assertEquals("S6",flow.state)
    }
    @Test fun interruptedAddDoesNotOfferAnotherAddWithoutEvidence() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노 한 잔",menu))
        flow.accept(screen("option",1,element("title","아메리카노","menu"),element("add","담기"),count=0)); flow.confirm(); flow.press()
        flow.verdict("unknown","timeout","확인하지 못했습니다"); flow.recover()
        assertNull(flow.accept(screen("option",2,element("title","아메리카노","menu"),element("add","담기"),count=0)))
        assertEquals("SE",flow.state); assertNull(flow.action)
    }
    @Test fun unexpectedOptionItemCannotBeAdded() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노 한 잔",menu))
        flow.accept(screen("option",1,element("title","카페라떼","menu"),element("add","담기"))); assertNull(flow.confirm()); assertEquals("SE",flow.state)
    }
    @Test fun cartMismatchCannotReachCheckout() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노 한 잔",menu))
        flow.accept(screen("option",1,element("title","아메리카노","menu"),element("add","담기"),count=0)); flow.confirm(); flow.press(); flow.verdict("success","cart","성공")
        assertNull(flow.accept(screen("cart",2,element("pay","결제"),count=1,total=5000))); assertEquals("SE",flow.state)
    }
    @Test fun oldScreensAndPausedInputAreIgnored() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노",menu)); flow.accept(screen("menu",5,element("m","아메리카노","menu"))); flow.confirm()
        assertNull(flow.accept(screen("payment",4))); assertEquals("S4",flow.state)
        flow.paused=true; flow.press(); assertEquals("S4",flow.state)
    }
    @Test fun uncertainOcrCannotBecomeUsableTarget() {
        val json=JSONObject("""{"screen_type":"menu","keyframe_id":1,"elements":[{"id":"e","kind":"menu","text":"아메리카노","box":[0,0,1,1],"conf":0.99,"conf_ocr":0.79}]}""")
        val value=RecognizedScreen.from(json); assertFalse(value.elements.single().readable)
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노",menu)); flow.accept(value); assertNull(flow.confirm())
    }
    @Test fun returningFromSettingsRequiresFreshScreenBeforeAnotherTarget() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노",menu))
        flow.accept(screen("menu",5,element("old","아메리카노","menu"))); flow.confirm()
        flow.paused=true; flow.recover(); flow.paused=false
        assertNull(flow.screen); assertNull(flow.plan())
        assertNull(flow.accept(screen("menu",5,element("old","아메리카노","menu"))))
        assertEquals("new",flow.accept(screen("menu",6,element("new","아메리카노","menu")))?.target?.id)
    }
    @Test fun confirmationCanWaitForACompletelyNewCameraFrame() {
        val flow=NativeOrderFlow(); val intent=NativeOrderParser.parse("아메리카노",menu)
        flow.accept(screen("menu",1,element("old","아메리카노","menu"))); flow.recover(); flow.submit(intent)
        assertNull(flow.confirm()); assertNull(flow.action)
        assertEquals("new",flow.accept(screen("menu",2,element("new","아메리카노","menu")))?.target?.id)
    }
    @Test fun progressCountsVerifiedAddsOnlyAndRecoveryRetainsDuplicateAddGuard() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrderParser.parse("아메리카노 두 잔",menu))
        flow.accept(screen("option",1,element("title","아메리카노","menu"),element("add","담기"),count=0)); flow.confirm(); flow.press()
        assertEquals(0,flow.completedQuantity()); flow.recover()
        assertEquals(0,flow.completedQuantity())
        flow.accept(screen("menu",2,element("m","아메리카노","menu"),count=1))
        assertEquals(1,flow.completedQuantity())
    }
    @Test fun offlineDetectedMenuKeepsOnlyReadableMenuNamesAndPrices() {
        val value=RecognizedScreen.from(JSONObject("""{"screen_type":"menu","keyframe_id":1,"elements":[{"id":"a","kind":"menu","text":"아메리카노 4,500원","price":4500,"box":[0,0,1,1],"conf":0.9,"conf_ocr":0.9},{"id":"b","kind":"menu","text":"불확실","box":[0,0,1,1],"conf":0.9,"conf_ocr":0.6},{"id":"c","kind":"button","text":"주문하기","box":[0,0,1,1],"conf":0.9}]}"""))
        assertEquals(listOf("아메리카노"),value.detectedMenu().map { it.name })
        assertEquals(4500,value.detectedMenu().single().price)
        assertEquals(4500,NativeOrderParser.parse("아메리카노 한 잔",value.detectedMenu()).items.single().price)
        assertTrue(value.copy(type="option").detectedMenu().isEmpty())
    }
}
