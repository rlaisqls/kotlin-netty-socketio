
package com.gribouille.socketio

import com.gribouille.socketio.SocketIOClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BroadcastAckCallback<T> @JvmOverloads constructor(val resultClass: Class<T>, val timeout: Int = -1) {
    val loopFinished = AtomicBoolean()
    val counter = AtomicInteger()
    val successExecuted = AtomicBoolean()
    fun createClientCallback(client: SocketIOClient?): com.gribouille.socketio.AckCallback<T> {
        counter.getAndIncrement()
        return object : com.gribouille.socketio.AckCallback<T>(resultClass, timeout) {
            override fun onSuccess(result: T) {
                counter.getAndDecrement()
                onClientSuccess(client, result)
                executeSuccess()
            }

            override fun onTimeout() {
                onClientTimeout(client)
            }
        }
    }

    protected fun onClientTimeout(client: SocketIOClient?) {}
    protected fun onClientSuccess(client: SocketIOClient?, result: T) {}
    protected fun onAllSuccess() {}
    private fun executeSuccess() {
        if (loopFinished.get() && counter.get() == 0 && successExecuted.compareAndSet(false, true)) {
            onAllSuccess()
        }
    }

    fun loopFinished() {
        loopFinished.set(true)
        executeSuccess()
    }
}