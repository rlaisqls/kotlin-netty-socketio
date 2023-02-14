
package com.gribouille.socketio.handler

import com.gribouille.socketio.listener.ExceptionListener
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
    protected override fun channelRead0(ctx: ChannelHandlerContext, message: PacketsMessage) {
        val content: ByteBuf = message.getContent()
        val client: ClientHead = message.getClient()
        if (log.isTraceEnabled) {
            log.trace("In message: {} sessionId: {}", content.toString(CharsetUtil.UTF_8), client.sessionId)
        }
        while (content.isReadable()) {
            try {
                val packet: Packet = decoder.decodePackets(content, client)
                if (packet.hasAttachments() && !packet.isAttachmentsLoaded()) {
                    return
                }
                val ns: Namespace = namespacesHub.get(packet.getNsp())
                if (ns == null) {
                    if (packet.getSubType() === PacketType.CONNECT) {
                        val p = Packet(PacketType.MESSAGE)
                        p.setSubType(PacketType.ERROR)
                        p.setNsp(packet.getNsp())
                        p.setData("Invalid namespace")
                        client.send(p)
                        return
                    }
                    log.debug(
                        "Can't find namespace for endpoint: {}, sessionId: {} probably it was removed.",
                        packet.getNsp(),
                        client.sessionId
                    )
                    return
                }
                if (packet.getSubType() === PacketType.CONNECT) {
                    client.addNamespaceClient(ns)
                }
                val nClient: NamespaceClient? = client.getChildClient(ns)
                if (nClient == null) {
                    log.debug(
                        "Can't find namespace client in namespace: {}, sessionId: {} probably it was disconnected.",
                        ns.getName(),
                        client.sessionId
                    )
                    return
                }
                packetListener.onPacket(packet, nClient, message.getTransport())
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