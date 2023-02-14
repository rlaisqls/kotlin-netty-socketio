
package com.gribouille.socketio.store.pubsub

import java.util.*

enum class PubSubType {
    CONNECT, DISCONNECT, JOIN, BULK_JOIN, LEAVE, BULK_LEAVE, DISPATCH;

    override fun toString(): String {
        return name.lowercase(Locale.getDefault())
    }
}