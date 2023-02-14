
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient

interface PongListener {
    fun onPong(client: SocketIOClient?)
}