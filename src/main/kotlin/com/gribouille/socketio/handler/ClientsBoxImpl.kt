
package com.gribouille.socketio.handler

import com.gribouille.socketio.HandshakeData
import io.netty.channel.Channel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal val clientsBox = object : ClientsBoxInterface by ClientsBoxImpl() {}

internal interface ClientsBoxInterface {
    fun getHandshakeData(sessionId: UUID?): HandshakeData?
    fun addClient(clientHead: ClientHead)
    fun removeClient(sessionId: UUID?)
    fun add(channel: Channel?, clientHead: ClientHead)
    fun remove(channel: Channel?)
    operator fun get(channel: Channel?): ClientHead?
    operator fun get(sessionId: UUID?): ClientHead?
}

private class ClientsBoxImpl: ClientsBoxInterface {

    private val uuid2clients: MutableMap<UUID?, ClientHead> = ConcurrentHashMap()
    private val channel2clients: MutableMap<Channel?, ClientHead> = ConcurrentHashMap()

    // TODO use storeFactory
    override fun getHandshakeData(sessionId: UUID?): HandshakeData? {
        val client = uuid2clients[sessionId] ?: return null
        return client.handshakeData
    }

    override fun addClient(clientHead: ClientHead) {
        uuid2clients[clientHead.sessionId] = clientHead
    }

    override fun removeClient(sessionId: UUID?) {
        uuid2clients.remove(sessionId)
    }

    override fun add(channel: Channel?, clientHead: ClientHead) {
        channel2clients[channel] = clientHead
    }

    override fun remove(channel: Channel?) {
        channel2clients.remove(channel)
    }

    override operator fun get(channel: Channel?): ClientHead? {
        return channel2clients[channel]
    }

    override operator fun get(sessionId: UUID?): ClientHead? {
        return uuid2clients[sessionId]
    }
}