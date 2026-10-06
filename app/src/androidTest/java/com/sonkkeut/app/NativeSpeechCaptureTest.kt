package com.sonkkeut.app

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeSpeechCaptureTest {
    @get:Rule val permission=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @Test fun manualFinishReportsAudioAndCancelAllowsAnotherCapture() {
        val capture=NativeSpeechCapture(InstrumentationRegistry.getInstrumentation().targetContext)
        val first=CountDownLatch(1); val stale=CountDownLatch(1); val second=CountDownLatch(1); val done=CountDownLatch(1)
        val result=AtomicReference<NativeSpeechCapture.Capture>(); val error=AtomicReference<Throwable>()
        try {
            capture.start({first.countDown()},{stale.countDown()},{stale.countDown()})
            assertTrue(first.await(5,TimeUnit.SECONDS)); capture.cancel()
            capture.start({second.countDown()},{result.set(it); done.countDown()},{error.set(it); done.countDown()})
            assertTrue(second.await(5,TimeUnit.SECONDS)); capture.finishCapture()
            assertTrue(done.await(5,TimeUnit.SECONDS)); assertNull(error.get()); assertEquals(1L,stale.count)
            assertTrue(result.get().pcm.size>=6400)
        } finally { capture.close() }
    }
}
