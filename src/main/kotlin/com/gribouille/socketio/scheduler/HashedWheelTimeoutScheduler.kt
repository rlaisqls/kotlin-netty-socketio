
/**
 * Modified version of HashedWheelScheduler specially for timeouts handling.
 * Difference:
 * - handling old timeout with same key after adding new one
 * fixes multithreaded problem that appears in highly concurrent non-atomic sequence cancel() -> schedule()
 *
 * (c) Alim Akbashev, 2015-02-11
 */
package com.gribouille.socketio.scheduler

import io.netty.channel.ChannelHandlerContext
import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import io.netty.util.internal.PlatformDependent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class HashedWheelTimeoutScheduler : CancelableScheduler {
    private val scheduledFutures = ConcurrentHashMap<SchedulerKey, Timeout>()
    private val executorService: HashedWheelTimer

    @Volatile
    private var ctx: ChannelHandlerContext? = null

    constructor() {
        executorService = HashedWheelTimer()
    }

    constructor(threadFactory: ThreadFactory?) {
        executorService = HashedWheelTimer(threadFactory)
    }

    override fun update(ctx: ChannelHandlerContext?) {
        this.ctx = ctx
    }

    override fun cancel(key: SchedulerKey) {
        val timeout = scheduledFutures.remove(key)
        timeout?.cancel()
    }

    override fun schedule(runnable: Runnable, delay: Int, unit: TimeUnit?) {
        executorService.newTimeout({ runnable.run() }, delay.toLong(), unit)
    }

    override fun scheduleCallback(key: SchedulerKey, runnable: Runnable, delay: Int, unit: TimeUnit?) {
        val timeout = executorService.newTimeout({
            ctx!!.executor().execute {
                try {
                    runnable.run()
                } finally {
                    scheduledFutures.remove(key)
                }
            }
        }, delay.toLong(), unit)
        replaceScheduledFuture(key, timeout)
    }

    override fun schedule(key: SchedulerKey, runnable: Runnable, delay: Int, unit: TimeUnit?) {
        val timeout = executorService.newTimeout({
            try {
                runnable.run()
            } finally {
                scheduledFutures.remove(key)
            }
        }, delay.toLong(), unit)
        replaceScheduledFuture(key, timeout)
    }

    override fun shutdown() {
        executorService.stop()
    }

    private fun replaceScheduledFuture(key: SchedulerKey, newTimeout: Timeout) {
        val oldTimeout: Timeout?
        oldTimeout = if (newTimeout.isExpired) {
            // no need to put already expired timeout to scheduledFutures map.
            // simply remove old timeout
            scheduledFutures.remove(key)
        } else {
            scheduledFutures.put(key, newTimeout)
        }

        // if there was old timeout, cancel it
        oldTimeout?.cancel()
    }
}