
package com.gribouille.socketio.handler

import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.Transport
import com.gribouille.socketio.ack.AckManager
import com.gribouille.socketio.namespace.NamespacesHub
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.protocol.PacketType
import com.gribouille.socketio.scheduler.CancelableScheduler
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.transport.NamespaceClient
import com.gribouille.socketio.transport.PollingTransport
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withTimeout

class PacketListener(
    private val namespacesHub: NamespacesHub,
    private val ackManager: AckManager,
    private val scheduler: CancelableScheduler
) {

    fun onPacket(packet: Packet, client: NamespaceClient, transport: Transport) {
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
                val namespace = namespacesHub[packet.nsp]!!
                namespace.onPing(client)
            }

            PacketType.PONG -> {
                client.baseClient.schedulePingTimeout()
                val namespace = namespacesHub[packet.nsp]!!
                namespace.onPong(client)
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