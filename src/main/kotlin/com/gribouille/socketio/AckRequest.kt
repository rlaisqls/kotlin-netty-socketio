
package com.gribouille.socketio

import com.gribouille.socketio.listener.DataListener
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

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
        get() = originalPacket.isAckRequested

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
        ackPacket.subType = PacketType.ACK
        ackPacket.ackId = originalPacket.ackId
        ackPacket.setData(objs)
        client.send(ackPacket)
    }
}