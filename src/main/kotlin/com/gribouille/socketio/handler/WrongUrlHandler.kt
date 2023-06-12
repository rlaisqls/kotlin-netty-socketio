
package com.gribouille.socketio.handler

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.slf4j.LoggerFactory

interface WrongUrlHandler: ChannelHandler

internal val wrongUrlHandler: WrongUrlHandler = @Sharable object :
    WrongUrlHandler, ChannelInboundHandlerAdapter() {

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {

        if (msg is FullHttpRequest) {
            val channel = ctx.channel()
            val queryDecoder = QueryStringDecoder(msg.uri())

            channel.writeAndFlush(
                DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
            ).apply {
                addListener(ChannelFutureListener.CLOSE)
            }

            msg.release()
            log.warn(
                "Blocked wrong socket.io-context request! url: {}, params: {}, ip: {}",
                queryDecoder.path(),
                queryDecoder.parameters(),
                channel.remoteAddress()
            )
            return
        }
        super.channelRead(ctx, msg)
    }

    private val log = LoggerFactory.getLogger(WrongUrlHandler::class.java)
}