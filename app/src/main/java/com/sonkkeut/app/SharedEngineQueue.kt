package com.sonkkeut.app

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Serializes initialization, commands, frame access and final release across Activity models. */
internal class SharedEngineQueue(
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val release: () -> Unit
) {
    private val gate=Any()
    private val clients=AtomicInteger()
    private var released=true

    fun open(): Session { clients.incrementAndGet(); return Session() }

    inner class Session internal constructor() {
        private val closed=AtomicBoolean(false)
        fun execute(block: () -> Unit) {
            if(closed.get()) return
            executor.execute { synchronized(gate) {
                if(!closed.get()) { released=false; block() }
            } }
        }
        fun <T> read(block: () -> T): T? = synchronized(gate) {
            if(closed.get()) null else block()
        }
        fun close(cleanup: () -> Unit = {}) {
            if(!closed.compareAndSet(false,true)) return
            clients.decrementAndGet()
            executor.execute { synchronized(gate) {
                try { cleanup() } finally {
                    if(clients.get()==0 && !released) { release(); released=true }
                }
            } }
        }
    }
}

internal object NativeEngineRuntime {
    val queue=SharedEngineQueue(release={ kr.sonkkeut.android.SonkkeutEngine.release() })
}
