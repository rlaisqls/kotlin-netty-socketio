
package com.gribouille.socketio.namespace

import com.gribouille.socketio.Configuration
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.SocketIONamespace
import com.gribouille.socketio.misc.CompositeIterable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class NamespacesHub(
    private val configuration: Configuration
) {
    private val namespaces: ConcurrentMap<String, SocketIONamespace> = ConcurrentHashMap()

    val allNamespaces: Collection<SocketIONamespace>
        get() = namespaces.values

    fun create(name: String) =
        namespaces[name] ?: Namespace(name, configuration).also { namespaces.putIfAbsent(name, it) }

    fun getRoomClients(room: String): Iterable<SocketIOClient> {
        val allClients = ArrayList<MutableIterable<SocketIOClient>>()
        namespaces.values.map {
            allClients.add((it as Namespace).getRoomClients(room))
        }
        return CompositeIterable(allClients.toList())
    }

    operator fun get(name: String?): Namespace {
        return namespaces[name] as Namespace
    }

    fun remove(name: String?) {
        namespaces.remove(name)?.broadcastOperations?.disconnect()
    }
}