package com.sonkkeut.app

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SharedEngineQueueTest {
    private class Pending : Executor {
        val tasks=ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while(tasks.isNotEmpty()) tasks.remove().run() }
    }
    @Test fun delayedOldReleaseCannotTearDownNewlyOpenedModel() {
        val executor=Pending(); var ready=false; var releases=0
        val queue=SharedEngineQueue(executor) { ready=false; releases++ }
        val first=queue.open(); first.execute { ready=true }; executor.drain()
        first.close()
        val second=queue.open(); second.execute { ready=true }
        executor.drain()
        assertTrue(ready); assertEquals(0,releases)
        second.close(); executor.drain()
        assertFalse(ready); assertEquals(1,releases)
    }
    @Test fun closedModelsCannotRunQueuedCommandsOrReadFrames() {
        val executor=Pending(); var commands=0; var cleanups=0; var releases=0
        val queue=SharedEngineQueue(executor) { releases++ }
        val session=queue.open(); session.execute { commands++ }; session.close { cleanups++ }
        session.close { cleanups++ }; session.execute { commands++ }; executor.drain()
        assertEquals(0,commands); assertEquals(1,cleanups); assertEquals(0,releases)
        assertNull(session.read { "stale frame" })
    }
    @Test fun overlappingModelsReleaseOnlyAfterLastClientCloses() {
        val executor=Pending(); var releases=0
        val queue=SharedEngineQueue(executor) { releases++ }
        val first=queue.open(); val second=queue.open()
        first.execute {}; executor.drain()
        first.close(); second.close(); executor.drain()
        assertEquals(1,releases)
    }
    @Test fun releaseWaitsForInFlightFrame() {
        val executor=Pending(); var released=false
        val queue=SharedEngineQueue(executor) { released=true }
        val session=queue.open(); session.execute {}; executor.drain()
        val entered=CountDownLatch(1); val finish=CountDownLatch(1)
        val frameThread=Thread { session.read { entered.countDown(); assertTrue(finish.await(3,TimeUnit.SECONDS)) } }
        frameThread.start(); assertTrue(entered.await(3,TimeUnit.SECONDS)); session.close()
        val releaseThread=Thread { executor.drain() }; releaseThread.start()
        assertFalse(released); finish.countDown(); frameThread.join(3000); releaseThread.join(3000)
        assertFalse(frameThread.isAlive); assertFalse(releaseThread.isAlive); assertTrue(released)
    }
}
