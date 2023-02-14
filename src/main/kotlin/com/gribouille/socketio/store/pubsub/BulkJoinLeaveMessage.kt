
package com.gribouille.socketio.store.pubsub

import java.util.*

class BulkJoinLeaveMessage : PubSubMessage {
    var sessionId: UUID? = null
        private set
    var namespace: String? = null
        private set
    var rooms: Set<String>? = null
        private set

    constructor()
    constructor(id: UUID?, rooms: Set<String>?, namespace: String?) : super() {
        sessionId = id
        this.rooms = rooms
        this.namespace = namespace
    }

    companion object {
        private const val serialVersionUID = 7506016762607624388L
    }
}