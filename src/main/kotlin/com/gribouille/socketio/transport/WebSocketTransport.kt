
package com.gribouille.socketio.transport

import com.gribouille.socketio.Configuration
import com.gribouille.socketio.SocketIOChannelInitializer
import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.AuthorizeHandler
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.handler.ClientsBox
import com.gribouille.socketio.messages.PacketsMessage
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.scheduler.CancelableScheduler
import com.gribouille.socketio.scheduler.SchedulerKey
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
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

@Sharable
class WebSocketTransport(
    val authorizeHandler: AuthorizeHandler,
    val configuration: Configuration,
    val scheduler: CancelableScheduler,
    val clientsBox: ClientsBox
) : ChannelInboundHandlerAdapter() {

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is CloseWebSocketFrame) {
            ctx.channel().writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE)
        } else if (msg is BinaryWebSocketFrame
            || msg is TextWebSocketFrame
        ) {
            val frame: ByteBufHolder = msg as ByteBufHolder
            val client: ClientHead? = clientsBox[ctx.channel()]
            if (client == null) {
                log.debug("Client with was already disconnected. Channel closed!")
                ctx.channel().close()
                frame.release()
                return
            }
            ctx.pipeline().fireChannelRead(
                PacketsMessage(
                    client,
                    frame.content(),
                    Transport.WEBSOCKET
                )
            )
            frame.release()
        } else if (msg is FullHttpRequest) {
            val req: FullHttpRequest = msg as FullHttpRequest
            val queryDecoder = QueryStringDecoder(req.uri())
            val path: String = queryDecoder.path()
            val transport = queryDecoder.parameters().get("transport")
            val sid = queryDecoder.parameters().get("sid")
            if (transport != null && NAME == transport[0]) {
                try {
                    if (!configuration.transports.contains(Transport.WEBSOCKET)) {
                        log.debug(
                            "{} transport not supported by configuration.",
                            Transport.WEBSOCKET
                        )
                        ctx.channel().close()
                        return
                    }
                    if (sid != null && sid[0] != null) {
                        val sessionId = UUID.fromString(sid[0])
                        handshake(ctx, sessionId, path, req)
                    } else {
                        val client: ClientHead = ctx.channel().attr(ClientHead.CLIENT).get()
                        // first connection
                        handshake(ctx, client.sessionId!!, path, req)
                    }
                } finally {
                    req.release()
                }
            } else {
                ctx.fireChannelRead(msg)
            }
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    @Throws(Exception::class)
    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        val client: ClientHead? = clientsBox.get(ctx.channel())
        if (client != null && client.isTransportChannel(
                ctx.channel(),
                com.gribouille.socketio.Transport.WEBSOCKET
            )
        ) {
            ctx.flush()
        } else {
            super.channelReadComplete(ctx)
        }
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel: Channel = ctx.channel()
        val client = clientsBox[channel]
        val packet = Packet(PacketType.MESSAGE)
        packet.subType = PacketType.DISCONNECT
        if (client != null && client.isTransportChannel(
                ctx.channel(), Transport.WEBSOCKET
            )
        ) {
            log.debug("channel inactive {}", client.sessionId)
            client.onChannelDisconnect()
        }
        super.channelInactive(ctx)
        if (client != null) {
            client.send(packet)
        }
        channel.close()
        ctx.close()
    }

    private fun handshake(ctx: ChannelHandlerContext, sessionId: UUID, path: String, req: FullHttpRequest) {
        val channel: Channel = ctx.channel()
        val factory = WebSocketServerHandshakerFactory(
            getWebSocketLocation(req),
            null,
            true,
            configuration.maxFramePayloadLength
        )
        val handshaker: WebSocketServerHandshaker = factory.newHandshaker(req)
        if (handshaker != null) {
            val f = handshaker.handshake(channel, req)
            f.addListener(object : ChannelFutureListener {
                @Throws(Exception::class)
                override fun operationComplete(future: ChannelFuture) {
                    if (!future.isSuccess()) {
                        log.error("Can't handshake $sessionId", future.cause())
                        return
                    }
                    channel.pipeline().addBefore(
                        SocketIOChannelInitializer.WEB_SOCKET_TRANSPORT,
                        SocketIOChannelInitializer.WEB_SOCKET_AGGREGATOR,
                        WebSocketFrameAggregator(configuration.maxFramePayloadLength)
                    )
                    connectClient(channel, sessionId)
                }
            })
        } else {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
        }
    }

    private fun connectClient(channel: Channel, sessionId: UUID) {
        val client = clientsBox.get(sessionId)
        if (client == null) {
            log.warn(
                "Unauthorized client with sessionId: {} with ip: {}. Channel closed!",
                sessionId, channel.remoteAddress()
            )
            channel.close()
            return
        }
        client.bindChannel(channel, Transport.WEBSOCKET)
        authorizeHandler.connect(client)
        if (client.getCurrentTransport() === Transport.POLLING) {
            val key = SchedulerKey(SchedulerKey.Type.UPGRADE_TIMEOUT, sessionId)
            scheduler.schedule(
                key = key,
                delay = configuration.upgradeTimeout,
                unit = TimeUnit.MILLISECONDS,
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

    companion object {
        const val NAME = "websocket"
        private val log = LoggerFactory.getLogger(WebSocketTransport::class.java)
    }
}