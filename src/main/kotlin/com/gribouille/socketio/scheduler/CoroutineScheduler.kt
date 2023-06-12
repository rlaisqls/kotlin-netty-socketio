package com.gribouille.socketio.scheduler

import io.netty.channel.ChannelHandlerContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CoroutineScheduler: CancelableScheduler {

    private val scheduledFutures = ConcurrentHashMap<SchedulerKey, CoroutineTimeout>()

    override fun update(ctx: ChannelHandlerContext?) {
        // Nothing to do
    }

    override fun cancel(key: SchedulerKey) {
        scheduledFutures.remove(key)?.cancel()
    }

    override fun schedule(key: SchedulerKey, runnable: Runnable, delay: Int) {
        val timeout = CoroutineTimeout()
            .apply { start(delay.toLong(), runnable) }
        scheduledFutures[key] = timeout
    }

    override fun shutdown() {
        // Nothing to do
    }

    private class CoroutineTimeout {

        var job: Job? = null

        @OptIn(DelicateCoroutinesApi::class)
        fun start(delay: Long, runnable: Runnable) {
            job = CoroutineScope(Dispatchers.Unconfined).launch {
                delay(delay)
                runnable.run()
            }
        }

        fun cancel() {
            job!!.cancel()
        }
    }
}