
package com.gribouille.socketio.transport

import com.gribouille.socketio.AckCallback
import com.gribouille.socketio.HandshakeData
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.namespace.Namespace
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import org.slf4j.LoggerFactory
import java.net.SocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class NamespaceClient(
    val baseClient: ClientHead,
    override val namespace: Namespace
) : SocketIOClient {
    private val disconnected: AtomicBoolean = AtomicBoolean()

    val isConnected: Boolean
        get() = !disconnected.get() && baseClient.isConnected
    override val handshakeData: HandshakeData
        get() = baseClient.handshakeData
    override val allRooms: Set<String>
        get() = namespace.getRooms(this)

    init {
        namespace.addClient(this)
    }

    override val transport: Transport
        get() = baseClient.getCurrentTransport()
    override val isChannelOpen: Boolean
        get() = baseClient.isChannelOpen

    override val sessionId: UUID
        get() = baseClient.sessionId!!
    
    override val remoteAddress: SocketAddress
        get() = baseClient.remoteAddress

    override fun sendEvent(
        name: String,
        data: Any
    ) {
        val packet = Packet(PacketType.MESSAGE)
        packet.subType = PacketType.EVENT
        packet.name = name
        packet.data = data
        send(packet)
    }

    override fun sendEvent(
        name: String,
        ackCallback: AckCallback,
        data: Any
    ) {
        val packet = Packet(PacketType.MESSAGE)
        packet.subType = PacketType.EVENT
        packet.name = name
        packet.data = data
        send(packet, ackCallback)
    }

    override fun send(
        packet: Packet,
        ackCallback: AckCallback
    ) {
        if (!isConnected) {
            ackCallback.onTimeout()
            return
        }
        val index = baseClient.ackManager
            .registerAck(sessionId, ackCallback)
        packet.ackId = index
        send(packet)
    }

    override fun send(packet: Packet) {
        if (!isConnected) return
        baseClient.send(packet.withNsp(namespace.name))?.get()
    }

    fun onDisconnect() {
        disconnected.set(true)
        baseClient.removeNamespaceClient(this)
        namespace.onDisconnect(this)
        log.debug(
            "Client {} for namespace {} has been disconnected",
            baseClient.sessionId,
            namespace.name
        )
    }

    override fun disconnect() {
        val packet = Packet(PacketType.MESSAGE)
        packet.subType = PacketType.DISCONNECT
        send(packet)
    }

    override fun joinRoom(room: String) {
        namespace.joinRoom(room, sessionId)
    }

    override fun joinRooms(rooms: Set<String>) {
        namespace.joinRooms(rooms, sessionId)
    }

    override fun leaveRoom(room: String) {
        namespace.leaveRoom(room, sessionId)
    }

    override fun leaveRooms(rooms: Set<String>) {
        namespace.leaveRooms(rooms, sessionId)
    }

    override operator fun set(key: String, value: Any) {
        baseClient.store[key] = value
    }

    override operator fun <T> get(key: String): T? {
        return baseClient.store[key]
    }

    override fun has(key: String): Boolean {
        return baseClient.store.has(key)
    }

    override fun del(key: String) {
        baseClient.store.del(key)
    }

    override fun getCurrentRoomSize(room: String): Int {
        return namespace.getRoomClientsInCluster(room)
    }

    companion object {
        private val log = LoggerFactory.getLogger(NamespaceClient::class.java)
    }
}