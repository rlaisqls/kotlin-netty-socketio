package com.gribouille.socketio

import com.gribouille.socketio.protocol.Packet

class MultiRoomBroadcastOperations(
    private val broadcastOperations: MutableCollection<BroadcastOperations>
) : BroadcastOperations {
    override val clients: Collection<SocketIOClient>
        get() = HashSet<SocketIOClient>().apply {
            broadcastOperations.forEach {
                addAll(it.clients)
            }
        }

    override fun <T> send(packet: Packet, ackCallback: BroadcastAckCallback<T>) {
        broadcastOperations.forEach {
            it.send(packet, ackCallback)
        }
    }

    override fun sendEvent(
        name: String,
        excludedClient: SocketIOClient,
        data: Any
    ) {
        broadcastOperations.forEach {
            it.sendEvent(name, excludedClient, data)
        }
    }

    override fun <T> sendEvent(
        name: String,
        data: Any,
        ackCallback: BroadcastAckCallback<T>
    ) {
        broadcastOperations.forEach {
            it.sendEvent(name, data)
        }
    }

    override fun <T> sendEvent(
        name: String,
        data: Any,
        excludedClient: SocketIOClient,
        ackCallback: BroadcastAckCallback<T>
    ) {
        broadcastOperations.forEach {
            it.sendEvent(name, data, excludedClient, ackCallback)
        }
    }

    override fun send(packet: Packet) {
        broadcastOperations.forEach {
            it.send(packet)
        }
    }

    override fun disconnect() {
        broadcastOperations.forEach {
            it.disconnect()
        }
    }

    override fun sendEvent(name: String, data: Any) {
        broadcastOperations.forEach {
            it.sendEvent(name, data)
        }
    }
}