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

import com.corundumstudio.socketio.misc.IterableCollection
import java.util.*

/**
 * Author: liangjiaqi
 * Date: 2020/8/8 6:08 PM
 */
class SingleRoomBroadcastOperations(
    private val namespace: String,
    private val room: String,
    clients: Iterable<SocketIOClient>,
    storeFactory: StoreFactory
) : BroadcastOperations {
    private val clients: Iterable<SocketIOClient>
    private val storeFactory: StoreFactory

    init {
        this.clients = clients
        this.storeFactory = storeFactory
    }

    private fun dispatch(packet: Packet) {
        storeFactory.pubSubStore().publish(
            PubSubType.DISPATCH,
            DispatchMessage(room, packet, namespace)
        )
    }

    fun getClients(): Collection<SocketIOClient> {
        return IterableCollection<SocketIOClient>(clients)
    }

    fun send(packet: Packet) {
        for (client in clients) {
            client.send(packet)
        }
        dispatch(packet)
    }

    fun <T> send(packet: Packet?, ackCallback: BroadcastAckCallback<T>) {
        for (client in clients) {
            client.send(packet, ackCallback.createClientCallback(client))
        }
        ackCallback.loopFinished()
    }

    fun disconnect() {
        for (client in clients) {
            client.disconnect()
        }
    }

    fun sendEvent(name: String?, excludedClient: SocketIOClient, vararg data: Any?) {
        val packet = Packet(PacketType.MESSAGE)
        packet.setSubType(PacketType.EVENT)
        packet.setName(name)
        packet.setData(Arrays.asList(*data))
        for (client in clients) {
            if (client.getSessionId().equals(excludedClient.getSessionId())) {
                continue
            }
            client.send(packet)
        }
        dispatch(packet)
    }

    fun sendEvent(name: String?, vararg data: Any?) {
        val packet = Packet(PacketType.MESSAGE)
        packet.setSubType(PacketType.EVENT)
        packet.setName(name)
        packet.setData(Arrays.asList(*data))
        send(packet)
    }

    fun <T> sendEvent(name: String?, data: Any?, ackCallback: BroadcastAckCallback<T>) {
        for (client in clients) {
            client.sendEvent(name, ackCallback.createClientCallback(client), data)
        }
        ackCallback.loopFinished()
    }

    fun <T> sendEvent(name: String?, data: Any?, excludedClient: SocketIOClient, ackCallback: BroadcastAckCallback<T>) {
        for (client in clients) {
            if (client.getSessionId().equals(excludedClient.getSessionId())) {
                continue
            }
            client.sendEvent(name, ackCallback.createClientCallback(client), data)
        }
        ackCallback.loopFinished()
    }
}