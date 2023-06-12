
package com.gribouille.socketio.handler

import com.gribouille.socketio.Transport
import com.gribouille.socketio.ack.AckRequest
import com.gribouille.socketio.ack.ackManager
import com.gribouille.socketio.namespace.namespacesHub
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.scheduler
import com.gribouille.socketio.transport.NamespaceClient

interface PacketListener {
    fun onPacket(packet: Packet, client: NamespaceClient, transport: Transport)
}

internal val packetListener = object : PacketListener {

    override fun onPacket(packet: Packet, client: NamespaceClient, transport: Transport) {
        val ackRequest = AckRequest(packet, client)
        if (packet.isAckRequested) {
            ackManager.initAckIndex(client.sessionId, packet.ackId!!)
        }

        when (packet.type) {

            PacketType.PING -> {
                val outPacket = Packet(PacketType.PONG).apply { data = packet.data }
                client.baseClient.send(outPacket, transport)
                if ("probe" == packet.data) {
                    client.baseClient.send(Packet(PacketType.NOOP), Transport.POLLING)
                } else {
                    client.baseClient.schedulePingTimeout()
                }
                namespacesHub[packet.nsp]!!.onPing(client)
            }

            PacketType.PONG -> {
                client.baseClient.schedulePingTimeout()
                 namespacesHub[packet.nsp]!!.onPong(client)
            }

            PacketType.UPGRADE -> {
                client.baseClient.schedulePingTimeout()
                SchedulerKey(SchedulerKey.Type.UPGRADE_TIMEOUT, client.sessionId)
                    .also { key -> scheduler.cancel(key) }
                client.baseClient.upgradeCurrentTransport(transport)
            }

            PacketType.MESSAGE -> {
                client.baseClient.schedulePingTimeout()
                when (packet.subType) {
                    PacketType.DISCONNECT -> {
                        client.onDisconnect()
                    }
                    PacketType.CONNECT -> {
                        val namespace = namespacesHub[packet.nsp]!!
                        namespace.onConnect(client)
                        // send connect handshake packet back to client
                        client.baseClient.send(packet, transport)
                    }
                    PacketType.ACK, PacketType.BINARY_ACK -> {
                        ackManager.onAck(client, packet)
                    }
                    PacketType.EVENT, PacketType.BINARY_EVENT -> {
                        namespacesHub[packet.nsp]!!.onEvent(
                            client = client,
                            eventName = packet.name!!,
                            args = packet.data?.let { it as List<Any> } ?: emptyList(),
                            ackRequest = ackRequest
                        )
                    }
                    else -> {}
                }
            }

            PacketType.CLOSE -> {
                client.baseClient.onChannelDisconnect()
            }
            else -> {}
        }
    }
}