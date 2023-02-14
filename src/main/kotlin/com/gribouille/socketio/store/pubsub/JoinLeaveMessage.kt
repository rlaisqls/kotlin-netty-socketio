
package com.gribouille.socketio.store.pubsub

import java.util.*

class JoinLeaveMessage : PubSubMessage {
    var sessionId: UUID? = null
        private set
    var namespace: String? = null
        private set
    var room: String? = null
        private set

    constructor()
    constructor(id: UUID?, room: String?, namespace: String?) : super() {
        sessionId = id
        this.room = room
        this.namespace = namespace
    }

    companion object {
        private const val serialVersionUID = -944515928988033174L
    }
}