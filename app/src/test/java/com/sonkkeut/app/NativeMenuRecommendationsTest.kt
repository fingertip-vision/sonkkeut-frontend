package com.sonkkeut.app

import kr.sonkkeut.android.MenuDocument
import kr.sonkkeut.android.MenuMatch
import kr.sonkkeut.android.MenuMatcher
import org.junit.Assert.*
import org.junit.Test

class NativeMenuRecommendationsTest {
    private val menu=listOf(MenuDocument("아메리카노",listOf("아아"),price=4500),MenuDocument("카페라떼",price=5000),MenuDocument("바닐라라떼",soldOut=true,price=5500))
    private fun screen(vararg names: String)=RecognizedScreen("menu",7,names.mapIndexed { i,name ->
        RecognizedElement("$i","menu",name,listOf(0.1,0.1,0.8,0.3),true)
    },null,null,null)
    private val observed get()=screen(*menu.map { it.name }.toTypedArray())
    @Test fun foodConceptRecommendationsUseOnlyVisibleAvailableCatalogAndStayBounded() {
        val candidates=NativeMenuRecommendations.suggest("커피 두 잔 추천해 주세요",menu,observed)
        assertTrue(candidates.isNotEmpty()); assertTrue(candidates.size<=3)
        assertTrue(candidates.all { it.menu in menu && !it.menu.soldOut })
        assertEquals("domain_concepts",candidates.first().basis)
    }
    @Test fun arbitraryFoodIsNotTurnedIntoCoffeeAndLongInputsAreIgnored() {
        assertTrue(NativeMenuRecommendations.suggest("피자",menu,observed).isEmpty())
        assertTrue(NativeMenuRecommendations.suggest("가".repeat(501),menu,observed).isEmpty())
    }
    @Test fun exactAliasAndSpellingSearchDoNotRequireANetworkModel() {
        assertEquals("아메리카노",MenuMatcher(menu).exact("아아").single().menu.name)
        assertEquals("아메리카노",NativeMenuRecommendations.suggest("아메리카느",menu,observed).first().menu.name)
    }
    @Test fun chosenAlternativeIsAnEditableNameAndNeverInheritsQuantityOrOptions() {
        val candidate=NativeMenuRecommendations.suggest("커피 세 잔",menu,observed).first()
        assertEquals(candidate.menu.name,NativeMenuRecommendations.draft(candidate,menu,observed))
        assertFalse(NativeMenuRecommendations.draft(candidate,menu,observed)!!.contains("잔"))
    }
    @Test fun soldOutRemovedOrNoLongerVisibleCandidateCannotBeSelectedFromAnOldList() {
        val candidate=MenuMatch(menu.first(),1.0,"exact")
        assertNull(NativeMenuRecommendations.draft(candidate,emptyList(),observed))
        assertNull(NativeMenuRecommendations.draft(candidate,listOf(menu.first().copy(soldOut=true)),observed))
        assertNull(NativeMenuRecommendations.draft(candidate,menu,screen("카페라떼")))
        assertNull(NativeMenuRecommendations.draft(candidate,menu,null))
    }
    @Test fun offscreenHighScoringMenuCannotBecomeARecommendation() {
        val candidates=NativeMenuRecommendations.suggest("아메리카노 추천",menu,screen("카페라떼"))
        assertTrue(candidates.all { it.menu.name=="카페라떼" })
        assertEquals(listOf("카페라떼"),NativeMenuRecommendations.suggest("라떼",menu,screen("카페라떼")).map { it.menu.name })
    }
    @Test fun missingNonMenuOrUnregisteredEvidenceNeverFallsBackToCatalog() {
        for(s in listOf(null,observed.copy(type="cart"),screen(),screen("피자"),screen("아메리카느")))
            assertTrue(NativeMenuRecommendations.suggest("커피",menu,s).isEmpty())
    }
    @Test fun unreadableNonMenuOrInvalidRegionIsNotVisibilityEvidence() {
        val element=observed.elements.first()
        val invalid=listOf(element.copy(readable=false),element.copy(kind="label"),element.copy(box=emptyList()),
            element.copy(box=listOf(0.1,0.1,Double.NaN,0.3)),element.copy(box=listOf(-0.1,0.1,0.8,0.3)),
            element.copy(box=listOf(0.8,0.1,0.1,0.3)))
        invalid.forEach { assertTrue(NativeMenuRecommendations.visibleCatalog(menu,observed.copy(elements=listOf(it))).isEmpty()) }
    }
    @Test fun canonicalAndUniqueAliasesAcceptPricesButAmbiguousAliasesAreRejected() {
        assertEquals(listOf(menu.first()),NativeMenuRecommendations.visibleCatalog(menu,screen("아메리카노 4,500원")))
        assertEquals(listOf(menu.first()),NativeMenuRecommendations.visibleCatalog(menu,screen("아아")))
        val ambiguous=menu+MenuDocument("아이스커피",listOf("아아"))
        assertTrue(NativeMenuRecommendations.visibleCatalog(ambiguous,screen("아아")).isEmpty())
        val canonical=ambiguous+MenuDocument("아아")
        assertEquals(listOf("아아"),NativeMenuRecommendations.visibleCatalog(canonical,screen("아아")).map { it.name })
    }
    @Test fun evidenceRequiresLiveCameraMatchingKeyframeAndRecentFrame() {
        assertEquals(observed,NativeMenuRecommendations.current(observed,7,1000,2500,true,true))
        assertNull(NativeMenuRecommendations.current(observed,7,1000,2501,true,true))
        assertNull(NativeMenuRecommendations.current(observed,7,1000,999,true,true))
        assertNull(NativeMenuRecommendations.current(observed,8,1000,1100,true,true))
        assertNull(NativeMenuRecommendations.current(observed,null,1000,1100,true,true))
        assertNull(NativeMenuRecommendations.current(observed,7,0,1100,true,true))
        assertNull(NativeMenuRecommendations.current(observed,7,1000,1100,false,true))
        assertNull(NativeMenuRecommendations.current(observed,7,1000,1100,true,false))
    }
}
