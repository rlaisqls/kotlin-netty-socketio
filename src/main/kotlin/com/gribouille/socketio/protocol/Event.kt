
package com.gribouille.socketio.protocol

data class Event(
    val name: String?,
    val args: List<Any>?
)