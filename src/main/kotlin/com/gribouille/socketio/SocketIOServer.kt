
package com.gribouille.socketio

import com.gribouille.socketio.listener.*
import com.gribouille.socketio.namespace.Namespace
import com.gribouille.socketio.namespace.namespacesHub
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.FixedRecvByteBufAllocator
import io.netty.channel.ServerChannel
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.FutureListener
import java.net.InetSocketAddress
import java.util.*
import org.slf4j.LoggerFactory

/**
 * Fully thread-safe.
 */
class SocketIOServer(
    private val configuration: SocketIOConfiguration,
    private val pipelineFactory: SocketIOChannelInitializer = SocketIOChannelInitializer()
) : ClientListeners {

    private val mainNamespace = addNamespace(Namespace.DEFAULT_NAME)

    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null

    val allClients: Collection<SocketIOClient>
        get() = namespacesHub[Namespace.DEFAULT_NAME]!!.allClients

    fun getClient(uuid: UUID): SocketIOClient {
        return namespacesHub[Namespace.DEFAULT_NAME]!!.getClient(uuid)!!
    }

    val allNamespaces: Collection<SocketIONamespace>
        get() = namespacesHub.allNamespaces

    val broadcastOperations: BroadcastOperations
        get() {
            val namespaces: Collection<SocketIONamespace> = namespacesHub.allNamespaces
            val list = ArrayList<BroadcastOperations>()
            var broadcast: BroadcastOperations? = null
            if (namespaces.isNotEmpty()) {
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
        val list = ArrayList<BroadcastOperations>()
        var broadcast: BroadcastOperations? = null

        if (namespaces.isNotEmpty()) {
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
        log.info("Session store / pubsub factory used: {}", configuration.storeFactory)
        pipelineFactory.start(configuration)
        initGroups()

        val channelClass: Class<out ServerChannel?> = if (configuration.isUseLinuxNativeEpoll) {
            EpollServerSocketChannel::class.java
        } else NioServerSocketChannel::class.java

        val b = ServerBootstrap()
            .apply {
                group(bossGroup, workerGroup)
                    .channel(channelClass)
                    .childHandler(pipelineFactory)
            }

        applyConnectionOptions(b)

        val addr = configuration.hostname?.let {
            InetSocketAddress(it, configuration.port)
        } ?: InetSocketAddress(configuration.port)

        return b.bind(addr)
            .addListener(object : FutureListener<Void?> {
                @Throws(Exception::class)
                override fun operationComplete(future: Future<Void?>) {
                    if (future.isSuccess) {
                        log.info("SocketIO server started at port: {}", configuration.port)
                    } else {
                        log.error("SocketIO server start failed at port: {}!", configuration.port)
                    }
                }
            }).sync()
    }

    protected fun applyConnectionOptions(bootstrap: ServerBootstrap) {
        val config = configuration.socketConfig
        with(bootstrap) {
            childOption(ChannelOption.TCP_NODELAY, config.isTcpNoDelay)
            childOption(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive)
            childOption(ChannelOption.SO_LINGER, config.soLinger)
            option(ChannelOption.SO_REUSEADDR, config.isReuseAddress)
            option(ChannelOption.SO_BACKLOG, config.acceptBackLog)
            if (config.tcpSendBufferSize != -1)
                childOption(ChannelOption.SO_SNDBUF, config.tcpSendBufferSize)
            if (config.tcpReceiveBufferSize != -1) {
                childOption(ChannelOption.SO_RCVBUF, config.tcpReceiveBufferSize)
                childOption(
                    ChannelOption.RCVBUF_ALLOCATOR,
                    FixedRecvByteBufAllocator(config.tcpReceiveBufferSize)
                )
            }
            //default value @see WriteBufferWaterMark.DEFAULT
            if (config.writeBufferWaterMarkLow != -1 && config.writeBufferWaterMarkHigh != -1) {
                childOption(
                    ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(
                        config.writeBufferWaterMarkLow, config.writeBufferWaterMarkHigh
                    )
                )
            }
        }
    }

    protected fun initGroups() {
        if (configuration.isUseLinuxNativeEpoll) {
            bossGroup = EpollEventLoopGroup(configuration.bossThreads)
            workerGroup = EpollEventLoopGroup(configuration.workerThreads)
        } else {
            bossGroup = NioEventLoopGroup(configuration.bossThreads)
            workerGroup = NioEventLoopGroup(configuration.workerThreads)
        }
    }

    fun stop() {
        bossGroup!!.shutdownGracefully().syncUninterruptibly()
        workerGroup!!.shutdownGracefully().syncUninterruptibly()
        pipelineFactory.stop()
        log.info("SocketIO server stopped")
    }

    fun addNamespace(name: String): SocketIONamespace {
        return namespacesHub.create(name, configuration)
    }

    fun getNamespace(name: String): SocketIONamespace {
        return namespacesHub[name]!!
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
    fun getConfiguration(): SocketIOConfiguration {
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