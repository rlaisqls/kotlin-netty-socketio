
package com.gribouille.socketio.store.pubsub

import com.gribouille.socketio.protocol.Packet

class DispatchMessage : PubSubMessage {
    var room: String? = null
        private set
    var namespace: String? = null
        private set
    private var packet: Packet? = null

    constructor()
    constructor(room: String?, packet: Packet?, namespace: String?) {
        this.room = room
        this.packet = packet
        this.namespace = namespace
    }

    fun getPacket(): Packet? {
        return packet
    }

    companion object {
        private const val serialVersionUID = 6692047718303934349L
    }
}