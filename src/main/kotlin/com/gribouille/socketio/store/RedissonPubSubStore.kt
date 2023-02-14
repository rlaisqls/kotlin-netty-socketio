
package com.gribouille.socketio.store

import io.netty.util.internal.PlatformDependent
import org.redisson.api.RTopic
import java.util.*

class RedissonPubSubStore(redissonPub: RedissonClient, redissonSub: RedissonClient, nodeId: Long) : PubSubStore {
    private val redissonPub: RedissonClient
    private val redissonSub: RedissonClient
    private val nodeId: Long
    private val map: ConcurrentMap<String, Queue<Int>> = PlatformDependent.newConcurrentHashMap<String, Queue<Int>>()

    init {
        this.redissonPub = redissonPub
        this.redissonSub = redissonSub
        this.nodeId = nodeId
    }

    fun publish(type: PubSubType, msg: PubSubMessage) {
        msg.setNodeId(nodeId)
        redissonPub.getTopic(type.toString()).publish(msg)
    }

    fun <T : PubSubMessage?> subscribe(type: PubSubType, listener: PubSubListener<T>, clazz: Class<T>?) {
        val name: String = type.toString()
        val topic: RTopic = redissonSub.getTopic(name)
        val regId: Int = topic.addListener(PubSubMessage::class.java, object : MessageListener<PubSubMessage?>() {
            fun onMessage(channel: CharSequence?, msg: PubSubMessage) {
                if (nodeId != msg.getNodeId()) {
                    listener.onMessage(msg as T)
                }
            }
        })
        var list: Queue<Int?>? = map.get(name)
        if (list == null) {
            list = ConcurrentLinkedQueue<Int>()
            val oldList: Queue<Int?> = map.putIfAbsent(name, list)
            if (oldList != null) {
                list = oldList
            }
        }
        list!!.add(regId)
    }

    fun unsubscribe(type: PubSubType) {
        val name: String = type.toString()
        val regIds: Queue<Int> = map.remove(name)
        val topic: RTopic = redissonSub.getTopic(name)
        for (id in regIds) {
            topic.removeListener(id)
        }
    }

    fun shutdown() {}
}