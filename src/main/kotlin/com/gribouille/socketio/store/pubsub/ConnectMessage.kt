
package com.gribouille.socketio.store.pubsub

import java.util.*

class ConnectMessage : PubSubMessage {
    var sessionId: UUID? = null
        private set

    constructor()
    constructor(sessionId: UUID?) : super() {
        this.sessionId = sessionId
    }

    companion object {
        private const val serialVersionUID = 3108918714495865101L
    }
}