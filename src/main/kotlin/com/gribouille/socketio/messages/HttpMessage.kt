
package com.gribouille.socketio.messages

import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.ClientHead
import java.util.UUID

abstract class HttpMessage(val origin: String?, val sessionId: UUID?)

class OutPacketMessage(
    val clientHead: ClientHead,
    val transport: Transport
) : HttpMessage(clientHead.origin, clientHead.sessionId)

class HttpErrorMessage(
    val data: Map<String, Any>
) : HttpMessage(null, null)

open class PostMessage(
    origin: String?,
    sessionId: UUID?
) : HttpMessage(origin, sessionId)

class OptionsMessage(
    origin: String?,
    sessionId: UUID?
) : PostMessage(origin, sessionId)