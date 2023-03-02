
package com.gribouille.socketio

abstract class AckCallback(
    val resultClass: Class<*>,
    val timeout: Int = -1
) {
    abstract fun onSuccess(result: Any?)
    open fun onTimeout() {}
}