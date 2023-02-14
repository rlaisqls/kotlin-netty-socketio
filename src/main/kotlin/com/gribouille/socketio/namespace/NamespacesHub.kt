
package com.gribouille.socketio.namespace

import com.gribouille.socketio.Configuration
import io.netty.util.internal.PlatformDependent

class NamespacesHub(configuration: Configuration) {
    private val namespaces: ConcurrentMap<String, SocketIONamespace> =
        PlatformDependent.newConcurrentHashMap<String, SocketIONamespace>()
    private val configuration: Configuration

    init {
        this.configuration = configuration
    }

    fun create(name: String?): Namespace {
        var namespace: Namespace? = namespaces.get(name)
        if (namespace == null) {
            namespace = Namespace(name, configuration)
            val oldNamespace = namespaces.putIfAbsent(name, namespace) as Namespace
            if (oldNamespace != null) {
                namespace = oldNamespace
            }
        }
        return namespace
    }

    fun getRoomClients(room: String?): Iterable<SocketIOClient> {
        val allClients: MutableList<Iterable<SocketIOClient?>?> = ArrayList<Iterable<SocketIOClient?>?>()
        for (namespace in namespaces.values) {
            val clients: Iterable<SocketIOClient?>? = (namespace as Namespace).getRoomClients(room)
            allClients.add(clients)
        }
        return CompositeIterable<SocketIOClient>(allClients)
    }

    operator fun get(name: String?): Namespace {
        return namespaces.get(name)
    }

    fun remove(name: String?) {
        val namespace: SocketIONamespace = namespaces.remove(name)
        if (namespace != null) {
            namespace.getBroadcastOperations().disconnect()
        }
    }

    val allNamespaces: Collection<Any>
        get() = namespaces.values
}