package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class AnnouncementGateTest {
    @Test fun repeatedStateDoesNotReplay() {
        val gate = AnnouncementGate()
        assertTrue(gate.accept("camera", "카메라 실행", 0))
        assertFalse(gate.accept("camera", "카메라 실행", 2000))
        assertEquals("카메라 실행", gate.lastText)
    }
    @Test fun changingDirectionsWaitsEightTenthsOfASecond() {
        val gate = AnnouncementGate()
        assertTrue(gate.accept("right", "오른쪽", 100, direction = true))
        assertFalse(gate.accept("left", "왼쪽", 899, direction = true))
        assertTrue(gate.accept("left", "왼쪽", 900, direction = true))
    }
    @Test fun recoveryInterruptsDirectionImmediately() {
        val gate = AnnouncementGate()
        gate.accept("right", "오른쪽", 100, direction = true)
        assertTrue(gate.accept("stop", "잠시 멈춰 주세요", 101))
        assertEquals("잠시 멈춰 주세요", gate.lastText)
    }
    @Test fun onlyOnePressUntilExplicitAttemptReset() {
        val gate = AnnouncementGate()
        assertTrue(gate.accept("press-a", "누름 안내", 0, press = true))
        gate.accept("recovery", "복구", 100)
        assertFalse(gate.accept("press-b", "누름 안내", 200, press = true))
        gate.clearDeduplication()
        assertFalse(gate.accept("press-c", "누름 안내", 300, press = true))
        gate.resetAttempt()
        assertTrue(gate.accept("press-c", "누름 안내", 400, press = true))
    }
    @Test fun resumeKeepsLastMessageAndAllowsStateReplay() {
        val gate = AnnouncementGate()
        gate.accept("camera", "카메라 실행", 0)
        gate.clearDeduplication()
        assertEquals("카메라 실행", gate.lastText)
        assertTrue(gate.accept("camera", "카메라 실행", 100))
    }
    @Test fun throttledMessagesDoNotReplaceRepeatText() {
        val gate = AnnouncementGate()
        gate.accept("right", "오른쪽", 100, direction = true)
        gate.accept("left", "왼쪽", 200, direction = true)
        assertEquals("오른쪽", gate.lastText)
    }
    @Test fun eightDirectionsAreDefinedAndUnknownIsRejected() {
        listOf("up", "up_right", "right", "down_right", "down", "down_left", "left", "up_left").forEach { assertNotNull(GuidancePhrases.movement(it, false)) }
        assertNull(GuidancePhrases.movement("unknown", false))
        assertTrue(GuidancePhrases.movement("right", true)!!.contains("조금"))
    }
    @Test fun vibrationPatternsAreFiniteWithPositivePulses() {
        listOf(false, true).forEach { near ->
            val timings = GuidancePhrases.vibration(near)
            assertEquals(0L, timings.first())
            assertTrue(timings.drop(1).all { it > 0 })
            assertTrue(timings.sum() < 2000)
        }
    }
}
