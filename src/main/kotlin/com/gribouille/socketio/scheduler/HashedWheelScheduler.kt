
package com.gribouille.socketio.scheduler

import io.netty.channel.ChannelHandlerContext
import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import io.netty.util.internal.PlatformDependent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class HashedWheelScheduler : CancelableScheduler {
    private val scheduledFutures: MutableMap<SchedulerKey, Timeout> = ConcurrentHashMap()
    private val executorService: HashedWheelTimer

    constructor() {
        executorService = HashedWheelTimer()
    }

    constructor(threadFactory: ThreadFactory?) {
        executorService = HashedWheelTimer(threadFactory)
    }

    @Volatile
    private var ctx: ChannelHandlerContext? = null
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

    override fun scheduleCallback(
        key: SchedulerKey,
        runnable: Runnable,
        delay: Int,
        unit: TimeUnit?
    ) {
        val timeout = executorService.newTimeout({
            ctx!!.executor().execute {
                try {
                    runnable.run()
                } finally {
                    scheduledFutures.remove(key)
                }
            }
        }, delay.toLong(), unit)
        if (!timeout.isExpired) {
            scheduledFutures[key] = timeout
        }
    }

    override fun schedule(
        key: SchedulerKey,
        runnable: Runnable,
        delay: Int,
        unit: TimeUnit?
    ) {
        val timeout = executorService.newTimeout({
            try {
                runnable.run()
            } finally {
                scheduledFutures.remove(key)
            }
        }, delay.toLong(), unit)
        if (!timeout.isExpired) {
            scheduledFutures[key] = timeout
        }
    }

    override fun shutdown() {
        executorService.stop()
    }
}