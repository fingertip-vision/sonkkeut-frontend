package com.sonkkeut.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VisualGuidanceCoreTest {
    private val reference get() = JSONObject(javaClass.getResource("/guidance-reference.json")!!.readText())
    private fun point(input: JSONObject, key: String): CorePoint? = if (input.isNull(key)) null else input.getJSONArray(key).let { CorePoint(it.getDouble(0), it.getDouble(1)) }
    @Test fun pythonObservationTracesMatchAllDecisionFields() {
        val cases = reference.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i); val core = VisualGuidanceCore(); val rows = case.getJSONArray("rows")
            for (j in 0 until rows.length()) {
                val row = rows.getJSONObject(j); val input = row.getJSONObject("input"); val expected = row.getJSONObject("expected")
                if (input.optBoolean("reset")) core.resetAttempt()
                val target = if (input.isNull("target")) null else input.getJSONObject("target").let { t ->
                    val b = t.getJSONArray("box"); CoreTarget(t.getString("id"), t.getString("kind"), CoreBox(b.getDouble(0), b.getDouble(1), b.getDouble(2), b.getDouble(3)), t.getDouble("conf"))
                }
                val label = "${case.getString("name")}[$j]"
                val result = runCatching { core.update(point(input,"point"), target, input.getDouble("now"), point(input,"raw"), input.getDouble("confidence"), input.getInt("keyframe"), input.getString("hand")) }
                if (expected.optBoolean("error")) { assertTrue(label, result.exceptionOrNull() is IllegalArgumentException); continue }
                val actual = result.getOrThrow()
                assertEquals(label, expected.getString("action"), actual.action)
                assertEquals(label, expected.getString("reason"), actual.reason)
                assertEquals(label, if (expected.isNull("direction")) null else expected.getString("direction"), actual.direction)
                assertEquals(label, if (expected.isNull("distance_band")) null else expected.getString("distance_band"), actual.distanceBand)
                assertEquals(label, expected.getBoolean("recenter"), actual.recenter)
                assertEquals(label, expected.getBoolean("reacquire"), actual.reacquire)
                assertEquals(label, expected.getDouble("dwell_seconds"), actual.dwellSeconds, 1e-9)
                listOf("dx" to actual.dx, "dy" to actual.dy, "distance" to actual.distance).forEach { (key,value) ->
                    if (expected.isNull(key)) assertNull(label, value) else assertEquals(label, expected.getDouble(key), value!!, 1e-9)
                }
            }
        }
    }
    @Test fun pythonDirectionSectorsMatch() {
        val rows = reference.getJSONArray("directions")
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            assertEquals("direction[$i]", if (r.isNull("expected")) null else r.getString("expected"), VisualGuidanceCore.direction(r.getDouble("dx"),r.getDouble("dy")))
        }
    }
    @Test fun nonFiniteAndInvalidTypedInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CorePoint(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { CoreBox(.5,.4,.4,.6) }
        assertThrows(IllegalArgumentException::class.java) { VisualGuidanceCore(dwellDuration = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { VisualGuidanceCore().update(null,null,Double.POSITIVE_INFINITY) }
    }
    @Test fun recenterAndReacquireInterruptMovementThroughAdapter() {
        listOf(CoreDecision("move", "right", recenter = true), CoreDecision("stop", reason = "missing_target", reacquire = true)).forEach { decision ->
            val session = SyntheticReplay(); session.start()
            val feedback = session.accept(ReplayEvent(session.session,1,session.target,0,decision.asReplayDecision()))!!
            assertEquals(ReplayState.RECOVERY, session.state)
            assertFalse(feedback.press)
            assertFalse(feedback.direction)
            assertTrue(feedback.text.contains("재시작"))
        }
    }
}
