
package com.gribouille.socketio.ack

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.listener.DataListener
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import java.util.concurrent.atomic.AtomicBoolean

class AckRequest(
    private val originalPacket: Packet,
    private val client: SocketIOClient
) {
    private val sended: AtomicBoolean = AtomicBoolean()

    val isAckRequested: Boolean
        get() = originalPacket.isAckRequested

    /**
     * client에게 ack data를 보낸다.
     * [DataListener.onData] 메소드가 호출되는 동안 한번 실행될 수 있다.
     */
    fun sendAckData(objs: List<Any>?) {
        if (!isAckRequested || !sended.compareAndSet(false, true)) {
            return
        }
        val ackPacket = Packet(PacketType.MESSAGE)
        ackPacket.subType = PacketType.ACK
        ackPacket.ackId = originalPacket.ackId
        ackPacket.data = objs
        client.send(ackPacket)
    }
}