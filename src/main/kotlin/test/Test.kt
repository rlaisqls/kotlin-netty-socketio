package test

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.gribouille.socketio.SocketConfig
import com.gribouille.socketio.SocketIOConfiguration
import com.gribouille.socketio.SocketIOServer
import org.slf4j.LoggerFactory

class Test {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
           val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val rootLogger = loggerContext.loggerList.map {
                it.level = Level.INFO
            }

            val socketConfig = SocketConfig()
            socketConfig.isReuseAddress = true

            val configuration = SocketIOConfiguration(
                port = 8081,
                origin = "*",
                socketConfig = socketConfig
            )

            val socketServer = SocketIOServer(configuration)
            socketServer.addListeners(TestClass())

            socketServer.start()
        }
    }

}

