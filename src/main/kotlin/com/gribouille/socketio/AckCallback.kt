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

/**
 * Base ack callback class.
 *
 * Notifies about acknowledgement received from client
 * via [.onSuccess] callback method.
 *
 * By default it may wait acknowledgement from client
 * while [SocketIOClient] is alive. Timeout can be
 * defined [.timeout] as constructor argument.
 *
 * This object is NOT actual anymore if [.onSuccess] or
 * [.onTimeout] was executed.
 *
 * @param <T> - any serializable type
 *
 * @see com.corundumstudio.socketio.VoidAckCallback
 *
 * @see com.corundumstudio.socketio.MultiTypeAckCallback
</T> */
abstract class AckCallback<T>
/**
 * Create AckCallback
 *
 * @param resultClass - result class
 */ @JvmOverloads constructor(
    /**
     * Returns class of argument in [.onSuccess] method
     *
     * @return - result class
     */
    val resultClass: Class<T>, val timeout: Int = -1
) {
    /**
     * Creates AckCallback with timeout
     *
     * @param resultClass - result class
     * @param timeout - callback timeout in seconds
     */
    /**
     * Executes only once when acknowledgement received from client.
     *
     * @param result - object sended by client
     */
    abstract fun onSuccess(result: T)

    /**
     * Invoked only once then `timeout` defined
     *
     */
    open fun onTimeout() {}
}