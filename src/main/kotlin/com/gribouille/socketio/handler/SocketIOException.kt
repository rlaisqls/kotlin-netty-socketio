
package com.gribouille.socketio.handler

class SocketIOException : RuntimeException {
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(message: String?) : super(message)
    constructor(cause: Throwable?) : super(cause)

    companion object {
        private const val serialVersionUID = -9218908839842557188L
    }
}