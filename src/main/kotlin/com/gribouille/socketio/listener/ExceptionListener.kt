
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient
import io.netty.channel.ChannelHandlerContext

interface ExceptionListener {
    fun onEventException(e: Exception, args: List<Any?>?, client: SocketIOClient?)
    fun onDisconnectException(e: Exception, client: SocketIOClient?)
    fun onConnectException(e: Exception, client: SocketIOClient?)
    fun onPingException(e: Exception, client: SocketIOClient?)
    fun onPongException(e: Exception, client: SocketIOClient?)

    @Throws(Exception::class)
    fun exceptionCaught(ctx: ChannelHandlerContext?, e: Throwable): Boolean
}