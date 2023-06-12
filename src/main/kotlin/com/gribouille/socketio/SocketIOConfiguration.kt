package com.gribouille.socketio

import com.gribouille.socketio.ack.AckMode
import com.gribouille.socketio.listener.DefaultExceptionListener
import com.gribouille.socketio.listener.ExceptionListener
import com.gribouille.socketio.protocol.JacksonJsonSupport
import com.gribouille.socketio.protocol.JsonSupport
import com.gribouille.socketio.store.MemoryStoreFactory
import com.gribouille.socketio.store.StoreFactory
import java.io.InputStream

internal var configuration: ConfigInterface =
    SocketIOConfiguration().also {
        ackMode = it.ackMode
        jsonSupport = it.jsonSupport
        storeFactory = it.storeFactory
        exceptionListener = it.exceptionListener
    }
    set(it) {
        field = it
        ackMode = it.ackMode
        jsonSupport = it.jsonSupport
        storeFactory = it.storeFactory
        exceptionListener = it.exceptionListener
    }

internal var ackMode: AckMode
    private set
internal var jsonSupport: JsonSupport
    private set
internal var storeFactory: StoreFactory
    private set
internal var exceptionListener: ExceptionListener
    private set

internal interface ConfigInterface {
    val context: String
    val transports: List<Transport>
    val bossThreads: Int
    val workerThreads: Int
    val isUseLinuxNativeEpoll: Boolean
    val isAllowCustomRequests: Boolean
    val upgradeTimeout: Int
    val pingTimeout: Int
    val pingInterval: Int
    val firstDataTimeout: Int
    val maxHttpContentLength: Int
    val maxFramePayloadLength: Int
    val packagePrefix: String?
    val hostname: String?
    val port: Int
    val sslProtocol: String
    val keyStoreFormat: String
    val keyStore: InputStream?
    val keyStorePassword: String?
    val allowHeaders: String?
    val trustStoreFormat: String
    val trustStore: InputStream?
    val trustStorePassword: String?
    val isPreferDirectBuffer: Boolean
    val socketConfig: SocketConfig
    val exceptionListener: ExceptionListener
    val storeFactory: StoreFactory
    val jsonSupport: JsonSupport
    val ackMode: AckMode
    val authorizationListener: AuthorizationListener
    val origin: String?
    val isHttpCompression: Boolean
    val isWebsocketCompression: Boolean
    val isRandomSession: Boolean
    val isNeedClientAuth: Boolean

    val isHeartbeatsEnabled: Boolean
        get() = pingTimeout > 0
}

class SocketIOConfiguration(
    override val context: String = "/socket.io",
    override val transports: List<Transport> = listOf(Transport.WEBSOCKET, Transport.POLLING),
    override val bossThreads: Int = 0, // 0 = current_processors_amount * 2
    override val workerThreads: Int = 0, // 0 = current_processors_amount * 2
    override val isUseLinuxNativeEpoll: Boolean = false,
    override val isAllowCustomRequests: Boolean = false,
    override val upgradeTimeout: Int = 10000,
    override val pingTimeout: Int = 60000000, // 60000
    override val pingInterval: Int = 60000000, // 25000
    override val firstDataTimeout: Int = 5000,
    override val maxHttpContentLength: Int = 64 * 1024,
    override val maxFramePayloadLength: Int = 64 * 1024,
    override val packagePrefix: String? = null,
    override val hostname: String? = null,
    override val port: Int = -1,
    override val sslProtocol: String = "TLSv1",
    override val keyStoreFormat: String = "JKS",
    override val keyStore: InputStream? = null,
    override val keyStorePassword: String? = null,
    override val allowHeaders: String? = null,
    override val trustStoreFormat: String = "JKS",
    override val trustStore: InputStream? = null,
    override val trustStorePassword: String? = null,
    override val isPreferDirectBuffer: Boolean = true,
    override val socketConfig: SocketConfig = SocketConfig(),
    override val exceptionListener: ExceptionListener = DefaultExceptionListener(),
    override val storeFactory: StoreFactory = MemoryStoreFactory(),
    override val jsonSupport: JsonSupport = JsonSupportWrapper(JacksonJsonSupport()),
    override var ackMode: AckMode = AckMode.AUTO_SUCCESS_ONLY,
    override val authorizationListener: AuthorizationListener = SuccessAuthorizationListener(),
    override val origin: String? = null,
    override val isHttpCompression: Boolean = true,
    override val isWebsocketCompression: Boolean = true,
    override val isRandomSession: Boolean = false,
    override val isNeedClientAuth: Boolean = false
) : ConfigInterface

/**
 * TCP socket configuration contains configuration for main server channel
 * and client channels
 *
 * @see java.net.SocketOptions
 */
class SocketConfig {
    var isTcpNoDelay = true
    var tcpSendBufferSize = -1
    var tcpReceiveBufferSize = -1
    var isTcpKeepAlive = false
    var soLinger = -1
    var isReuseAddress = false
    var acceptBackLog = 1024
    var writeBufferWaterMarkLow = -1
    var writeBufferWaterMarkHigh = -1
}