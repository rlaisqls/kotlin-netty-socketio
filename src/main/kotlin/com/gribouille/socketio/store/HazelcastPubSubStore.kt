
package com.gribouille.socketio.store

import com.gribouille.socketio.store.pubsub.PubSubListener
import io.netty.util.internal.PlatformDependent
import java.util.*

class HazelcastPubSubStore(hazelcastPub: HazelcastInstance, hazelcastSub: HazelcastInstance, nodeId: Long) :
    PubSubStore {
    private val hazelcastPub: HazelcastInstance
    private val hazelcastSub: HazelcastInstance
    private val nodeId: Long
    private val map: ConcurrentMap<String, Queue<String>> =
        PlatformDependent.newConcurrentHashMap<String, Queue<String>>()

    init {
        this.hazelcastPub = hazelcastPub
        this.hazelcastSub = hazelcastSub
        this.nodeId = nodeId
    }

    fun publish(type: PubSubType, msg: PubSubMessage) {
        msg.setNodeId(nodeId)
        hazelcastPub.getTopic(type.toString()).publish(msg)
    }

    fun <T : PubSubMessage?> subscribe(type: PubSubType, listener: PubSubListener<T>, clazz: Class<T>?) {
        val name: String = type.toString()
        val topic: ITopic<T> = hazelcastSub.getTopic(name)
        val regId: String = topic.addMessageListener(object : MessageListener<T>() {
            fun onMessage(message: Message<T>) {
                val msg: PubSubMessage = message.getMessageObject()
                if (nodeId != msg.getNodeId()) {
                    listener.onMessage(message.getMessageObject())
                }
            }
        })
        var list: Queue<String?>? = map.get(name)
        if (list == null) {
            list = ConcurrentLinkedQueue<String>()
            val oldList: Queue<String?> = map.putIfAbsent(name, list)
            if (oldList != null) {
                list = oldList
            }
        }
        list!!.add(regId)
    }

    fun unsubscribe(type: PubSubType) {
        val name: String = type.toString()
        val regIds: Queue<String> = map.remove(name)
        val topic: ITopic<Any> = hazelcastSub.getTopic(name)
        for (id in regIds) {
            topic.removeMessageListener(id)
        }
    }

    fun shutdown() {}
}