
package com.gribouille.socketio

import com.gribouille.socketio.listener.ClientListeners
import java.util.*

interface SocketIONamespace : ClientListeners {
    val name: String
    val broadcastOperations: BroadcastOperations?
    val allClients: Collection<SocketIOClient>
    fun getRoomOperations(room: String): BroadcastOperations?
    fun getClient(uuid: UUID): SocketIOClient?
}