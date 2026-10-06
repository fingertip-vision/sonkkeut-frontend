package com.sonkkeut.app

import kr.sonkkeut.android.MenuDocument
import kr.sonkkeut.android.ScreenMenuResolver
import java.util.UUID

/** Visit-scoped observations, never a list of products assumed to exist at an unknown store. */
class ObservedMenuCatalog {
    private data class Observation(val name: String,var reads: Int=0,val prices: MutableSet<Int> = mutableSetOf(),var soldOut: Boolean=false)
    private val observations=linkedMapOf<String,Observation>()
    private var lastFrame=-1
    private var session=UUID.randomUUID().toString()
    var needsAnotherRead=false; private set
    val documents get()=observations.filterValues { it.reads>=2 }.map { (key,o) ->
        MenuDocument(o.name,soldOut=o.soldOut,price=o.prices.singleOrNull(),id="observed:$session:$key")
    }
    fun reset() { observations.clear(); lastFrame=-1; session=UUID.randomUUID().toString(); needsAnotherRead=false }
    fun observe(screen: RecognizedScreen): Boolean {
        if(screen.keyframe<=lastFrame) return false
        lastFrame=screen.keyframe
        val before=documents
        if(screen.type!="menu") { needsAnotherRead=false; return false }
        val menus=screen.elements.filter { e -> e.kind=="menu" && e.readable && e.box.size==4 &&
            e.box.all { it.isFinite() && it in 0.0..1.0 } && e.box[2]>e.box[0] && e.box[3]>e.box[1] }
        val seen=mutableSetOf<String>()
        for(e in menus) {
            val raw=e.text.trim()
            val name=raw.replace(Regex("[0-9,]+\\s*원"),"").replace(Regex("품절|SOLD\\s*OUT",RegexOption.IGNORE_CASE),"").trim()
            val key=ScreenMenuResolver.normalize(name)
            if(name.length !in 2..80 || key.length<2 || !Regex("[가-힣a-zA-Z]").containsMatchIn(key) ||
                key in listOf("담기","주문하기","뒤로","장바구니","다음","메뉴","포장","매장","결제","더보기")) continue
            if(observations.size>=300 && key !in observations) continue
            val o=observations.getOrPut(key) { Observation(name) }
            if(seen.add(key)) o.reads=(o.reads+1).coerceAtMost(2)
            e.price?.takeIf { it in 0..10000000 }?.let { o.prices+=it }
            Regex("([0-9][0-9,]*)\\s*원").find(raw)?.groupValues?.get(1)?.replace(",","")?.toIntOrNull()?.takeIf { it in 0..10000000 }?.let { o.prices+=it }
            o.soldOut=o.soldOut || e.soldOut || raw.contains("품절") || Regex("SOLD\\s*OUT",RegexOption.IGNORE_CASE).containsMatchIn(raw)
        }
        needsAnotherRead=seen.any { observations.getValue(it).reads<2 }
        return before!=documents
    }
}
