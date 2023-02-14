
package com.gribouille.socketio.store

import com.gribouille.socketio.Disconnectable
import java.util.*

/**
 *
 * Creates a client Store and PubSubStore
 *
 */
interface StoreFactory : Disconnectable {
    fun pubSubStore(): PubSubStore?
    fun <K, V> createMap(name: String?): Map<K, V>?
    fun createStore(sessionId: UUID?): Store?
    fun init(namespacesHub: NamespacesHub?, authorizeHandler: AuthorizeHandler?, jsonSupport: JsonSupport?)
    fun shutdown()
}