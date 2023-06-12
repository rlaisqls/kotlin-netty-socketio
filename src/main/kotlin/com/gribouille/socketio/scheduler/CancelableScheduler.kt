
package com.gribouille.socketio.scheduler

import io.netty.channel.ChannelHandlerContext


//internal val scheduler = object: CancelableScheduler by CoroutineScheduler() {}
internal val scheduler = object: CancelableScheduler by HashedWheelTimeoutScheduler() {}

interface CancelableScheduler {
    fun update(ctx: ChannelHandlerContext?)
    fun cancel(key: SchedulerKey)
    fun cancel(type: SchedulerKey.Type, sessionId: Any?) = cancel(SchedulerKey(type, sessionId))
    fun schedule(key: SchedulerKey, runnable: Runnable, delay: Int)
    fun shutdown()
}

data class SchedulerKey(
    private val type: Type?,
    private val sessionId: Any?
) {
    enum class Type {
        PING, PING_TIMEOUT, ACK_TIMEOUT, UPGRADE_TIMEOUT
    }
}