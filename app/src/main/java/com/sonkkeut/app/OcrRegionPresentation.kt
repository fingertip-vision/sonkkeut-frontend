package com.sonkkeut.app

import kr.sonkkeut.core.Mat3
import kr.sonkkeut.core.Pt
import kr.sonkkeut.core.UNIT_SQUARE
import kr.sonkkeut.core.isConvexQuad
import kr.sonkkeut.core.quadArea

/** Existing OCR-labelled element regions, NOT glyph or line-level OCR bounds. */
internal data class OcrRegionOutline(val points: List<Pt>, val readable: Boolean)
internal data class OcrOutlineState(val requested: Boolean, val regions: List<OcrRegionOutline>) {
    val status: String get() = when {
        !requested -> ""
        regions.isEmpty() -> "최근 글자 인식 영역이 없습니다. 키오스크 전체 화면을 비춰 주세요."
        else -> "글자 인식 영역 ${regions.size}개 · 실선: 읽기 신뢰도 높음 · 점선: 확인 필요"
    }
}

internal object OcrRegionPresentation {
    const val DURATION_MS=5000L
    fun state(screen: RecognizedScreen?, screenAt: Long, frame: Map<String,Any?>, frameAt: Long,
              now: Long, until: Long, active: Boolean): OcrOutlineState {
        val requested=active && now<until
        fun empty()=OcrOutlineState(requested,emptyList())
        if(!requested || screen==null || frame["found"]!=true || frameAt<=0 || screenAt<=0 ||
            now-frameAt !in 0..1500L || now-screenAt !in 0..DURATION_MS ||
            (frame["keyframe_id"] as? Number)?.toInt()!=screen.keyframe) return empty()
        val dimensions=numbers(frame["frame_size"],2) ?: return empty()
        if(dimensions.any { it<=0 }) return empty()
        val corners=numbers(frame["corners"],8) ?: return empty()
        val quad=corners.chunked(2).map { Pt(it[0],it[1]) }
        if(quad.any { it.x !in 0.0..dimensions[0] || it.y !in 0.0..dimensions[1] } ||
            !isConvexQuad(quad) || quadArea(quad)<1.0) return empty()
        val transform=runCatching { Mat3.perspective(UNIT_SQUARE,quad) }.getOrNull() ?: return empty()
        val regions=screen.elements.filter { it.text.isNotBlank() && it.box.size==4 &&
            it.box.all { p -> p.isFinite() && p in 0.0..1.0 } && it.box[2]>it.box[0] && it.box[3]>it.box[1] }
            .mapNotNull { element ->
                val b=element.box
                val points=listOf(Pt(b[0],b[1]),Pt(b[2],b[1]),Pt(b[2],b[3]),Pt(b[0],b[3])).map(transform::apply)
                if(points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0.0..dimensions[0] || it.y !in 0.0..dimensions[1] }) null
                else OcrRegionOutline(points,element.readable)
            }.distinctBy { it.points }.take(80)
        return OcrOutlineState(requested,regions)
    }
    private fun numbers(value: Any?, count: Int): List<Double>? {
        val list=value as? List<*> ?: return null
        if(list.size!=count) return null
        return list.map { (it as? Number)?.toDouble()?.takeIf { n -> n.isFinite() } ?: return null }
    }
}
