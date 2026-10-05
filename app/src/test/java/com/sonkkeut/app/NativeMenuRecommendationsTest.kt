package com.sonkkeut.app

import kr.sonkkeut.android.MenuDocument
import kr.sonkkeut.android.MenuMatch
import kr.sonkkeut.android.MenuMatcher
import org.junit.Assert.*
import org.junit.Test

class NativeMenuRecommendationsTest {
    private val menu=listOf(MenuDocument("아메리카노",listOf("아아"),price=4500),MenuDocument("카페라떼",price=5000),MenuDocument("바닐라라떼",soldOut=true,price=5500))
    @Test fun foodConceptRecommendationsUseOnlyAvailableCatalogAndStayBounded() {
        val candidates=NativeMenuRecommendations.suggest("커피 두 잔 추천해 주세요",menu,null)
        assertTrue(candidates.isNotEmpty()); assertTrue(candidates.size<=3)
        assertTrue(candidates.all { it.menu in menu && !it.menu.soldOut })
        assertEquals("domain_concepts",candidates.first().basis)
    }
    @Test fun arbitraryFoodIsNotTurnedIntoCoffeeAndLongInputsAreIgnored() {
        assertTrue(NativeMenuRecommendations.suggest("피자",menu,null).isEmpty())
        assertTrue(NativeMenuRecommendations.suggest("가".repeat(501),menu,null).isEmpty())
    }
    @Test fun exactAliasAndSpellingSearchDoNotRequireANetworkModel() {
        assertEquals("아메리카노",MenuMatcher(menu).exact("아아").single().menu.name)
        assertEquals("아메리카노",NativeMenuRecommendations.suggest("아메리카느",menu,null).first().menu.name)
    }
    @Test fun chosenAlternativeIsAnEditableNameAndNeverInheritsQuantityOrOptions() {
        val candidate=NativeMenuRecommendations.suggest("커피 세 잔",menu,null).first()
        assertEquals(candidate.menu.name,NativeMenuRecommendations.draft(candidate,menu))
        assertFalse(NativeMenuRecommendations.draft(candidate,menu)!!.contains("잔"))
    }
    @Test fun soldOutOrRemovedCandidateCannotBeSelectedFromAnOldList() {
        val candidate=MenuMatch(menu.first(),1.0,"exact")
        assertNull(NativeMenuRecommendations.draft(candidate,emptyList()))
        assertNull(NativeMenuRecommendations.draft(candidate,listOf(menu.first().copy(soldOut=true))))
    }
}
