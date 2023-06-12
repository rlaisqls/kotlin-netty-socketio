
package com.gribouille.socketio.ack

class AckArgs(val args: List<Any>)

abstract class AckCallback(
    val resultClass: Class<*>,
    val timeout: Int = -1
) {
    abstract fun onSuccess(result: Any?)
    open fun onTimeout() {}
}