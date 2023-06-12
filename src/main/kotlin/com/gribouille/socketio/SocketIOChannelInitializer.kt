
package com.gribouille.socketio

import com.gribouille.socketio.ack.ackManager
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.handler.authorizeHandler
import com.gribouille.socketio.handler.encoderHandler
import com.gribouille.socketio.handler.packetHandler
import com.gribouille.socketio.handler.wrongUrlHandler
import com.gribouille.socketio.scheduler.scheduler
import com.gribouille.socketio.transport.pollingTransport
import com.gribouille.socketio.transport.webSocketTransport
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

open class SocketIOChannelInitializer : ChannelInitializer<Channel>() {

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        scheduler.update(ctx)
    }

    fun start(
        customConfiguration: SocketIOConfiguration,
    ) {
        configuration = customConfiguration
    }

    @Throws(Exception::class)
    override fun initChannel(ch: Channel) {
        val pipeline = ch.pipeline()
        addSocketioHandlers(pipeline)
    }

    protected fun addSocketioHandlers(pipeline: ChannelPipeline) {
        with(pipeline) {
            addLast(HTTP_REQUEST_DECODER, HttpRequestDecoder())
            addLast(HTTP_AGGREGATOR, object : HttpObjectAggregator(configuration.maxHttpContentLength) {
                override fun newContinueResponse(
                    start: HttpMessage, maxContentLength: Int,
                    pipeline: ChannelPipeline
                ): Any? {
                    return null
                }
            })
            addLast(HTTP_ENCODER, HttpResponseEncoder())
            if (configuration.isHttpCompression)
                addLast(HTTP_COMPRESSION, HttpContentCompressor())
            addLast(PACKET_HANDLER, packetHandler)
            addLast(AUTHORIZE_HANDLER, authorizeHandler)
            addLast(XHR_POLLING_TRANSPORT, pollingTransport)
            if (configuration.isWebsocketCompression)
                addLast(WEB_SOCKET_TRANSPORT_COMPRESSION, WebSocketServerCompressionHandler())
            addLast(WEB_SOCKET_TRANSPORT, webSocketTransport)
            addLast(SOCKETIO_ENCODER, encoderHandler)
            addLast(WRONG_URL_HANDLER, wrongUrlHandler)
        }
    }

    protected fun setDisconnectableHub(customDisconnectableHub: DisconnectableHub) {
        disconnectableHub = customDisconnectableHub
    }

    fun stop() {
        configuration.storeFactory.shutdown()
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

    }
}

internal var disconnectableHub = object : DisconnectableHub by DisconnectableHubImpl() {}
internal class DisconnectableHubImpl : DisconnectableHub {

    override fun onDisconnect(client: ClientHead) {
        ackManager.onDisconnect(client)
        authorizeHandler.onDisconnect(client)
        configuration.storeFactory.onDisconnect(client)
        log.debug("Client with sessionId: {} disconnected", client.sessionId)
    }
    private val log = LoggerFactory.getLogger(SocketIOChannelInitializer::class.java)
}
