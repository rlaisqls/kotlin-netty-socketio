
package com.gribouille.socketio.ack

import com.gribouille.socketio.AckCallback
import com.gribouille.socketio.Disconnectable
import com.gribouille.socketio.MultiTypeAckCallback
import com.gribouille.socketio.MultiTypeArgs
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.namespace.Namespace
import com.gribouille.socketio.protocol.Packet
import com.gribouille.socketio.scheduler.CancelableScheduler
import com.gribouille.socketio.scheduler.SchedulerKey
import com.gribouille.socketio.scheduler.SchedulerKey.Type
import io.netty.util.internal.PlatformDependent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AckManager(scheduler: CancelableScheduler) : Disconnectable {
    internal inner class AckEntry {
        val ackCallbacks: MutableMap<Long, AckCallback> = ConcurrentHashMap()
        val ackIndex: AtomicLong = AtomicLong(-1)
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

    private val ackEntries: ConcurrentMap<UUID, AckEntry> = PlatformDependent.newConcurrentHashMap<UUID, AckEntry>()
    private val scheduler: CancelableScheduler

    init {
        this.scheduler = scheduler
    }

    fun initAckIndex(sessionId: UUID, index: Long) {
        val ackEntry = getAckEntry(sessionId)
        ackEntry.initAckIndex(index)
    }

    private fun getAckEntry(sessionId: UUID) =
        ackEntries[sessionId] ?: AckEntry().also { ackEntries.putIfAbsent(sessionId, it) }

    fun onAck(
        client: SocketIOClient,
        packet: Packet
    ) {
        val key = AckSchedulerKey(Type.ACK_TIMEOUT, client.sessionId, packet.ackId!!)
        scheduler.cancel(key)
        val callback = removeCallback(client.sessionId, packet.ackId!!) ?: return
        if (callback is MultiTypeAckCallback) {
            callback.onSuccess(MultiTypeArgs(packet.data as List<Any>))
        } else {
            var param: Any? = null
            val args = packet.data as List<Any>
            if (!args.isEmpty()) {
                param = args[0]
            }
            if (args.size > 1) {
                log.error(
                    "Wrong ack args amount. Should be only one argument, but current amount is: {}. Ack id: {}, sessionId: {}",
                    args.size, packet.ackId, client.sessionId
                )
            }
            callback.onSuccess(param)
        }
    }

    private fun removeCallback(sessionId: UUID, index: Long): AckCallback? {
        // may be null if client disconnected
        // before timeout occurs
        return ackEntries[sessionId]?.removeCallback(index)
    }

    fun getCallback(sessionId: UUID, index: Long): AckCallback? {
        val ackEntry = getAckEntry(sessionId)
        return ackEntry.getAckCallback(index)
    }

    fun registerAck(sessionId: UUID, callback: AckCallback): Long {
        val ackEntry = getAckEntry(sessionId)
        ackEntry.initAckIndex(0)
        val index = ackEntry.addAckCallback(callback)
        if (log.isDebugEnabled) {
            log.debug("AckCallback registered with id: {} for client: {}", index, sessionId)
        }
        scheduleTimeout(index, sessionId, callback)
        return index
    }

    private fun scheduleTimeout(index: Long, sessionId: UUID, callback: AckCallback) {
        if (callback.timeout == -1) {
            return
        }
        val key: SchedulerKey = AckSchedulerKey(Type.ACK_TIMEOUT, sessionId, index)
        scheduler.scheduleCallback(key, Runnable {
            removeCallback(sessionId, index)?.onTimeout()
        }, callback.timeout, TimeUnit.SECONDS)
    }

    override fun onDisconnect(client: ClientHead) {
        val e: AckEntry = ackEntries.remove(client.sessionId) ?: return
        val indexes = e.ackIndexes
        for (index in indexes) {
            e.getAckCallback(index)?.onTimeout()
            val key: SchedulerKey = AckSchedulerKey(Type.ACK_TIMEOUT, client.sessionId, index)
            scheduler.cancel(key)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AckManager::class.java)
    }
}