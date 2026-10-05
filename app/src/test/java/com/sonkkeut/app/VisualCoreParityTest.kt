package com.sonkkeut.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VisualCoreParityTest {
    private val data get()=JSONObject(javaClass.getResource("/visual-reference.json")!!.readText())
    private fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
    private fun JSONArray.point()=CorePoint(getDouble(0),getDouble(1))
    private fun JSONObject.point()=CorePoint(getDouble("x"),getDouble("y"))
    private fun JSONArray.box()=CoreBox(getDouble(0),getDouble(1),getDouble(2),getDouble(3))
    private fun JSONArray.corners()=(0 until length()).map { getJSONArray(it).point() }
    private fun JSONObject.optionalString(key: String)=if(isNull(key)) null else getString(key)
    private fun JSONObject.optionalInt(key: String)=if(isNull(key)) null else getInt(key)
    private fun JSONObject.optionalDouble(key: String)=if(isNull(key)) null else getDouble(key)
    private fun JSONObject.element()=CoreElement(CoreTarget(getString("id"),getString("kind"),getJSONArray("box").box(),getDouble("conf")),optionalString("text"),optionalInt("price"))
    private fun JSONObject.screen()=CoreScreen(getString("type"),getInt("keyframe"),getJSONArray("elements").objects().map { it.element() })
    private fun assertPoint(label: String,expected: JSONObject?,actual: CorePoint?,tolerance: Double=1e-9) {
        if(expected==null) { assertNull(label,actual); return }
        assertNotNull(label,actual); assertEquals(label,expected.getDouble("x"),actual!!.x,tolerance); assertEquals(label,expected.getDouble("y"),actual.y,tolerance)
    }
    @Test fun pythonHomographiesForwardInverseAndInvalidOrderMatch() {
        data.getJSONArray("geometry").objects().forEachIndexed { index,r ->
            val transform=runCatching { CoreHomography.fromCorners(r.getJSONArray("corners").corners()) }
            if(r.has("expected")) { assertEquals("geometry[$index]",!r.getJSONObject("expected").optBoolean("error"),transform.isSuccess) }
            else r.getJSONArray("samples").objects().forEach { s ->
                val camera=transform.getOrThrow().screenToCamera(s.getJSONArray("screen").point())
                assertPoint("inverse[$index]",s.getJSONObject("camera"),camera,1e-7)
                assertPoint("forward[$index]",s.getJSONObject("projected"),transform.getOrThrow().cameraToScreen(camera))
            }
        }
    }
    @Test fun pythonPlaneLossInvalidationAndTimeoutMatch() {
        val tracker=CorePlaneTracker()
        data.getJSONArray("plane").objects().forEachIndexed { index,row ->
            val i=row.getJSONObject("input"); val e=row.getJSONObject("expected")
            val result=runCatching { tracker.update(if(i.isNull("corners")) null else i.getJSONArray("corners").corners(),i.getDouble("now"),i.getDouble("confidence")) }
            if(e.optBoolean("error")) { assertTrue("plane[$index]",result.exceptionOrNull() is IllegalArgumentException) }
            else { val a=result.getOrThrow(); assertEquals(e.getBoolean("valid"),a.valid); assertEquals(e.getDouble("lost"),a.lostForSeconds,1e-9); assertEquals(e.getBoolean("timeout"),a.lossTimeout); assertEquals(e.getBoolean("reacquire"),a.requiresReacquisition); assertEquals(e.getBoolean("has_reason"),a.reason != null) }
        }
    }
    @Test fun pythonClassAwareNmsOrderingAndIdsMatch() {
        data.getJSONArray("elements").objects().forEach { row ->
            val i=row.getJSONObject("input"); val detections=i.getJSONArray("detections").objects().map { CoreDetection(it.getString("kind"),it.getJSONArray("box").box(),it.getDouble("conf")) }
            val a=CoreElementProcessor.process(detections,i.getInt("keyframe"),nmsThreshold=i.optDouble("nms",.5)); val e=row.getJSONArray("expected").objects().map { it.element() }
            assertEquals(e,a.elements); assertEquals(row.getBoolean("reacquire"),a.requiresPlaneReacquisition)
        }
    }
    @Test fun pythonSmoothingIdentityAmbiguityAndLossMatch() {
        val traces=data.getJSONArray("tracking")
        for(trace in 0 until traces.length()) {
            val tracker=CoreFingertipTracker()
            traces.getJSONArray(trace).objects().forEachIndexed { index,row ->
                val i=row.getJSONObject("input"); val e=row.getJSONObject("expected"); val label="tracking[$trace:$index]"
                val hands=i.getJSONArray("hands").objects().map { h -> CoreHand(h.getString("id"),List(21) { h.getJSONArray("point").point() },h.getDouble("conf"),h.optionalDouble("distance")) }
                val plane=if(i.isNull("corners")) null else CoreHomography.fromCorners(i.getJSONArray("corners").corners())
                val result=runCatching { tracker.update(hands,plane,i.getDouble("now"),i.getInt("keyframe")) }
                if(e.optBoolean("error")) { assertTrue(label,result.exceptionOrNull() is IllegalArgumentException) }
                else {
                    val a=result.getOrThrow(); assertEquals(label,e.getString("reason"),a.reason); assertEquals(label,e.optionalString("hand_id"),a.handId); assertEquals(label,e.optionalString("selection_basis"),a.selectionBasis); assertEquals(label,e.getBoolean("valid"),a.valid); assertEquals(label,e.getDouble("confidence"),a.confidence,1e-9)
                    assertPoint(label,if(e.isNull("raw_point")) null else e.getJSONObject("raw_point"),a.rawPoint)
                    assertPoint(label,if(e.isNull("point")) null else e.getJSONObject("point"),a.point)
                }
            }
        }
    }
    @Test fun pythonExpectedEffectStalenessTimeoutAndTerminalResultsMatch() {
        data.getJSONArray("verification").objects().forEachIndexed { trace,flow ->
            val verifier=CorePressVerifier(); val effect=flow.getJSONObject("effect")
            verifier.begin(flow.getJSONObject("before").screen(),CoreExpectedEffect(effect.optionalString("screen_type"),effect.optionalString("cart_item_key"),effect.optionalInt("before_quantity"),effect.optInt("quantity_delta",1)),flow.getDouble("started"))
            flow.getJSONArray("rows").objects().forEachIndexed { index,row ->
                val i=row.getJSONObject("input"); val e=row.getJSONObject("expected"); val label="verification[$trace:$index]"
                val cart=if(i.isNull("cart")) null else i.getJSONObject("cart").let { c -> c.keys().asSequence().associateWith { c.getInt(it) } }
                val result=runCatching { verifier.observe(if(i.isNull("after")) null else i.getJSONObject("after").screen(),i.getDouble("now"),i.optionalDouble("captured"),cart,i.optionalInt("cart_keyframe"),i.getBoolean("returned")) }
                if(e.optBoolean("error")) assertTrue(label,result.exceptionOrNull() is IllegalArgumentException)
                else { val a=result.getOrThrow(); assertEquals(label,e.getString("verdict"),a.verdict); assertEquals(label,e.getString("reason"),a.reason); assertEquals(label,e.getBoolean("terminal"),a.terminal); assertEquals(label,e.getBoolean("retain_progress"),a.retainProgress) }
            }
        }
    }
    @Test fun pythonScreenComparisonIgnoresIdsAndConfidence() {
        data.getJSONArray("changes").objects().forEach { r -> assertEquals(r.getBoolean("expected"),coreScreenChanged(r.getJSONObject("before").screen(),r.getJSONObject("after").screen())) }
    }
    @Test fun horizonAndInvalidTypedInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CoreHomography(List(9) { 0.0 }) }
        val horizon=CoreHomography(listOf(1.0,0.0,0.0,0.0,1.0,0.0,1.0,0.0,-1.0))
        assertThrows(IllegalArgumentException::class.java) { horizon.cameraToScreen(CorePoint(1.0,0.0)) }
        assertThrows(IllegalArgumentException::class.java) { CoreHand("h",List(20) { CorePoint(0.0,0.0) },.9) }
        assertThrows(IllegalArgumentException::class.java) { CoreExpectedEffect() }
        assertThrows(IllegalArgumentException::class.java) { CoreExpectedEffect(screenType="option",cartItemKey="x",beforeQuantity=0) }
        assertThrows(IllegalStateException::class.java) { CorePressVerifier().observe(null,0.0) }
    }
    @Test fun explicitNewAttemptReplacesTerminalAndQuantityDoesNotOverflow() {
        val target=CoreTarget("x","button",CoreBox(.1,.1,.2,.2),.95)
        val before=CoreScreen("menu",1,listOf(CoreElement(target)))
        val verifier=CorePressVerifier()
        verifier.begin(before,CoreExpectedEffect(screenType="option"),0.0)
        assertEquals("success",verifier.observe(before.copy(type="option",keyframe=2),.5,.4).verdict)
        verifier.begin(before,CoreExpectedEffect(screenType="option"),1.0)
        assertEquals("uncertain",verifier.observe(null,1.1).verdict)
        verifier.begin(before,CoreExpectedEffect(cartItemKey="x",beforeQuantity=Int.MAX_VALUE,quantityDelta=Int.MAX_VALUE),0.0)
        assertEquals("failure",verifier.observe(before.copy(keyframe=2),.5,.4,mapOf("x" to 0),2).verdict)
    }
}
