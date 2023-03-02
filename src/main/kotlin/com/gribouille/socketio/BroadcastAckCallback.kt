
package com.gribouille.socketio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BroadcastAckCallback<T> @JvmOverloads constructor(val resultClass: Class<T>, val timeout: Int = -1) {
    val loopFinished = AtomicBoolean()
    val counter = AtomicInteger()
    val successExecuted = AtomicBoolean()

    fun createClientCallback(client: SocketIOClient?): AckCallback {
        counter.getAndIncrement()
        return object : AckCallback(resultClass, timeout) {
            override fun onSuccess(result: Any?) {
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
    protected fun onClientSuccess(client: SocketIOClient?, result: Any?) {}
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