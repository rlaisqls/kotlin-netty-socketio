
package com.gribouille.socketio

import com.gribouille.socketio.protocol.Packet
import java.util.*

/**
 * Fully thread-safe.
 *
 */
interface SocketIOClient : ClientOperations, Store {
    /**
     * Handshake data used during client connection
     *
     * @return HandshakeData
     */
    val handshakeData: HandshakeData?

    /**
     * Current client transport protocol
     *
     * @return transport protocol
     */
    val transport: com.gribouille.socketio.Transport?

    /**
     * Send event with ack callback
     *
     * @param name - event name
     * @param data - event data
     * @param ackCallback - ack callback
     */
    fun sendEvent(name: String?, ackCallback: AckCallback<*>?, vararg data: Any?)

    /**
     * Send packet with ack callback
     *
     * @param packet - packet to send
     * @param ackCallback - ack callback
     */
    fun send(packet: Packet?, ackCallback: AckCallback<*>?)

    /**
     * Client namespace
     *
     * @return - namespace
     */
    val namespace: com.gribouille.socketio.SocketIONamespace?

    /**
     * Client session id, uses [UUID] object
     *
     * @return - session id
     */
    val sessionId: UUID

    /**
     * Get client remote address
     *
     * @return remote address
     */
    val remoteAddress: SocketAddress?

    /**
     * Check is underlying channel open
     *
     * @return `true` if channel open, otherwise `false`
     */
    val isChannelOpen: Boolean

    /**
     * Join client to room
     *
     * @param room - name of room
     */
    fun joinRoom(room: String?)

    /**
     * Join client to rooms
     *
     * @param rooms - names of rooms
     */
    fun joinRooms(rooms: Set<String?>?)

    /**
     * Leave client from room
     *
     * @param room - name of room
     */
    fun leaveRoom(room: String?)

    /**
     * Leave client from rooms
     *
     * @param rooms - names of rooms
     */
    fun leaveRooms(rooms: Set<String?>?)

    /**
     * Get all rooms a client is joined in.
     *
     * @return name of rooms
     */
    val allRooms: Set<String?>?

    /**
     * Get current room Size (contain in cluster)
     *
     * @param room - name of room
     *
     * @return int
     */
    fun getCurrentRoomSize(room: String?): Int
}