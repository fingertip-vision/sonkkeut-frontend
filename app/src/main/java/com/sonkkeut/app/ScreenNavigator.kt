package com.sonkkeut.app

import kotlin.math.abs

enum class DetectionStatus { FOUND, SCROLL, SWITCH_TAB, WAITING_CHANGE, NOT_FOUND_IN_VIEWPORT, UNREADABLE }
data class DetectionResult(val status: DetectionStatus,val target: RecognizedElement?=null,val message: String="")

/** Cooperative kiosk navigation. An action is completed only by fresh visual evidence. */
class ScreenNavigator(private val maxScrolls: Int=3,private val maxTabs: Int=4) {
    val observed=linkedSetOf<String>()
    private val visited=linkedSetOf<String>()
    private val views=linkedSetOf<String>()
    private var offset=0
    private var unchanged=0
    private var total=0
    private var awaiting: DetectionResult?=null
    private var pendingSignature=""
    private var lastFrame=-1
    private var pendingSince=0L
    private var fingerStart: Pair<Double,Double>?=null
    private var dragged=false
    private var started=Long.MIN_VALUE
    private var scrollRegion=listOf(.15,.25,.85,.85)
    private fun n(s: String)=NativeOrderParser.normalize(s.replace(Regex("[0-9,]+\\s*원"),""))
    fun reset() { observed.clear(); visited.clear(); views.clear(); offset=0; unchanged=0; total=0; awaiting=null; lastFrame=-1; dragged=false; fingerStart=null; started=Long.MIN_VALUE }
    fun signature(screen: RecognizedScreen)=screen.type+":"+screen.elements.filter { it.readable && it.kind in listOf("menu","tab") }.map { it.kind+":"+n(it.text) }.sorted().joinToString("|")
    fun hasChanged(screen: RecognizedScreen)=awaiting!=null && signature(screen)!=pendingSignature
    fun findMenuItem(name: String,screen: RecognizedScreen,now: Long): DetectionResult {
        if(screen.keyframe<=lastFrame) return DetectionResult(DetectionStatus.WAITING_CHANGE)
        lastFrame=screen.keyframe
        if(started==Long.MIN_VALUE) started=now
        screen.elements.filter { it.readable && it.kind=="menu" }.forEach { observed+=it.text }
        val exact=screen.elements.filter { it.readable && it.kind=="menu" && n(it.text)==n(name) && it.box.size==4 && it.box.all { p -> p.isFinite() && p in 0.0..1.0 } && it.box[2]>it.box[0] && it.box[3]>it.box[1] }
        if(exact.size==1) { awaiting=null; return DetectionResult(DetectionStatus.FOUND,exact.single()) }
        if(now-started>=90000) return DetectionResult(DetectionStatus.NOT_FOUND_IN_VIEWPORT,message="화면 탐색 시간이 지났습니다.")
        val readable=screen.elements.any { it.readable && it.kind=="menu" }
        if(!readable && screen.elements.none { it.readable && it.kind=="tab" }) return DetectionResult(DetectionStatus.UNREADABLE,message="메뉴 글자를 확실하게 읽지 못했습니다.")
        val sig=signature(screen)
        awaiting?.let {
            if(sig==pendingSignature && now-pendingSince<15000) return DetectionResult(DetectionStatus.WAITING_CHANGE,message=it.message)
            if(sig==pendingSignature) unchanged++ else { unchanged=0; views+=sig }
            awaiting=null; dragged=false; fingerStart=null
        }
        if(total>=12) return DetectionResult(DetectionStatus.NOT_FOUND_IN_VIEWPORT,message="탐색한 화면에서 메뉴를 확인하지 못했습니다.")
        if(readable && offset<maxScrolls && unchanged<2) {
            val boxes=screen.elements.filter { it.readable && it.kind=="menu" && it.box.size==4 && it.box.all { p -> p.isFinite() && p in 0.0..1.0 } && it.box[2]>it.box[0] && it.box[3]>it.box[1] }.map { it.box }
            if(boxes.isNotEmpty()) scrollRegion=listOf(boxes.minOf { it[0] },boxes.minOf { it[1] },boxes.maxOf { it[2] },boxes.maxOf { it[3] })
            offset++; total++
            return schedule(DetectionResult(DetectionStatus.SCROLL,message="메뉴 영역의 아래쪽에서 위로 천천히 쓸어 주세요. 화면이 바뀌면 다시 읽겠습니다."),sig,now)
        }
        val tab=screen.elements.firstOrNull { it.readable && it.kind=="tab" && n(it.text) !in visited }
            ?: screen.elements.firstOrNull { it.readable && it.kind=="button" && n(it.text) in listOf("다음","다음페이지","더보기") && n(it.text) !in visited }
        if(tab!=null && visited.size<maxTabs) {
            visited+=n(tab.text); offset=0; unchanged=0; total++
            return schedule(DetectionResult(DetectionStatus.SWITCH_TAB,tab,"${tab.text} 탭으로 이동하겠습니다."),sig,now)
        }
        return DetectionResult(DetectionStatus.NOT_FOUND_IN_VIEWPORT,message="탐색한 화면에서 메뉴를 확인하지 못했습니다. 다른 메뉴를 제안할까요?")
    }
    private fun schedule(result: DetectionResult,signature: String,now: Long): DetectionResult { awaiting=result; pendingSignature=signature; pendingSince=now; return result }
    fun swipeHint(tip: List<Double>?,now: Long): String? {
        if(awaiting?.status!=DetectionStatus.SCROLL || tip==null || tip.size!=2 || tip.any { !it.isFinite() || it !in 0.0..1.0 }) return null
        val x=(tip[0]-scrollRegion[0])/(scrollRegion[2]-scrollRegion[0]).coerceAtLeast(.01)
        val y=(tip[1]-scrollRegion[1])/(scrollRegion[3]-scrollRegion[1]).coerceAtLeast(.01)
        if(dragged) return null
        val start=fingerStart
        if(start!=null && abs(x-start.first)<.4 && start.second-y>.5) {
            dragged=true; pendingSince=minOf(pendingSince,now-12000)
            return "손을 떼어 주세요. 바뀐 화면을 읽겠습니다."
        }
        if(start==null && x in .25.. .75 && y in .7.. .95) { fingerStart=x to y; return "지금 화면을 터치한 채 위로 쓸어 주세요." }
        return if(start!=null) "터치한 채 위로 천천히 쓸어 주세요." else if(y<.7) "검지를 메뉴 영역 아래쪽으로 옮겨 주세요." else if(y>.95) "검지를 메뉴 영역 안쪽 위로 옮겨 주세요." else if(x<.25) "검지를 오른쪽으로 옮겨 주세요." else if(x>.75) "검지를 왼쪽으로 옮겨 주세요." else null
    }
}
