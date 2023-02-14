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

import com.corundumstudio.socketio.listener.DataListener
import java.util.*

/**
 * Ack request received from Socket.IO client.
 * You can always check is it `true` through
 * [.isAckRequested] method.
 *
 * You can call [.sendAckData] methods only during
 * [DataListener.onData] invocation. If [.sendAckData]
 * not called it will be invoked with empty arguments right after
 * [DataListener.onData] method execution by server.
 *
 * This object is NOT actual anymore if [.sendAckData] was
 * executed or [DataListener.onData] invocation finished.
 *
 */
class AckRequest(originalPacket: Packet, client: SocketIOClient) {
    private val originalPacket: Packet
    private val client: SocketIOClient
    private val sended: AtomicBoolean = AtomicBoolean()

    init {
        this.originalPacket = originalPacket
        this.client = client
    }

    val isAckRequested: Boolean
        /**
         * Check whether ack request was made
         *
         * @return true if ack requested by client
         */
        get() = originalPacket.isAckRequested()

    /**
     * Send ack data to client.
     * Can be invoked only once during [DataListener.onData]
     * method invocation.
     *
     * @param objs - ack data objects
     */
    fun sendAckData(vararg objs: Any?) {
        val args = Arrays.asList(*objs)
        sendAckData(args)
    }

    /**
     * Send ack data to client.
     * Can be invoked only once during [DataListener.onData]
     * method invocation.
     *
     * @param objs - ack data object list
     */
    fun sendAckData(objs: List<Any>?) {
        if (!isAckRequested || !sended.compareAndSet(false, true)) {
            return
        }
        val ackPacket = Packet(PacketType.MESSAGE)
        ackPacket.setSubType(PacketType.ACK)
        ackPacket.setAckId(originalPacket.getAckId())
        ackPacket.setData(objs)
        client.send(ackPacket)
    }
}