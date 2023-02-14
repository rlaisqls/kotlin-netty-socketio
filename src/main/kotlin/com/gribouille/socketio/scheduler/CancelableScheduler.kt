
package com.gribouille.socketio.scheduler

import io.netty.channel.ChannelHandlerContext
import java.util.concurrent.TimeUnit

interface CancelableScheduler {
    fun update(ctx: ChannelHandlerContext?)
    fun cancel(key: SchedulerKey)
    fun scheduleCallback(key: SchedulerKey, runnable: Runnable, delay: Int, unit: TimeUnit?)
    fun schedule(runnable: Runnable, delay: Int, unit: TimeUnit?)
    fun schedule(key: SchedulerKey, runnable: Runnable, delay: Int, unit: TimeUnit?)
    fun shutdown()
}