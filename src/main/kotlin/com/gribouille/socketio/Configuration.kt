/**
 * Copyright (c) 2012-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.gribouille

import com.corundumstudio.socketio.handler.SuccessAuthorizationListener
import java.io.InputStream
import java.util.*

class Configuration {
    /**
     * Exception listener invoked on any exception in
     * SocketIO listener
     *
     * @param exceptionListener - listener
     *
     * @see com.corundumstudio.socketio.listener.ExceptionListener
     */
    var exceptionListener: ExceptionListener = DefaultExceptionListener()
    var context = "/socket.io"
    private var transports: List<Transport> = Arrays.asList<Transport>(Transport.WEBSOCKET, Transport.POLLING)
    var bossThreads = 0 // 0 = current_processors_amount * 2
    var workerThreads = 0 // 0 = current_processors_amount * 2
    var isUseLinuxNativeEpoll = false

    /**
     * Allow to service custom requests differs from socket.io protocol.
     * In this case it's necessary to add own handler which handle them
     * to avoid hang connections.
     * Default is `false`
     *
     * @param allowCustomRequests - `true` to allow
     */
    var isAllowCustomRequests = false

    /**
     * Transport upgrade timeout in milliseconds
     *
     * @param upgradeTimeout - upgrade timeout
     */
    var upgradeTimeout = 10000

    /**
     * Ping timeout
     * Use `0` to disable it
     *
     * @param heartbeatTimeoutSecs - time in milliseconds
     */
    var pingTimeout = 60000

    /**
     * Ping interval
     *
     * @param heartbeatIntervalSecs - time in milliseconds
     */
    var pingInterval = 25000

    /**
     * Timeout between channel opening and first data transfer
     * Helps to avoid 'silent channel' attack and prevents
     * 'Too many open files' problem in this case
     *
     * @param firstDataTimeout - timeout value
     */
    var firstDataTimeout = 5000

    /**
     * Set maximum http content length limit
     *
     * @param value
     * the maximum length of the aggregated http content.
     */
    var maxHttpContentLength = 64 * 1024

    /**
     * Set maximum websocket frame content length limit
     *
     * @param maxFramePayloadLength - length
     */
    var maxFramePayloadLength = 64 * 1024

    /**
     * Package prefix for sending json-object from client
     * without full class name.
     *
     * With defined package prefix socket.io client
     * just need to define '@class: 'SomeType'' in json object
     * instead of '@class: 'com.full.package.name.SomeType''
     *
     * @param packagePrefix - prefix string
     */
    var packagePrefix: String? = null

    /**
     * Optional parameter. If not set then bind address
     * will be 0.0.0.0 or ::0
     *
     * @param hostname - name of host
     */
    var hostname: String? = null
    var port = -1

    /**
     * Set the name of the requested SSL protocol
     *
     * @param sslProtocol - name of protocol
     */
    var sSLProtocol = "TLSv1"

    /**
     * Key store format
     *
     * @param keyStoreFormat - key store format
     */
    var keyStoreFormat = "JKS"

    /**
     * SSL key store stream, maybe appointed to any source
     *
     * @param keyStore - key store input stream
     */
    var keyStore: InputStream? = null

    /**
     * SSL key store password
     *
     * @param keyStorePassword - password of key store
     */
    var keyStorePassword: String? = null

    /**
     * Set the response Access-Control-Allow-Headers
     * @param allowHeaders - allow headers
     */
    var allowHeaders: String? = null
    var trustStoreFormat = "JKS"
    var trustStore: InputStream? = null
    var trustStorePassword: String? = null
    var keyManagerFactoryAlgorithm: String = KeyManagerFactory.getDefaultAlgorithm()

    /**
     * Buffer allocation method used during packet encoding.
     * Default is `true`
     *
     * @param preferDirectBuffer    `true` if a direct buffer should be tried to be used as target for
     * the encoded messages. If `false` is used it will allocate a heap
     * buffer, which is backed by an byte array.
     */
    var isPreferDirectBuffer = true

    /**
     * TCP socket configuration
     *
     * @param socketConfig - config
     */
    var socketConfig: SocketConfig = SocketConfig()

    /**
     * Data store - used to store session data and implements distributed pubsub.
     * Default is `MemoryStoreFactory`
     *
     * @param clientStoreFactory - implements StoreFactory
     *
     * @see com.corundumstudio.socketio.store.MemoryStoreFactory
     *
     * @see com.corundumstudio.socketio.store.RedissonStoreFactory
     *
     * @see com.corundumstudio.socketio.store.HazelcastStoreFactory
     */
    var storeFactory: StoreFactory = MemoryStoreFactory()

    /**
     * Allows to setup custom implementation of
     * JSON serialization/deserialization
     *
     * @param jsonSupport - json mapper
     *
     * @see JsonSupport
     */
    var jsonSupport: JsonSupport? = null

    /**
     * Authorization listener invoked on every handshake.
     * Accepts or denies a client by `AuthorizationListener.isAuthorized` method.
     * **Accepts** all clients by default.
     *
     * @param authorizationListener - authorization listener itself
     *
     * @see com.corundumstudio.socketio.AuthorizationListener
     */
    var authorizationListener: AuthorizationListener = SuccessAuthorizationListener()

    /**
     * Auto ack-response mode
     * Default is `AckMode.AUTO_SUCCESS_ONLY`
     *
     * @see AckMode
     *
     *
     * @param ackMode - ack mode
     */
    var ackMode = AckMode.AUTO_SUCCESS_ONLY

    /**
     * Adds **Server** header with lib version to http response.
     *
     *
     * Default is `true`
     *
     * @param addVersionHeader - `true` to add header
     */
    var isAddVersionHeader = true

    /**
     * Set **Access-Control-Allow-Origin** header value for http each
     * response.
     * Default is `null`
     *
     * If value is `null` then request **ORIGIN** header value used.
     *
     * @param origin - origin
     */
    var origin: String? = null

    /**
     * Activate http protocol compression. Uses `gzip` or
     * `deflate` encoding choice depends on the `"Accept-Encoding"` header value.
     *
     *
     * Default is `true`
     *
     * @param httpCompression - `true` to use http compression
     */
    var isHttpCompression = true

    /**
     * Activate websocket protocol compression.
     * Uses `permessage-deflate` encoding only.
     *
     *
     * Default is `true`
     *
     * @param websocketCompression - `true` to use websocket compression
     */
    var isWebsocketCompression = true
    var isRandomSession = false

    /**
     * Enable/disable client authentication.
     * Has no effect unless a trust store has been provided.
     *
     * Default is `false`
     *
     * @param needClientAuth - `true` to use client authentication
     */
    var isNeedClientAuth = false

    constructor()

    /**
     * Defend from further modifications by cloning
     *
     * @param conf - Configuration object to clone
     */
    internal constructor(conf: Configuration) {
        bossThreads = conf.bossThreads
        workerThreads = conf.workerThreads
        isUseLinuxNativeEpoll = conf.isUseLinuxNativeEpoll
        pingInterval = conf.pingInterval
        pingTimeout = conf.pingTimeout
        hostname = conf.hostname
        port = conf.port
        if (conf.jsonSupport == null) {
            try {
                javaClass.classLoader.loadClass("com.fasterxml.jackson.databind.ObjectMapper")
                try {
                    val jjs = javaClass.classLoader.loadClass("com.corundumstudio.socketio.protocol.JacksonJsonSupport")
                    val js: JsonSupport = jjs.getConstructor().newInstance() as JsonSupport
                    conf.jsonSupport = js
                } catch (e: Exception) {
                    throw IllegalArgumentException(e)
                }
            } catch (e: ClassNotFoundException) {
                throw IllegalArgumentException("Can't find jackson lib in classpath", e)
            }
        }
        jsonSupport = JsonSupportWrapper(conf.jsonSupport)
        context = conf.context
        isAllowCustomRequests = conf.isAllowCustomRequests
        keyStorePassword = conf.keyStorePassword
        keyStore = conf.keyStore
        keyStoreFormat = conf.keyStoreFormat
        trustStore = conf.trustStore
        trustStoreFormat = conf.trustStoreFormat
        trustStorePassword = conf.trustStorePassword
        keyManagerFactoryAlgorithm = conf.keyManagerFactoryAlgorithm
        setTransports(conf.getTransports().toArray(arrayOfNulls<Transport>(conf.getTransports().size)))
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
        sSLProtocol = conf.sSLProtocol
        isHttpCompression = conf.isHttpCompression
        isWebsocketCompression = conf.isWebsocketCompression
        isRandomSession = conf.isRandomSession
        isNeedClientAuth = conf.isNeedClientAuth
    }

    val isHeartbeatsEnabled: Boolean
        get() = pingTimeout > 0

    /**
     * Transports supported by server
     *
     * @param transports - list of transports
     */
    fun setTransports(vararg transports: Transport?) {
        require(transports.size != 0) { "Transports list can't be empty" }
        this.transports = Arrays.asList(*transports)
    }

    fun getTransports(): List<Transport> {
        return transports
    }
}