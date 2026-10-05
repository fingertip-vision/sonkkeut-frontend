package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test
import kr.sonkkeut.android.MenuDocument
import kr.sonkkeut.android.MenuMatcher

class OrderExtensionsTest {
    private fun item(name: String="커피",qty: Int=1)=NativeOrderItem(name,qty,3000)
    private fun button(text: String,id: String=text)=RecognizedElement(id,"button",text,listOf(.1,.2,.7,.4),true)
    private fun options(vararg elements: RecognizedElement)=RecognizedScreen("option",1,elements.toList(),0,null,null)
    @Test fun editsAndUndoPreserveOtherItemsAndTheirOptions() {
        val draft=OrderDraft(); draft.add(item().copy(temperature="ice")); draft.add(item("홍차",2))
        draft.edit("1번 3개로 변경"); assertEquals(listOf(3,2),draft.items.map { it.qty })
        draft.edit("2번 삭제"); assertEquals("ice",draft.items.single().temperature)
        assertTrue(draft.undo()); assertEquals(listOf(3,2),draft.items.map { it.qty })
        assertTrue(draft.undo()); assertEquals(listOf(1,2),draft.items.map { it.qty })
    }
    @Test fun duplicateNamesRequireAnItemNumber() {
        val draft=OrderDraft(); draft.add(item()); draft.add(item().copy(temperature="hot"))
        assertFalse(draft.choices().containsKey("커피 삭제")); assertEquals("2번 삭제",draft.choices()["두번째 삭제"])
    }
    @Test fun boundsAndEmptyDraftPreventInvalidOrder() {
        val draft=OrderDraft(); assertFalse(draft.undo()); assertFalse(draft.choices().containsKey("주문 시작"))
        draft.add(item()); assertThrows(IllegalArgumentException::class.java) { draft.quantity(0,0) }
        assertThrows(IllegalArgumentException::class.java) { draft.quantity(0,11) }
        assertThrows(IllegalArgumentException::class.java) { draft.remove(4) }
        assertEquals(1,draft.items.single().qty)
        repeat(9) { draft.add(item()) }; assertThrows(IllegalArgumentException::class.java) { draft.add(item()) }
        assertEquals(10,draft.items.size)
    }
    @Test fun voiceEditsWaitForConfirmationAndRejectionLeavesNoAcceptedEdit() {
        val draft=OrderDraft(); draft.add(item())
        val dialog=VoiceDialogManager(); dialog.begin(VoiceSlot.DRAFT,"목록",draft.choices(),true)
        assertNull(dialog.input("커피 하나 더",SpeechEvidence(probability=.99)).accepted)
        assertNull(dialog.input("아니요").accepted)
        dialog.input("첫번째 삭제"); assertEquals("1번 삭제",dialog.input("네").accepted)
    }
    @Test fun alwaysConfirmOverridesHighScoresAndDoesNotAffectExplicitYesNo() {
        val d=VoiceDialogManager(); d.alwaysConfirm=true
        d.begin(VoiceSlot.MENU,"메뉴",mapOf("커피" to "커피"),true)
        assertNull(d.input("커피",SpeechEvidence(probability=.99)).accepted)
        d.begin(VoiceSlot.DRAFT_CONFIRM,"시작할까요",mapOf("네" to "네"))
        assertEquals("네",d.input("네").accepted)
    }
    @Test fun optionsComeOnlyFromVisibleReadableSupportedButtons() {
        val groups=ScreenOptions.groups(options(button("당도 50%"),button("당도 100%"),button("얼음 적게"),button("1샷 추가 (+500원)"),button("토핑"),button("당도 0%").copy(readable=false)))
        assertEquals(listOf("당도","얼음","샷"),groups.map { it.name })
        assertEquals(2,groups.first().choices.size); assertEquals(500,groups.last().choices.single().surcharge)
        assertNull(groups.first().choices.first().surcharge)
        assertTrue(ScreenOptions.groups(options(button("당도 50%")).copy(type="menu")).isEmpty())
    }
    @Test fun duplicateOrInvalidBoxesCannotBeOptionTargets() {
        assertTrue(ScreenOptions.groups(options(button("얼음 적게","a"),button("얼음 적게","b"),button("당도 50%").copy(box=listOf(0.0,0.0,0.0,1.0)))).isEmpty())
    }
    @Test fun knownExtraPricesMultiplyPerItemAndUnknownPriceIsNotAssumedFree() {
        val base=item(qty=2).copy(extras=mapOf("샷" to ExtraOption("1샷 추가 +500원",500)))
        assertEquals(7000,NativeOrder(listOf(base),null).expectedTotal())
        assertNull(NativeOrder(listOf(base.copy(extras=base.extras+("얼음" to ExtraOption("얼음 적게",null)))),null).expectedTotal())
    }
    @Test fun extraChoiceIsAppliedBeforeAddAndCountIsPreserved() {
        val flow=NativeOrderFlow(); flow.submit(NativeOrder(listOf(item(qty=2)),null))
        flow.accept(options(button("커피"),button("1샷 추가 +500원"),button("담기")))
        flow.updateExtra("샷",ExtraOption("1샷 추가 +500원",500))
        // updateExtra is normally called on a confirmed flow by the conversation coordinator.
        flow.submit(flow.order!!); flow.confirm()
        assertEquals("option",flow.action?.role); assertEquals("1샷 추가 +500원",flow.action?.value)
        assertEquals(2,flow.currentItem()!!.qty)
        flow.press(); flow.verdict("success","changed","선택됨")
        assertEquals("add",flow.accept(options(button("커피"),button("1샷 추가 +500원"),button("담기")).copy(keyframe=2))?.role)
    }
    @Test fun candidateNumberStillNeedsAnExplicitConfirmation() {
        val d=VoiceDialogManager(); d.begin(VoiceSlot.MENU_CANDIDATE,"후보",mapOf("1번" to "까르보나라","2번" to "크림리조또"),true)
        assertNull(d.input("2번",SpeechEvidence(probability=.99)).accepted)
        assertEquals("크림리조또",d.input("네").accepted)
    }
    @Test fun canonicalNameWinsOverAnotherProductsAlias() {
        val matcher=MenuMatcher(listOf(MenuDocument("라떼"),MenuDocument("바닐라라떼",listOf("라떼"))))
        assertEquals("라떼",matcher.exact("라떼").single().menu.name)
        assertTrue(MenuMatcher(listOf(MenuDocument("라떼",soldOut=true),MenuDocument("바닐라라떼",listOf("라떼")))).exact("라떼").isEmpty())
    }
    @Test fun nextMenuGetsItsOwnSearchBudget() {
        val navigator=ScreenNavigator()
        val screen=RecognizedScreen("menu",1,listOf(button("커피").copy(kind="menu")),0,null,null)
        assertEquals(DetectionStatus.SCROLL,navigator.findMenuItem("홍차",screen,0).status)
        assertEquals(DetectionStatus.NOT_FOUND_IN_VIEWPORT,navigator.findMenuItem("홍차",screen.copy(keyframe=2),90001).status)
        assertEquals(DetectionStatus.SCROLL,navigator.findMenuItem("우유",screen.copy(keyframe=3),90002).status)
    }
}
