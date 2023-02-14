
package com.gribouille.socketio

import com.gribouille.socketio.ack.AckManager
import io.netty.channel.Channel
import io.netty.handler.codec.http.HttpMessage
import org.slf4j.LoggerFactory

class SocketIOChannelInitializer : ChannelInitializer<Channel?>(), DisconnectableHub {
    private var ackManager: AckManager? = null
    private val clientsBox: ClientsBox = ClientsBox()
    private var authorizeHandler: AuthorizeHandler? = null
    private var xhrPollingTransport: PollingTransport? = null
    private var webSocketTransport: WebSocketTransport? = null
    private val webSocketTransportCompression: WebSocketServerCompressionHandler = WebSocketServerCompressionHandler()
    private var encoderHandler: EncoderHandler? = null
    private var wrongUrlHandler: WrongUrlHandler? = null
    private val scheduler: CancelableScheduler = HashedWheelTimeoutScheduler()
    private var packetHandler: InPacketHandler? = null
    private var sslContext: SSLContext? = null
    private var configuration: Configuration? = null
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        scheduler.update(ctx)
    }

    fun start(configuration: Configuration, namespacesHub: NamespacesHub?) {
        this.configuration = configuration
        ackManager = AckManager(scheduler)
        val jsonSupport: JsonSupport = configuration.getJsonSupport()
        val encoder = PacketEncoder(configuration, jsonSupport)
        val decoder = PacketDecoder(jsonSupport, ackManager)
        val connectPath: String = configuration.getContext() + "/"
        val isSsl = configuration.getKeyStore() != null
        if (isSsl) {
            sslContext = try {
                createSSLContext(configuration)
            } catch (e: Exception) {
                throw IllegalStateException(e)
            }
        }
        val factory: StoreFactory = configuration.getStoreFactory()
        authorizeHandler = AuthorizeHandler(
            connectPath,
            scheduler,
            configuration,
            namespacesHub,
            factory,
            this,
            ackManager,
            clientsBox
        )
        factory.init(namespacesHub, authorizeHandler, jsonSupport)
        xhrPollingTransport = PollingTransport(decoder, authorizeHandler, clientsBox)
        webSocketTransport = WebSocketTransport(isSsl, authorizeHandler, configuration, scheduler, clientsBox)
        val packetListener = PacketListener(ackManager, namespacesHub, xhrPollingTransport, scheduler)
        packetHandler = InPacketHandler(packetListener, decoder, namespacesHub, configuration.getExceptionListener())
        try {
            encoderHandler = EncoderHandler(configuration, encoder)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
        wrongUrlHandler = WrongUrlHandler()
    }

    @Throws(Exception::class)
    protected override fun initChannel(ch: Channel) {
        val pipeline: ChannelPipeline = ch.pipeline()
        addSslHandler(pipeline)
        addSocketioHandlers(pipeline)
    }

    /**
     * Adds the ssl handler
     *
     * @param pipeline - channel pipeline
     */
    protected fun addSslHandler(pipeline: ChannelPipeline) {
        if (sslContext != null) {
            val engine: SSLEngine = sslContext.createSSLEngine()
            engine.setUseClientMode(false)
            if (configuration.isNeedClientAuth() && configuration.getTrustStore() != null) {
                engine.setNeedClientAuth(true)
            }
            pipeline.addLast(SSL_HANDLER, SslHandler(engine))
        }
    }

    /**
     * Adds the socketio channel handlers
     *
     * @param pipeline - channel pipeline
     */
    protected fun addSocketioHandlers(pipeline: ChannelPipeline) {
        pipeline.addLast(HTTP_REQUEST_DECODER, HttpRequestDecoder())
        pipeline.addLast(HTTP_AGGREGATOR, object : HttpObjectAggregator(configuration.getMaxHttpContentLength()) {
            protected override fun newContinueResponse(
                start: HttpMessage, maxContentLength: Int,
                pipeline: ChannelPipeline
            ): Any {
                return null
            }
        })
        pipeline.addLast(HTTP_ENCODER, HttpResponseEncoder())
        if (configuration.isHttpCompression()) {
            pipeline.addLast(HTTP_COMPRESSION, HttpContentCompressor())
        }
        pipeline.addLast(PACKET_HANDLER, packetHandler)
        pipeline.addLast(AUTHORIZE_HANDLER, authorizeHandler)
        pipeline.addLast(XHR_POLLING_TRANSPORT, xhrPollingTransport)
        // TODO use single instance when https://github.com/netty/netty/issues/4755 will be resolved
        if (configuration.isWebsocketCompression()) {
            pipeline.addLast(WEB_SOCKET_TRANSPORT_COMPRESSION, WebSocketServerCompressionHandler())
        }
        pipeline.addLast(WEB_SOCKET_TRANSPORT, webSocketTransport)
        pipeline.addLast(SOCKETIO_ENCODER, encoderHandler)
        pipeline.addLast(WRONG_URL_HANDLER, wrongUrlHandler)
    }

    @Throws(Exception::class)
    private fun createSSLContext(configuration: Configuration): SSLContext {
        var managers: Array<TrustManager?>? = null
        if (configuration.getTrustStore() != null) {
            val ts: KeyStore = KeyStore.getInstance(configuration.getTrustStoreFormat())
            ts.load(configuration.getTrustStore(), configuration.getTrustStorePassword().toCharArray())
            val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(ts)
            managers = tmf.getTrustManagers()
        }
        val ks: KeyStore = KeyStore.getInstance(configuration.getKeyStoreFormat())
        ks.load(configuration.getKeyStore(), configuration.getKeyStorePassword().toCharArray())
        val kmf: KeyManagerFactory = KeyManagerFactory.getInstance(configuration.getKeyManagerFactoryAlgorithm())
        kmf.init(ks, configuration.getKeyStorePassword().toCharArray())
        val serverContext: SSLContext = SSLContext.getInstance(configuration.getSSLProtocol())
        serverContext.init(kmf.getKeyManagers(), managers, null)
        return serverContext
    }

    fun onDisconnect(client: ClientHead) {
        ackManager.onDisconnect(client)
        authorizeHandler.onDisconnect(client)
        configuration.getStoreFactory().onDisconnect(client)
        configuration.getStoreFactory().pubSubStore()
            .publish(PubSubType.DISCONNECT, DisconnectMessage(client.getSessionId()))
        log.debug("Client with sessionId: {} disconnected", client.getSessionId())
    }

    fun stop() {
        val factory: StoreFactory = configuration.getStoreFactory()
        factory.shutdown()
        scheduler.shutdown()
    }

    companion object {
        const val SOCKETIO_ENCODER = "socketioEncoder"
        const val WEB_SOCKET_TRANSPORT_COMPRESSION = "webSocketTransportCompression"
        const val WEB_SOCKET_TRANSPORT = "webSocketTransport"
        const val WEB_SOCKET_AGGREGATOR = "webSocketAggregator"
        const val XHR_POLLING_TRANSPORT = "xhrPollingTransport"
        const val AUTHORIZE_HANDLER = "authorizeHandler"
        const val PACKET_HANDLER = "packetHandler"
        const val HTTP_ENCODER = "httpEncoder"
        const val HTTP_COMPRESSION = "httpCompression"
        const val HTTP_AGGREGATOR = "httpAggregator"
        const val HTTP_REQUEST_DECODER = "httpDecoder"
        const val SSL_HANDLER = "ssl"
        const val RESOURCE_HANDLER = "resourceHandler"
        const val WRONG_URL_HANDLER = "wrongUrlBlocker"
        private val log = LoggerFactory.getLogger(SocketIOChannelInitializer::class.java)
    }
}