package com.gribouille.socketio

import com.gribouille.socketio.ack.BroadcastAckCallback
import com.gribouille.socketio.protocol.Packet

interface BroadcastOperations : ClientOperations {
    val clients: Collection<SocketIOClient>
    fun <T> send(packet: Packet, ackCallback: BroadcastAckCallback<T>)
    fun sendEvent(
        name: String,
        excludedClient: SocketIOClient,
        data: Any
    )
    fun <T> sendEvent(
        name: String,
        data: Any,
        ackCallback: BroadcastAckCallback<T>
    )
    fun <T> sendEvent(
        name: String,
        data: Any,
        excludedClient: SocketIOClient,
        ackCallback: BroadcastAckCallback<T>
    )
}