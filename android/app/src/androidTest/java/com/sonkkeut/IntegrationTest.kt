package com.sonkkeut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kr.sonkkeut.android.KoreanStructure
import kr.sonkkeut.android.MatFrame
import kr.sonkkeut.android.SonkkeutEngine
import kr.sonkkeut.android.OnnxModel
import kr.sonkkeut.android.KioskCtcRecognizer
import kr.sonkkeut.core.Box
import kr.sonkkeut.core.Element
import kr.sonkkeut.core.Expect
import kr.sonkkeut.core.PressVerifier
import kr.sonkkeut.core.ScreenStructure
import kr.sonkkeut.core.Guide
import kr.sonkkeut.core.Fingertip
import kr.sonkkeut.core.Pt
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.Utils
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    @Test fun losingScreenResetsPressDwell() {
        val guide = Guide(speakInterval = 0.0)
        guide.setTarget(Element("target", "button", Box(0.4, 0.4, 0.6, 0.6), 0.95))
        val tip = Fingertip(Pt(0.5, 0.5), 0.95)
        guide.update(tip, 0.0)
        assertNotEquals("press", guide.update(tip, 0.2)?.type)
        assertEquals("hold", guide.update(tip, 0.25, targetConf = 0.0)?.type)
        assertNotEquals("press", guide.update(tip, 0.4)?.type)
        assertNotEquals("press", guide.update(tip, 0.6)?.type)
        assertEquals("press", guide.update(tip, 0.71)?.type)
    }

    @Test fun bundledModelsAndKoreanOcrWorkOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(OpenCVLoader.initLocal())
        for (name in listOf("m1_screen_corners_int8.onnx", "m2_screen_elements_int8.onnx")) {
            val bytes = context.assets.open("sonkkeut/$name").use { it.readBytes() }
            OnnxModel(bytes).use { model -> assertTrue(model.run(FloatArray(3 * 640 * 640), longArrayOf(1, 3, 640, 640)).isNotEmpty()) }
        }
        val refiner = context.assets.open("sonkkeut/m1r_corner_refiner.onnx").use { it.readBytes() }
        OnnxModel(refiner).use { model -> assertEquals(2, model.run(FloatArray(64 * 64), longArrayOf(1, 1, 64, 64)).size) }
        val bitmap = Bitmap.createBitmap(900, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 55f }
        canvas.drawText("아메리카노", 100f, 150f, paint)
        canvas.drawText("온도", 100f, 260f, paint)
        canvas.drawText("HOT 선택됨", 100f, 400f, paint)
        canvas.drawText("ICE", 100f, 550f, paint)
        canvas.drawText("담기", 100f, 700f, paint)
        canvas.drawText("장바구니 0개", 100f, 880f, paint)
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val elements = listOf(
            Element("menu", "menu", Box(0.03, 0.03, 0.85, 0.19), 0.95),
            Element("hot", "button", Box(0.03, 0.27, 0.85, 0.44), 0.95),
            Element("ice", "button", Box(0.03, 0.45, 0.85, 0.60), 0.95),
            Element("add", "button", Box(0.03, 0.60, 0.85, 0.78), 0.95),
        )
        val provider = KoreanStructure(context)
        try {
            val screen = provider.build(elements, emptyMap(), MatFrame(mat), 1)
            assertEquals("option", screen.screenType)
            assertTrue(screen.elements.first().text!!.contains("아메리카노"))
            assertTrue(screen.selected!!.contains("hot"))
            assertEquals(0, screen.cartCount)
        } finally {provider.close(); mat.release(); bitmap.recycle()}
    }

    @Test fun armDeviceInitializesFullVisionEngine() {
        org.junit.Assume.assumeTrue("MediaPipe JNI supports ARM Android devices", Build.SUPPORTED_ABIS.any { it.startsWith("arm") })
        SonkkeutEngine.init(InstrumentationRegistry.getInstrumentation().targetContext, false)
        assertTrue(SonkkeutEngine.isReady)
    }

    @Test fun deviceReachesConfiguredBackendMenuAndHealth() {
        val arguments = InstrumentationRegistry.getArguments()
        val base = arguments.getString("backendUrl") ?: "https://sonkkeutgil-mvp-oct02.enterenter0311.chatgpt.site"
        fun get(path: String): String {
            val connection = URL("$base$path").openConnection() as HttpURLConnection
            connection.connectTimeout = 15000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "SonkkeutAndroidIntegration/0.1.1")
            try {assertEquals(200, connection.responseCode); return connection.inputStream.bufferedReader().use { it.readText() }}
            finally {connection.disconnect()}
        }
        assertTrue(get("/healthz").contains("true"))
        val code = arguments.getString("storeCode") ?: "QXWBW2"
        require(!code.isNullOrBlank()) { "Pass -Pandroid.testInstrumentationRunnerArguments.storeCode=DEMO_CODE" }
        assertTrue(get("/api/stores/$code/menu").contains("아메리카노"))
    }

    @Test fun fineTunedOcrRunsOnAndroidWithoutFallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(OpenCVLoader.initLocal())
        KioskCtcRecognizer(instrumentation.targetContext).use { recognizer ->
            listOf("아메리카노", "4,500원", "따뜻하게").forEachIndexed { index, expected ->
                val bitmap = instrumentation.context.assets.open("ocr/sample-$index.png").use { BitmapFactory.decodeStream(it) }
                val rgba = Mat(); val rgb = Mat()
                try {
                    Utils.bitmapToMat(bitmap, rgba)
                    Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
                    val reading = recognizer.recognize(rgb)
                    assertEquals(expected, reading.text)
                    assertFalse(reading.uncertain)
                    assertTrue(reading.confidence >= 0.98)
                } finally {rgb.release(); rgba.release(); bitmap.recycle()}
            }
        }
    }

    @Test fun partialPressEvidenceNeverAdvancesOrder() {
        val verifier = PressVerifier()
        val before = ScreenStructure("option", 1, emptyList())
        verifier.arm(0.0, before, Expect(screenTypeNot = "option", cartDelta = 1))
        assertEquals("uncertain", verifier.judge(ScreenStructure("menu", 2, emptyList())).result)
    }
}
