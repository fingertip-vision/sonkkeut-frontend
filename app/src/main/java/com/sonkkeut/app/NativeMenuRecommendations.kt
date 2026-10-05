package com.sonkkeut.app

import kr.sonkkeut.android.MenuDocument
import kr.sonkkeut.android.MenuMatch
import kr.sonkkeut.android.MenuMatcher

/** Suggestions never become an order: the user must enter quantity/options and confirm. */
internal object NativeMenuRecommendations {
    fun suggest(text: String, catalog: List<MenuDocument>, screen: RecognizedScreen?): List<MenuMatch> {
        if(text.isBlank() || text.length>500) return emptyList()
        val observed=screen?.detectedMenu()?.map { it.name }?.toSet() ?: emptySet()
        val query=text.replace(Regex("(?:\\d+|한|두|세|네|하나|둘|셋|넷)\\s*(?:잔|개)|주세요|해줘|해주세요|주문|추천|포장|매장"),"").trim()
        return MenuMatcher(catalog).recommend(query,observed)
    }
    fun draft(candidate: MenuMatch, catalog: List<MenuDocument>): String? =
        candidate.menu.name.takeIf { name -> catalog.any { it.name==name && !it.soldOut } }
}
