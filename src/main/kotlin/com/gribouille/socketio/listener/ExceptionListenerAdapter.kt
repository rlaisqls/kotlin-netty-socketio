
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient
import io.netty.channel.ChannelHandlerContext

/**
 * Base callback exceptions listener
 */
abstract class ExceptionListenerAdapter : ExceptionListener {
    override fun onEventException(e: Exception, args: List<Any?>?, client: SocketIOClient?) {}
    override fun onDisconnectException(e: Exception, client: SocketIOClient?) {}
    override fun onConnectException(e: Exception, client: SocketIOClient?) {}
    override fun onPingException(e: Exception, client: SocketIOClient?) {}
    override fun onPongException(e: Exception, client: SocketIOClient?) {}
    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext?, e: Throwable): Boolean {
        return false
    }
}