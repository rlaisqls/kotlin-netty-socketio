
package com.gribouille.socketio.store

import com.gribouille.socketio.Disconnectable
import java.util.UUID

interface StoreFactory : Disconnectable {
    fun <K, V> createMap(name: String?): Map<K, V>?
    fun createStore(sessionId: UUID?): Store?
    fun shutdown()
}