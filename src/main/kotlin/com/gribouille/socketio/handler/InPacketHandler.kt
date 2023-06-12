
package com.gribouille.socketio.handler

import com.gribouille.socketio.exceptionListener
import com.gribouille.socketio.messages.PacketsMessage
import com.gribouille.socketio.namespace.namespacesHub
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.protocol.packetDecoder
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.CharsetUtil
import org.slf4j.LoggerFactory

interface InPacketHandler: ChannelHandler

internal val packetHandler: InPacketHandler = @Sharable object :
    InPacketHandler, SimpleChannelInboundHandler<PacketsMessage?>() {

    @Throws(Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext?, message: PacketsMessage?) {
        val content = message!!.content
        val client = message.client

        if (log.isTraceEnabled) {
            log.trace("In message: {} sessionId: {}", content.toString(CharsetUtil.UTF_8), client.sessionId)
        }
        while (content.isReadable) {
            try {
                val packet = packetDecoder.decodePackets(content, client)
                if (packet.isAttachmentsNotLoaded) {
                    return
                }
                val ns = namespacesHub[packet.nsp] ?: run {
                    if (packet.subType == PacketType.CONNECT) {
                        val p = Packet(PacketType.MESSAGE)
                        p.subType = PacketType.ERROR
                        p.nsp = packet.nsp
                        p.data = "Invalid namespace"
                        client.send(p)
                        return
                    }
                    log.debug(
                        "Can't find namespace for endpoint: {}, sessionId: {} probably it was removed.",
                        packet.nsp,
                        client.sessionId
                    )
                    return
                }
                if (packet.subType == PacketType.CONNECT) {
                    client.addNamespaceClient(ns)
                }
                val nClient = client.getChildClient(ns) ?: run {
                    log.debug(
                        "Can't find namespace client in namespace: ${ns.name}," +
                                "sessionId: ${client.sessionId} probably it was disconnected."
                    )
                    return
                }
                packetListener.onPacket(
                    packet = packet,
                    client = nClient,
                    transport = message.transport
                )
            } catch (e: Exception) {
                log.error("Error during data processing. Client sessionId: ${client.sessionId}," +
                        "data: ${content.toString(CharsetUtil.UTF_8)}", e)
                throw e
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, e: Throwable) {
        if (!exceptionListener.exceptionCaught(ctx, e)) {
            super.exceptionCaught(ctx, e)
        }
    }

    private val log = LoggerFactory.getLogger(InPacketHandler::class.java)
}