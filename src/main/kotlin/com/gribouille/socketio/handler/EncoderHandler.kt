
package com.gribouille.socketio.handler

import com.gribouille.socketio.messages.HttpErrorMessage
import com.gribouille.socketio.Configuration
import com.gribouille.socketio.messages.HttpMessage
import com.gribouille.socketio.messages.OutPacketMessage
import com.gribouille.socketio.messages.OptionsMessage
import com.gribouille.socketio.messages.PostMessage
import com.gribouille.socketio.protocol.PacketEncoder
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.ByteBufUtil
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
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
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.util.AttributeKey
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.jar.Manifest

@Sharable
class EncoderHandler(
    val configuration: Configuration,
    val encoder: PacketEncoder
) : ChannelOutboundHandlerAdapter() {
    private var version: String? = null

    init {
        if (configuration.isAddVersionHeader) {
            readVersion()
        }
    }

    @Throws(IOException::class)
    private fun readVersion() {
        val resources: Enumeration<URL> = javaClass.classLoader.getResources("META-INF/MANIFEST.MF")
        while (resources.hasMoreElements()) {
            try {
                val manifest: Manifest = Manifest(resources.nextElement().openStream())
                val attrs = manifest.mainAttributes ?: continue
                val name = attrs.getValue("Bundle-Name")
                if (name != null && name == "netty-socketio") {
                    version = name + "/" + attrs.getValue("Bundle-Version")
                    break
                }
            } catch (_: IOException) { }
        }
    }

    private fun write(msg: OptionsMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val res: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        res.headers().add(HttpHeaderNames.SET_COOKIE, "io=" + msg.sessionId)
            .add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, HttpHeaderNames.CONTENT_TYPE)
        val origin: String = ctx.channel().attr<String>(ORIGIN).get()
        addOriginHeaders(origin, res)
        val out = encoder.allocateBuffer(ctx.alloc())
        sendMessage(msg, ctx.channel(), out, res, promise)
    }

    private fun write(msg: PostMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val out = encoder.allocateBuffer(ctx.alloc())
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
        val origin = channel.attr(ORIGIN).get()
        addOriginHeaders(origin, res)
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
        if (out.isReadable()) {
            channel.write(DefaultHttpContent(out))
        } else {
            out.release()
        }
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise).addListener(ChannelFutureListener.CLOSE)
    }

    @Throws(IOException::class)
    private fun sendError(errorMsg: HttpErrorMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val encBuf: ByteBuf = encoder.allocateBuffer(ctx.alloc())
        val out = ByteBufOutputStream(encBuf)
        encoder.jsonSupport.writeValue(out, errorMsg.data)
        sendMessage(errorMsg, ctx.channel(), encBuf, "application/json", promise, HttpResponseStatus.BAD_REQUEST)
    }

    private fun addOriginHeaders(origin: String?, res: HttpResponse) {
        if (version != null) {
            res.headers().add(HttpHeaderNames.SERVER, version)
        }
        if (configuration.origin != null) {
            res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, configuration.origin)
            res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, java.lang.Boolean.TRUE)
        } else {
            if (origin != null) {
                res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, java.lang.Boolean.TRUE)
            } else {
                res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            }
        }
        if (configuration.allowHeaders != null) {
            res.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, configuration.allowHeaders)
        }
    }

    @Throws(Exception::class)
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is HttpMessage) {
            super.write(ctx, msg, promise)
            return
        }
        if (msg is OutPacketMessage) {
            if (msg.transport == com.gribouille.socketio.Transport.WEBSOCKET) {
                handleWebsocket(msg, ctx, promise)
            }
            if (msg.transport == com.gribouille.socketio.Transport.POLLING) {
                handleHTTP(msg, ctx, promise)
            }
        } else if (msg is OptionsMessage) {
            write(msg, ctx, promise)
        } else if (msg is PostMessage) {
            write(msg, ctx, promise)
        } else if (msg is HttpErrorMessage) {
            sendError(msg, ctx, promise)
        }
    }

    @Throws(IOException::class)
    private fun handleWebsocket(msg: OutPacketMessage, ctx: ChannelHandlerContext, promise: ChannelPromise) {
        val writeFutureList = ChannelFutureList()
        while (true) {
            val queue = msg.clientHead.getPacketsQueue(msg.transport)!!
            val packet = queue.poll()
            if (packet == null) {
                writeFutureList.setChannelPromise(promise)
                break
            }
            val out: ByteBuf = encoder.allocateBuffer(ctx.alloc())
            encoder.encodePacket(packet, out, ctx.alloc(), true)
            if (log.isTraceEnabled) {
                log.trace("Out message: {} sessionId: {}", out.toString(CharsetUtil.UTF_8), msg.sessionId)
            }
            if (out.isReadable() && out.readableBytes() > configuration.maxFramePayloadLength) {
                val dstStart: ByteBuf = ByteBufUtil.readBytes(ctx.alloc(), out, FRAME_BUFFER_SIZE)
                val start: WebSocketFrame = TextWebSocketFrame(false, 0, dstStart)
                ctx.channel().write(start)
                while (out.isReadable()) {
                    val re = if (out.readableBytes() > FRAME_BUFFER_SIZE) FRAME_BUFFER_SIZE else out.readableBytes()
                    val dst: ByteBuf = ByteBufUtil.readBytes(ctx.alloc(), out, re)
                    val res: WebSocketFrame = ContinuationWebSocketFrame(if (out.isReadable()) false else true, 0, dst)
                    ctx.channel().write(res)
                }
                ctx.channel().flush()
            } else if (out.isReadable()) {
                val res: WebSocketFrame = TextWebSocketFrame(out)
                ctx.channel().writeAndFlush(res)
            } else {
                out.release()
            }
            for (buf in packet.attachments) {
                val outBuf: ByteBuf = encoder.allocateBuffer(ctx.alloc())
                outBuf.writeByte(4)
                outBuf.writeBytes(buf)
                if (log.isTraceEnabled) {
                    log.trace("Out attachment: {} sessionId: {}", ByteBufUtil.hexDump(outBuf), msg.sessionId)
                }
                writeFutureList.add(ctx.channel().writeAndFlush(BinaryWebSocketFrame(outBuf)))
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
        val out = encoder.allocateBuffer(ctx.alloc())
        val b64 = ctx.channel().attr(B64).get()
        if (b64 != null && b64) {
            val jsonpIndex = ctx.channel().attr(JSONP_INDEX).get()
            encoder.encodeJsonP(jsonpIndex, queue!!, out, ctx.alloc(), 50)
            var type = "application/javascript"
            if (jsonpIndex == null) {
                type = "text/plain"
            }
            sendMessage(msg, channel, out, type, promise, HttpResponseStatus.OK)
        } else {
            encoder.encodePackets(queue!!, out, ctx.alloc(), 50)
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

    companion object {
        private val OK = "ok".toByteArray(CharsetUtil.UTF_8)
        val ORIGIN = AttributeKey.valueOf<String>("origin")
        val USER_AGENT = AttributeKey.valueOf<String>("userAgent")
        val B64 = AttributeKey.valueOf<Boolean>("b64")
        val JSONP_INDEX = AttributeKey.valueOf<Int>("jsonpIndex")
        val WRITE_ONCE = AttributeKey.valueOf<Boolean>("writeOnce")
        private val log = LoggerFactory.getLogger(EncoderHandler::class.java)
        private const val FRAME_BUFFER_SIZE = 8192
    }
}