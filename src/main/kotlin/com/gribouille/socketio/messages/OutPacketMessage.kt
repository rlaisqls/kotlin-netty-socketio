
package com.gribouille.socketio.messages

import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.ClientHead

class OutPacketMessage(
    val clientHead: ClientHead,
    val transport: Transport
) : HttpMessage(clientHead.origin, clientHead.sessionId) {
}