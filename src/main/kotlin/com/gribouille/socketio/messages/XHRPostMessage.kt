
package com.gribouille.socketio.messages

import java.util.*

open class XHRPostMessage(origin: String?, sessionId: UUID?) : HttpMessage(origin, sessionId)