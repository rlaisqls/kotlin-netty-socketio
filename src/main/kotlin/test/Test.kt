package test

import com.gribouille.socketio.Configuration
import com.gribouille.socketio.SocketConfig
import com.gribouille.socketio.SocketIOServer

class Test {
}

fun main() {

    val socketConfig = SocketConfig()
    socketConfig.isReuseAddress = true

    val configuration = Configuration()
    configuration.port = 8081
    configuration.origin = "*"
    configuration.socketConfig = socketConfig

    val socketServer = SocketIOServer(configuration)
    socketServer.addListeners(TestClass())

    socketServer.start()
}