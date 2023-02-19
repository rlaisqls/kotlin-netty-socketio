
package com.gribouille.socketio

import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.store.Store
import java.net.SocketAddress
import java.util.*

/**
 * Fully thread-safe.
 *
 */
interface SocketIOClient : ClientOperations, Store {

    val handshakeData: HandshakeData?
    val transport: Transport?
    val namespace: SocketIONamespace?
    val sessionId: UUID
    val remoteAddress: SocketAddress?
    val isChannelOpen: Boolean
    val allRooms: Set<String>
    fun sendEvent(name: String, ackCallback: AckCallback, vararg data: Any)
    fun send(packet: Packet, ackCallback: AckCallback)
    fun joinRoom(room: String)
    fun joinRooms(rooms: Set<String>)
    fun leaveRoom(room: String)
    fun leaveRooms(rooms: Set<String>)
    fun getCurrentRoomSize(room: String): Int
}