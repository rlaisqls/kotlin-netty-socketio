
package com.gribouille.socketio.listener

import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.transport.NamespaceClient

interface EventInterceptor {
    fun onEvent(
        client: NamespaceClient?,
        eventName: String?,
        args: List<Any?>?,
        ackRequest: AckRequest?
    )
}