
package com.gribouille.socketio

import com.gribouille.socketio.protocol.Packet

/**
 * author: liangjiaqi
 * date: 2020/8/8 6:02 PM
 */
class MultiRoomBroadcastOperations(private val broadcastOperations: Collection<com.gribouille.socketio.BroadcastOperations>?) :
    com.gribouille.socketio.BroadcastOperations {
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

    override fun <T> send(packet: Packet?, ackCallback: com.gribouille.socketio.BroadcastAckCallback<T>?) {
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

    override fun <T> sendEvent(name: String?, data: Any?, ackCallback: com.gribouille.socketio.BroadcastAckCallback<T>?) {
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
        ackCallback: com.gribouille.socketio.BroadcastAckCallback<T>?
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