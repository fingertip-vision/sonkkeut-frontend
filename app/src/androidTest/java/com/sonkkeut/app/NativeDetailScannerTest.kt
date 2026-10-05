package com.sonkkeut.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import kr.sonkkeut.android.DetailScanner
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc

class NativeDetailScannerTest {
    @Test fun overlappingTilesReadActualKoreanFixtureAndDeduplicateTheSameLine() {
        assertTrue(OpenCVLoader.initLocal())
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val sample=instrumentation.context.assets.open("ocr/sample-0.png").use { BitmapFactory.decodeStream(it) }
        val bitmap=Bitmap.createBitmap(1200,1800,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(sample,null,Rect(220,520,660,520+440*sample.height/sample.width),null)
        val rgba=Mat(); val rgb=Mat()
        try {
            Utils.bitmapToMat(bitmap,rgba); Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB)
            DetailScanner(instrumentation.targetContext).use { scanner ->
                val result=scanner.scanRgb(rgb)
                assertTrue(result.tilesCompleted>0)
                val matches=result.lines.filter { it.text.replace(" ","").contains("아메리카노") }
                assertEquals("The overlapping tiles must not duplicate one line",1,matches.size)
                assertTrue(matches.single().confidence>=.8)
                assertTrue(matches.single().box.all { it in 0.0..1.0 })
            }
        } finally { rgb.release(); rgba.release(); bitmap.recycle(); sample.recycle() }
    }
    @Test fun cancellationStopsBeforeReadingAnyTile() {
        assertTrue(OpenCVLoader.initLocal())
        val rgb=Mat.zeros(480,640,CvType.CV_8UC3)
        try { DetailScanner(InstrumentationRegistry.getInstrumentation().targetContext).use { scanner ->
            val result=scanner.scanRgb(rgb) { true }
            assertTrue(result.timedOut); assertTrue(result.lines.isEmpty()); assertEquals(0,result.tilesCompleted)
        } } finally { rgb.release() }
    }
}
