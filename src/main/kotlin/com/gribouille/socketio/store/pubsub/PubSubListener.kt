
package com.gribouille.socketio.store.pubsub

interface PubSubListener<T> {
    fun onMessage(data: T)
}