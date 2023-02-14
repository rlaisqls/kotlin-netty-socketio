
package com.gribouille.socketio.handler

import com.gribouille.socketio.HandshakeData
import com.gribouille.socketio.Configuration
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import io.netty.util.internal.PlatformDependent
import org.slf4j.LoggerFactory
import java.util.*

class ClientHead(
    val sessionId: UUID?,
    ackManager: AckManager,
    disconnectable: DisconnectableHub,
    storeFactory: StoreFactory,
    handshakeData: HandshakeData,
    clientsBox: ClientsBox,
    transport: com.gribouille.socketio.Transport,
    scheduler: CancelableScheduler,
    configuration: com.gribouille.socketio.Configuration
) {
    private val disconnected: AtomicBoolean = AtomicBoolean()
    private val namespaceClients: MutableMap<Namespace?, NamespaceClient> =
        PlatformDependent.newConcurrentHashMap<Namespace?, NamespaceClient>()
    private val channels: MutableMap<com.gribouille.socketio.Transport, TransportState> =
        HashMap<com.gribouille.socketio.Transport, TransportState>(2)
    private val handshakeData: HandshakeData
    private val store: Store
    private val disconnectableHub: DisconnectableHub
    private val ackManager: AckManager
    private val clientsBox: ClientsBox
    private val scheduler: CancelableScheduler
    private val configuration: com.gribouille.socketio.Configuration
    var lastBinaryPacket: Packet? = null

    // TODO use lazy set
    @Volatile
    private var currentTransport: com.gribouille.socketio.Transport

    init {
        this.ackManager = ackManager
        disconnectableHub = disconnectable
        store = storeFactory.createStore(sessionId)
        this.handshakeData = handshakeData
        this.clientsBox = clientsBox
        currentTransport = transport
        this.scheduler = scheduler
        this.configuration = configuration
        channels[com.gribouille.socketio.Transport.POLLING] = TransportState()
        channels[com.gribouille.socketio.Transport.WEBSOCKET] = TransportState()
    }

    fun bindChannel(channel: Channel?, transport: com.gribouille.socketio.Transport) {
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
        val state = channels[com.gribouille.socketio.Transport.POLLING]
        if (channel == state.getChannel()) {
            clientsBox.remove(channel)
            state!!.update(null)
        }
    }

    val origin: String
        get() = handshakeData.getHttpHeaders().get(HttpHeaderNames.ORIGIN)

    fun send(packet: Packet?): ChannelFuture? {
        return send(packet, getCurrentTransport())
    }

    fun cancelPing() {
        val key = SchedulerKey(Type.PING, sessionId)
        scheduler.cancel(key)
    }

    fun cancelPingTimeout() {
        val key = SchedulerKey(Type.PING_TIMEOUT, sessionId)
        scheduler.cancel(key)
    }

    fun schedulePing() {
        cancelPing()
        val key = SchedulerKey(Type.PING, sessionId)
        scheduler.schedule(key, Runnable {
            val client = clientsBox[sessionId]
            if (client != null) {
                client.send(Packet(PacketType.PING))
                schedulePing()
            }
        }, configuration.pingInterval, TimeUnit.MILLISECONDS)
    }

    fun schedulePingTimeout() {
        cancelPingTimeout()
        val key = SchedulerKey(Type.PING_TIMEOUT, sessionId)
        scheduler.schedule(key, Runnable {
            val client = clientsBox[sessionId]
            if (client != null) {
                client.disconnect()
                log.debug("{} removed due to ping timeout", sessionId)
            }
        }, configuration.pingTimeout + configuration.pingInterval, TimeUnit.MILLISECONDS)
    }

    fun send(packet: Packet?, transport: com.gribouille.socketio.Transport): ChannelFuture? {
        val state = channels[transport]
        state.getPacketsQueue().add(packet)
        val channel = state.getChannel()
        return if (channel == null || transport == com.gribouille.socketio.Transport.POLLING && channel.attr<Boolean?>(
                EncoderHandler.Companion.WRITE_ONCE
            ).get() != null
        ) {
            null
        } else sendPackets(transport, channel)
    }

    private fun sendPackets(transport: com.gribouille.socketio.Transport, channel: Channel?): ChannelFuture {
        return channel!!.writeAndFlush(OutPacketMessage(this, transport))
    }

    fun removeNamespaceClient(client: NamespaceClient) {
        namespaceClients.remove(client.getNamespace())
        if (namespaceClients.isEmpty()) {
            disconnectableHub.onDisconnect(this)
        }
    }

    fun getChildClient(namespace: Namespace?): NamespaceClient? {
        return namespaceClients[namespace]
    }

    fun addNamespaceClient(namespace: Namespace?): NamespaceClient {
        val client = NamespaceClient(this, namespace)
        namespaceClients[namespace] = client
        return client
    }

    val namespaces: Set<Any?>
        get() = namespaceClients.keys
    val isConnected: Boolean
        get() = !disconnected.get()

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

    fun getHandshakeData(): HandshakeData {
        return handshakeData
    }

    fun getAckManager(): AckManager {
        return ackManager
    }

    val remoteAddress: SocketAddress
        get() = handshakeData.getAddress()

    fun disconnect() {
        val future: ChannelFuture? = send(Packet(PacketType.DISCONNECT))
        if (future != null) {
            future.addListener(ChannelFutureListener.CLOSE)
        }
        onChannelDisconnect()
    }

    val isChannelOpen: Boolean
        get() {
            for (state in channels.values) {
                if (state.channel != null
                    && state.channel.isActive
                ) {
                    return true
                }
            }
            return false
        }

    fun getStore(): Store {
        return store
    }

    fun isTransportChannel(channel: Channel, transport: com.gribouille.socketio.Transport): Boolean {
        val state = channels[transport]
        return if (state.getChannel() == null) {
            false
        } else state.getChannel() == channel
    }

    fun upgradeCurrentTransport(currentTransport: com.gribouille.socketio.Transport) {
        val state = channels[currentTransport]
        for ((key, value) in channels) {
            if (key != currentTransport) {
                val queue: Queue<Packet?> = value.packetsQueue
                state.setPacketsQueue(queue)
                sendPackets(currentTransport, state.getChannel())
                this.currentTransport = currentTransport
                log.debug("Transport upgraded to: {} for: {}", currentTransport, sessionId)
                break
            }
        }
    }

    fun getCurrentTransport(): com.gribouille.socketio.Transport {
        return currentTransport
    }

    fun getPacketsQueue(transport: com.gribouille.socketio.Transport): Queue<Packet?>? {
        return channels[transport].getPacketsQueue()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ClientHead::class.java)
        val CLIENT = AttributeKey.valueOf<ClientHead>("client")
    }
}