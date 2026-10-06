package com.sonkkeut.app

import kr.sonkkeut.android.MenuDocument
import kr.sonkkeut.android.MenuMatch
import kr.sonkkeut.android.MenuMatcher

/** Suggestions never become an order: the user must enter quantity/options and confirm. */
internal object NativeMenuRecommendations {
    fun current(screen: RecognizedScreen?, keyframe: Int?, frameAt: Long, now: Long, found: Boolean, active: Boolean): RecognizedScreen? =
        screen?.takeIf { active && found && frameAt>0 && now-frameAt in 0..1500L && it.keyframe==keyframe && it.type=="menu" }

    /** Exact canonical/unique alias evidence only; similarity is never evidence of visibility. */
    fun visibleCatalog(catalog: List<MenuDocument>, screen: RecognizedScreen?): List<MenuDocument> {
        if(screen?.type!="menu") return emptyList()
        val observed=screen.elements.filter { it.kind=="menu" && it.readable && it.box.size==4 &&
            it.box.all { p -> p.isFinite() && p in 0.0..1.0 } && it.box[2]>it.box[0] && it.box[3]>it.box[1] }
            .map { NativeOrderParser.normalize(it.text.replace(Regex("[\\d,]+\\s*원"),"")) }.filter { it.isNotBlank() }
        val names=observed.flatMap { text ->
            val canonical=catalog.filter { NativeOrderParser.normalize(it.name)==text }
            if(canonical.isNotEmpty()) canonical.map { it.name }
            else catalog.filter { item -> item.aliases.any { NativeOrderParser.normalize(it)==text } }
                .takeIf { matches -> matches.map { it.name }.distinct().size==1 }.orEmpty().map { it.name }
        }.toSet()
        return catalog.filter { it.name in names && !it.soldOut }
    }
    fun suggest(text: String, catalog: List<MenuDocument>, screen: RecognizedScreen?): List<MenuMatch> {
        if(text.isBlank() || text.length>500) return emptyList()
        val visible=visibleCatalog(catalog,screen)
        if(visible.isEmpty()) return emptyList()
        val query=text.replace(Regex("(?:\\d+|한|두|세|네|하나|둘|셋|넷)\\s*(?:잔|개)|주세요|해줘|해주세요|주문|추천|포장|매장"),"").trim()
        return MenuMatcher(visible).recommend(query)
    }
    fun draft(candidate: MenuMatch, catalog: List<MenuDocument>, screen: RecognizedScreen?): String? =
        candidate.menu.name.takeIf { name -> visibleCatalog(catalog,screen).any { it.name==name } }
}
