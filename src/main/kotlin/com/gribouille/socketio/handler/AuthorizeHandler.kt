
package com.gribouille.socketio.handler

import com.gribouille.socketio.HandshakeData
import com.gribouille.socketio.Configuration
import io.netty.channel.Channel
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.cookie.Cookie
import org.slf4j.LoggerFactory
import java.util.*

@Sharable
class AuthorizeHandler(
    private val connectPath: String,
    scheduler: CancelableScheduler,
    private val configuration: com.gribouille.socketio.Configuration,
    namespacesHub: NamespacesHub,
    storeFactory: StoreFactory,
    disconnectable: DisconnectableHub,
    ackManager: AckManager,
    clientsBox: ClientsBox
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
        scheduler.schedule(key, Runnable {
            ctx.channel().close()
            log.debug(
                "Client with ip {} opened channel but doesn't send any data! Channel closed!",
                ctx.channel().remoteAddress()
            )
        }, configuration.firstDataTimeout, TimeUnit.MILLISECONDS)
        super.channelActive(ctx)
    }

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val key = SchedulerKey(Type.PING_TIMEOUT, ctx.channel())
        scheduler.cancel(key)
        if (msg is FullHttpRequest) {
            val req: FullHttpRequest = msg as FullHttpRequest
            val channel: Channel = ctx.channel()
            val queryDecoder = QueryStringDecoder(req.uri())
            if (!configuration.isAllowCustomRequests
                && !queryDecoder.path().startsWith(connectPath)
            ) {
                val res: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
                channel.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
                req.release()
                return
            }
            val sid: List<String> = queryDecoder.parameters().get("sid")
            if (queryDecoder.path() == connectPath && sid == null) {
                val origin: String = req.headers().get(HttpHeaderNames.ORIGIN)
                if (!authorize(ctx, channel, origin, queryDecoder.parameters(), req)) {
                    req.release()
                    return
                }
                // forward message to polling or websocket handler to bind channel
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
        req: FullHttpRequest
    ): Boolean {
        val headers: MutableMap<String, List<String>> = HashMap<String, List<String>>(req.headers().names().size)
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
        var result = false
        try {
            result = configuration.authorizationListener.isAuthorized(data)
        } catch (e: Exception) {
            log.error("Authorization error", e)
        }
        if (!result) {
            val res: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED)
            channel.writeAndFlush(res)
                .addListener(ChannelFutureListener.CLOSE)
            log.debug("Handshake unauthorized, query params: {} headers: {}", params, headers)
            return false
        }
        var sessionId: UUID? = null
        sessionId = if (configuration.isRandomSession) {
            UUID.randomUUID()
        } else {
            generateOrGetSessionIdFromRequest(req.headers())
        }
        val transportValue = params["transport"]
        if (transportValue == null) {
            log.error("Got no transports for request {}", req.uri())
            writeAndFlushTransportError(channel, origin)
            return false
        }
        var transport: com.gribouille.socketio.Transport? = null
        transport = try {
            com.gribouille.socketio.Transport.valueOf(transportValue[0].uppercase(Locale.getDefault()))
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
            sessionId,
            ackManager,
            disconnectable,
            storeFactory,
            data,
            clientsBox,
            transport,
            scheduler,
            configuration
        )
        channel.attr<ClientHead>(ClientHead.Companion.CLIENT).set(client)
        clientsBox.addClient(client)
        var transports = arrayOf<String?>()
        if (configuration.transports.contains(com.gribouille.socketio.Transport.WEBSOCKET)) {
            transports = arrayOf("websocket")
        }
        val authPacket = AuthPacket(
            sessionId, transports, configuration.pingInterval,
            configuration.pingTimeout
        )
        val packet = Packet(PacketType.OPEN)
        packet.setData(authPacket)
        client.send(packet)
        client.schedulePing()
        client.schedulePingTimeout()
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
            packet.setSubType(PacketType.CONNECT)
            client.send(packet)
            configuration.storeFactory.pubSubStore().publish(PubSubType.CONNECT, ConnectMessage(client.sessionId))
            val nsClient: SocketIOClient? = client.addNamespaceClient(ns)
            ns.onConnect(nsClient)
        }
    }

    fun onDisconnect(client: ClientHead) {
        clientsBox.removeClient(client.sessionId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthorizeHandler::class.java)
    }
}