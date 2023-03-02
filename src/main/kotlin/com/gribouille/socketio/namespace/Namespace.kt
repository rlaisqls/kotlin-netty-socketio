package com.gribouille.socketio.namespace

import com.gribouille.socketio.AckMode
import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.BroadcastOperations
import com.gribouille.socketio.Configuration
import com.gribouille.socketio.SingleRoomBroadcastOperations
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.SocketIONamespace
import com.gribouille.socketio.annotation.ScannerEngine
import com.gribouille.socketio.listener.ConnectListener
import com.gribouille.socketio.listener.DataListener
import com.gribouille.socketio.listener.DisconnectListener
import com.gribouille.socketio.listener.EventInterceptor
import com.gribouille.socketio.listener.ExceptionListener
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
    private val eventListeners = ConcurrentHashMap<String, EventEntry>()
    private val connectListeners = ConcurrentLinkedQueue<ConnectListener>()
    private val disconnectListeners = ConcurrentLinkedQueue<DisconnectListener>()
    private val pingListeners = ConcurrentLinkedQueue<PingListener>()
    private val pongListeners = ConcurrentLinkedQueue<PongListener>()
    private val eventInterceptors = ConcurrentLinkedQueue<EventInterceptor>()
    private val roomClients = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val clientRooms = ConcurrentHashMap<UUID, MutableSet<String>>()
    private val ackMode: AckMode = configuration.ackMode
    private val jsonSupport = configuration.jsonSupport!!
    private val storeFactory = configuration.storeFactory
    private val exceptionListener = configuration.exceptionListener

    private val clients: MutableMap<UUID, SocketIOClient> = ConcurrentHashMap()
    override val allClients: Collection<SocketIOClient>
        get() = clients.values

    val rooms: Set<String>
        get() = roomClients.keys.toSet()

    override val broadcastOperations: BroadcastOperations
        get() = SingleRoomBroadcastOperations(clients.values, name, name, storeFactory)

    override fun getRoomOperations(room: String): BroadcastOperations {
        return SingleRoomBroadcastOperations(getRoomClients(room).toList(), name, room, storeFactory)
    }

    fun addClient(client: SocketIOClient) {
        clients[client.sessionId] = client
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
        val entry = eventListeners[eventName] ?: return
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
            if (ackMode == AckMode.AUTO_SUCCESS_ONLY) {
                return
            }
        }
        sendAck(ackRequest)
    }

    private fun sendAck(ackRequest: AckRequest) {
        if (ackMode == AckMode.AUTO || ackMode == AckMode.AUTO_SUCCESS_ONLY) {
            // {@link DataListener#onData}가 호출되는 동안 ack 응답이 전송되지 않는 경우 전송
            ackRequest.sendAckData(emptyList())
        }
    }

    private fun getEventData(
        args: List<Any>,
        dataListener: DataListener
    ): Any {
        require(args.isNotEmpty())
        return args[0]
    }

    override fun addDisconnectListener(listener: DisconnectListener) {
        disconnectListeners.add(listener)
    }

    fun onDisconnect(client: SocketIOClient) {
        val joinedRooms = client.allRooms
        clients.remove(client.sessionId)

        // client가 disconnect 될때, 모든 방에서 leave한다.
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

    private fun leave(room: String, sessionId: UUID) {
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