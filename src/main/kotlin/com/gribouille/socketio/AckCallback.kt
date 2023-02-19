
package com.gribouille.socketio

import com.gribouille.socketio.SocketIOClient

abstract class AckCallback(
    val resultClass: Class<*>,
    val timeout: Int = -1
) {
    abstract fun onSuccess(result: Any?)
    open fun onTimeout() {}
}