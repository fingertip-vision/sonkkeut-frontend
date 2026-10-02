package com.sonkkeut.app

import kotlin.math.abs
import kotlin.math.max

// Python geometry.py, revision ba55be0248c709ab9222d40c100dcb9d61362a50.
// Ordered camera-pixel corners: TL, TR, BR, BL. No detection or raster warping.
class CoreHomography(coefficients: List<Double>) {
    private val matrix = coefficients.toList()
    init { require(matrix.size == 9 && matrix.all { it.isFinite() }); inverse(matrix) }
    fun cameraToScreen(point: CorePoint) = map(matrix, point)
    fun screenToCamera(point: CorePoint) = map(inverse(matrix), point)
    companion object {
        private const val EPS = 1e-12
        private fun inverse(m: List<Double>): List<Double> {
            val (a,b,c) = m; val d=m[3]; val e=m[4]; val f=m[5]; val g=m[6]; val h=m[7]; val i=m[8]
            val cofactors = listOf(e*i-f*h,c*h-b*i,b*f-c*e,f*g-d*i,a*i-c*g,c*d-a*f,d*h-e*g,b*g-a*h,a*e-b*d)
            val terms = listOf(a*cofactors[0],b*cofactors[3],c*cofactors[6])
            val determinant=terms.sum(); val scale=terms.sumOf { abs(it) }
            require(determinant.isFinite() && scale != 0.0 && abs(determinant)>EPS*scale) { "homography is singular" }
            return cofactors.map { it/determinant }.also { require(it.all(Double::isFinite)) }
        }
        private fun map(m: List<Double>, p: CorePoint): CorePoint {
            val terms=listOf(m[6]*p.x,m[7]*p.y,m[8]); val denominator=terms.sum(); val scale=terms.sumOf { abs(it) }
            require(denominator.isFinite() && scale.isFinite() && scale != 0.0 && abs(denominator)>EPS*scale) { "projective horizon" }
            return CorePoint((m[0]*p.x+m[1]*p.y+m[2])/denominator,(m[3]*p.x+m[4]*p.y+m[5])/denominator)
        }
        private fun solve(matrix: List<List<Double>>, values: List<Double>): List<Double> {
            val rows=matrix.mapIndexed { index,row -> (row+values[index]).toMutableList() }.toMutableList()
            val scales=matrix.map { row -> row.maxOf { abs(it) } }.toMutableList()
            for (column in rows.indices) {
                var pivot=column
                fun ratio(index: Int) = if(scales[index] == 0.0) 0.0 else abs(rows[index][column])/scales[index]
                for (index in column until rows.size) if(ratio(index)>ratio(pivot)) pivot=index
                require(scales[pivot] != 0.0 && abs(rows[pivot][column])>EPS*scales[pivot]) { "unstable homography" }
                val row=rows[column]; rows[column]=rows[pivot]; rows[pivot]=row
                val scale=scales[column]; scales[column]=scales[pivot]; scales[pivot]=scale
                val divisor=rows[column][column]
                rows[column]=rows[column].map { it/divisor }.toMutableList()
                for(index in rows.indices) if(index != column) {
                    val factor=rows[index][column]
                    rows[index]=rows[index].mapIndexed { j,value -> value-factor*rows[column][j] }.toMutableList()
                }
            }
            return rows.map { it.last() }.also { require(it.all(Double::isFinite)) }
        }
        fun fromCorners(corners: List<CorePoint>): CoreHomography {
            require(corners.size == 4) { "four ordered corners required" }
            val cx=corners.sumOf { it.x/4 }; val cy=corners.sumOf { it.y/4 }
            val scale=max(corners.maxOf { it.x }-corners.minOf { it.x },corners.maxOf { it.y }-corners.minOf { it.y })
            require(scale.isFinite() && scale>0)
            val normalized=corners.map { CorePoint((it.x-cx)/scale,(it.y-cy)/scale) }
            for(index in 0..3) {
                val first=normalized[index]; val second=normalized[(index+1)%4]; val third=normalized[(index+2)%4]
                require((second.x-first.x)*(third.y-second.y)-(second.y-first.y)*(third.x-second.x)>EPS) { "invalid corner order" }
            }
            val unit=listOf(CorePoint(0.0,0.0),CorePoint(1.0,0.0),CorePoint(1.0,1.0),CorePoint(0.0,1.0))
            val rows=mutableListOf<List<Double>>(); val values=mutableListOf<Double>()
            normalized.zip(unit).forEach { (p,q) ->
                rows.add(listOf(p.x,p.y,1.0,0.0,0.0,0.0,-q.x*p.x,-q.x*p.y)); values.add(q.x)
                rows.add(listOf(0.0,0.0,0.0,p.x,p.y,1.0,-q.y*p.x,-q.y*p.y)); values.add(q.y)
            }
            val s=solve(rows,values); val a=s[0]; val b=s[1]; val c=s[2]; val d=s[3]; val e=s[4]; val f=s[5]; val g=s[6]; val h=s[7]
            val transform=CoreHomography(listOf(a/scale,b/scale,c-(a*cx+b*cy)/scale,d/scale,e/scale,f-(d*cx+e*cy)/scale,g/scale,h/scale,1-(g*cx+h*cy)/scale))
            corners.zip(unit).forEach { (p,q) -> val projected=transform.cameraToScreen(p); require(abs(projected.x-q.x)<=1e-8 && abs(projected.y-q.y)<=1e-8) }
            return transform
        }
    }
}

data class CorePlaneUpdate(val homography: CoreHomography?, val lostForSeconds: Double, val lossTimeout: Boolean, val reason: String? = null) {
    val valid get()=homography != null
    val requiresReacquisition get()=!valid
}
class CorePlaneTracker(private val minConfidence: Double=.5, private val lossTimeout: Double=3.0) {
    init { require(minConfidence.isFinite() && minConfidence in 0.0..1.0 && lossTimeout.isFinite() && lossTimeout>0) }
    private var lastTimestamp: Double?=null
    private var lossStarted: Double?=null
    fun update(corners: List<CorePoint>?, now: Double, confidence: Double=1.0): CorePlaneUpdate {
        require(now.isFinite() && now>=0 && lastTimestamp?.let { now<it } != true)
        lastTimestamp=now
        var reason: String?="missing corners"
        val transform=try {
            require(confidence.isFinite() && confidence in 0.0..1.0) { "invalid confidence" }
            if(confidence<minConfidence) { reason="low corner confidence"; null }
            else if(corners != null) CoreHomography.fromCorners(corners).also { reason=null } else null
        } catch(e: IllegalArgumentException) { reason=e.message; null }
        if(transform != null) { lossStarted=null; return CorePlaneUpdate(transform,0.0,false) }
        if(lossStarted == null) lossStarted=now
        val elapsed=now-lossStarted!!
        return CorePlaneUpdate(null,elapsed,elapsed>=lossTimeout,reason)
    }
}
