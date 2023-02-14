
package com.gribouille.socketio.transport

import com.gribouille.socketio.AckCallback
import org.slf4j.LoggerFactory
import java.util.*

class NamespaceClient(baseClient: ClientHead, namespace: Namespace) : SocketIOClient {
    private val disconnected: AtomicBoolean = AtomicBoolean()
    private val baseClient: ClientHead
    private val namespace: Namespace

    init {
        this.baseClient = baseClient
        this.namespace = namespace
        namespace.addClient(this)
    }

    fun getBaseClient(): ClientHead {
        return baseClient
    }

    val transport: com.gribouille.socketio.Transport
        get() = baseClient.getCurrentTransport()
    val isChannelOpen: Boolean
        get() = baseClient.isChannelOpen()

    override fun getNamespace(): Namespace {
        return namespace
    }

    fun sendEvent(name: String?, vararg data: Any?) {
        val packet = Packet(PacketType.MESSAGE)
        packet.setSubType(PacketType.EVENT)
        packet.setName(name)
        packet.setData(Arrays.asList(*data))
        send(packet)
    }

    override fun sendEvent(name: String?, ackCallback: AckCallback<*>, vararg data: Any?) {
        val packet = Packet(PacketType.MESSAGE)
        packet.setSubType(PacketType.EVENT)
        packet.setName(name)
        packet.setData(Arrays.asList(*data))
        send(packet, ackCallback)
    }

    private val isConnected: Boolean
        private get() = !disconnected.get() && baseClient.isConnected()

    override fun send(packet: Packet, ackCallback: AckCallback<*>) {
        if (!isConnected) {
            ackCallback.onTimeout()
            return
        }
        val index: Long = baseClient.getAckManager().registerAck(sessionId, ackCallback)
        packet.setAckId(index)
        send(packet)
    }

    fun send(packet: Packet) {
        if (!isConnected) {
            return
        }
        baseClient.send(packet.withNsp(namespace.getName()))
    }

    fun onDisconnect() {
        disconnected.set(true)
        baseClient.removeNamespaceClient(this)
        namespace.onDisconnect(this)
        log.debug(
            "Client {} for namespace {} has been disconnected",
            baseClient.getSessionId(),
            getNamespace().getName()
        )
    }

    fun disconnect() {
        val packet = Packet(PacketType.MESSAGE)
        packet.setSubType(PacketType.DISCONNECT)
        send(packet)
        //        onDisconnect();
    }

    val sessionId: UUID?
        get() = baseClient.getSessionId()
    val remoteAddress: SocketAddress
        get() = baseClient.getRemoteAddress()

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (sessionId == null) 0 else sessionId.hashCode()
        result = (prime * result
                + if (getNamespace().getName() == null) 0 else getNamespace().getName().hashCode())
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as NamespaceClient
        if (sessionId == null) {
            if (other.sessionId != null) return false
        } else if (sessionId != other.sessionId) return false
        if (getNamespace().getName() == null) {
            if (other.getNamespace().getName() != null) return false
        } else if (!getNamespace().getName().equals(other.getNamespace().getName())) return false
        return true
    }

    override fun joinRoom(room: String?) {
        namespace.joinRoom(room, sessionId)
    }

    override fun joinRooms(rooms: Set<String?>?) {
        namespace.joinRooms(rooms, sessionId)
    }

    override fun leaveRoom(room: String?) {
        namespace.leaveRoom(room, sessionId)
    }

    override fun leaveRooms(rooms: Set<String?>?) {
        namespace.leaveRooms(rooms, sessionId)
    }

    operator fun set(key: String?, `val`: Any?) {
        baseClient.getStore().set(key, `val`)
    }

    operator fun <T> get(key: String?): T {
        return baseClient.getStore().get(key)
    }

    fun has(key: String?): Boolean {
        return baseClient.getStore().has(key)
    }

    fun del(key: String?) {
        baseClient.getStore().del(key)
    }

    val allRooms: Set<String>
        get() = namespace.getRooms(this)

    override fun getCurrentRoomSize(room: String?): Int {
        return namespace.getRoomClientsInCluster(room)
    }

    val handshakeData: HandshakeData
        get() = baseClient.getHandshakeData()

    companion object {
        private val log = LoggerFactory.getLogger(NamespaceClient::class.java)
    }
}