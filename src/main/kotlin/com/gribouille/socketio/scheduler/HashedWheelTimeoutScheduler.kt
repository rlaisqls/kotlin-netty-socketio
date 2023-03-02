
package com.gribouille.socketio.scheduler

import io.netty.channel.ChannelHandlerContext
import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
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
        replaceScheduledFuture(key, timeout)
    }

    override fun schedule(
        key: SchedulerKey,
        runnable: Runnable,
        delay: Int,
        unit: TimeUnit?
    ) {
        val timeout = executorService.newTimeout(
            {
                try {
                    runnable.run()
                } finally {
                    scheduledFutures.remove(key)
                }
            },
            delay.toLong(),
            unit
        )

        replaceScheduledFuture(key, timeout)
    }

    override fun shutdown() {
        executorService.stop()
    }

    private fun replaceScheduledFuture(key: SchedulerKey, newTimeout: Timeout) {
        val oldTimeout = if (newTimeout.isExpired) {
            // 이미 expired 된 timeout이 들어온 경우 정보를 지운다.
            scheduledFutures.remove(key)
        } else {
            scheduledFutures.put(key, newTimeout)
        }

        // 동일한 키에 대한 timeout이 이미 존재한다면 취소한다.
        oldTimeout?.cancel()
    }
}