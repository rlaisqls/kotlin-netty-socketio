
package com.gribouille.socketio.store

import org.redisson.Redisson
import java.util.*

class RedissonStoreFactory : BaseStoreFactory {
    private val redisClient: RedissonClient
    private val redisPub: RedissonClient
    private val redisSub: RedissonClient
    private val pubSubStore: PubSubStore

    @JvmOverloads
    constructor(redisson: RedissonClient = Redisson.create()) {
        redisClient = redisson
        redisPub = redisson
        redisSub = redisson
        pubSubStore = RedissonPubSubStore(redisPub, redisSub, getNodeId())
    }

    constructor(redisClient: Redisson, redisPub: Redisson, redisSub: Redisson) {
        this.redisClient = redisClient
        this.redisPub = redisPub
        this.redisSub = redisSub
        pubSubStore = RedissonPubSubStore(redisPub, redisSub, getNodeId())
    }

    fun createStore(sessionId: UUID): Store {
        return RedissonStore(sessionId, redisClient)
    }

    fun pubSubStore(): PubSubStore {
        return pubSubStore
    }

    fun shutdown() {
        redisClient.shutdown()
        redisPub.shutdown()
        redisSub.shutdown()
    }

    fun <K, V> createMap(name: String?): Map<K, V> {
        return redisClient.getMap(name)
    }
}