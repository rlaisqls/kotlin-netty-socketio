
package com.gribouille.socketio.messages

import com.gribouille.socketio.handler.ClientHead

class OutPacketMessage(clientHead: ClientHead, transport: com.gribouille.socketio.Transport) :
    HttpMessage(clientHead.getOrigin(), clientHead.getSessionId()) {
    private val clientHead: ClientHead
    private val transport: com.gribouille.socketio.Transport

    init {
        this.clientHead = clientHead
        this.transport = transport
    }

    fun getTransport(): com.gribouille.socketio.Transport {
        return transport
    }

    fun getClientHead(): ClientHead {
        return clientHead
    }
}