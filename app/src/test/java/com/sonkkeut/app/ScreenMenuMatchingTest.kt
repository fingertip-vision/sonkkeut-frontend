package com.sonkkeut.app

import kr.sonkkeut.android.*
import org.junit.Assert.*
import org.junit.Test

class ScreenMenuMatchingTest {
    private val pasta=MenuDocument("까르보나라",listOf("크림 파스타"),price=9000,id="store-A:1")
    private val box=listOf(.1,.1,.5,.4)
    private fun region(text: String,id: String="menu-1",kind: String="menu",b: List<Double> = box,readable: Boolean=true)=MenuTextRegion(id,kind,text,b,readable)
    private fun resolve(vararg r: MenuTextRegion,docs: List<MenuDocument> = listOf(pasta))=ScreenMenuResolver(docs).resolve(pasta.id,r.toList())
    private fun screen(text: String,frame: Int=1,b: List<Double> = box,type: String="menu")=RecognizedScreen(type,frame,listOf(RecognizedElement("menu-1","menu",text,b,true)),0,null,null)
    @Test fun canonicalPriceAndWhitespace() { assertEquals(ScreenMenuStatus.FOUND,resolve(region("까르보\n나라 9,000 원")).status) }
    @Test fun registeredAliasIsSameProduct() { assertEquals(ScreenMenuStatus.FOUND,resolve(region("크림 파스타")).status) }
    @Test fun damagedCharacterRequiresExplicitConfirmation() { assertEquals(ScreenMenuStatus.CONFIRM,resolve(region("까르보나랴")).status) }
    @Test fun genericRelatedConceptDoesNotProveButtonIdentity() {
        val m=pasta.copy(aliases=emptyList(),relatedTerms=listOf("크림 파스타"))
        assertEquals(ScreenMenuStatus.MISSING,resolve(region("크림파스타"),docs=listOf(m)).status)
    }
    @Test fun sharedAliasIsAmbiguous() { assertEquals(ScreenMenuStatus.AMBIGUOUS,resolve(region("크림파스타"),docs=listOf(pasta,MenuDocument("버섯크림",listOf("크림파스타"),id="store-A:2"))).status) }
    @Test fun duplicateVisibleLocationsAreAmbiguous() { assertEquals(ScreenMenuStatus.AMBIGUOUS,resolve(region(pasta.name),region(pasta.name,"menu-2")).status) }
    @Test fun canonicalCompetitorShadowsAliasEvenWhenSoldOut() {
        assertEquals(ScreenMenuStatus.MISSING,resolve(region("크림파스타"),docs=listOf(pasta,MenuDocument("크림파스타",soldOut=true,id="store-A:2"))).status)
    }
    @Test fun similarCompetingNamesAreAmbiguous() {
        assertEquals(ScreenMenuStatus.AMBIGUOUS,resolve(region("까르보나랴"),docs=listOf(pasta,MenuDocument("까르보나리",id="store-A:2"))).status)
    }
    @Test fun qualifiersCannotDisappearThroughTypoCorrection() {
        listOf("제로콜라" to "콜라","핫카페라떼" to "카페라떼","라지파스타" to "파스타","디카페인라떼" to "카페인라떼").forEach { (name,text) ->
            val m=MenuDocument(name,id="x")
            assertEquals(ScreenMenuStatus.MISSING,ScreenMenuResolver(listOf(m)).resolve("x",listOf(region(text))).status)
        }
    }
    @Test fun unreadableOrInvalidGeometryNeverSelects() {
        assertEquals(ScreenMenuStatus.MISSING,resolve(region(pasta.name,readable=false)).status)
        assertEquals(ScreenMenuStatus.MISSING,resolve(region(pasta.name,b=listOf(.5,.1,.1,.4))).status)
    }
    @Test fun containedSplitLinesKeepExistingOwnerAndRequireConfirmation() {
        val m=MenuDocument("베이컨크림파스타",id="x")
        val r=ScreenMenuResolver(listOf(m)).resolve("x",listOf(region("","owner"),region("베이컨","a","text",listOf(.15,.15,.4,.2)),region("크림파스타","b","text",listOf(.15,.22,.4,.3))))
        assertEquals(ScreenMenuStatus.CONFIRM,r.status); assertEquals("owner",r.candidate!!.regionId)
    }
    @Test fun independentCardsCannotBeJoined() {
        val m=MenuDocument("베이컨크림파스타",id="x")
        assertEquals(ScreenMenuStatus.MISSING,ScreenMenuResolver(listOf(m)).resolve("x",listOf(region("베이컨","a"),region("크림파스타","b",b=listOf(.6,.1,.9,.4)))).status)
    }
    @Test fun staleStoreIdentityCannotFindSameName() {
        val links=MenuScreenLinks(); links.configure(listOf(pasta))
        assertEquals(ScreenMenuStatus.MISSING,links.resolve(pasta.name,"store-B:1",screen(pasta.name)).status)
    }
    @Test fun approvalIsBoundToObservedLayoutAndCatalog() {
        val links=MenuScreenLinks(); links.configure(listOf(pasta)); val s=screen("까르보나랴")
        val candidate=links.resolve(pasta.name,pasta.id,s).candidate!!
        assertFalse(links.approve(pasta.name,pasta.id,s.copy(elements=s.elements.map { it.copy(box=listOf(.2,.2,.6,.5)) }),candidate,links.signature(s)))
        assertTrue(links.approve(pasta.name,pasta.id,s,candidate,links.signature(s)))
        assertEquals(ScreenMenuStatus.FOUND,links.resolve(pasta.name,pasta.id,s.copy(keyframe=2)).status)
        assertEquals(ScreenMenuStatus.CONFIRM,links.resolve(pasta.name,pasta.id,s.copy(elements=s.elements.map { it.copy(box=listOf(.2,.2,.6,.5)) })).status)
        links.configure(listOf(pasta.copy(price=10000)))
        assertEquals(ScreenMenuStatus.CONFIRM,links.resolve(pasta.name,pasta.id,s).status)
    }
    @Test fun flowDoesNotGuideTypoUntilApproved() {
        val links=MenuScreenLinks(); links.configure(listOf(pasta)); val flow=NativeOrderFlow(links)
        val s=screen("까르보나랴"); flow.submit(NativeOrder(listOf(NativeOrderItem(pasta.name,1,9000,menuId=pasta.id)),null)); flow.accept(s)
        assertNull(flow.confirm()); assertEquals("SM",flow.state); assertNull(flow.action)
        val c=links.resolve(pasta.name,pasta.id,s).candidate!!
        assertTrue(links.approve(pasta.name,pasta.id,s,c,links.signature(s)))
        assertEquals("menu-1",flow.accept(s.copy(keyframe=2))!!.target.id)
    }
    @Test fun optionScreenUsesSameAliasIdentity() {
        val links=MenuScreenLinks(); links.configure(listOf(pasta)); val flow=NativeOrderFlow(links)
        val s=screen("크림파스타",type="option").copy(elements=screen("크림파스타").elements+RecognizedElement("add","button","담기",listOf(.1,.6,.5,.8),true))
        flow.submit(NativeOrder(listOf(NativeOrderItem(pasta.name,1,9000,menuId=pasta.id)),null)); flow.accept(s)
        assertEquals("add",flow.confirm()!!.target.id)
    }
    @Test fun navigatorStopsExplorationAtUnconfirmedCandidate() {
        val links=MenuScreenLinks(); links.configure(listOf(pasta))
        assertEquals(DetectionStatus.CONFIRM_MATCH,ScreenNavigator(links=links).findMenuItem(pasta.name,screen("까르보나랴"),0).status)
    }
    @Test fun speechOrderRetainsCatalogIdentity() {
        assertEquals(pasta.id,NativeOrderParser.parse("크림파스타 한 개",listOf(pasta)).items.single().menuId)
    }
    @Test fun confirmationQuestionAcceptsOnlyExplicitYesOrNo() {
        val d=VoiceDialogManager(); d.begin(VoiceSlot.SCREEN_MENU_CONFIRM,"이 상품이 맞나요?",mapOf("네" to "네","아니요" to "아니요"))
        assertNull(d.input("까르보나라").accepted)
        assertEquals("아니요",d.input("아니요").accepted)
    }
    @Test fun textWithoutDetectedButtonCannotStartGuidance() {
        val links=MenuScreenLinks(); links.configure(listOf(pasta)); val s=screen(pasta.name).copy(elements=screen(pasta.name).elements.map { it.copy(kind="text") })
        assertEquals(DetectionStatus.UNREADABLE,ScreenNavigator(links=links).findMenuItem(pasta.name,s,0).status)
        val flow=NativeOrderFlow(links); flow.submit(NativeOrder(listOf(NativeOrderItem(pasta.name,1,9000,menuId=pasta.id)),null)); flow.accept(s)
        assertNull(flow.confirm()); assertEquals("SM",flow.state)
    }
}
