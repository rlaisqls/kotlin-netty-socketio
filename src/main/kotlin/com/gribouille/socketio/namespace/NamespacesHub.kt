
package com.gribouille.socketio.namespace

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.SocketIOConfiguration
import com.gribouille.socketio.SocketIONamespace
import com.gribouille.socketio.misc.CompositeIterable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

interface NamespacesHub {
    val allNamespaces: Collection<SocketIONamespace>
    fun create(name: String, configuration: SocketIOConfiguration): SocketIONamespace
    fun getRoomClients(room: String): Iterable<SocketIOClient>
    operator fun get(name: String?): Namespace?
    fun remove(name: String?)
}

internal val namespacesHub = object : NamespacesHub {

    private val namespaces: ConcurrentMap<String, SocketIONamespace> = ConcurrentHashMap()

    override val allNamespaces: Collection<SocketIONamespace>
        get() = namespaces.values

    override fun create(name: String, configuration: SocketIOConfiguration) =
        namespaces[name] ?: Namespace(name, configuration).also { namespaces.putIfAbsent(name, it) }

    override fun getRoomClients(room: String): Iterable<SocketIOClient> {
        val allClients = ArrayList<MutableIterable<SocketIOClient>>()
        namespaces.values.map {
            allClients.add((it as Namespace).getRoomClients(room))
        }
        return CompositeIterable(allClients.toList())
    }

    override operator fun get(name: String?): Namespace? {
        return namespaces[name] as Namespace?
    }

    override fun remove(name: String?) {
        namespaces.remove(name)?.broadcastOperations?.disconnect()
    }
}