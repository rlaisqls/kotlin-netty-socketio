
package com.gribouille.socketio.store.pubsub

interface PubSubStore {
    fun publish(type: PubSubType?, msg: PubSubMessage?)
    fun <T : PubSubMessage?> subscribe(type: PubSubType?, listener: PubSubListener<T>?, clazz: Class<T>?)
    fun unsubscribe(type: PubSubType?)
    fun shutdown()
}