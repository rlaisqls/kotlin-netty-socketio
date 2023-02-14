
package com.gribouille.socketio.handler

import com.gribouille.socketio.HandshakeData
import io.netty.channel.Channel
import io.netty.util.internal.PlatformDependent
import java.util.*

class ClientsBox {
    private val uuid2clients: MutableMap<UUID?, ClientHead> = ConcurrentHashMap()
    private val channel2clients: MutableMap<Channel?, ClientHead> = ConcurrentHashMap()

    // TODO use storeFactory
    fun getHandshakeData(sessionId: UUID?): HandshakeData? {
        val client = uuid2clients[sessionId] ?: return null
        return client.handshakeData
    }

    fun addClient(clientHead: ClientHead) {
        uuid2clients[clientHead.sessionId] = clientHead
    }

    fun removeClient(sessionId: UUID?) {
        uuid2clients.remove(sessionId)
    }

    operator fun get(sessionId: UUID?): ClientHead? {
        return uuid2clients[sessionId]
    }

    fun add(channel: Channel?, clientHead: ClientHead) {
        channel2clients[channel] = clientHead
    }

    fun remove(channel: Channel?) {
        channel2clients.remove(channel)
    }

    operator fun get(channel: Channel?): ClientHead? {
        return channel2clients[channel]
    }
}