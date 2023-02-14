
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.MultiTypeArgs

interface DataListener<T> {
    /**
     * Invokes when data object received from client
     *
     * @param client - receiver
     * @param data - received object
     * @param ackSender - ack request
     */
    @Throws(Exception::class)
    fun onData(client: SocketIOClient?, data: MultiTypeArgs, ackSender: AckRequest?)
}