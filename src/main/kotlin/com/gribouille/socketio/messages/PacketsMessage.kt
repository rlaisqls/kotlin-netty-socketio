
package com.gribouille.socketio.messages

import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.ClientHead
import io.netty.buffer.ByteBuf

class PacketsMessage(
    val client: ClientHead,
    val content: ByteBuf,
    val transport: Transport
)