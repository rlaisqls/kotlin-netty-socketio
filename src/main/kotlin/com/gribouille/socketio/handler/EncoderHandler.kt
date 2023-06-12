
package com.gribouille.socketio.handler

import com.gribouille.socketio.Transport
import com.gribouille.socketio.configuration
import com.gribouille.socketio.handler.EncoderHandler.Companion.B64
import com.gribouille.socketio.handler.EncoderHandler.Companion.JSONP_INDEX
import com.gribouille.socketio.handler.EncoderHandler.Companion.ORIGIN
import com.gribouille.socketio.handler.EncoderHandler.Companion.USER_AGENT
import com.gribouille.socketio.handler.EncoderHandler.Companion.WRITE_ONCE
import com.gribouille.socketio.jsonSupport
import com.gribouille.socketio.messages.HttpErrorMessage
import com.gribouille.socketio.messages.HttpMessage
import com.gribouille.socketio.messages.OptionsMessage
import com.gribouille.socketio.messages.OutPacketMessage
import com.gribouille.socketio.messages.PostMessage
import com.gribouille.socketio.protocol.packetEncoder
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.ByteBufUtil
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.util.AttributeKey
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import java.io.IOException
import java.util.*
import org.slf4j.LoggerFactory


interface EncoderHandler: ChannelHandler {
    companion object {
        val ORIGIN: AttributeKey<String> = AttributeKey.valueOf("origin")
        val USER_AGENT: AttributeKey<String> = AttributeKey.valueOf("userAgent")
        val B64: AttributeKey<Boolean> = AttributeKey.valueOf("b64")
        val JSONP_INDEX: AttributeKey<Int> = AttributeKey.valueOf("jsonpIndex")
        val WRITE_ONCE: AttributeKey<Boolean> = AttributeKey.valueOf("writeOnce")
    }
}

internal val encoderHandler: EncoderHandler = @Sharable object :
    EncoderHandler, ChannelOutboundHandlerAdapter() {

    private var version: String? = null

    private fun write(msg: OptionsMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val res: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        res.headers().add(HttpHeaderNames.SET_COOKIE, "io=" + msg.sessionId)
            .add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, HttpHeaderNames.CONTENT_TYPE)
        addOriginHeaders(ctx.channel(), res)
        val out = packetEncoder.allocateBuffer(ctx.alloc())
        sendMessage(msg, ctx.channel(), out, res, promise)
    }

    private fun write(msg: PostMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val out = packetEncoder.allocateBuffer(ctx.alloc())
        out.writeBytes(OK)
        sendMessage(msg, ctx.channel(), out, "text/html", promise, HttpResponseStatus.OK)
    }

    private fun sendMessage(
        msg: HttpMessage,
        channel: Channel,
        out: ByteBuf,
        type: String,
        promise: ChannelPromise,
        status: HttpResponseStatus
    ) {
        val res: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
        res.headers().add(HttpHeaderNames.CONTENT_TYPE, type)
            .add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        if (msg.sessionId != null) {
            res.headers().add(HttpHeaderNames.SET_COOKIE, "io=" + msg.sessionId)
        }
        addOriginHeaders(channel, res)
        HttpUtil.setContentLength(res, out.readableBytes().toLong())

        // prevent XSS warnings on IE
        // https://github.com/LearnBoost/socket.io/pull/1333
        val userAgent = channel.attr(USER_AGENT).get()
        if (userAgent != null && (userAgent.contains(";MSIE") || userAgent.contains("Trident/"))) {
            res.headers().add("X-XSS-Protection", "0")
        }
        sendMessage(msg, channel, out, res, promise)
    }

    private fun sendMessage(
        msg: HttpMessage,
        channel: Channel,
        out: ByteBuf,
        res: HttpResponse,
        promise: ChannelPromise
    ) {
        channel.write(res)
        if (log.isTraceEnabled) {
            if (msg.sessionId != null) {
                log.trace("Out message: {} - sessionId: {}", out.toString(CharsetUtil.UTF_8), msg.sessionId)
            } else {
                log.trace("Out message: {}", out.toString(CharsetUtil.UTF_8))
            }
        }
        if (out.isReadable) {
            channel.write(DefaultHttpContent(out))
        } else {
            out.release()
        }
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise).addListener(ChannelFutureListener.CLOSE)
    }

    @Throws(IOException::class)
    private fun sendError(errorMsg: HttpErrorMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val encBuf: ByteBuf = packetEncoder.allocateBuffer(ctx.alloc())
        val out = ByteBufOutputStream(encBuf)
        jsonSupport.writeValue(out, errorMsg.data)
        sendMessage(errorMsg, ctx.channel(), encBuf, "application/json", promise, HttpResponseStatus.BAD_REQUEST)
    }

    private fun addOriginHeaders(channel: Channel, res: HttpResponse) {
        if (version != null) {
            res.headers().add(HttpHeaderNames.SERVER, version)
        }
        if (configuration.origin != null) {
            res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, configuration.origin)
            res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, java.lang.Boolean.TRUE)
        } else {
            channel.attr(ORIGIN).get()?.let {
                res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, channel)
                res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, java.lang.Boolean.TRUE)
            } ?: res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        }
        if (configuration.allowHeaders != null) {
            res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, configuration.allowHeaders)
        }
    }

    @Throws(Exception::class)
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        when (msg) {
            !is HttpMessage -> {
                super.write(ctx, msg, promise)
                return
            }
            is OutPacketMessage -> {
                if (msg.transport == Transport.WEBSOCKET) {
                    handleWebsocket(msg, ctx, promise)
                }
                if (msg.transport == Transport.POLLING) {
                    handleHTTP(msg, ctx, promise)
                }
            }
            is OptionsMessage -> write(msg, ctx, promise)
            is PostMessage -> write(msg, ctx, promise)
            is HttpErrorMessage -> sendError(msg, ctx, promise)
        }
    }

    @Throws(IOException::class)
    private fun handleWebsocket(msg: OutPacketMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val writeFutureList = ChannelFutureList()

        outer@ while (true) {
            val packet = msg.clientHead.getPacketsQueue(msg.transport)!!.poll()
            if (packet == null) {
                writeFutureList.setChannelPromise(promise)
                break
            }

            val out = packetEncoder.allocateBuffer(ctx.alloc())
            packetEncoder.encodePacket(packet, out, ctx.alloc(), true)

            if (log.isTraceEnabled) {
                log.trace("Out message: {} sessionId: {}", out.toString(CharsetUtil.UTF_8), msg.sessionId)
            }

            if (out.isReadable && out.readableBytes() > configuration.maxFramePayloadLength) {

                val dstStart = ByteBufUtil.readBytes(ctx.alloc(), out, FRAME_BUFFER_SIZE)
                ctx.channel().write(
                    TextWebSocketFrame(false, 0, dstStart)
                )

                while (out.isReadable) {
                    val re = if (out.readableBytes() > FRAME_BUFFER_SIZE) {
                        FRAME_BUFFER_SIZE
                    } else out.readableBytes()
                    val dst = ByteBufUtil.readBytes(ctx.alloc(), out, re)
                    ctx.channel().write(
                        ContinuationWebSocketFrame(!out.isReadable, 0, dst)
                    )
                }
                ctx.channel().flush()
            } else if (out.isReadable) {
                ctx.channel().writeAndFlush(
                    TextWebSocketFrame(out)
                )
            } else {
                out.release()
            }
            for (buf in packet.attachments) {
                val outBuf = packetEncoder.allocateBuffer(ctx.alloc()).apply {
                    writeByte(4)
                    writeBytes(buf)
                }
                if (log.isTraceEnabled) {
                    log.trace("Out attachment: {} sessionId: {}", ByteBufUtil.hexDump(outBuf), msg.sessionId)
                }
                writeFutureList.add(
                    ctx.channel().writeAndFlush(BinaryWebSocketFrame(outBuf))
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun handleHTTP(msg: OutPacketMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val channel: Channel = ctx.channel()
        val attr = channel.attr(WRITE_ONCE)
        val queue = msg.clientHead.getPacketsQueue(msg.transport)
        if (!channel.isActive || queue?.isEmpty() == true || !attr.compareAndSet(null, true)) {
            promise.trySuccess()
            return
        }
        val out = packetEncoder.allocateBuffer(ctx.alloc())
        val b64 = ctx.channel().attr(B64).get()
        if (b64 != null && b64) {
            val jsonpIndex = ctx.channel().attr(JSONP_INDEX).get()
            packetEncoder.encodeJsonP(jsonpIndex, queue!!, out, ctx.alloc(), 50)
            var type = "application/javascript"
            if (jsonpIndex == null) {
                type = "text/plain"
            }
            sendMessage(msg, channel, out, type, promise, HttpResponseStatus.OK)
        } else {
            packetEncoder.encodePackets(queue!!, out, ctx.alloc(), 50)
            sendMessage(msg, channel, out, "application/octet-stream", promise, HttpResponseStatus.OK)
        }
    }

    /**
     * Helper class for the handleWebsocket method, handles a list of ChannelFutures and
     * sets the status of a promise when
     * - any of the operations fail
     * - all of the operations succeed
     * The setChannelPromise method should be called after all the futures are added
     */
    private inner class ChannelFutureList : GenericFutureListener<Future<Void>> {
        private val futureList: MutableList<ChannelFuture> = ArrayList<ChannelFuture>()
        private var promise: ChannelPromise? = null
        private fun cleanup() {
            promise = null
            for (f in futureList) f.removeListener(this)
        }

        private fun validate() {
            var allSuccess = true
            for (f in futureList) {
                if (f.isDone) {
                    if (!f.isSuccess) {
                        promise!!.tryFailure(f.cause())
                        cleanup()
                        return
                    }
                } else {
                    allSuccess = false
                }
            }
            if (allSuccess) {
                promise!!.trySuccess()
                cleanup()
            }
        }

        fun add(f: ChannelFuture) {
            futureList.add(f)
            f.addListener(this)
        }

        fun setChannelPromise(p: ChannelPromise?) {
            promise = p
            validate()
        }

        @Throws(Exception::class)
        override fun operationComplete(voidFuture: Future<Void>) {
            if (promise != null) validate()
        }
    }

    private val log = LoggerFactory.getLogger(EncoderHandler::class.java)
    private val FRAME_BUFFER_SIZE = 8192
    private val OK = "ok".toByteArray(CharsetUtil.UTF_8)
}