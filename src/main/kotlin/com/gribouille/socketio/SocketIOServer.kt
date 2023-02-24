
package com.gribouille.socketio

import com.gribouille.socketio.listener.*
import com.gribouille.socketio.namespace.Namespace
import com.gribouille.socketio.namespace.NamespacesHub
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.FixedRecvByteBufAllocator
import io.netty.channel.RecvByteBufAllocator
import io.netty.channel.ServerChannel
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.FutureListener
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
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

    val allClients: Collection<com.gribouille.socketio.SocketIOClient>
        get() = namespacesHub.get(Namespace.DEFAULT_NAME).allClients

    fun getClient(uuid: UUID): SocketIOClient {
        return namespacesHub[Namespace.DEFAULT_NAME].getClient(uuid)!!
    }

    val allNamespaces: Collection<SocketIONamespace>
        get() = namespacesHub.allNamespaces
    val broadcastOperations: BroadcastOperations
        get() {
            val namespaces: Collection<SocketIONamespace> = namespacesHub.allNamespaces
            val list: MutableList<BroadcastOperations> = ArrayList<BroadcastOperations>()
            var broadcast: BroadcastOperations? = null
            if (namespaces != null && namespaces.size > 0) {
                for (n in namespaces) {
                    list.add(n.broadcastOperations!!)
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
    fun getRoomOperations(room: String): BroadcastOperations {
        val namespaces: Collection<SocketIONamespace> = namespacesHub.allNamespaces
        val list: MutableList<BroadcastOperations> = ArrayList<BroadcastOperations>()
        var broadcast: BroadcastOperations? = null
        if (namespaces != null && namespaces.size > 0) {
            for (n in namespaces) {
                list.add(n.getRoomOperations(room)!!)
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
        log.info("Session store / pubsub factory used: {}", configCopy.storeFactory)
        initGroups()
        pipelineFactory.start(configCopy, namespacesHub)
        var channelClass: Class<out ServerChannel?> = NioServerSocketChannel::class.java
        if (configCopy.isUseLinuxNativeEpoll) {
            channelClass = EpollServerSocketChannel::class.java
        }
        val b = ServerBootstrap()
        b.group(bossGroup, workerGroup)
            .channel(channelClass)
            .childHandler(pipelineFactory)
        applyConnectionOptions(b)
        var addr: InetSocketAddress? = InetSocketAddress(configCopy.port)
        if (configCopy.hostname != null) {
            addr = InetSocketAddress(configCopy.hostname, configCopy.port)
        }
        return b.bind(addr).addListener(object : FutureListener<Void?> {
            @Throws(Exception::class)
            override fun operationComplete(future: Future<Void?>) {
                if (future.isSuccess) {
                    log.info("SocketIO server started at port: {}", configCopy.port)
                } else {
                    log.error("SocketIO server start failed at port: {}!", configCopy.port)
                }
            }
        })
    }

    protected fun applyConnectionOptions(bootstrap: ServerBootstrap) {
        val config: SocketConfig = configCopy.socketConfig
        bootstrap.childOption(ChannelOption.TCP_NODELAY, config.isTcpNoDelay)
        if (config.tcpSendBufferSize !== -1) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, config.tcpSendBufferSize)
        }
        if (config.tcpReceiveBufferSize !== -1) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, config.tcpReceiveBufferSize)
            bootstrap.childOption<RecvByteBufAllocator>(
                ChannelOption.RCVBUF_ALLOCATOR,
                FixedRecvByteBufAllocator(config.tcpReceiveBufferSize)
            )
        }
        //default value @see WriteBufferWaterMark.DEFAULT
        if (config.writeBufferWaterMarkLow !== -1 && config.writeBufferWaterMarkHigh !== -1) {
            bootstrap.childOption<WriteBufferWaterMark>(
                ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(
                    config.writeBufferWaterMarkLow, config.writeBufferWaterMarkHigh
                )
            )
        }
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive)
        bootstrap.childOption(ChannelOption.SO_LINGER, config.soLinger)
        bootstrap.option(ChannelOption.SO_REUSEADDR, config.isReuseAddress)
        bootstrap.option(ChannelOption.SO_BACKLOG, config.acceptBackLog)
    }

    protected fun initGroups() {
        if (configCopy.isUseLinuxNativeEpoll) {
            bossGroup = EpollEventLoopGroup(configCopy.bossThreads)
            workerGroup = EpollEventLoopGroup(configCopy.workerThreads)
        } else {
            bossGroup = NioEventLoopGroup(configCopy.bossThreads)
            workerGroup = NioEventLoopGroup(configCopy.workerThreads)
        }
    }

    fun stop() {
        bossGroup!!.shutdownGracefully().syncUninterruptibly()
        workerGroup!!.shutdownGracefully().syncUninterruptibly()
        pipelineFactory.stop()
        log.info("SocketIO server stopped")
    }

    fun addNamespace(name: String): SocketIONamespace {
        return namespacesHub.create(name)
    }

    fun getNamespace(name: String): SocketIONamespace {
        return namespacesHub[name]
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

    override fun addEventListener(eventName: String, eventClass: Class<*>, listener: DataListener) {
        mainNamespace.addEventListener(eventName, eventClass, listener)
    }

    override fun addEventInterceptor(eventInterceptor: EventInterceptor) {
        mainNamespace.addEventInterceptor(eventInterceptor)
    }

    override fun removeAllListeners(eventName: String) {
        mainNamespace.removeAllListeners(eventName)
    }

    override fun addDisconnectListener(listener: DisconnectListener) {
        mainNamespace.addDisconnectListener(listener)
    }

    override fun addConnectListener(listener: ConnectListener) {
        mainNamespace.addConnectListener(listener)
    }

    override fun addPingListener(listener: PingListener) {
        mainNamespace.addPingListener(listener)
    }

    override fun addPongListener(listener: PongListener) {
        mainNamespace.addPongListener(listener)
    }

    override fun addListeners(listeners: Any) {
        mainNamespace.addListeners(listeners)
    }

    override fun addListeners(listeners: Any, listenersClass: Class<*>) {
        mainNamespace.addListeners(listeners, listenersClass)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SocketIOServer::class.java)
    }
}