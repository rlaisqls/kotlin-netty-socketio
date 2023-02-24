package com.gribouille.socketio.transport

import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.AuthorizeHandler
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.handler.ClientsBox
import com.gribouille.socketio.handler.EncoderHandler
import com.gribouille.socketio.messages.PacketsMessage
import com.gribouille.socketio.messages.OptionsMessage
import com.gribouille.socketio.messages.PostMessage
import com.gribouille.socketio.protocol.PacketDecoder
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
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
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

@Sharable
class PollingTransport(
    private val decoder: PacketDecoder,
    private val authorizeHandler: AuthorizeHandler,
    private val clientsBox: ClientsBox
) : ChannelInboundHandlerAdapter() {

    @Throws(Exception::class)
    override fun channelRead(
        ctx: ChannelHandlerContext,
        msg: Any
    ) {
        if (msg is FullHttpRequest) {
            val queryDecoder = QueryStringDecoder(msg.uri())
            if (getParam("transport", queryDecoder) == NAME) {

                ctx.channel().attr(EncoderHandler.ORIGIN).set(
                    msg.headers().get(HttpHeaderNames.ORIGIN)
                )
                ctx.channel().attr(EncoderHandler.USER_AGENT).set(
                    msg.headers().get(HttpHeaderNames.USER_AGENT)
                )

                getParam("j", queryDecoder)?.let { j ->
                    val index = Integer.valueOf(j)
                    ctx.channel().attr(EncoderHandler.JSONP_INDEX).set(index)
                }

                getParam("b64", queryDecoder)?.let { b64 ->
                    val enable = (b64 == "1" || b64 == "true")
                    ctx.channel().attr(EncoderHandler.B64).set(enable)
                }

                try {
                    getParam("sid", queryDecoder)?.let { sid ->
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

    private fun getParam(pramName: String, queryDecoder: QueryStringDecoder) =
        queryDecoder.parameters()[pramName]?.get(0)

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
        val client: ClientHead? = clientsBox.get(sessionId)
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
        var content: ByteBuf = content
        val client = clientsBox[sessionId] ?: run {
            log.error("{} is not registered. Closing connection", sessionId)
            sendError(ctx)
            return
        }

        // 메시지 처리 전 POST 응답 release
        ctx.channel().writeAndFlush(PostMessage(origin, sessionId))
        val b64 = ctx.channel().attr(EncoderHandler.B64).get()
        if (b64 == true) {
            val jsonIndex = ctx.channel().attr(EncoderHandler.JSONP_INDEX).get()
            content = decoder.preprocessJson(jsonIndex, content)
        }
        ctx.pipeline().fireChannelRead(
            PacketsMessage(
                client = client,
                content = content,
                transport = Transport.POLLING
            )
        )
    }

    protected fun onGet(sessionId: UUID?, ctx: ChannelHandlerContext, origin: String?) {
        val client: ClientHead? = clientsBox.get(sessionId)
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
        val channel: Channel = ctx.channel()
        val client: ClientHead? = clientsBox.get(channel)
        if (client != null && client.isTransportChannel(ctx.channel(), Transport.POLLING)) {
            log.debug("channel inactive {}", client.sessionId)
            client.releasePollingChannel(channel)
        }
        super.channelInactive(ctx)
    }

    companion object {
        const val NAME = "polling"
        private val log = LoggerFactory.getLogger(PollingTransport::class.java)
    }
}