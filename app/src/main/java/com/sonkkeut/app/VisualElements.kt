package com.sonkkeut.app

import kotlin.math.max
import kotlin.math.min

// Python elements.py/contracts.py, revision ba55be0. External normalized detections.
fun CoreBox.iou(other: CoreBox): Double {
    val overlap=max(0.0,min(right,other.right)-max(left,other.left))*max(0.0,min(bottom,other.bottom)-max(top,other.top))
    return overlap/((right-left)*(bottom-top)+(other.right-other.left)*(other.bottom-other.top)-overlap)
}
data class CoreDetection(val kind: String,val box: CoreBox,val confidence: Double) {
    init { require(kind in listOf("button","menu","price","tab","back") && confidence.isFinite() && confidence in 0.0..1.0) }
}
data class CoreElement(val target: CoreTarget,val text: String?=null,val price: Int?=null) {
    init { require(price == null || price>=0) }
}
data class CoreElementResult(val elements: List<CoreElement>) { val requiresPlaneReacquisition get()=elements.isEmpty() }
object CoreElementProcessor {
    private val reading=compareBy<CoreDetection>({it.box.top},{it.box.left},{it.box.bottom},{it.box.right},{it.kind},{-it.confidence})
    fun process(detections: List<CoreDetection>,keyframe: Int,minConfidence: Double=.5,nmsThreshold: Double=.5): CoreElementResult {
        require(keyframe>=0 && minConfidence.isFinite() && minConfidence in 0.0..1.0 && nmsThreshold.isFinite() && nmsThreshold in 0.0..1.0)
        val ranked=detections.filter { it.confidence>=minConfidence }.sortedWith(compareBy<CoreDetection> { -it.confidence }.then(reading))
        val kept=mutableListOf<CoreDetection>()
        ranked.forEach { candidate -> if(kept.none { it.kind==candidate.kind && it.box.iou(candidate.box)>nmsThreshold }) kept.add(candidate) }
        return CoreElementResult(kept.sortedWith(reading).mapIndexed { index,d -> CoreElement(CoreTarget("e${keyframe}_${index+1}",d.kind,d.box,d.confidence)) })
    }
}
