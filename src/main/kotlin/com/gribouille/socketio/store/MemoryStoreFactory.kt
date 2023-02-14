
package com.gribouille.socketio.store

import com.gribouille.socketio.store.pubsub.BaseStoreFactory
import io.netty.util.internal.PlatformDependent
import java.util.*

class MemoryStoreFactory : BaseStoreFactory() {
    private val pubSubMemoryStore = MemoryPubSubStore()
    fun createStore(sessionId: UUID?): Store {
        return MemoryStore()
    }

    fun pubSubStore(): PubSubStore {
        return pubSubMemoryStore
    }

    fun shutdown() {}
    override fun toString(): String {
        return javaClass.simpleName + " (local session store only)"
    }

    fun <K, V> createMap(name: String?): Map<K, V> {
        return ConcurrentHashMap()
    }
}