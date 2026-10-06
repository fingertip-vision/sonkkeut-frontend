package com.sonkkeut.app

import android.Manifest
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeRestartTest {
    @get:Rule val permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @Test fun rapidActivityReplacementKeepsNativeEngineAndFramesAlive() {
        repeat(3) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var model: NativeAppModel?=null
                scenario.onActivity { model=ViewModelProvider(it)[NativeAppModel::class.java] }
                waitUntil(60000) { model!!.ready }
                scenario.onActivity { model!!.start() }
                waitUntil(45000) { model!!.frames>=2 }
            }
            // Deliberately no engine-release wait: the next Activity may start immediately.
        }
    }
    private fun waitUntil(timeout: Long, condition: () -> Boolean) {
        val end=SystemClock.elapsedRealtime()+timeout
        while(!condition() && SystemClock.elapsedRealtime()<end) Thread.sleep(50)
        assertTrue("native readiness / frames timed out",condition())
    }
}
