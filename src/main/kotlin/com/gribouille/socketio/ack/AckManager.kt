package com.gribouille.socketio.ack

import com.corundumstudio.socketio.ack.AckManager
import com.gribouille.socketio.Disconnectable
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.SchedulerKey.Type
import com.gribouille.socketio.scheduler.scheduler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory

internal interface AckManagerInterface : Disconnectable {
    fun initAckIndex(sessionId: UUID, index: Long)
    fun onAck(client: SocketIOClient, packet: Packet)
    fun getCallback(sessionId: UUID, index: Long): AckCallback?
    fun registerAck(sessionId: UUID, callback: AckCallback): Long
}

internal val ackManager = object : AckManagerInterface {

    private val ackEntries: ConcurrentMap<UUID, AckEntry> = ConcurrentHashMap()

    private inner class AckEntry {

        private val ackCallbacks: MutableMap<Long, AckCallback> = ConcurrentHashMap()
        private val ackIndex: AtomicLong = AtomicLong(-1)

        fun addAckCallback(callback: AckCallback): Long {
            val index: Long = ackIndex.incrementAndGet()
            ackCallbacks[index] = callback
            return index
        }

        val ackIndexes: Set<Long>
            get() = ackCallbacks.keys

        fun getAckCallback(index: Long): AckCallback? {
            return ackCallbacks[index]
        }

        fun removeCallback(index: Long): AckCallback? {
            return ackCallbacks.remove(index)
        }

        fun initAckIndex(index: Long) {
            ackIndex.compareAndSet(-1, index)
        }
    }

    override fun initAckIndex(sessionId: UUID, index: Long) {
        getAckEntry(sessionId).apply {
            initAckIndex(index)
        }
    }

    private fun getAckEntry(sessionId: UUID) =
        ackEntries[sessionId] ?: AckEntry().also { ackEntries.putIfAbsent(sessionId, it) }

    override fun onAck(client: SocketIOClient, packet: Packet) {
        val key = SchedulerKey(Type.ACK_TIMEOUT, client.sessionId)
        scheduler.cancel(key)
        val callback = removeCallback(client.sessionId, packet.ackId!!) ?: return

        val args = packet.data as List<Any>
        require(args.size <= 1) {
            "Wrong ack args amount.Should be only one argument," +
                    "but current amount is: ${args.size}. Ack id: ${packet.ackId}, sessionId: ${client.sessionId}"
        }
        val param = if (args.isNotEmpty()) args[0] else null
        callback.onSuccess(param)
    }

    private fun removeCallback(sessionId: UUID, index: Long): AckCallback? {
        // timeout 발생 전 disconnect 된 경우 null
        return ackEntries[sessionId]?.removeCallback(index)
    }

    override fun getCallback(sessionId: UUID, index: Long): AckCallback? {
        val ackEntry = getAckEntry(sessionId)
        return ackEntry.getAckCallback(index)
    }

    override fun registerAck(sessionId: UUID, callback: AckCallback): Long {
        val index = getAckEntry(sessionId)
            .apply { initAckIndex(0) }
            .addAckCallback(callback)
        log.debug("AckCallback registered with id: {} for client: {}", index, sessionId)
        scheduleTimeout(index, sessionId, callback)
        return index
    }

    private fun scheduleTimeout(index: Long, sessionId: UUID, callback: AckCallback) {
        if (callback.timeout == -1) return
        scheduler.schedule(
            key = SchedulerKey(Type.ACK_TIMEOUT, sessionId),
            delay = callback.timeout,
            runnable = {
                removeCallback(sessionId, index)?.onTimeout()
            }
        )
    }

    override fun onDisconnect(client: ClientHead) {
        val ackEntry = ackEntries.remove(client.sessionId) ?: return
        for (index in ackEntry.ackIndexes) {
            ackEntry.getAckCallback(index)?.onTimeout()
            scheduler.cancel(Type.ACK_TIMEOUT, client.sessionId)
        }
    }

    private val log = LoggerFactory.getLogger(AckManager::class.java)
}