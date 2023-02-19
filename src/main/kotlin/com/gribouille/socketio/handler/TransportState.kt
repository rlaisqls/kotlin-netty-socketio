
package com.gribouille.socketio.handler

import com.gribouille.socketio.protocol.Packet
import io.netty.channel.Channel
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class TransportState {
    var packetsQueue: Queue<Packet?>? = ConcurrentLinkedQueue<Packet>()
    var channel: Channel? = null
        private set

    fun update(channel: Channel?): Channel? {
        val prevChannel = this.channel
        this.channel = channel
        return prevChannel
    }
}