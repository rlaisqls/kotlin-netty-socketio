
package com.gribouille.socketio

import com.gribouille.socketio.handler.SuccessAuthorizationListener
import com.gribouille.socketio.listener.DefaultExceptionListener
import com.gribouille.socketio.listener.ExceptionListener
import com.gribouille.socketio.protocol.JacksonJsonSupport
import com.gribouille.socketio.protocol.JsonSupport
import com.gribouille.socketio.store.MemoryStoreFactory
import com.gribouille.socketio.store.StoreFactory
import java.io.InputStream
import java.util.*

class Configuration {
    /**
     * Exception listener invoked on any exception in
     * SocketIO listener
     *
     * @param exceptionListener - listener
     *
     * @see com.gribouille.socketio.listener.ExceptionListener
     */
    var exceptionListener: ExceptionListener = DefaultExceptionListener()
    var context = "/socket.io/"
    var transports: List<Transport> = listOf(Transport.WEBSOCKET, Transport.POLLING)
    var bossThreads = 0 // 0 = current_processors_amount * 2
    var workerThreads = 0 // 0 = current_processors_amount * 2
    var isUseLinuxNativeEpoll = false
    var isAllowCustomRequests = false
    var upgradeTimeout = 10000
    var pingTimeout = 60000
    var pingInterval = 2500000
    var firstDataTimeout = 5000
    var maxHttpContentLength = 64 * 1024
    var maxFramePayloadLength = 64 * 1024
    var packagePrefix: String? = null
    var hostname: String? = null
    var port = -1
    var sslProtocol = "TLSv1"
    var keyStoreFormat = "JKS"
    var keyStore: InputStream? = null
    var keyStorePassword: String? = null
    var allowHeaders: String? = null
    var trustStoreFormat = "JKS"
    var trustStore: InputStream? = null
    var trustStorePassword: String? = null
    var isPreferDirectBuffer = true
    var socketConfig: SocketConfig = SocketConfig()
    var storeFactory: StoreFactory = MemoryStoreFactory()
    var jsonSupport: JsonSupport? = JsonSupportWrapper(JacksonJsonSupport())
    var authorizationListener: AuthorizationListener = SuccessAuthorizationListener()
    var ackMode = AckMode.MANUAL
    var isAddVersionHeader = true
    var origin: String? = null
    var isHttpCompression = true
    var isWebsocketCompression = true
    var isRandomSession = false
    var isNeedClientAuth = false

    val isHeartbeatsEnabled: Boolean
        get() = pingTimeout > 0

    constructor()

    internal constructor(conf: Configuration) {
        bossThreads = conf.bossThreads
        workerThreads = conf.workerThreads
        isUseLinuxNativeEpoll = conf.isUseLinuxNativeEpoll
        pingInterval = conf.pingInterval
        pingTimeout = conf.pingTimeout
        hostname = conf.hostname
        port = conf.port
        jsonSupport = conf.jsonSupport
        context = conf.context
        isAllowCustomRequests = conf.isAllowCustomRequests
        keyStorePassword = conf.keyStorePassword
        keyStore = conf.keyStore
        keyStoreFormat = conf.keyStoreFormat
        trustStore = conf.trustStore
        trustStoreFormat = conf.trustStoreFormat
        trustStorePassword = conf.trustStorePassword
        transports = conf.transports
        maxHttpContentLength = conf.maxHttpContentLength
        packagePrefix = conf.packagePrefix
        isPreferDirectBuffer = conf.isPreferDirectBuffer
        storeFactory = conf.storeFactory
        authorizationListener = conf.authorizationListener
        exceptionListener = conf.exceptionListener
        socketConfig = conf.socketConfig
        ackMode = conf.ackMode
        maxFramePayloadLength = conf.maxFramePayloadLength
        upgradeTimeout = conf.upgradeTimeout
        isAddVersionHeader = conf.isAddVersionHeader
        origin = conf.origin
        allowHeaders = conf.allowHeaders
        sslProtocol = conf.sslProtocol
        isHttpCompression = conf.isHttpCompression
        isWebsocketCompression = conf.isWebsocketCompression
        isRandomSession = conf.isRandomSession
        isNeedClientAuth = conf.isNeedClientAuth
    }

}