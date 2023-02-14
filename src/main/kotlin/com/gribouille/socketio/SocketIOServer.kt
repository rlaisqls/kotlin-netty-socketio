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
package com.corundumstudio.socketio

import com.corundumstudio.socketio.listener.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.util.concurrent.Future
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Fully thread-safe.
 *
 */
class SocketIOServer(configuration: Configuration) : ClientListeners {
    private val configCopy: Configuration
    private val configuration: Configuration
    private val namespacesHub: NamespacesHub
    private val mainNamespace: SocketIONamespace
    private var pipelineFactory: SocketIOChannelInitializer = SocketIOChannelInitializer()
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null

    init {
        this.configuration = configuration
        configCopy = Configuration(configuration)
        namespacesHub = NamespacesHub(configCopy)
        mainNamespace = addNamespace(Namespace.DEFAULT_NAME)
    }

    fun setPipelineFactory(pipelineFactory: SocketIOChannelInitializer) {
        this.pipelineFactory = pipelineFactory
    }

    val allClients: Collection<com.corundumstudio.socketio.SocketIOClient>
        /**
         * Get all clients connected to default namespace
         *
         * @return clients collection
         */
        get() = namespacesHub.get(Namespace.DEFAULT_NAME).getAllClients()

    /**
     * Get client by uuid from default namespace
     *
     * @param uuid - id of client
     * @return client
     */
    fun getClient(uuid: UUID?): SocketIOClient {
        return namespacesHub.get(Namespace.DEFAULT_NAME).getClient(uuid)
    }

    val allNamespaces: Collection<com.corundumstudio.socketio.SocketIONamespace>
        /**
         * Get all namespaces
         *
         * @return namespaces collection
         */
        get() = namespacesHub.getAllNamespaces()
    val broadcastOperations: BroadcastOperations
        get() {
            val namespaces: Collection<SocketIONamespace> = namespacesHub.getAllNamespaces()
            val list: MutableList<BroadcastOperations?> = ArrayList<BroadcastOperations?>()
            var broadcast: BroadcastOperations? = null
            if (namespaces != null && namespaces.size > 0) {
                for (n in namespaces) {
                    broadcast = n.getBroadcastOperations()
                    list.add(broadcast)
                }
            }
            return MultiRoomBroadcastOperations(list)
        }

    /**
     * Get broadcast operations for clients within
     * room by `room` name
     *
     * @param room - name of room
     * @return broadcast operations
     */
    fun getRoomOperations(room: String?): BroadcastOperations {
        val namespaces: Collection<SocketIONamespace> = namespacesHub.getAllNamespaces()
        val list: MutableList<BroadcastOperations?> = ArrayList<BroadcastOperations?>()
        var broadcast: BroadcastOperations? = null
        if (namespaces != null && namespaces.size > 0) {
            for (n in namespaces) {
                broadcast = n.getRoomOperations(room)
                list.add(broadcast)
            }
        }
        return MultiRoomBroadcastOperations(list)
    }

    /**
     * Start server
     */
    fun start() {
        startAsync().syncUninterruptibly()
    }

    /**
     * Start server asynchronously
     *
     * @return void
     */
    fun startAsync(): Future<Void> {
        log.info("Session store / pubsub factory used: {}", configCopy.getStoreFactory())
        initGroups()
        pipelineFactory.start(configCopy, namespacesHub)
        var channelClass: Class<out ServerChannel?> = NioServerSocketChannel::class.java
        if (configCopy.isUseLinuxNativeEpoll()) {
            channelClass = EpollServerSocketChannel::class.java
        }
        val b = ServerBootstrap()
        b.group(bossGroup, workerGroup)
            .channel(channelClass)
            .childHandler(pipelineFactory)
        applyConnectionOptions(b)
        var addr: InetSocketAddress? = InetSocketAddress(configCopy.getPort())
        if (configCopy.getHostname() != null) {
            addr = InetSocketAddress(configCopy.getHostname(), configCopy.getPort())
        }
        return b.bind(addr).addListener(object : FutureListener<Void?> {
            @Throws(Exception::class)
            override fun operationComplete(future: Future<Void?>) {
                if (future.isSuccess) {
                    log.info("SocketIO server started at port: {}", configCopy.getPort())
                } else {
                    log.error("SocketIO server start failed at port: {}!", configCopy.getPort())
                }
            }
        })
    }

    protected fun applyConnectionOptions(bootstrap: ServerBootstrap) {
        val config: SocketConfig = configCopy.getSocketConfig()
        bootstrap.childOption(ChannelOption.TCP_NODELAY, config.isTcpNoDelay())
        if (config.getTcpSendBufferSize() !== -1) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, config.getTcpSendBufferSize())
        }
        if (config.getTcpReceiveBufferSize() !== -1) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, config.getTcpReceiveBufferSize())
            bootstrap.childOption<RecvByteBufAllocator>(
                ChannelOption.RCVBUF_ALLOCATOR,
                FixedRecvByteBufAllocator(config.getTcpReceiveBufferSize())
            )
        }
        //default value @see WriteBufferWaterMark.DEFAULT
        if (config.getWriteBufferWaterMarkLow() !== -1 && config.getWriteBufferWaterMarkHigh() !== -1) {
            bootstrap.childOption<WriteBufferWaterMark>(
                ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(
                    config.getWriteBufferWaterMarkLow(), config.getWriteBufferWaterMarkHigh()
                )
            )
        }
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive())
        bootstrap.childOption(ChannelOption.SO_LINGER, config.getSoLinger())
        bootstrap.option(ChannelOption.SO_REUSEADDR, config.isReuseAddress())
        bootstrap.option(ChannelOption.SO_BACKLOG, config.getAcceptBackLog())
    }

    protected fun initGroups() {
        if (configCopy.isUseLinuxNativeEpoll()) {
            bossGroup = EpollEventLoopGroup(configCopy.getBossThreads())
            workerGroup = EpollEventLoopGroup(configCopy.getWorkerThreads())
        } else {
            bossGroup = NioEventLoopGroup(configCopy.getBossThreads())
            workerGroup = NioEventLoopGroup(configCopy.getWorkerThreads())
        }
    }

    /**
     * Stop server
     */
    fun stop() {
        bossGroup.shutdownGracefully().syncUninterruptibly()
        workerGroup.shutdownGracefully().syncUninterruptibly()
        pipelineFactory.stop()
        log.info("SocketIO server stopped")
    }

    fun addNamespace(name: String?): SocketIONamespace {
        return namespacesHub.create(name)
    }

    fun getNamespace(name: String?): SocketIONamespace {
        return namespacesHub.get(name)
    }

    fun removeNamespace(name: String?) {
        namespacesHub.remove(name)
    }

    /**
     * Allows to get configuration provided
     * during server creation. Further changes on
     * this object not affect server.
     *
     * @return Configuration object
     */
    fun getConfiguration(): Configuration {
        return configuration
    }

    fun addMultiTypeEventListener(eventName: String?, listener: MultiTypeEventListener?, vararg eventClass: Class<*>?) {
        mainNamespace.addMultiTypeEventListener(eventName, listener, eventClass)
    }

    fun <T> addEventListener(eventName: String?, eventClass: Class<T>?, listener: DataListener<T>?) {
        mainNamespace.addEventListener(eventName, eventClass, listener)
    }

    fun addEventInterceptor(eventInterceptor: EventInterceptor?) {
        mainNamespace.addEventInterceptor(eventInterceptor)
    }

    fun removeAllListeners(eventName: String?) {
        mainNamespace.removeAllListeners(eventName)
    }

    fun addDisconnectListener(listener: DisconnectListener?) {
        mainNamespace.addDisconnectListener(listener)
    }

    fun addConnectListener(listener: ConnectListener?) {
        mainNamespace.addConnectListener(listener)
    }

    fun addPingListener(listener: PingListener?) {
        mainNamespace.addPingListener(listener)
    }

    fun addPongListener(listener: PongListener?) {
        mainNamespace.addPongListener(listener)
    }

    fun addListeners(listeners: Any?) {
        mainNamespace.addListeners(listeners)
    }

    fun addListeners(listeners: Any?, listenersClass: Class<*>?) {
        mainNamespace.addListeners(listeners, listenersClass)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SocketIOServer::class.java)
    }
}