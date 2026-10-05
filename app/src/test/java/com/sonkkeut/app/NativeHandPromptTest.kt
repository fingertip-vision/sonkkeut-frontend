package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class NativeHandPromptTest {
    @Test fun uncertainHandAndBentIndexNeverProduceADirectionPrompt() {
        assertEquals("☝ 손끝을 다시 보여 주세요",NativeHandPrompt.hint(mapOf("tip_conf" to .49,"tip_pointing" to 1)))
        assertEquals("☝ 검지만 펴 주세요",NativeHandPrompt.hint(mapOf("tip_conf" to .9,"tip_pointing" to .49)))
        assertEquals("☝ 손끝을 다시 보여 주세요",NativeHandPrompt.hint(mapOf("tip_conf" to Double.NaN)))
    }
    @Test fun missingOrAcceptedMeasurementsKeepTheExistingNativeGuidance() {
        assertNull(NativeHandPrompt.hint(emptyMap()))
        assertNull(NativeHandPrompt.hint(mapOf("tip_conf" to .5,"tip_pointing" to .5)))
    }
}
