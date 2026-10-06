package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ObservedMenuCatalogTest {
    private fun el(name: String="돈코츠라멘",kind: String="menu",price: Int?=8500,readable: Boolean=true)=RecognizedElement(name,kind,name,listOf(.1,.1,.5,.3),readable,price)
    private fun screen(frame: Int=1,type: String="menu",vararg elements: RecognizedElement)=RecognizedScreen(type,frame,if(elements.isEmpty()) listOf(el()) else elements.toList(),0,null,null)
    private fun learn(c: ObservedMenuCatalog,s: RecognizedScreen=screen()) { c.observe(s); c.observe(s.copy(keyframe=s.keyframe+1)) }
    @Test fun independentFramesAreRequired() { val c=ObservedMenuCatalog(); c.observe(screen()); assertTrue(c.documents.isEmpty()); assertTrue(c.needsAnotherRead); c.observe(screen(2)); assertEquals("돈코츠라멘",c.documents.single().name); assertFalse(c.needsAnotherRead) }
    @Test fun sameFrameCannotCountTwice() { val c=ObservedMenuCatalog(); c.observe(screen()); c.observe(screen()); assertTrue(c.documents.isEmpty()) }
    @Test fun unreadableCannotBecomeMenu() { val c=ObservedMenuCatalog(); learn(c,screen(1,"menu",el(readable=false))); assertTrue(c.documents.isEmpty()) }
    @Test fun invalidBoxCannotBecomeMenu() { val c=ObservedMenuCatalog(); learn(c,screen(1,"menu",el().copy(box=listOf(.5,.1,.1,.3)))); assertTrue(c.documents.isEmpty()) }
    @Test fun buttonsAndNumbersAreNotProducts() { val c=ObservedMenuCatalog(); learn(c,screen(1,"menu",el("담기"),el("8500원"),el("매장","button"))); assertTrue(c.documents.isEmpty()) }
    @Test fun priceAndNameAreKeptWithoutBackend() { val c=ObservedMenuCatalog(); learn(c,screen(1,"menu",el("치즈버거 6,500원",price=null))); assertEquals("치즈버거",c.documents.single().name); assertEquals(6500,c.documents.single().price) }
    @Test fun unreadPriceStaysUnknown() { val c=ObservedMenuCatalog(); learn(c,screen(1,"menu",el(price=null))); assertNull(c.documents.single().price) }
    @Test fun competingPricesAreNotGuessed() { val c=ObservedMenuCatalog(); c.observe(screen(1,"menu",el(price=8000))); c.observe(screen(2,"menu",el(price=8500))); assertNull(c.documents.single().price) }
    @Test fun multiplePagesAccumulateInVisit() { val c=ObservedMenuCatalog(); learn(c); learn(c,screen(3,"menu",el("제육덮밥"))); assertEquals(setOf("돈코츠라멘","제육덮밥"),c.documents.map { it.name }.toSet()) }
    @Test fun optionAndCartRowsAreNotCatalogEntries() { val c=ObservedMenuCatalog(); learn(c,screen(1,"option",el("샷추가"))); learn(c,screen(3,"cart",el("라멘1개"))); assertTrue(c.documents.isEmpty()) }
    @Test fun resetChangesIdentityAndDropsPriorVisit() { val c=ObservedMenuCatalog(); learn(c); val id=c.documents.single().id; c.reset(); assertTrue(c.documents.isEmpty()); learn(c); assertNotEquals(id,c.documents.single().id) }
    @Test fun soldOutIndicatorNeverBecomesAvailableProduct() { val c=ObservedMenuCatalog(); learn(c,screen(1,"menu",el("돈코츠라멘 품절"))); assertEquals("돈코츠라멘",c.documents.single().name); assertTrue(c.documents.single().soldOut) }
    @Test fun duplicateBoxesCannotCountAsIndependentFrames() { val c=ObservedMenuCatalog(); c.observe(screen(1,"menu",el(),el().copy(id="duplicate"))); assertTrue(c.documents.isEmpty()) }
    @Test fun structurePreservesPriceForFieldCatalog() {
        val s=RecognizedScreen.from(JSONObject("""{"screen_type":"menu","keyframe_id":1,"elements":[{"id":"x","kind":"menu","text":"라멘","box":[0.1,0.1,0.5,0.4],"conf":0.95,"conf_ocr":0.9,"uncertain":false,"price":8000}]}"""))
        assertEquals(8000,s.elements.single().price)
    }
    @Test fun fieldOrderCanBeParsedAndLocatedWithoutStoreData() {
        val c=ObservedMenuCatalog(); learn(c); val menus=c.documents; val links=MenuScreenLinks(); links.configure(menus)
        val order=NativeOrderParser.parse("돈코츠라멘 한 개",menus); val flow=NativeOrderFlow(links,true)
        flow.submit(order); flow.accept(screen(3)); assertEquals("돈코츠라멘",flow.confirm()!!.target.text)
        assertEquals(menus.single().id,order.items.single().menuId)
    }
    @Test fun unknownOptionsRequireCompletionAndChangedLayoutRevokesIt() {
        val c=ObservedMenuCatalog(); learn(c); val links=MenuScreenLinks(); links.configure(c.documents)
        val flow=NativeOrderFlow(links,true)
        val options=screen(3,"option",el(),el("면단단하게","button",price=null),el("담기","button",price=null))
        flow.submit(NativeOrderParser.parse("돈코츠라멘 한 개",c.documents)); flow.accept(options)
        assertNull(flow.confirm()); assertEquals("SE",flow.state)
        flow.approveVisibleOptions(options); assertEquals("add",flow.accept(options.copy(keyframe=4))!!.role)
        val changed=options.copy(keyframe=5,elements=options.elements+el("계란추가","button",price=1000))
        assertNull(flow.accept(changed)); assertFalse(flow.visibleOptionsApproved(changed))
    }
    @Test fun explicitNonCafeOptionsAreRecognized() {
        val s=screen(1,"option",el("맵기순한맛","button"),el("맵기매운맛","button"),el("굽기미디엄","button"))
        assertEquals(setOf("맵기","굽기"),ScreenOptions.groups(s).map { it.name }.toSet())
    }
    @Test fun generalFoodNameIsOnlyAChoiceAmongObservedProducts() {
        val docs=listOf(kr.sonkkeut.android.MenuDocument("돈코츠라멘"),kr.sonkkeut.android.MenuDocument("미소라멘"))
        val matcher=kr.sonkkeut.android.MenuMatcher(docs)
        assertTrue(matcher.exact("라멘").isEmpty())
        assertEquals(setOf("돈코츠라멘","미소라멘"),matcher.recommend("라멘").map { it.menu.name }.toSet())
        assertTrue(matcher.recommend("치즈버거").isEmpty())
    }
    @Test fun duplicateUnknownOptionsCannotBeIgnoredOrGuideArbitraryFirstButton() {
        val c=ObservedMenuCatalog(); learn(c); val links=MenuScreenLinks(); links.configure(c.documents)
        val flow=NativeOrderFlow(links,true)
        val s=screen(3,"option",el(),el("보통","button",price=null).copy(id="a"),el("보통","button",price=null).copy(id="b"),el("담기","button",price=null))
        assertEquals(2,ScreenOptions.unclassified(s,"돈코츠라멘").size)
        flow.submit(NativeOrderParser.parse("돈코츠라멘 한 개",c.documents)); flow.accept(s); assertNull(flow.confirm())
        flow.updateExtra("직접선택",ExtraOption("보통",null)); assertNull(flow.accept(s.copy(keyframe=4))); assertNull(flow.action)
    }
}
