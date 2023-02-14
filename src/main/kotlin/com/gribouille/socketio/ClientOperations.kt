
package com.gribouille.socketio

import com.gribouille.socketio.protocol.Packet

/**
 * Available client operations
 *
 */
interface ClientOperations {
    /**
     * Send custom packet.
     * But [ClientOperations.sendEvent] method
     * usage is enough for most cases.
     *
     * @param packet - packet to send
     */
    fun send(packet: Packet?)

    /**
     * Disconnect client
     *
     */
    fun disconnect()

    /**
     * Send event
     *
     * @param name - event name
     * @param data - event data
     */
    fun sendEvent(name: String?, vararg data: Any?)
}