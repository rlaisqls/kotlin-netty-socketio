
package com.gribouille.socketio.handler

import com.gribouille.socketio.ack.AckManager

class PacketListener(
    ackManager: AckManager, namespacesHub: NamespacesHub, xhrPollingTransport: PollingTransport?,
    scheduler: CancelableScheduler
) {
    private val namespacesHub: NamespacesHub
    private val ackManager: AckManager
    private val scheduler: CancelableScheduler

    init {
        this.ackManager = ackManager
        this.namespacesHub = namespacesHub
        this.scheduler = scheduler
    }

    fun onPacket(packet: Packet, client: NamespaceClient, transport: com.gribouille.socketio.Transport?) {
        val ackRequest = AckRequest(packet, client)
        if (packet.isAckRequested()) {
            ackManager.initAckIndex(client.getSessionId(), packet.getAckId())
        }
        when (packet.getType()) {
            PING -> {
                val outPacket = Packet(PacketType.PONG)
                outPacket.setData(packet.getData())
                // TODO use future
                client.getBaseClient().send(outPacket, transport)
                if ("probe" == packet.getData()) {
                    client.getBaseClient().send(Packet(PacketType.NOOP), com.gribouille.socketio.Transport.POLLING)
                } else {
                    client.getBaseClient().schedulePingTimeout()
                }
                val namespace: Namespace = namespacesHub.get(packet.getNsp())
                namespace.onPing(client)
            }

            PONG -> {
                client.getBaseClient().schedulePingTimeout()
                val namespace: Namespace = namespacesHub.get(packet.getNsp())
                namespace.onPong(client)
            }

            UPGRADE -> {
                client.getBaseClient().schedulePingTimeout()
                val key = SchedulerKey(SchedulerKey.Type.UPGRADE_TIMEOUT, client.getSessionId())
                scheduler.cancel(key)
                client.getBaseClient().upgradeCurrentTransport(transport)
            }

            MESSAGE -> {
                client.getBaseClient().schedulePingTimeout()
                if (packet.getSubType() === PacketType.DISCONNECT) {
                    client.onDisconnect()
                }
                if (packet.getSubType() === PacketType.CONNECT) {
                    val namespace: Namespace = namespacesHub.get(packet.getNsp())
                    namespace.onConnect(client)
                    // send connect handshake packet back to client
                    client.getBaseClient().send(packet, transport)
                }
                if (packet.getSubType() === PacketType.ACK
                    || packet.getSubType() === PacketType.BINARY_ACK
                ) {
                    ackManager.onAck(client, packet)
                }
                if (packet.getSubType() === PacketType.EVENT
                    || packet.getSubType() === PacketType.BINARY_EVENT
                ) {
                    val namespace: Namespace = namespacesHub.get(packet.getNsp())
                    var args: List<Any?> = emptyList<Any>()
                    if (packet.getData() != null) {
                        args = packet.getData()
                    }
                    namespace.onEvent(client, packet.getName(), args, ackRequest)
                }
            }

            CLOSE -> client.getBaseClient().onChannelDisconnect()
            else -> {}
        }
    }
}