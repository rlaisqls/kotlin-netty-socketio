
package com.gribouille.socketio.transport

import com.gribouille.socketio.SocketIOChannelInitializer
import com.gribouille.socketio.Transport
import com.gribouille.socketio.configuration
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.handler.authorizeHandler
import com.gribouille.socketio.handler.clientsBox
import com.gribouille.socketio.messages.PacketsMessage
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.scheduler
import io.netty.buffer.ByteBufHolder
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import java.util.*
import org.slf4j.LoggerFactory

interface WebSocketTransport : ChannelHandler {
    companion object {
        const val NAME = "websocket"
    }
}

internal val webSocketTransport: WebSocketTransport = @Sharable object :
    WebSocketTransport, ChannelInboundHandlerAdapter() {

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is CloseWebSocketFrame -> {
                ctx.channel().writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE)
            }

            is BinaryWebSocketFrame, is TextWebSocketFrame -> {
                val frame = msg as ByteBufHolder
                clientsBox[ctx.channel()]?.let { client ->
                    ctx.pipeline().fireChannelRead(
                        PacketsMessage(
                            client,
                            frame.content(),
                            Transport.WEBSOCKET
                        )
                    )
                    frame.release()
                } ?: run {
                    log.debug("Client with was already disconnected. Channel closed!")
                    ctx.channel().close()
                    frame.release()
                }
            }
            is FullHttpRequest -> {
                val queryDecoder = QueryStringDecoder(msg.uri())
                val path = queryDecoder.path()

                if (getParam("transport", queryDecoder) == WebSocketTransport.NAME) try {
                    if (!configuration.transports.contains(Transport.WEBSOCKET)) {
                        log.debug(
                            "{} transport not supported by configuration.",
                            Transport.WEBSOCKET
                        )
                        ctx.channel().close()
                        return
                    }
                    getParam("sid", queryDecoder)?.let { sid ->
                        val sessionId = UUID.fromString(sid)
                        handshake(ctx, sessionId, path, msg)
                    } ?: run {
                        val client: ClientHead = ctx.channel().attr(ClientHead.CLIENT).get()
                        // first connection
                        handshake(ctx, client.sessionId!!, path, msg)
                    }
                } finally {
                    msg.release()
                } else ctx.fireChannelRead(msg)
            }
            else -> {
                ctx.fireChannelRead(msg)
            }
        }
    }

    private fun getParam(pramName: String, queryDecoder: QueryStringDecoder) =
        queryDecoder.parameters()[pramName]?.get(0)

    @Throws(Exception::class)
    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        if (clientsBox[ctx.channel()]?.isTransportChannel(ctx.channel(), Transport.WEBSOCKET) == true) {
            ctx.flush()
        } else {
            super.channelReadComplete(ctx)
        }
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()

        clientsBox[channel]?.let { client ->
            if (client.isTransportChannel(ctx.channel(), Transport.WEBSOCKET)) {
                log.debug("channel inactive {}", client.sessionId)
                client.onChannelDisconnect()
            }
            val packet = Packet(PacketType.MESSAGE).apply { subType = PacketType.DISCONNECT }
            client.send(packet)
        }

        super.channelInactive(ctx)
        channel.close()
        ctx.close()
    }

    private fun handshake(
        ctx: ChannelHandlerContext,
        sessionId: UUID,
        path: String,
        req: FullHttpRequest
    ) {
        val channel = ctx.channel()

        val handshaker = WebSocketServerHandshakerFactory(
            getWebSocketLocation(req),
            null,
            true,
            configuration.maxFramePayloadLength
        ).newHandshaker(req)

        handshaker?.let {
            addFutureListener(handshaker, channel, req, sessionId)
        } ?: run {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
        }
    }

    private fun addFutureListener(
        handshaker: WebSocketServerHandshaker,
        channel: Channel,
        req: FullHttpRequest,
        sessionId: UUID
    ) {
        val future = handshaker.handshake(channel, req)

        future.addListener(object : ChannelFutureListener {
            override fun operationComplete(future: ChannelFuture) {
                if (future.isSuccess) {
                    channel.pipeline().addBefore(
                        SocketIOChannelInitializer.WEB_SOCKET_TRANSPORT,
                        SocketIOChannelInitializer.WEB_SOCKET_AGGREGATOR,
                        WebSocketFrameAggregator(configuration.maxFramePayloadLength)
                    )
                    connectClient(channel, sessionId)
                } else {
                    log.error("Can't handshake $sessionId", future.cause())
                }
            }
        })
    }

    private fun connectClient(channel: Channel, sessionId: UUID) {
        val client = clientsBox[sessionId] ?: run {
            log.warn(
                "Unauthorized client with sessionId: {} with ip: {}. Channel closed!",
                sessionId, channel.remoteAddress()
            )
            channel.close()
            return
        }
        client.bindChannel(channel, Transport.WEBSOCKET)
        authorizeHandler.connect(client)

        if (client.getCurrentTransport() == Transport.POLLING) {
            scheduler.schedule(
                key = SchedulerKey(SchedulerKey.Type.UPGRADE_TIMEOUT, sessionId),
                delay = configuration.upgradeTimeout,
                runnable = {
                    val clientHead: ClientHead? = clientsBox[sessionId]
                    if (clientHead != null) {
                        if (log.isDebugEnabled) {
                            log.debug("client did not complete upgrade - closing transport")
                        }
                        clientHead.onChannelDisconnect()
                    }
                }
            )
        }
        log.debug("сlient {} handshake completed", sessionId)
    }

    private fun getWebSocketLocation(req: HttpRequest): String =
        "ws://" + req.headers()[HttpHeaderNames.HOST] + req.uri()

    private val log = LoggerFactory.getLogger(WebSocketTransport::class.java)
}