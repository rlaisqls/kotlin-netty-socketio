
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

class PacketListener(
    private val namespacesHub: NamespacesHub,
    private val ackManager: AckManager,
    private val scheduler: CancelableScheduler,
    xhrPollingTransport: PollingTransport
) {

    fun onPacket(packet: Packet, client: NamespaceClient, transport: Transport) {
        val ackRequest = AckRequest(packet, client)
        if (packet.isAckRequested) {
            ackManager.initAckIndex(client.sessionId, packet.ackId!!)
        }
        when (packet.type) {
            PacketType.PING -> {
                val outPacket = Packet(PacketType.PONG)
                outPacket.data = packet.data
                // TODO use future
                client.baseClient.send(outPacket, transport)
                if ("probe" == packet.data) {
                    client.baseClient.send(Packet(PacketType.NOOP), Transport.POLLING)
                } else {
                    client.baseClient.schedulePingTimeout()
                }
                val namespace = namespacesHub.get(packet.nsp)
                namespace.onPing(client)
            }

            PacketType.PONG -> {
                client.baseClient.schedulePingTimeout()
                val namespace = namespacesHub.get(packet.nsp)
                namespace.onPong(client)
            }

            PacketType.UPGRADE -> {
                client.baseClient.schedulePingTimeout()
                val key = SchedulerKey(SchedulerKey.Type.UPGRADE_TIMEOUT, client.sessionId)
                scheduler.cancel(key)
                client.baseClient.upgradeCurrentTransport(transport)
            }

            PacketType.MESSAGE -> {
                client.baseClient.schedulePingTimeout()
                if (packet.subType === PacketType.DISCONNECT) {
                    client.onDisconnect()
                }
                if (packet.subType === PacketType.CONNECT) {
                    val namespace = namespacesHub[packet.nsp]
                    namespace.onConnect(client)
                    // send connect handshake packet back to client
                    client.baseClient.send(packet, transport)
                }
                if (packet.subType === PacketType.ACK
                    || packet.subType === PacketType.BINARY_ACK
                ) {
                    ackManager.onAck(client, packet)
                }
                if (packet.subType === PacketType.EVENT
                    || packet.subType === PacketType.BINARY_EVENT
                ) {
                    val namespace = namespacesHub[packet.nsp]
                    var args: List<Any> = emptyList()
                    if (packet.data != null) {
                        args = packet.data as List<Any>
                    }
                    namespace.onEvent(client, packet.name!!, args, ackRequest)
                }
            }

            PacketType.CLOSE -> client.baseClient.onChannelDisconnect()
            else -> {}
        }
    }
}