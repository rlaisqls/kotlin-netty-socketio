
package com.gribouille.socketio.store.pubsub

import java.io.Serializable

abstract class PubSubMessage : Serializable {
    var nodeId: Long? = null

    companion object {
        private const val serialVersionUID = -8789343104393884987L
    }
}