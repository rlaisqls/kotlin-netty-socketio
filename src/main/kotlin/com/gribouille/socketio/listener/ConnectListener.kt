
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient

interface ConnectListener {
    fun onConnect(client: SocketIOClient?)
}