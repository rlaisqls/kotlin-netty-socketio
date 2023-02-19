
package com.gribouille.socketio.store

import io.netty.util.internal.PlatformDependent
import java.util.concurrent.ConcurrentHashMap

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