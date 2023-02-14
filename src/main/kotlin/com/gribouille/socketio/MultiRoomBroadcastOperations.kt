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

import com.corundumstudio.socketio.protocol.Packet

/**
 * author: liangjiaqi
 * date: 2020/8/8 6:02 PM
 */
class MultiRoomBroadcastOperations(private val broadcastOperations: Collection<BroadcastOperations>?) :
    BroadcastOperations {
    override val clients: Collection<Any?>
        get() {
            val clients: MutableSet<SocketIOClient?> = HashSet<SocketIOClient?>()
            if (broadcastOperations == null || broadcastOperations.size == 0) {
                return clients
            }
            for (b in broadcastOperations) {
                clients.addAll(b.clients)
            }
            return clients
        }

    override fun <T> send(packet: Packet?, ackCallback: BroadcastAckCallback<T>?) {
        if (broadcastOperations == null || broadcastOperations.size == 0) {
            return
        }
        for (b in broadcastOperations) {
            b.send(packet, ackCallback)
        }
    }

    override fun sendEvent(name: String?, excludedClient: SocketIOClient?, vararg data: Any?) {
        if (broadcastOperations == null || broadcastOperations.size == 0) {
            return
        }
        for (b in broadcastOperations) {
            b.sendEvent(name, excludedClient, *data)
        }
    }

    override fun <T> sendEvent(name: String?, data: Any?, ackCallback: BroadcastAckCallback<T>?) {
        if (broadcastOperations == null || broadcastOperations.size == 0) {
            return
        }
        for (b in broadcastOperations) {
            b.sendEvent(name, data, ackCallback)
        }
    }

    override fun <T> sendEvent(
        name: String?,
        data: Any?,
        excludedClient: SocketIOClient?,
        ackCallback: BroadcastAckCallback<T>?
    ) {
        if (broadcastOperations == null || broadcastOperations.size == 0) {
            return
        }
        for (b in broadcastOperations) {
            b.sendEvent(name, data, excludedClient, ackCallback)
        }
    }

    override fun send(packet: Packet?) {
        if (broadcastOperations == null || broadcastOperations.size == 0) {
            return
        }
        for (b in broadcastOperations) {
            b.send(packet)
        }
    }

    override fun disconnect() {
        if (broadcastOperations == null || broadcastOperations.size == 0) {
            return
        }
        for (b in broadcastOperations) {
            b.disconnect()
        }
    }

    override fun sendEvent(name: String?, vararg data: Any?) {
        if (broadcastOperations == null || broadcastOperations.size == 0) {
            return
        }
        for (b in broadcastOperations) {
            b.sendEvent(name, *data)
        }
    }
}