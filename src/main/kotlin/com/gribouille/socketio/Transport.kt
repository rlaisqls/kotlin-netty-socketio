
package com.gribouille.socketio

import com.gribouille.socketio.transport.WebSocketTransport

enum class Transport(val value: String) {
    WEBSOCKET(WebSocketTransport.NAME), POLLING(PollingTransport.NAME)

}