package com.gribouille.socketio.handler

import com.gribouille.socketio.Disconnectable
import com.gribouille.socketio.HandshakeData
import com.gribouille.socketio.Transport
import com.gribouille.socketio.ack.AuthPacket
import com.gribouille.socketio.configuration
import com.gribouille.socketio.messages.HttpErrorMessage
import com.gribouille.socketio.namespace.Namespace
import com.gribouille.socketio.namespace.namespacesHub
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.SchedulerKey.Type
import com.gribouille.socketio.scheduler.scheduler
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.cookie.ServerCookieDecoder
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Locale
import java.util.UUID
import org.slf4j.LoggerFactory

interface AuthorizeHandler: Disconnectable, ChannelHandler {
    fun connect(sessionId: UUID?)
    fun connect(client: ClientHead)
}

internal val authorizeHandler: AuthorizeHandler = @Sharable object :
    AuthorizeHandler, ChannelInboundHandlerAdapter() {

    private val connectPath: String = "${configuration.context}/"

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        scheduler.schedule(
            key = SchedulerKey(Type.PING_TIMEOUT, ctx.channel()),
            delay = configuration.firstDataTimeout,
            runnable = {
                ctx.channel().close()
                log.debug(
                    "Client with ip {} opened channel but doesn't send any data! Channel closed!",
                    ctx.channel().remoteAddress()
                )
            }
        )
        super.channelActive(ctx)
    }

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        scheduler.cancel(Type.PING_TIMEOUT, ctx.channel())

        if (msg is FullHttpRequest) {
            val channel = ctx.channel()
            val queryDecoder = QueryStringDecoder(msg.uri())

            if (!configuration.isAllowCustomRequests && !queryDecoder.path().startsWith(connectPath)) {
                channel.writeAndFlush(
                    DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
                ).addListener(ChannelFutureListener.CLOSE)
                msg.release()
                return
            }

            val sid = queryDecoder.parameters()["sid"]
            if (queryDecoder.path() == connectPath && sid == null) {
                val origin = msg.headers().get(HttpHeaderNames.ORIGIN)
                if (!authorize(ctx, channel, origin, queryDecoder.parameters(), msg)) {
                    msg.release()
                    return
                }
                // 메시지를 polling 혹은 websocket 핸들러에 전달하여 채널에 바인딩
            }
        }
        ctx.fireChannelRead(msg)
    }

    @Throws(IOException::class)
    private fun authorize(
        ctx: ChannelHandlerContext,
        channel: Channel,
        origin: String?,
        params: Map<String, List<String>>,
        req: FullHttpRequest,
    ): Boolean {
        val headers = HashMap<String, List<String>>(req.headers().names().size)
        for (name in req.headers().names()) {
            val values: List<String> = req.headers().getAll(name)
            headers[name] = values
        }
        val data = HandshakeData(
            httpHeaders = req.headers(), urlParams = params,
            address = channel.remoteAddress() as InetSocketAddress,
            local = channel.localAddress() as InetSocketAddress,
            url = req.uri(),
            xdomain = origin != null && !origin.equals("null", ignoreCase = true)
        )

        if (!configuration.authorizationListener.isAuthorized(data)) {
            val res = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED)
            channel.writeAndFlush(res)
                .addListener(ChannelFutureListener.CLOSE)
            log.debug("Handshake unauthorized, query params: {} headers: {}", params, headers)
            return false
        }

        val sessionId = if (configuration.isRandomSession) {
            UUID.randomUUID()
        } else generateOrGetSessionIdFromRequest(req.headers())

        val transportValue = params["transport"] ?: run {
            log.error("Got no transports for request {}", req.uri())
            writeAndFlushTransportError(channel, origin)
            return false
        }

        val transport = try {
            Transport.valueOf(transportValue[0].uppercase(Locale.getDefault()))
        } catch (e: IllegalArgumentException) {
            log.error("Unknown transport for request {}", req.uri())
            writeAndFlushTransportError(channel, origin)
            return false
        }

        if (!configuration.transports.contains(transport)) {
            log.error("Unsupported transport for request {}", req.uri())
            writeAndFlushTransportError(channel, origin)
            return false
        }

        val client = ClientHead(
            sessionId = sessionId,
            handshakeData = data,
            currentTransport = transport
        )

        channel.attr(ClientHead.CLIENT).set(client)
        clientsBox.addClient(client)

        val transports = if (configuration.transports.contains(Transport.WEBSOCKET)) {
            arrayOf("websocket")
        } else arrayOf()

        with(client) {
            val authPacket = AuthPacket(
                sid = sessionId!!,
                upgrades = transports,
                pingInterval = configuration.pingInterval,
                pingTimeout = configuration.pingTimeout
            )
            send(
                packet = Packet(PacketType.OPEN)
                    .apply { this.data = authPacket }
            )
            schedulePing()
            schedulePingTimeout()
        }
        log.debug("Handshake authorized for sessionId: {}, query params: {} headers: {}", sessionId, params, headers)
        return true
    }

    private fun writeAndFlushTransportError(channel: Channel, origin: String?) {
        val errorData: MutableMap<String, Any> = HashMap()
        errorData["code"] = 0
        errorData["message"] = "Transport unknown"
        channel.attr(EncoderHandler.ORIGIN).set(origin)
        channel.writeAndFlush(HttpErrorMessage(errorData))
    }

    /**
     * This method will either generate a new random sessionId or will retrieve the value stored
     * in the "io" cookie.  Failures to parse will cause a logging warning to be generated and a
     * random uuid to be generated instead (same as not passing a cookie in the first place).
     */
    private fun generateOrGetSessionIdFromRequest(headers: HttpHeaders): UUID {
        val values = headers.getAll("io")
        if (values.size == 1) {
            try {
                return UUID.fromString(values[0])
            } catch (e: IllegalArgumentException) {
                log.warn("Malformed UUID received for session! io=" + values[0])
            }
        }
        for (cookieHeader in headers.getAll(HttpHeaderNames.COOKIE)) {
            for (cookie in ServerCookieDecoder.LAX.decode(cookieHeader)) {
                if (cookie.name() == "io") {
                    try {
                        return UUID.fromString(cookie.value())
                    } catch (e: IllegalArgumentException) {
                        log.warn("Malformed UUID received for session! io=" + cookie.value())
                    }
                }
            }
        }
        return UUID.randomUUID()
    }

    override fun connect(sessionId: UUID?) {
        val key = SchedulerKey(Type.PING_TIMEOUT, sessionId)
        scheduler.cancel(key)
    }

    override fun connect(client: ClientHead) {
        val ns = namespacesHub[Namespace.DEFAULT_NAME]!!
        if (!client.namespaces.contains(ns)) {
            val packet = Packet(PacketType.MESSAGE).apply {
                subType = PacketType.CONNECT
            }
            client.send(packet)
            val nsClient = client.addNamespaceClient(ns)
            ns.onConnect(nsClient)
        }
    }

    override fun onDisconnect(client: ClientHead) {
        clientsBox.removeClient(client.sessionId)
    }

    private val log = LoggerFactory.getLogger(AuthorizeHandler::class.java)
}