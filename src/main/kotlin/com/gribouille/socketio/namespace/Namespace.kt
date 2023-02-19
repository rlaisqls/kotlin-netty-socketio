package com.gribouille.socketio.namespace

import com.gribouille.socketio.AckMode
import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.BroadcastOperations
import com.gribouille.socketio.Configuration
import com.gribouille.socketio.MultiTypeArgs
import com.gribouille.socketio.SingleRoomBroadcastOperations
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.SocketIONamespace
import com.gribouille.socketio.annotation.ScannerEngine
import com.gribouille.socketio.listener.ConnectListener
import com.gribouille.socketio.listener.DataListener
import com.gribouille.socketio.listener.DisconnectListener
import com.gribouille.socketio.listener.EventInterceptor
import com.gribouille.socketio.listener.ExceptionListener
import com.gribouille.socketio.listener.MultiTypeEventListener
import com.gribouille.socketio.listener.PingListener
import com.gribouille.socketio.listener.PongListener
import com.gribouille.socketio.protocol.JsonSupport
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.store.StoreFactory
import com.gribouille.socketio.transport.NamespaceClient
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap

class Namespace(
    override val name: String,
    configuration: Configuration
) : SocketIONamespace {
    private val engine = ScannerEngine()
    private val eventListeners: ConcurrentMap<String, EventEntry> = ConcurrentHashMap()
    private val connectListeners: Queue<ConnectListener> = ConcurrentLinkedQueue()
    private val disconnectListeners: Queue<DisconnectListener> = ConcurrentLinkedQueue()
    private val pingListeners: Queue<PingListener> = ConcurrentLinkedQueue()
    private val pongListeners: Queue<PongListener> = ConcurrentLinkedQueue()
    private val eventInterceptors: Queue<EventInterceptor> = ConcurrentLinkedQueue()
    private val roomClients: ConcurrentMap<String, MutableSet<UUID>> = ConcurrentHashMap()
    private val clientRooms: ConcurrentMap<UUID, MutableSet<String>> = ConcurrentHashMap()
    private val ackMode: AckMode = configuration.ackMode
    private val jsonSupport: JsonSupport = configuration.jsonSupport!!
    private val storeFactory: StoreFactory = configuration.storeFactory
    private val exceptionListener: ExceptionListener = configuration.exceptionListener

    private val clients: MutableMap<UUID, SocketIOClient> = ConcurrentHashMap()
    override val allClients: Collection<SocketIOClient>
        get() = clients.values
    val rooms: Set<String>
        get() = roomClients.keys.toSet()

    fun addClient(client: SocketIOClient) {
        clients[client.sessionId] = client
    }

    override fun addMultiTypeEventListener(
        eventName: String,
        listener: MultiTypeEventListener,
        vararg eventClass: Class<*>
    ) {
        val entry = eventListeners[eventName] ?: EventEntry().also { eventListeners.putIfAbsent(eventName, it) }
        entry.addListener(listener)
        jsonSupport.addEventMapping(name, eventName, *eventClass)
    }

    override fun removeAllListeners(eventName: String) {
        val entry = eventListeners.remove(eventName)
        if (entry != null) {
            jsonSupport.removeEventMapping(name, eventName)
        }
    }

    override fun addEventListener(
        eventName: String,
        eventClass: Class<*>,
        listener: DataListener
    ) {
        var entry = eventListeners[eventName] ?: EventEntry().also { eventListeners.putIfAbsent(eventName, it) }
        entry.addListener(listener)
        jsonSupport.addEventMapping(name, eventName, eventClass)
    }

    override fun addEventInterceptor(eventInterceptor: EventInterceptor) {
        eventInterceptors.add(eventInterceptor)
    }

    fun onEvent(
        client: NamespaceClient,
        eventName: String,
        args: List<Any>,
        ackRequest: AckRequest
    ) {
        val entry = eventListeners.get(eventName) ?: return
        try {
            val listeners: Queue<DataListener> = entry.listeners
            for (dataListener in listeners) {
                val data = getEventData(args, dataListener)
                dataListener.onData(client, data, ackRequest)
            }
            for (eventInterceptor in eventInterceptors) {
                eventInterceptor.onEvent(client, eventName, args, ackRequest)
            }
        } catch (e: Exception) {
            exceptionListener.onEventException(e, args, client)
            if (ackMode === AckMode.AUTO_SUCCESS_ONLY) {
                return
            }
        }
        sendAck(ackRequest)
    }

    private fun sendAck(ackRequest: AckRequest) {
        if (ackMode === AckMode.AUTO || ackMode === AckMode.AUTO_SUCCESS_ONLY) {
            // send ack response if it not executed
            // during {@link DataListener#onData} invocation
            ackRequest.sendAckData(emptyList())
        }
    }

    private fun getEventData(
        args: List<Any>,
        dataListener: DataListener
    ): Any {
        if (dataListener is MultiTypeEventListener) {
            return MultiTypeArgs(args)
        } else if (args.isNotEmpty()) {
            return args[0]

        } else error("")
    }

    override fun addDisconnectListener(listener: DisconnectListener) {
        disconnectListeners.add(listener)
    }

    fun onDisconnect(client: SocketIOClient) {
        val joinedRooms = client.allRooms
        clients.remove(client.sessionId)

        // client must leave all rooms and publish the leave msg one by one on disconnect.
        for (joinedRoom in joinedRooms) {
            leave(roomClients, joinedRoom, client.sessionId)
        }
        clientRooms.remove(client.sessionId)
        try {
            for (listener in disconnectListeners) {
                listener.onDisconnect(client)
            }
        } catch (e: Exception) {
            exceptionListener.onDisconnectException(e, client)
        }
    }

    override fun addConnectListener(listener: ConnectListener) {
        connectListeners.add(listener)
    }

    fun onConnect(client: SocketIOClient) {
        join(name, client.sessionId)
        try {
            for (listener in connectListeners) {
                listener.onConnect(client)
            }
        } catch (e: Exception) {
            exceptionListener.onConnectException(e, client)
        }
    }

    override fun addPingListener(listener: PingListener) {
        pingListeners.add(listener)
    }

    override fun addPongListener(listener: PongListener) {
        pongListeners.add(listener)
    }

    fun onPing(client: SocketIOClient?) {
        try {
            for (listener in pingListeners) {
                listener.onPing(client)
            }
        } catch (e: Exception) {
            exceptionListener.onPingException(e, client)
        }
    }

    fun onPong(client: SocketIOClient?) {
        try {
            for (listener in pongListeners) {
                listener.onPong(client)
            }
        } catch (e: Exception) {
            exceptionListener.onPingException(e, client)
        }
    }

    override val broadcastOperations: BroadcastOperations
        get() = SingleRoomBroadcastOperations(clients.values, name, name, storeFactory)

    override fun getRoomOperations(room: String): BroadcastOperations {
        return SingleRoomBroadcastOperations(getRoomClients(room).toList(), name, room, storeFactory)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (name?.hashCode() ?: 0)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as Namespace
        if (name == null) {
            if (other.name != null) return false
        } else if (name != other.name) return false
        return true
    }

    override fun addListeners(listeners: Any) {
        addListeners(listeners, listeners.javaClass)
    }

    override fun addListeners(listeners: Any, listenersClass: Class<*>) {
        engine.scan(this, listeners, listenersClass)
    }

    fun joinRoom(room: String, sessionId: UUID) {
        join(room, sessionId)
    }

    fun joinRooms(rooms: Set<String>, sessionId: UUID) {
        for (room in rooms) {
            join(room, sessionId)
        }
    }

    fun dispatch(room: String, packet: Packet) {
        val clients = getRoomClients(room)
        for (socketIOClient in clients) {
            socketIOClient.send(packet)
        }
    }

    private fun <K, V> join(map: MutableMap<K, MutableSet<V>>, key: K, value: V) {
        val clients = map[key] ?: Collections.newSetFromMap<V>(ConcurrentHashMap())
            .let {
                map.putIfAbsent(key, it) ?: it
            }
        clients!!.add(value)
        if (clients !== map[key]) {
            join(map, key, value)
        }
    }

    private fun join(room: String, sessionId: UUID) {
        join(roomClients, room, sessionId)
        join(clientRooms, sessionId, room)
    }

    fun leaveRoom(room: String, sessionId: UUID) {
        leave(room, sessionId)
    }

    fun leaveRooms(rooms: Set<String>, sessionId: UUID) {
        for (room in rooms) {
            leave(room, sessionId)
        }
    }

    private fun <K, V> leave(map: ConcurrentMap<K, MutableSet<V>>, room: K, sessionId: V) {
        val clients: MutableSet<V> = map[room] ?: return
        clients.remove(sessionId)
        if (clients.isEmpty()) {
            map.remove(room, emptySet())
        }
    }

    fun leave(room: String, sessionId: UUID) {
        leave(roomClients, room, sessionId)
        leave(clientRooms, sessionId, room)
    }

    fun getRooms(client: SocketIOClient): Set<String> {
        return clientRooms[client.sessionId]?.toSet()
            ?: return emptySet()
    }

    fun getRoomClients(room: String?): MutableIterable<SocketIOClient> {
        val sessionIds = roomClients[room] ?: return mutableListOf()
        return sessionIds.mapNotNull { sessionId ->
            clients[sessionId]
        }.toMutableList()
    }

    fun getRoomClientsInCluster(room: String?): Int {
        val sessionIds = roomClients.get(room)
        return sessionIds?.size ?: 0
    }

    override fun getClient(uuid: UUID): SocketIOClient? {
        return clients[uuid]
    }

    companion object {
        const val DEFAULT_NAME = ""
    }
}