
package com.gribouille.socketio

import com.gribouille.socketio.listener.ClientListeners
import java.util.*

/**
 * Fully thread-safe.
 *
 */
interface SocketIONamespace : ClientListeners {
    val name: String?
    val broadcastOperations: BroadcastOperations?
    fun getRoomOperations(room: String?): BroadcastOperations?

    /**
     * Get all clients connected to namespace
     *
     * @return collection of clients
     */
    val allClients: Collection<com.gribouille.socketio.SocketIOClient?>?

    /**
     * Get client by uuid connected to namespace
     *
     * @param uuid - id of client
     * @return client
     */
    fun getClient(uuid: UUID?): SocketIOClient?
}