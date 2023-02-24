
package com.gribouille.socketio.handler

import com.gribouille.socketio.listener.ExceptionListener
import com.gribouille.socketio.messages.PacketsMessage
import com.gribouille.socketio.namespace.NamespacesHub
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketDecoder
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.transport.NamespaceClient
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.CharsetUtil
import org.slf4j.LoggerFactory

@Sharable
class InPacketHandler(
    private val packetListener: PacketListener,
    decoder: PacketDecoder,
    namespacesHub: NamespacesHub,
    exceptionListener: ExceptionListener
) : SimpleChannelInboundHandler<PacketsMessage?>() {
    private val decoder: PacketDecoder
    private val namespacesHub: NamespacesHub
    private val exceptionListener: ExceptionListener

    init {
        this.decoder = decoder
        this.namespacesHub = namespacesHub
        this.exceptionListener = exceptionListener
    }

    @Throws(Exception::class)
    protected override fun channelRead0(ctx: ChannelHandlerContext?, message: PacketsMessage?) {
        val content = message!!.content
        val client = message.client
        if (log.isTraceEnabled) {
            log.trace("In message: {} sessionId: {}", content.toString(CharsetUtil.UTF_8), client.sessionId)
        }
        while (content.isReadable) {
            try {
                val packet = decoder.decodePackets(content, client)
                if (packet.hasAttachments() && !packet.isAttachmentsLoaded) {
                    return
                }
                val ns = namespacesHub[packet.nsp]
                if (ns == null) {
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
                val nClient: NamespaceClient? = client.getChildClient(ns)
                if (nClient == null) {
                    log.debug(
                        "Can't find namespace client in namespace: {}, sessionId: {} probably it was disconnected.",
                        ns.name,
                        client.sessionId
                    )
                    return
                }
                packetListener.onPacket(packet, nClient, message.transport)
            } catch (ex: Exception) {
                val c: String = content.toString(CharsetUtil.UTF_8)
                log.error("Error during data processing. Client sessionId: " + client.sessionId + ", data: " + c, ex)
                throw ex
            }
        }
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, e: Throwable) {
        if (!exceptionListener.exceptionCaught(ctx, e)) {
            super.exceptionCaught(ctx, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(InPacketHandler::class.java)
    }
}