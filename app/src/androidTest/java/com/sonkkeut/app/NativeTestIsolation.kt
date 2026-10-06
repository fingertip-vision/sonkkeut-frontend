package com.sonkkeut.app

import android.os.SystemClock
import kr.sonkkeut.android.SonkkeutEngine
import org.junit.Assert.assertFalse

/** ActivityScenario closes synchronously; NativeAppModel's singleton release is queued. */
internal fun awaitNativeEngineRelease() {
    val deadline=SystemClock.elapsedRealtime()+15000L
    while(SonkkeutEngine.isReady && SystemClock.elapsedRealtime()<deadline) Thread.sleep(25)
    assertFalse("Previous Activity's native engine must finish releasing before the next test starts",SonkkeutEngine.isReady)
}
