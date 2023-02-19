
package com.gribouille.socketio

import com.gribouille.socketio.protocol.Packet

interface ClientOperations {
    fun send(packet: Packet)
    fun disconnect()
    fun sendEvent(name: String, vararg data: Any)
}