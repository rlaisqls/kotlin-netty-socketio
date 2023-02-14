
package com.gribouille.socketio.store.pubsub

import java.util.*

class DisconnectMessage : PubSubMessage {
    var sessionId: UUID? = null
        private set

    constructor()
    constructor(sessionId: UUID?) : super() {
        this.sessionId = sessionId
    }

    companion object {
        private const val serialVersionUID = -2763553673397520368L
    }
}