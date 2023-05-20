
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

    class MemoryStore : Store {
        private val store: MutableMap<String, Any> = ConcurrentHashMap()
        override fun set(key: String, value: Any) {
            store[key] = value
        }

        override fun <T> get(key: String): T? {
            return store[key] as T?
        }

        override fun has(key: String): Boolean {
            return store.containsKey(key)
        }

        override fun del(key: String) {
            store.remove(key)
        }
    }
}