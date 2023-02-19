package com.gribouille.socketio.transport

import com.gribouille.socketio.Transport
import com.gribouille.socketio.handler.AuthorizeHandler
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.handler.ClientsBox
import com.gribouille.socketio.handler.EncoderHandler
import com.gribouille.socketio.messages.PacketsMessage
import com.gribouille.socketio.messages.XHROptionsMessage
import com.gribouille.socketio.messages.XHRPostMessage
import com.gribouille.socketio.protocol.PacketDecoder
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
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
            val req = msg as FullHttpRequest
            val queryDecoder = QueryStringDecoder(req.uri())
            val transport = queryDecoder.parameters().get("transport")
            if (transport != null && NAME == transport[0]) {
                val sid = queryDecoder.parameters().get("sid")
                val j = queryDecoder.parameters().get("j")
                val b64 = queryDecoder.parameters().get("b64")
                val origin: String = req.headers().get(HttpHeaderNames.ORIGIN)
                ctx.channel().attr(EncoderHandler.ORIGIN).set(origin)
                val userAgent: String = req.headers().get(HttpHeaderNames.USER_AGENT)
                ctx.channel().attr(EncoderHandler.USER_AGENT).set(userAgent)
                if (j != null && j[0] != null) {
                    val index = Integer.valueOf(j[0])
                    ctx.channel().attr(EncoderHandler.JSONP_INDEX).set(index)
                }
                if (b64 != null && b64[0] != null) {
                    var flag = b64[0]
                    if ("true" == flag) {
                        flag = "1"
                    } else if ("false" == flag) {
                        flag = "0"
                    }
                    val enable = Integer.valueOf(flag)
                    ctx.channel().attr(EncoderHandler.B64).set(enable == 1)
                }
                try {
                    if (sid != null && sid[0] != null) {
                        val sessionId = UUID.fromString(sid[0])
                        handleMessage(req, sessionId, queryDecoder, ctx)
                    } else {
                        // first connection
                        val client = ctx.channel().attr(ClientHead.CLIENT).get()
                        handleMessage(req, client.sessionId!!, queryDecoder, ctx)
                    }
                } finally {
                    req.release()
                }
                return
            }
        }
        ctx.fireChannelRead(msg)
    }

    @Throws(IOException::class)
    private fun handleMessage(
        req: FullHttpRequest,
        sessionId: UUID,
        queryDecoder: QueryStringDecoder,
        ctx: ChannelHandlerContext
    ) {
        val origin: String = req.headers().get(HttpHeaderNames.ORIGIN)
        if (queryDecoder.parameters().containsKey("disconnect")) {
            val client = clientsBox.get(sessionId)!!
            client.onChannelDisconnect()
            ctx.channel().writeAndFlush(XHRPostMessage(origin, sessionId))
        } else if (HttpMethod.POST == req.method()) {
            onPost(sessionId, ctx, origin, req.content())
        } else if (HttpMethod.GET == req.method()) {
            onGet(sessionId, ctx, origin)
        } else if (HttpMethod.OPTIONS == req.method()) {
            onOptions(sessionId, ctx, origin)
        } else {
            log.error("Wrong {} method invocation for {}", req.method(), sessionId)
            sendError(ctx)
        }
    }

    private fun onOptions(sessionId: UUID, ctx: ChannelHandlerContext, origin: String) {
        val client: ClientHead? = clientsBox.get(sessionId)
        if (client == null) {
            log.error("{} is not registered. Closing connection", sessionId)
            sendError(ctx)
            return
        }
        ctx.channel().writeAndFlush(XHROptionsMessage(origin, sessionId))
    }

    @Throws(IOException::class)
    private fun onPost(sessionId: UUID, ctx: ChannelHandlerContext, origin: String, content: ByteBuf) {
        var content: ByteBuf = content
        val client: ClientHead? = clientsBox.get(sessionId)
        if (client == null) {
            log.error("{} is not registered. Closing connection", sessionId)
            sendError(ctx)
            return
        }


        // release POST response before message processing
        ctx.channel().writeAndFlush(XHRPostMessage(origin, sessionId))
        val b64 = ctx.channel().attr(EncoderHandler.B64).get()
        if (b64 != null && b64) {
            val jsonIndex: Int = ctx.channel().attr(EncoderHandler.JSONP_INDEX).get()
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
        client.bindChannel(ctx.channel(), com.gribouille.socketio.Transport.POLLING)
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
        if (client != null && client.isTransportChannel(ctx.channel(), com.gribouille.socketio.Transport.POLLING)) {
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