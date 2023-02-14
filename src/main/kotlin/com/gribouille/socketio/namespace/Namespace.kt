
package com.gribouille.socketio.namespace

import com.gribouille.socketio.AckMode
import io.netty.util.internal.PlatformDependent
import java.util.*

/**
 * Hub object for all clients in one namespace.
 * Namespace shares by different namespace-clients.
 *
 * @see com.gribouille.socketio.transport.NamespaceClient
 */
class Namespace(val name: String?, configuration: Configuration) : SocketIONamespace {
    private val engine: ScannerEngine = ScannerEngine()
    private val eventListeners: ConcurrentMap<String, EventEntry<*>> =
        PlatformDependent.newConcurrentHashMap<String, EventEntry<*>>()
    private val connectListeners: Queue<ConnectListener> = ConcurrentLinkedQueue<ConnectListener>()
    private val disconnectListeners: Queue<DisconnectListener> = ConcurrentLinkedQueue<DisconnectListener>()
    private val pingListeners: Queue<PingListener> = ConcurrentLinkedQueue<PingListener>()
    private val pongListeners: Queue<PongListener> = ConcurrentLinkedQueue<PongListener>()
    private val eventInterceptors: Queue<EventInterceptor> = ConcurrentLinkedQueue<EventInterceptor>()
    private val allClients: MutableMap<UUID, SocketIOClient> =
        PlatformDependent.newConcurrentHashMap<UUID, SocketIOClient>()
    private val roomClients: ConcurrentMap<String?, Set<UUID>> =
        PlatformDependent.newConcurrentHashMap<String?, Set<UUID>>()
    private val clientRooms: ConcurrentMap<UUID, Set<String?>> =
        PlatformDependent.newConcurrentHashMap<UUID, Set<String?>>()
    private val ackMode: AckMode
    private val jsonSupport: JsonSupport
    private val storeFactory: StoreFactory
    private val exceptionListener: ExceptionListener

    init {
        jsonSupport = configuration.getJsonSupport()
        storeFactory = configuration.getStoreFactory()
        exceptionListener = configuration.getExceptionListener()
        ackMode = configuration.getAckMode()
    }

    fun addClient(client: SocketIOClient) {
        allClients[client.getSessionId()] = client
    }

    fun addMultiTypeEventListener(
        eventName: String?, listener: MultiTypeEventListener,
        vararg eventClass: Class<*>?
    ) {
        var entry: EventEntry<*>? = eventListeners.get(eventName)
        if (entry == null) {
            entry = EventEntry<Any?>()
            val oldEntry: EventEntry<*> = eventListeners.putIfAbsent(eventName, entry)
            if (oldEntry != null) {
                entry = oldEntry
            }
        }
        entry.addListener(listener)
        jsonSupport.addEventMapping(name, eventName, eventClass)
    }

    fun removeAllListeners(eventName: String?) {
        val entry: EventEntry<*> = eventListeners.remove(eventName)
        if (entry != null) {
            jsonSupport.removeEventMapping(name, eventName)
        }
    }

    fun <T> addEventListener(eventName: String?, eventClass: Class<T>?, listener: DataListener<T>) {
        var entry: EventEntry<*>? = eventListeners.get(eventName)
        if (entry == null) {
            entry = EventEntry<T>()
            val oldEntry: EventEntry<*> = eventListeners.putIfAbsent(eventName, entry)
            if (oldEntry != null) {
                entry = oldEntry
            }
        }
        entry.addListener(listener)
        jsonSupport.addEventMapping(name, eventName, eventClass)
    }

    fun addEventInterceptor(eventInterceptor: EventInterceptor) {
        eventInterceptors.add(eventInterceptor)
    }

    fun onEvent(client: NamespaceClient?, eventName: String?, args: List<Any>, ackRequest: AckRequest) {
        val entry: EventEntry<*> = eventListeners.get(eventName) ?: return
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

    private fun getEventData(args: List<Any>, dataListener: DataListener<*>): Any? {
        if (dataListener is MultiTypeEventListener) {
            return MultiTypeArgs(args)
        } else {
            if (!args.isEmpty()) {
                return args[0]
            }
        }
        return null
    }

    fun addDisconnectListener(listener: DisconnectListener) {
        disconnectListeners.add(listener)
    }

    fun onDisconnect(client: SocketIOClient) {
        val joinedRooms: Set<String> = client.getAllRooms()
        allClients.remove(client.getSessionId())

        // client must leave all rooms and publish the leave msg one by one on disconnect.
        for (joinedRoom in joinedRooms) {
            leave<String?, UUID>(roomClients, joinedRoom, client.getSessionId())
            storeFactory.pubSubStore()
                .publish(PubSubType.LEAVE, JoinLeaveMessage(client.getSessionId(), joinedRoom, name))
        }
        clientRooms.remove(client.getSessionId())
        try {
            for (listener in disconnectListeners) {
                listener.onDisconnect(client)
            }
        } catch (e: Exception) {
            exceptionListener.onDisconnectException(e, client)
        }
    }

    fun addConnectListener(listener: ConnectListener) {
        connectListeners.add(listener)
    }

    fun onConnect(client: SocketIOClient) {
        join(name, client.getSessionId())
        storeFactory.pubSubStore().publish(PubSubType.JOIN, JoinLeaveMessage(client.getSessionId(), name, name))
        try {
            for (listener in connectListeners) {
                listener.onConnect(client)
            }
        } catch (e: Exception) {
            exceptionListener.onConnectException(e, client)
        }
    }

    fun addPingListener(listener: PingListener) {
        pingListeners.add(listener)
    }

    fun addPongListener(listener: PongListener) {
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

    val broadcastOperations: BroadcastOperations
        get() = SingleRoomBroadcastOperations(name, name, allClients.values, storeFactory)

    override fun getRoomOperations(room: String?): BroadcastOperations {
        return SingleRoomBroadcastOperations(name, room, getRoomClients(room), storeFactory)
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

    @JvmOverloads
    fun addListeners(listeners: Any, listenersClass: Class<*>? = listeners.javaClass) {
        engine.scan(this, listeners, listenersClass)
    }

    fun joinRoom(room: String?, sessionId: UUID) {
        join(room, sessionId)
        storeFactory.pubSubStore().publish(PubSubType.JOIN, JoinLeaveMessage(sessionId, room, name))
    }

    fun joinRooms(rooms: Set<String?>, sessionId: UUID) {
        for (room in rooms) {
            join(room, sessionId)
        }
        storeFactory.pubSubStore().publish(PubSubType.BULK_JOIN, BulkJoinLeaveMessage(sessionId, rooms, name))
    }

    fun dispatch(room: String?, packet: Packet?) {
        val clients: Iterable<SocketIOClient?> = getRoomClients(room)
        for (socketIOClient in clients) {
            socketIOClient.send(packet)
        }
    }

    private fun <K, V> join(map: ConcurrentMap<K, Set<V>>, key: K, value: V) {
        var clients: MutableSet<V>? = map.get(key)
        if (clients == null) {
            clients = Collections.newSetFromMap(ConcurrentHashMap())
            val oldClients: MutableSet<V> = map.putIfAbsent(key, clients)
            if (oldClients != null) {
                clients = oldClients
            }
        }
        clients!!.add(value)
        // object may be changed due to other concurrent call
        if (clients !== map.get(key)) {
            // re-join if queue has been replaced
            join(map, key, value)
        }
    }

    fun join(room: String?, sessionId: UUID) {
        join(roomClients, room, sessionId)
        join(clientRooms, sessionId, room)
    }

    fun leaveRoom(room: String?, sessionId: UUID) {
        leave(room, sessionId)
        storeFactory.pubSubStore().publish(PubSubType.LEAVE, JoinLeaveMessage(sessionId, room, name))
    }

    fun leaveRooms(rooms: Set<String?>, sessionId: UUID) {
        for (room in rooms) {
            leave(room, sessionId)
        }
        storeFactory.pubSubStore().publish(PubSubType.BULK_LEAVE, BulkJoinLeaveMessage(sessionId, rooms, name))
    }

    private fun <K, V> leave(map: ConcurrentMap<K, Set<V>>, room: K, sessionId: V) {
        val clients: MutableSet<V> = map.get(room) ?: return
        clients.remove(sessionId)
        if (clients.isEmpty()) {
            map.remove(room, emptySet<Any>())
        }
    }

    fun leave(room: String?, sessionId: UUID) {
        leave(roomClients, room, sessionId)
        leave(clientRooms, sessionId, room)
    }

    fun getRooms(client: SocketIOClient): Set<String> {
        val res: Set<String> = clientRooms.get(client.getSessionId())
            ?: return emptySet()
        return Collections.unmodifiableSet(res)
    }

    val rooms: Set<String>
        get() = roomClients.keys

    fun getRoomClients(room: String?): Iterable<SocketIOClient?> {
        val sessionIds: Set<UUID> = roomClients.get(room)
            ?: return emptyList<SocketIOClient>()
        val result: MutableList<SocketIOClient?> = ArrayList<SocketIOClient?>()
        for (sessionId in sessionIds) {
            val client: SocketIOClient? = allClients[sessionId]
            if (client != null) {
                result.add(client)
            }
        }
        return result
    }

    fun getRoomClientsInCluster(room: String?): Int {
        val sessionIds: Set<UUID> = roomClients.get(room)
        return sessionIds?.size ?: 0
    }

    override fun getAllClients(): Collection<SocketIOClient> {
        return Collections.unmodifiableCollection<SocketIOClient>(allClients.values)
    }

    fun getJsonSupport(): JsonSupport {
        return jsonSupport
    }

    override fun getClient(uuid: UUID): SocketIOClient? {
        return allClients[uuid]
    }

    companion object {
        const val DEFAULT_NAME = ""
    }
}