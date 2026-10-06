package com.sonkkeut.app

import kr.sonkkeut.android.DetailScan

enum class VisionFinding { SEARCHING, RESOLVED_BY_CLOSEUP, NOT_OBSERVED, CATALOG_NOT_LISTED, CANCELLED, TIMED_OUT }
class VisionVerifier(private val timeoutMs: Long=90000) {
    var active=false; private set
    var section=0; private set
    var finding=VisionFinding.SEARCHING; private set
    var reads=0; private set
    private var requested=""
    private var started=0L
    private var scans=0
    fun begin(name: String,now: Long) { requested=NativeOrderParser.normalize(name); started=now; active=true; finding=VisionFinding.SEARCHING; section=0; reads=0; scans=0 }
    fun detectsRequested(scan: DetailScan)=scan.lines.any { it.confidence>=.8 && NativeOrderParser.normalize(it.text.replace(Regex("[0-9,]+\\s*원"),""))==requested }
    fun accept(scan: DetailScan,now: Long): VisionFinding {
        if(!active) return finding
        if(expire(now)) return finding
        reads+=scan.lines.count { it.confidence>=.8 }; scans++
        if(detectsRequested(scan)) {
            finding=VisionFinding.RESOLVED_BY_CLOSEUP; active=false
        } else if(scans>=3) { finding=VisionFinding.NOT_OBSERVED; active=false }
        else section++
        return finding
    }
    fun expire(now: Long): Boolean { if(active && now-started>=timeoutMs) { active=false; finding=VisionFinding.TIMED_OUT }; return finding==VisionFinding.TIMED_OUT }
    fun cancel() { active=false; finding=VisionFinding.CANCELLED }
    fun guidance()=listOf("화면 위쪽","화면 가운데","화면 아래쪽")[section.coerceIn(0,2)]+"을 가까이 비춰 주세요. 메뉴가 보이면 다음 구역 또는 취소라고 말씀해 주세요."
    fun absenceInCatalog(name: String,names: Set<String>): VisionFinding = if(names.none { NativeOrderParser.normalize(it)==NativeOrderParser.normalize(name) }) VisionFinding.CATALOG_NOT_LISTED else VisionFinding.NOT_OBSERVED
}
