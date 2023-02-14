
package com.gribouille.socketio.messages

import com.gribouille.socketio.handler.ClientHead

class PacketsMessage(client: ClientHead, content: ByteBuf, transport: com.gribouille.socketio.Transport) {
    private val client: ClientHead
    private val content: ByteBuf
    private val transport: com.gribouille.socketio.Transport

    init {
        this.client = client
        this.content = content
        this.transport = transport
    }

    fun getTransport(): com.gribouille.socketio.Transport {
        return transport
    }

    fun getClient(): ClientHead {
        return client
    }

    fun getContent(): ByteBuf {
        return content
    }
}