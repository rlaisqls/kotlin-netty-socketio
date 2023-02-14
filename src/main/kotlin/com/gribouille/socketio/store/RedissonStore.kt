
package com.gribouille.socketio.store

import org.redisson.api.RedissonClient
import java.util.*

class RedissonStore(sessionId: UUID, redisson: RedissonClient) : Store {
    private val map: MutableMap<String, Any>

    init {
        map = redisson.getMap(sessionId.toString())
    }

    override fun set(key: String, value: Any) {
        map[key] = value
    }

    override fun <T> get(key: String): T? {
        return map[key] as T?
    }

    override fun has(key: String): Boolean {
        return map.containsKey(key)
    }

    override fun del(key: String) {
        map.remove(key)
    }
}