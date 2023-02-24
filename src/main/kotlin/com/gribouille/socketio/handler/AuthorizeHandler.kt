package com.gribouille.socketio.handler

import com.gribouille.socketio.HandshakeData
import com.gribouille.socketio.Configuration
import com.gribouille.socketio.Disconnectable
import com.gribouille.socketio.DisconnectableHub
import com.gribouille.socketio.Transport
import com.gribouille.socketio.ack.AckManager
import com.gribouille.socketio.messages.HttpErrorMessage
import com.gribouille.socketio.namespace.Namespace
import com.gribouille.socketio.namespace.NamespacesHub
import com.gribouille.socketio.protocol.AuthPacket
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.scheduler.CancelableScheduler
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.SchedulerKey.Type
import com.gribouille.socketio.store.StoreFactory
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.cookie.Cookie
import io.netty.handler.codec.http.cookie.ServerCookieDecoder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.TimeUnit

@Sharable
class AuthorizeHandler(
    private val connectPath: String,
    scheduler: CancelableScheduler,
    private val configuration: Configuration,
    namespacesHub: NamespacesHub,
    storeFactory: StoreFactory,
    disconnectable: DisconnectableHub,
    ackManager: AckManager,
    clientsBox: ClientsBox,
) : ChannelInboundHandlerAdapter(), Disconnectable {
    private val scheduler: CancelableScheduler
    private val namespacesHub: NamespacesHub
    private val storeFactory: StoreFactory
    private val disconnectable: DisconnectableHub
    private val ackManager: AckManager
    private val clientsBox: ClientsBox

    init {
        this.scheduler = scheduler
        this.namespacesHub = namespacesHub
        this.storeFactory = storeFactory
        this.disconnectable = disconnectable
        this.ackManager = ackManager
        this.clientsBox = clientsBox
    }

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        val key = SchedulerKey(Type.PING_TIMEOUT, ctx.channel())
        scheduler.schedule(
            key = key,
            delay = configuration.firstDataTimeout,
            unit = TimeUnit.MILLISECONDS,
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
        scheduler.cancel(key = SchedulerKey(Type.PING_TIMEOUT, ctx.channel()))

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
            req.headers(), params,
            channel.remoteAddress() as InetSocketAddress,
            channel.localAddress() as InetSocketAddress,
            req.uri(), origin != null && !origin.equals("null", ignoreCase = true)
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
            ackManager = ackManager,
            disconnectableHub = disconnectable,
            storeFactory = storeFactory,
            handshakeData = data,
            clientsBox = clientsBox,
            currentTransport = transport,
            scheduler = scheduler,
            configuration = configuration
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
        channel.attr<String?>(EncoderHandler.Companion.ORIGIN).set(origin)
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
            } catch (iaex: IllegalArgumentException) {
                log.warn("Malformed UUID received for session! io=" + values[0])
            }
        }
        for (cookieHeader in headers.getAll(HttpHeaderNames.COOKIE)) {
            val cookies: Set<Cookie> = ServerCookieDecoder.LAX.decode(cookieHeader)
            for (cookie in cookies) {
                if (cookie.name() == "io") {
                    try {
                        return UUID.fromString(cookie.value())
                    } catch (iaex: IllegalArgumentException) {
                        log.warn("Malformed UUID received for session! io=" + cookie.value())
                    }
                }
            }
        }
        return UUID.randomUUID()
    }

    fun connect(sessionId: UUID?) {
        val key = SchedulerKey(Type.PING_TIMEOUT, sessionId)
        scheduler.cancel(key)
    }

    fun connect(client: ClientHead) {
        val ns: Namespace = namespacesHub.get(Namespace.DEFAULT_NAME)
        if (!client.namespaces.contains(ns)) {
            val packet = Packet(PacketType.MESSAGE)
            packet.subType = PacketType.CONNECT
            client.send(packet)
            val nsClient = client.addNamespaceClient(ns)
            ns.onConnect(nsClient)
        }
    }

    override fun onDisconnect(client: ClientHead) {
        clientsBox.removeClient(client.sessionId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthorizeHandler::class.java)
    }
}