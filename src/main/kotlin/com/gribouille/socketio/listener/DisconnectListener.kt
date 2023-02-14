
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient

interface DisconnectListener {
    fun onDisconnect(client: SocketIOClient?)
}