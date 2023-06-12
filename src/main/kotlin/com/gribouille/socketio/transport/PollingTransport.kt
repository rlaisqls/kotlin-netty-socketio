package com.gribouille.socketio.transport

import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.handler.EncoderHandler
import com.gribouille.socketio.handler.authorizeHandler
import com.gribouille.socketio.handler.clientsBox
import com.gribouille.socketio.messages.OptionsMessage
import com.gribouille.socketio.messages.PacketsMessage
import com.gribouille.socketio.messages.PostMessage
import com.gribouille.socketio.protocol.packetDecoder
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.IOException
import java.util.*
import org.slf4j.LoggerFactory

interface PollingTransport: ChannelHandler {
    companion object {
        const val NAME = "polling"
    }
}

internal val pollingTransport: PollingTransport = @Sharable object :
    PollingTransport, ChannelInboundHandlerAdapter() {

    @Throws(Exception::class)
    override fun channelRead(
        ctx: ChannelHandlerContext,
        msg: Any
    ) {
        if (msg is FullHttpRequest) {
            val queryDecoder = QueryStringDecoder(msg.uri())
            if (queryDecoder.getParam("transport") == PollingTransport.NAME) {

                ctx.channel().attr(EncoderHandler.ORIGIN).set(
                    msg.headers().get(HttpHeaderNames.ORIGIN)
                )
                ctx.channel().attr(EncoderHandler.USER_AGENT).set(
                    msg.headers().get(HttpHeaderNames.USER_AGENT)
                )

                queryDecoder.getParam("j")?.let { j ->
                    val index = Integer.valueOf(j)
                    ctx.channel().attr(EncoderHandler.JSONP_INDEX).set(index)
                }

                queryDecoder.getParam("b64")?.let { b64 ->
                    val enable = (b64 == "1" || b64 == "true")
                    ctx.channel().attr(EncoderHandler.B64).set(enable)
                }

                try {
                    queryDecoder.getParam("sid")?.let { sid ->
                        val sessionId = UUID.fromString(sid)
                        handleMessage(msg, sessionId, queryDecoder, ctx)
                    } ?: run {
                        // first connection
                        val client = ctx.channel().attr(ClientHead.CLIENT).get()
                        handleMessage(msg, client.sessionId!!, queryDecoder, ctx)
                    }
                } finally {
                    msg.release()
                }
                return
            }
        }
        ctx.fireChannelRead(msg)
    }

    private fun QueryStringDecoder.getParam(pramName: String) =
        parameters()[pramName]?.get(0)

    @Throws(IOException::class)
    private fun handleMessage(
        req: FullHttpRequest,
        sessionId: UUID,
        queryDecoder: QueryStringDecoder,
        ctx: ChannelHandlerContext
    ) {
        val origin = req.headers().get(HttpHeaderNames.ORIGIN)

        if (queryDecoder.parameters().containsKey("disconnect")) {
            val client = clientsBox[sessionId]!!
            client.onChannelDisconnect()
            ctx.channel().writeAndFlush(PostMessage(origin, sessionId))
        } else {
            when (req.method()) {
                HttpMethod.POST -> {
                    onPost(sessionId, ctx, origin, req.content())
                }
                HttpMethod.GET -> {
                    onGet(sessionId, ctx, origin)
                }
                HttpMethod.OPTIONS -> {
                    onOptions(sessionId, ctx, origin)
                }
                else -> {
                    log.error("Wrong {} method invocation for {}", req.method(), sessionId)
                    sendError(ctx)
                }
            }
        }
    }

    private fun onOptions(sessionId: UUID, ctx: ChannelHandlerContext, origin: String) {
        val client = clientsBox[sessionId]
        if (client == null) {
            log.error("{} is not registered. Closing connection", sessionId)
            sendError(ctx)
            return
        }
        ctx.channel().writeAndFlush(OptionsMessage(origin, sessionId))
    }

    @Throws(IOException::class)
    private fun onPost(
        sessionId: UUID,
        ctx: ChannelHandlerContext,
        origin: String,
        content: ByteBuf
    ) {
        val client = clientsBox[sessionId] ?: run {
            log.error("{} is not registered. Closing connection", sessionId)
            sendError(ctx)
            return
        }

        // 메시지 처리 전 POST 응답 release
        ctx.channel().writeAndFlush(PostMessage(origin, sessionId))

        var packetContent = content
        val b64 = ctx.channel().attr(EncoderHandler.B64).get()
        if (b64 == true) {
            val jsonIndex = ctx.channel().attr(EncoderHandler.JSONP_INDEX).get()
            packetContent = packetDecoder.preprocessJson(jsonIndex, packetContent)
        }
        ctx.pipeline().fireChannelRead(
            PacketsMessage(
                client = client,
                content = packetContent,
                transport = Transport.POLLING
            )
        )
    }

    private fun onGet(sessionId: UUID?, ctx: ChannelHandlerContext, origin: String?) {
        val client = clientsBox[sessionId]
        if (client == null) {
            log.error("{} is not registered. Closing connection", sessionId)
            sendError(ctx)
            return
        }
        client.bindChannel(ctx.channel(), Transport.POLLING)
        authorizeHandler.connect(client)
    }

    private fun sendError(ctx: ChannelHandlerContext) {
        val res: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        ctx.channel().writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        val client = clientsBox[channel]
        if (client != null && client.isTransportChannel(ctx.channel(), Transport.POLLING)) {
            log.debug("channel inactive {}", client.sessionId)
            client.releasePollingChannel(channel)
        }
        super.channelInactive(ctx)
    }

    private val log = LoggerFactory.getLogger(PollingTransport::class.java)
}