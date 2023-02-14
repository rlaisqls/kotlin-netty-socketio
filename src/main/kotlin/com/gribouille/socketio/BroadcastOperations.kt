
package com.gribouille.socketio

import com.gribouille.socketio.protocol.Packet

/**
 * broadcast interface
 *
 */
interface BroadcastOperations : com.gribouille.socketio.ClientOperations {
    val clients: Collection<Any?>
    fun <T> send(packet: Packet?, ackCallback: com.gribouille.socketio.BroadcastAckCallback<T>?)
    fun sendEvent(name: String?, excludedClient: SocketIOClient?, vararg data: Any?)
    fun <T> sendEvent(name: String?, data: Any?, ackCallback: com.gribouille.socketio.BroadcastAckCallback<T>?)
    fun <T> sendEvent(name: String?, data: Any?, excludedClient: SocketIOClient?, ackCallback: com.gribouille.socketio.BroadcastAckCallback<T>?)
}