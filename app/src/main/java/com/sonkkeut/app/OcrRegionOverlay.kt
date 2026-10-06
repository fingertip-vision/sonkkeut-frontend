package com.sonkkeut.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Decorative only: TalkBack uses the single summary/control, never one focus stop per region. */
@Composable
internal fun OcrRegionOverlay(frame: Map<String,Any?>, regions: List<OcrRegionOutline>, modifier: Modifier=Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val dimensions=frame["frame_size"] as? List<*> ?: return@Canvas
        val width=(dimensions.getOrNull(0) as? Number)?.toFloat() ?: return@Canvas
        val height=(dimensions.getOrNull(1) as? Number)?.toFloat() ?: return@Canvas
        if(!width.isFinite() || !height.isFinite() || width<=0 || height<=0) return@Canvas
        // Same FIT_CENTER mapping as the existing upright camera/target overlay.
        val scale=minOf(size.width/width,size.height/height)
        val dx=(size.width-width*scale)/2; val dy=(size.height-height*scale)/2
        regions.forEach { region ->
            val path=Path().apply {
                region.points.forEachIndexed { index,p ->
                    val x=dx+p.x.toFloat()*scale; val y=dy+p.y.toFloat()*scale
                    if(index==0) moveTo(x,y) else lineTo(x,y)
                }
                close()
            }
            val effect=if(region.readable) null else PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(),5.dp.toPx()))
            drawPath(path,Color(0xFF080F1E),style=Stroke(5.dp.toPx(),pathEffect=effect))
            drawPath(path,Color(0xFF7EE7F2),style=Stroke(2.dp.toPx(),pathEffect=effect))
        }
    }
}
