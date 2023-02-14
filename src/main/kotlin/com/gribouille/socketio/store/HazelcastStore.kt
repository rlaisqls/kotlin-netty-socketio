
package com.gribouille.socketio.store

import com.hazelcast.core.HazelcastInstance
import java.util.*

class HazelcastStore(sessionId: UUID, hazelcastInstance: HazelcastInstance) : Store {
    private val map: IMap<String, Any>

    init {
        map = hazelcastInstance.getMap(sessionId.toString())
    }

    override fun set(key: String, `val`: Any) {
        map.put(key, `val`)
    }

    override fun <T> get(key: String): T? {
        return map.get(key)
    }

    override fun has(key: String): Boolean {
        return map.containsKey(key)
    }

    override fun del(key: String) {
        map.delete(key)
    }
}