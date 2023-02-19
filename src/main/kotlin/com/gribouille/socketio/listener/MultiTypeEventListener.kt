
package com.gribouille.socketio.listener

import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.MultiTypeArgs
import com.gribouille.socketio.SocketIOClient

interface MultiTypeEventListener : DataListener {
    override fun onData(client: SocketIOClient, data: Any, ackSender: AckRequest) {
        onData(client, data as MultiTypeArgs, ackSender)
    }
    fun onMultiTypeArgs(client: SocketIOClient, data: MultiTypeArgs, ackSender: AckRequest)
}