
package com.gribouille.socketio.store

import com.gribouille.socketio.store.pubsub.BaseStoreFactory
import java.util.*

/**
 * WARN: It's necessary to add netty-socketio.jar in hazelcast server classpath.
 *
 */
class HazelcastStoreFactory : BaseStoreFactory {
    private val hazelcastClient: HazelcastInstance
    private val hazelcastPub: HazelcastInstance
    private val hazelcastSub: HazelcastInstance
    private val pubSubStore: PubSubStore

    @JvmOverloads
    constructor(instance: HazelcastInstance = HazelcastClient.newHazelcastClient()) {
        hazelcastClient = instance
        hazelcastPub = instance
        hazelcastSub = instance
        pubSubStore = HazelcastPubSubStore(hazelcastPub, hazelcastSub, getNodeId())
    }

    constructor(hazelcastClient: HazelcastInstance, hazelcastPub: HazelcastInstance, hazelcastSub: HazelcastInstance) {
        this.hazelcastClient = hazelcastClient
        this.hazelcastPub = hazelcastPub
        this.hazelcastSub = hazelcastSub
        pubSubStore = HazelcastPubSubStore(hazelcastPub, hazelcastSub, getNodeId())
    }

    fun createStore(sessionId: UUID): Store {
        return HazelcastStore(sessionId, hazelcastClient)
    }

    fun shutdown() {
        hazelcastClient.shutdown()
        hazelcastPub.shutdown()
        hazelcastSub.shutdown()
    }

    fun pubSubStore(): PubSubStore {
        return pubSubStore
    }

    fun <K, V> createMap(name: String?): Map<K, V> {
        return hazelcastClient.getMap(name)
    }
}