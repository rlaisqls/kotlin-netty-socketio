
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.AckRequest

interface DataListener {

    @Throws(Exception::class)
    fun onData(client: SocketIOClient, data: Any, ackSender: AckRequest)
}