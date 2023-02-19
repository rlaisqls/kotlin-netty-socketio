
package com.gribouille.socketio.store

import com.gribouille.socketio.handler.ClientHead
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MemoryStoreFactory : BaseStoreFactory() {

    override fun createStore(sessionId: UUID?): Store {
        return MemoryStore()
    }

    override fun onDisconnect(client: ClientHead) {}

    override fun shutdown() {}
    override fun toString(): String {
        return javaClass.simpleName + " (local session store only)"
    }

    override fun <K, V> createMap(name: String?): Map<K, V> {
        return ConcurrentHashMap()
    }
}