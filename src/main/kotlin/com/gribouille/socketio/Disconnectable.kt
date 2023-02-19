
package com.gribouille.socketio

import com.gribouille.socketio.handler.ClientHead

interface Disconnectable {
    fun onDisconnect(client: ClientHead)
}