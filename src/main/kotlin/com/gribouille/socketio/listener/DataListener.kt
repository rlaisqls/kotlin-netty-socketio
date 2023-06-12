
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.ack.AckRequest

interface DataListener {
    fun onData(client: SocketIOClient, data: Any, ackSender: AckRequest)
}