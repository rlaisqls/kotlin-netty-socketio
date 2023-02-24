
package com.gribouille.socketio.messages

import java.util.*

open class PostMessage(origin: String?, sessionId: UUID?) : HttpMessage(origin, sessionId)