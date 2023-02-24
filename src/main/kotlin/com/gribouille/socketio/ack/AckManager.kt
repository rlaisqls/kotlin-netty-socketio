package com.gribouille.socketio.ack

import com.gribouille.socketio.AckCallback
import com.gribouille.socketio.Disconnectable
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.scheduler.CancelableScheduler
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.SchedulerKey.Type
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AckManager(
    private val scheduler: CancelableScheduler,
) : Disconnectable {

    private val ackEntries: ConcurrentMap<UUID, AckEntry> = ConcurrentHashMap()

    internal inner class AckEntry {
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


    fun initAckIndex(sessionId: UUID, index: Long) {
        getAckEntry(sessionId).apply {
            initAckIndex(index)
        }
    }

    private fun getAckEntry(sessionId: UUID) =
        ackEntries[sessionId] ?: AckEntry().also { ackEntries.putIfAbsent(sessionId, it) }

    fun onAck(
        client: SocketIOClient,
        packet: Packet,
    ) {
        val key = AckSchedulerKey(Type.ACK_TIMEOUT, client.sessionId, packet.ackId!!)
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

    fun getCallback(sessionId: UUID, index: Long): AckCallback? {
        val ackEntry = getAckEntry(sessionId)
        return ackEntry.getAckCallback(index)
    }

    fun registerAck(sessionId: UUID, callback: AckCallback): Long {
        val index = getAckEntry(sessionId)
            .apply { initAckIndex(0) }
            .addAckCallback(callback)
        log.debug("AckCallback registered with id: {} for client: {}", index, sessionId)
        scheduleTimeout(index, sessionId, callback)
        return index
    }

    private fun scheduleTimeout(index: Long, sessionId: UUID, callback: AckCallback) {
        if (callback.timeout == -1) return
        scheduler.scheduleCallback(
            key = AckSchedulerKey(Type.ACK_TIMEOUT, sessionId, index),
            delay = callback.timeout,
            unit = TimeUnit.SECONDS,
            runnable = {
                removeCallback(sessionId, index)?.onTimeout()
            }
        )
    }

    override fun onDisconnect(client: ClientHead) {
        val ackEntry = ackEntries.remove(client.sessionId) ?: return
        for (index in ackEntry.ackIndexes) {
            ackEntry.getAckCallback(index)?.onTimeout()
            AckSchedulerKey(Type.ACK_TIMEOUT, client.sessionId, index).let { key ->
                scheduler.cancel(key)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AckManager::class.java)
    }
}