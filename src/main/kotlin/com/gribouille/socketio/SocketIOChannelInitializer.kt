
package com.gribouille.socketio

import com.gribouille.socketio.ack.AckManager
import com.gribouille.socketio.handler.AuthorizeHandler
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.handler.ClientsBox
import com.gribouille.socketio.handler.EncoderHandler
import com.gribouille.socketio.handler.InPacketHandler
import com.gribouille.socketio.handler.PacketListener
import com.gribouille.socketio.handler.WrongUrlHandler
import com.gribouille.socketio.namespace.NamespacesHub
import com.gribouille.socketio.protocol.PacketDecoder
import com.gribouille.socketio.protocol.PacketEncoder
import com.gribouille.socketio.scheduler.CancelableScheduler
import com.gribouille.socketio.scheduler.HashedWheelTimeoutScheduler
import com.gribouille.socketio.store.StoreFactory
import com.gribouille.socketio.transport.PollingTransport
import com.gribouille.socketio.transport.WebSocketTransport
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler
import org.slf4j.LoggerFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

class SocketIOChannelInitializer : ChannelInitializer<Channel>(), DisconnectableHub {

    private var ackManager: AckManager? = null
    private val clientsBox: ClientsBox = ClientsBox()
    private var authorizeHandler: AuthorizeHandler? = null
    private var xhrPollingTransport: PollingTransport? = null
    private var webSocketTransport: WebSocketTransport? = null
    private var encoderHandler: EncoderHandler? = null
    private var wrongUrlHandler: WrongUrlHandler? = null
    private val scheduler: CancelableScheduler = HashedWheelTimeoutScheduler()
    private var packetHandler: InPacketHandler? = null
    private var configuration: Configuration? = null

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        scheduler.update(ctx)
    }

    fun start(
        configuration: Configuration,
        namespacesHub: NamespacesHub?
    ) {
        this.configuration = configuration
        ackManager = AckManager(scheduler)
        val jsonSupport = configuration.jsonSupport!!
        val encoder = PacketEncoder(configuration, jsonSupport)
        val decoder = PacketDecoder(jsonSupport, ackManager!!)

        val factory = configuration.storeFactory
        authorizeHandler = AuthorizeHandler(
            configuration.context,
            scheduler,
            configuration,
            namespacesHub!!,
            factory,
            this,
            ackManager!!,
            clientsBox
        )
        xhrPollingTransport = PollingTransport(decoder, authorizeHandler!!, clientsBox)
        webSocketTransport = WebSocketTransport(authorizeHandler!!, configuration, scheduler, clientsBox)
        val packetListener = PacketListener(namespacesHub, ackManager!!, scheduler)
        packetHandler = InPacketHandler(packetListener, decoder, namespacesHub, configuration.exceptionListener)
        encoderHandler = EncoderHandler(configuration, encoder)
        wrongUrlHandler = WrongUrlHandler()
    }

    @Throws(Exception::class)
    protected override fun initChannel(ch: Channel) {
        val pipeline: ChannelPipeline = ch.pipeline()
        addSocketioHandlers(pipeline)
    }

    protected fun addSocketioHandlers(pipeline: ChannelPipeline) {
        pipeline.addLast(HTTP_REQUEST_DECODER, HttpRequestDecoder())
        pipeline.addLast(HTTP_AGGREGATOR, object : HttpObjectAggregator(configuration!!.maxHttpContentLength) {
            override fun newContinueResponse(
                start: HttpMessage, maxContentLength: Int,
                pipeline: ChannelPipeline
            ): Any? {
                return null
            }
        })
        pipeline.addLast(HTTP_ENCODER, HttpResponseEncoder())
        if (configuration!!.isHttpCompression) {
            pipeline.addLast(HTTP_COMPRESSION, HttpContentCompressor())
        }
        pipeline.addLast(PACKET_HANDLER, packetHandler)
        pipeline.addLast(AUTHORIZE_HANDLER, authorizeHandler)
        pipeline.addLast(XHR_POLLING_TRANSPORT, xhrPollingTransport)
        if (configuration!!.isWebsocketCompression) {
            pipeline.addLast(WEB_SOCKET_TRANSPORT_COMPRESSION, WebSocketServerCompressionHandler())
        }
        pipeline.addLast(WEB_SOCKET_TRANSPORT, webSocketTransport)
        pipeline.addLast(SOCKETIO_ENCODER, encoderHandler)
        pipeline.addLast(WRONG_URL_HANDLER, wrongUrlHandler)
    }

    override fun onDisconnect(client: ClientHead) {
        ackManager!!.onDisconnect(client)
        authorizeHandler!!.onDisconnect(client)
        configuration!!.storeFactory.onDisconnect(client)
        log.debug("Client with sessionId: {} disconnected", client.sessionId)
    }

    fun stop() {
        val factory: StoreFactory = configuration!!.storeFactory
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