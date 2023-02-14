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
package com.corundumstudio.socketio

import com.corundumstudio.socketio.listener.ClientListeners
import java.util.*

/**
 * Fully thread-safe.
 *
 */
interface SocketIONamespace : ClientListeners {
    val name: String?
    val broadcastOperations: BroadcastOperations?
    fun getRoomOperations(room: String?): BroadcastOperations?

    /**
     * Get all clients connected to namespace
     *
     * @return collection of clients
     */
    val allClients: Collection<com.corundumstudio.socketio.SocketIOClient?>?

    /**
     * Get client by uuid connected to namespace
     *
     * @param uuid - id of client
     * @return client
     */
    fun getClient(uuid: UUID?): SocketIOClient?
}