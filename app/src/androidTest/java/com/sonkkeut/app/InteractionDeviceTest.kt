package com.sonkkeut.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kr.sonkkeut.android.*
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class InteractionDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun ui(block: ()->Unit)=instrumentation.runOnMainSync(block)
    private fun replay(block: (NativeAppModel)->Unit) {
        lateinit var model: NativeAppModel
        val store=ViewModelStore()
        ui { model=NativeAppModel(instrumentation.targetContext.applicationContext as Application); store.put("test",model) }
        try {
            val deadline=SystemClock.elapsedRealtime()+60000
            while(SystemClock.elapsedRealtime()<deadline && (!model.ready || model.menu.isEmpty())) Thread.sleep(100)
            assertTrue(model.ready && model.menu.isNotEmpty())
            ui { block(model) }
        } finally { ui { store.clear() } }
    }
    private fun say(model: NativeAppModel,text: String) { model.conversation.spoken(text,SpeechEvidence()) }
    private fun completeAck(model: NativeAppModel) { val turn=model.dialogTurn!!; assertNotNull(turn.accepted); model.dialogPromptCompleted(turn.generation,true) }
    private fun beginMenu(model: NativeAppModel) {
        model.conversation.begin(); assertEquals(DialogState.WAITING_SCREEN,model.conversation.dialog.state)
        model.conversation.onScreen(RecognizedScreen("menu",1,model.menu.map { RecognizedElement(it.name,"menu",it.name,listOf(.1,.1,.5,.3),true) },0,null,null))
    }
    @Test fun scriptedVoiceCorrectionAndQuantityCreateOnlyConfirmedOrder() = replay { model ->
        beginMenu(model); val first=model.menu.first { !it.soldOut }.name
        say(model,first); assertNull(model.order); say(model,"아니요"); assertNull(model.order)
        say(model,first); say(model,"네"); completeAck(model)
        assertEquals(VoiceSlot.QUANTITY,model.conversation.dialog.slot); assertNull(model.order)
        say(model,"두개"); say(model,"네"); completeAck(model)
        assertEquals(first,model.order!!.items.single().menu); assertEquals(2,model.order!!.items.single().qty)
    }
    @Test fun scriptedScreenTransitionInvalidatesOldConfirmationAndAsksOnlyTemperature() = replay { model ->
        beginMenu(model); val name=model.menu.first { !it.soldOut }.name
        say(model,name); say(model,"네"); completeAck(model); say(model,"한개"); say(model,"네"); completeAck(model)
        fun el(id: String,text: String)=RecognizedElement(id,"button",text,listOf(.1,.1,.5,.3),true)
        model.conversation.onScreen(RecognizedScreen("option",10,listOf(el("name",name),el("ice","아이스"),el("hot","따뜻하게"),el("size","라지")),0,null,null))
        assertEquals(VoiceSlot.TEMPERATURE,model.conversation.dialog.slot)
        val old=model.dialogTurn!!.generation
        say(model,"아이스")
        model.conversation.onScreen(RecognizedScreen("method",11,listOf(el("dine","매장"),el("takeout","포장")),0,null,null))
        assertEquals(VoiceSlot.DINE,model.conversation.dialog.slot)
        model.dialogPromptCompleted(old,true); assertFalse(model.recording); assertNull(model.order!!.items.single().temperature)
        model.start(); assertFalse(model.paused)
        model.dialogPromptCompleted(model.dialogTurn!!.generation,false)
        assertTrue(model.paused); assertFalse(model.conversation.active)
    }
    @Test fun scriptedRecommendationRejectionStartsCloseupAndVoiceCancelExits() = replay { model ->
        beginMenu(model); say(model,"크림 파스타"); say(model,"네"); completeAck(model)
        assertEquals(VoiceSlot.EXACT_SEARCH,model.conversation.dialog.slot)
        say(model,"네"); completeAck(model); assertTrue(model.conversation.closeup)
        say(model,"읽어줘"); completeAck(model)
        assertEquals(DialogState.WAITING_SCREEN,model.conversation.dialog.state)
        model.conversation.detail(DetailScan(emptyList(),6,100,false))
        assertEquals(VoiceSlot.SCAN_CONTROL,model.conversation.dialog.slot)
        say(model,"다음구역"); completeAck(model); assertEquals(1,model.conversation.verifier.section)
        say(model,"취소"); val turn=model.dialogTurn!!; assertTrue(turn.stopped)
        model.dialogPromptCompleted(turn.generation,true); assertFalse(model.conversation.active); assertFalse(model.conversation.closeup)
    }
    @Test fun initialMethodScreenAsksDineBeforeAnyMenuOrQuantity() = replay { model ->
        model.conversation.begin()
        assertNull(model.dialogTurn)
        model.conversation.onScreen(RecognizedScreen("method",1,listOf(RecognizedElement("takeout","button","포장",listOf(.1,.1,.5,.3),true)),0,null,null))
        assertEquals(VoiceSlot.DINE,model.conversation.dialog.slot)
        assertNull(model.order); assertFalse(model.dialogTurn!!.prompt.contains("몇 개"))
    }
    @Test fun categoryOnlyScreenLimitsVoiceChoiceToVisibleTabs() = replay { model ->
        model.conversation.begin()
        model.conversation.onScreen(RecognizedScreen("unknown",1,listOf(RecognizedElement("coffee","tab","커피",listOf(.1,.1,.5,.3),true)),0,null,null))
        assertEquals(VoiceSlot.CATEGORY,model.conversation.dialog.slot)
        say(model,"아메리카노"); assertNull(model.dialogTurn!!.accepted); assertNull(model.order)
        say(model,"커피"); assertTrue(model.dialogTurn!!.prompt.contains("맞으신가요"))
    }
    @Test fun realTileScannerReadsBundledKoreanMenuImage() {
        assertTrue(OpenCVLoader.initLocal())
        val sample=instrumentation.context.assets.open("ocr/sample-0.png").use { BitmapFactory.decodeStream(it) }
        val bitmap=Bitmap.createBitmap(900,1200,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(sample,80f,120f,null)
        val rgba=Mat(); val rgb=Mat()
        try {
            Utils.bitmapToMat(bitmap,rgba); Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB)
            DetailScanner(instrumentation.targetContext).use { scanner ->
                val result=scanner.scanRgb(rgb)
                assertTrue("Actual M3/ML Kit tiles: ${result.lines}",result.lines.any { it.text=="아메리카노" && it.confidence>=.8 })
                assertTrue(result.tilesCompleted>0); assertTrue(result.elapsedMs<12000)
            }
        } finally { rgb.release(); rgba.release(); bitmap.recycle(); sample.recycle() }
    }
}
