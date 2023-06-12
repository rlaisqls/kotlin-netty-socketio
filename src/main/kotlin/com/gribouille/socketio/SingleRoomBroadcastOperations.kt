package com.gribouille.socketio

import com.gribouille.socketio.ack.BroadcastAckCallback
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType

class SingleRoomBroadcastOperations(
    override val clients: Collection<SocketIOClient>,
    private val namespace: String,
    private val room: String
) : BroadcastOperations {

    override fun send(packet: Packet) {
        for (client in clients) {
            client.send(packet)
        }
    }

    override fun <T> send(packet: Packet, ackCallback: BroadcastAckCallback<T>) {
        for (client in clients) {
            client.send(packet, ackCallback.createClientCallback(client))
        }
        ackCallback.loopFinished()
    }

    override fun disconnect() {
        for (client in clients) {
            client.disconnect()
        }
    }

    override fun sendEvent(name: String, excludedClient: SocketIOClient, data: Any) {
        val packet = Packet(PacketType.MESSAGE).apply {
            this.subType = PacketType.EVENT
            this.name = name
            this.data = data
        }
        for (client in clients) {
            if (client.sessionId == excludedClient.sessionId) {
                continue
            }
            client.send(packet)
        }
    }

    override fun sendEvent(name: String, data: Any) {
        val packet = Packet(PacketType.MESSAGE).apply {
            this.subType = PacketType.EVENT
            this.name = name
            this.data = data
        }
        send(packet)
    }

    override fun <T> sendEvent(
        name: String,
        data: Any,
        ackCallback: BroadcastAckCallback<T>
    ) {
        for (client in clients) {
            client.sendEvent(name, ackCallback.createClientCallback(client), data)
        }
        ackCallback.loopFinished()
    }

    override fun <T> sendEvent(
        name: String,
        data: Any,
        excludedClient: SocketIOClient,
        ackCallback: BroadcastAckCallback<T>,
    ) {
        for (client in clients) {
            if (client.sessionId == excludedClient.sessionId) {
                continue
            }
            client.sendEvent(name, ackCallback.createClientCallback(client), data)
        }
        ackCallback.loopFinished()
    }
}