
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient

@Deprecated("")
interface PingListener {
    fun onPing(client: SocketIOClient?)
}