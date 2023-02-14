
package com.gribouille.socketio.handler

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.slf4j.LoggerFactory

@Sharable
class WrongUrlHandler : ChannelInboundHandlerAdapter() {
    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is FullHttpRequest) {
            val req = msg
            val channel = ctx.channel()
            val queryDecoder = QueryStringDecoder(req.uri())
            val res: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
            val f = channel.writeAndFlush(res)
            f.addListener(ChannelFutureListener.CLOSE)
            req.release()
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

    companion object {
        private val log = LoggerFactory.getLogger(WrongUrlHandler::class.java)
    }
}