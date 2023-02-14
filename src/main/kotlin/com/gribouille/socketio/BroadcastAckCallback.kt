/**
 * Copyright (c) 2012-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.gribouille

import com.corundumstudio.socketio.SocketIOClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BroadcastAckCallback<T> @JvmOverloads constructor(val resultClass: Class<T>, val timeout: Int = -1) {
    val loopFinished = AtomicBoolean()
    val counter = AtomicInteger()
    val successExecuted = AtomicBoolean()
    fun createClientCallback(client: SocketIOClient?): AckCallback<T> {
        counter.getAndIncrement()
        return object : AckCallback<T>(resultClass, timeout) {
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