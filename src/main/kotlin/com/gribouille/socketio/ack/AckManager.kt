
package com.gribouille.socketio.ack

import com.gribouille.socketio.*
import io.netty.util.internal.PlatformDependent
import org.slf4j.LoggerFactory
import java.util.*

class AckManager(scheduler: CancelableScheduler) : Disconnectable {
    internal inner class AckEntry {
        val ackCallbacks: MutableMap<Long, AckCallback<*>> =
            PlatformDependent.newConcurrentHashMap<Long, AckCallback<*>>()
        val ackIndex: AtomicLong = AtomicLong(-1)
        fun addAckCallback(callback: AckCallback<*>): Long {
            val index: Long = ackIndex.incrementAndGet()
            ackCallbacks[index] = callback
            return index
        }

        val ackIndexes: Set<Long>
            get() = ackCallbacks.keys

        fun getAckCallback(index: Long): AckCallback<*> {
            return ackCallbacks[index]
        }

        fun removeCallback(index: Long): AckCallback<*> {
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

    private fun getAckEntry(sessionId: UUID): AckEntry {
        var ackEntry: AckEntry? = ackEntries.get(sessionId)
        if (ackEntry == null) {
            ackEntry = AckEntry()
            val oldAckEntry: AckEntry = ackEntries.putIfAbsent(sessionId, ackEntry)
            if (oldAckEntry != null) {
                ackEntry = oldAckEntry
            }
        }
        return ackEntry
    }

    fun onAck(client: SocketIOClient, packet: Packet) {
        val key = AckSchedulerKey(Type.ACK_TIMEOUT, client.getSessionId(), packet.getAckId())
        scheduler.cancel(key)
        val callback: AckCallback = removeCallback(client.getSessionId(), packet.getAckId()) ?: return
        if (callback is MultiTypeAckCallback) {
            callback.onSuccess(MultiTypeArgs(packet.< List < Object > > getData<List<Any>>()))
        } else {
            var param: Any? = null
            val args: List<Any> = packet.getData()
            if (!args.isEmpty()) {
                param = args[0]
            }
            if (args.size > 1) {
                log.error(
                    "Wrong ack args amount. Should be only one argument, but current amount is: {}. Ack id: {}, sessionId: {}",
                    args.size, packet.getAckId(), client.getSessionId()
                )
            }
            callback.onSuccess(param)
        }
    }

    private fun removeCallback(sessionId: UUID, index: Long): AckCallback<*>? {
        val ackEntry: AckEntry = ackEntries.get(sessionId)
        // may be null if client disconnected
        // before timeout occurs
        return if (ackEntry != null) {
            ackEntry.removeCallback(index)
        } else null
    }

    fun getCallback(sessionId: UUID, index: Long): AckCallback<*> {
        val ackEntry = getAckEntry(sessionId)
        return ackEntry.getAckCallback(index)
    }

    fun registerAck(sessionId: UUID, callback: AckCallback<*>): Long {
        val ackEntry = getAckEntry(sessionId)
        ackEntry.initAckIndex(0)
        val index = ackEntry.addAckCallback(callback)
        if (log.isDebugEnabled) {
            log.debug("AckCallback registered with id: {} for client: {}", index, sessionId)
        }
        scheduleTimeout(index, sessionId, callback)
        return index
    }

    private fun scheduleTimeout(index: Long, sessionId: UUID, callback: AckCallback<*>) {
        if (callback.getTimeout() === -1) {
            return
        }
        val key: SchedulerKey = AckSchedulerKey(Type.ACK_TIMEOUT, sessionId, index)
        scheduler.scheduleCallback(key, Runnable {
            val cb: AckCallback<*>? = removeCallback(sessionId, index)
            if (cb != null) {
                cb.onTimeout()
            }
        }, callback.getTimeout(), TimeUnit.SECONDS)
    }

    fun onDisconnect(client: ClientHead) {
        val e: AckEntry = ackEntries.remove(client.getSessionId()) ?: return
        val indexes = e.ackIndexes
        for (index in indexes) {
            val callback: AckCallback<*> = e.getAckCallback(index)
            if (callback != null) {
                callback.onTimeout()
            }
            val key: SchedulerKey = AckSchedulerKey(Type.ACK_TIMEOUT, client.getSessionId(), index)
            scheduler.cancel(key)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AckManager::class.java)
    }
}