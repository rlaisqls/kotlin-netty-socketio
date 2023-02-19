
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient

interface PingListener {
    fun onPing(client: SocketIOClient?)
}