package com.sonkkeut.app

import kr.sonkkeut.android.*

/** In-memory only. An approval belongs to the current catalog and observed screen layout. */
class MenuScreenLinks {
    private var catalog: List<MenuDocument>? = null
    private var resolver: ScreenMenuResolver? = null
    private var approved: String? = null
    var diagnostic = "아직 메뉴와 화면을 비교하지 않았습니다."; private set
    fun configure(documents: List<MenuDocument>) {
        if(catalog!=documents) { catalog=documents.toList(); resolver=ScreenMenuResolver(documents); approved=null }
    }
    fun reset() { approved=null; diagnostic="화면 매칭 확인을 초기화했습니다." }
    fun signature(screen: RecognizedScreen) = screen.type + ":" + screen.elements.joinToString("|") { "${it.id}:${it.kind}:${it.text}:${it.box}:${it.readable}:${it.price}:${it.soldOut}" }
    private fun token(key: String,screen: RecognizedScreen,region: String) = key+":"+region+":"+signature(screen)
    fun resolve(name: String,menuId: String,screen: RecognizedScreen): ScreenMenuResolution {
        val docs=catalog ?: listOf(MenuDocument(name,id=menuId)) // unit-test / standalone flow compatibility
        val target=if(menuId.isNotBlank()) docs.singleOrNull { ScreenMenuResolver.key(it)==menuId }
            else docs.singleOrNull { ScreenMenuResolver.normalize(it.name)==ScreenMenuResolver.normalize(name) }
        if(target==null) { approved=null; diagnostic="현재 메뉴 목록에서 주문 메뉴를 확인하지 못했습니다: $name"; return ScreenMenuResolution(ScreenMenuStatus.MISSING) }
        val key=ScreenMenuResolver.key(target)
        val result=(resolver ?: ScreenMenuResolver(docs)).resolve(key,screen.elements.map { MenuTextRegion(it.id,it.kind,it.text,it.box,it.readable) })
        val candidate=result.candidate
        val binding=candidate?.let { token(key,screen,it.regionId) }
        if(approved!=null && approved!=binding) approved=null
        diagnostic="주문: ${target.name}\n메뉴 ID: $key\n화면: ${screen.type}\n결과: ${result.status}" +
            (candidate?.let { "\n화면 글자: ${it.observed}\n근거: ${it.basis}\n후보 점수: ${"%.2f".format(it.score)} (정확도 확률 아님)" } ?: "\nOCR: "+screen.elements.take(12).joinToString(" / ") { if(it.readable) "${it.kind}: ${it.text}" else "${it.kind}: 읽기 불확실" })
        return if(result.status==ScreenMenuStatus.CONFIRM && approved==binding) result.copy(status=ScreenMenuStatus.FOUND) else result
    }
    fun approve(name: String,menuId: String,screen: RecognizedScreen,expected: ScreenMenuCandidate, expectedSignature: String): Boolean {
        if(signature(screen)!=expectedSignature) return false
        val fresh=resolve(name,menuId,screen)
        if(fresh.status!=ScreenMenuStatus.CONFIRM || fresh.candidate!=expected) return false
        approved=token(ScreenMenuResolver.key(expected.menu),screen,expected.regionId)
        return true
    }
}
