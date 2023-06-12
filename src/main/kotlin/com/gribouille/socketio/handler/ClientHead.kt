package com.gribouille.socketio.handler

import com.gribouille.socketio.HandshakeData
import com.gribouille.socketio.Transport
import com.gribouille.socketio.configuration
import com.gribouille.socketio.disconnectableHub
import com.gribouille.socketio.messages.OutPacketMessage
import com.gribouille.socketio.namespace.Namespace
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.SchedulerKey.Type
import com.gribouille.socketio.scheduler.scheduler
import com.gribouille.socketio.store.Store
import com.gribouille.socketio.storeFactory
import com.gribouille.socketio.transport.NamespaceClient
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.util.AttributeKey
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

class ClientHead(
    @Volatile
    private var currentTransport: Transport,
    val sessionId: UUID?,
    val handshakeData: HandshakeData
) {
    private val disconnected: AtomicBoolean = AtomicBoolean()
    private val namespaceClients: MutableMap<Namespace?, NamespaceClient> = ConcurrentHashMap()
    private val channels: MutableMap<Transport, TransportState> = HashMap(2)

    val store: Store = storeFactory.createStore(sessionId)!!
    var lastBinaryPacket: Packet? = null
    val origin: String?
        get() = handshakeData.httpHeaders.get(HttpHeaderNames.ORIGIN)

    init {
        channels[Transport.POLLING] = TransportState()
        channels[Transport.WEBSOCKET] = TransportState()
    }

    fun bindChannel(channel: Channel?, transport: Transport) {
        log.debug("binding channel: {} to transport: {}", channel, transport)
        val state = channels[transport]
        val prevChannel = state!!.update(channel)
        if (prevChannel != null) {
            clientsBox.remove(prevChannel)
        }
        clientsBox.add(channel, this)
        sendPackets(transport, channel)
    }

    fun releasePollingChannel(channel: Channel) {
        val state = channels[Transport.POLLING]!!
        if (channel == state.channel) {
            clientsBox.remove(channel)
            state.update(null)
        }
    }

    fun send(packet: Packet?): ChannelFuture? {
        return send(packet, getCurrentTransport())
    }

    private fun cancelPing() {
        val key = SchedulerKey(Type.PING, sessionId)
        scheduler.cancel(key)
    }

    private fun cancelPingTimeout() {
        val key = SchedulerKey(Type.PING_TIMEOUT, sessionId)
        scheduler.cancel(key)
    }

    fun schedulePing() {
        cancelPing()
        scheduler.schedule(
            key = SchedulerKey(Type.PING, sessionId),
            delay = configuration.pingInterval,
            runnable = {
                clientsBox[sessionId]?.run {
                    send(Packet(PacketType.PING))
                    schedulePing()
                }
            }
        )
    }

    fun schedulePingTimeout() {
        cancelPingTimeout()
        scheduler.schedule(
            key = SchedulerKey(Type.PING_TIMEOUT, sessionId),
            delay = configuration.pingTimeout + configuration.pingInterval,
            runnable = {
                clientsBox[sessionId]?.let {
                    it.disconnect()
                    log.debug("{} removed due to ping timeout", sessionId)
                }
            }
        )
    }

    fun send(packet: Packet?, transport: Transport): ChannelFuture? {
        val state = channels[transport]!!
        state.packetsQueue.add(packet)
        val channel = state.channel
        return if (channel == null || transport == Transport.POLLING &&
            channel.attr(EncoderHandler.WRITE_ONCE).get() != null
        ) null else sendPackets(transport, channel)
    }

    private fun sendPackets(transport: Transport, channel: Channel?): ChannelFuture {
        return channel!!.writeAndFlush(OutPacketMessage(this, transport))
    }

    fun removeNamespaceClient(client: NamespaceClient) {
        namespaceClients.remove(client.namespace)
        if (namespaceClients.isEmpty()) {
            disconnectableHub.onDisconnect(this)
        }
    }

    fun getChildClient(namespace: Namespace) =
        namespaceClients[namespace]

    fun addNamespaceClient(namespace: Namespace) =
        NamespaceClient(this, namespace).also {
            namespaceClients[namespace] = it
        }

    val namespaces: Set<Any?>
        get() = namespaceClients.keys

    val isConnected: Boolean
        get() = !disconnected.get()

    val remoteAddress: SocketAddress
        get() = handshakeData.address

    fun isTransportChannel(channel: Channel, transport: Transport) =
        channels[transport]?.channel == channel

    val isChannelOpen: Boolean
        get() {
            for (state in channels.values) {
                if (state.channel != null && state.channel!!.isActive) return true
            }
            return false
        }

    fun onChannelDisconnect() {
        cancelPing()
        cancelPingTimeout()
        disconnected.set(true)
        for (client in namespaceClients.values) {
            client.onDisconnect()
        }
        for (state in channels.values) {
            if (state.channel != null) {
                clientsBox.remove(state.channel)
            }
        }
    }

    fun disconnect() {
        send(Packet(PacketType.DISCONNECT))?.addListener(ChannelFutureListener.CLOSE)
        onChannelDisconnect()
    }

    fun upgradeCurrentTransport(currentTransport: Transport) {
        val state = channels[currentTransport]!!
        for ((key, value) in channels) {
            if (key != currentTransport) {

                state.packetsQueue = value.packetsQueue
                sendPackets(currentTransport, state.channel)
                this.currentTransport = currentTransport

                log.debug("Transport upgraded to: {} for: {}", currentTransport, sessionId)
                break
            }
        }
    }

    fun getCurrentTransport() =
        currentTransport

    fun getPacketsQueue(transport: Transport) =
        channels[transport]!!.packetsQueue

    companion object {
        private val log = LoggerFactory.getLogger(ClientHead::class.java)
        val CLIENT = AttributeKey.valueOf<ClientHead>("client")
    }
}