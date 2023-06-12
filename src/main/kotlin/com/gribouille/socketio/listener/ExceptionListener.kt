
package com.gribouille.socketio.listener

import com.gribouille.socketio.SocketIOClient
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

interface ExceptionListener {
    fun onEventException(e: Exception, args: List<Any?>?, client: SocketIOClient?)
    fun onDisconnectException(e: Exception, client: SocketIOClient?)
    fun onConnectException(e: Exception, client: SocketIOClient?)
    fun onPingException(e: Exception, client: SocketIOClient?)
    fun onPongException(e: Exception, client: SocketIOClient?)

    @Throws(Exception::class)
    fun exceptionCaught(ctx: ChannelHandlerContext?, e: Throwable): Boolean
}

class DefaultExceptionListener : ExceptionListenerAdapter() {

    override fun onEventException(e: Exception, args: List<Any?>?, client: SocketIOClient?) {
        log.error(e.message, e)
    }

    override fun onDisconnectException(e: Exception, client: SocketIOClient?) {
        log.error(e.message, e)
    }

    override fun onConnectException(e: Exception, client: SocketIOClient?) {
        log.error(e.message, e)
    }

    override fun onPingException(e: Exception, client: SocketIOClient?) {
        log.error(e.message, e)
    }

    override fun onPongException(e: Exception, client: SocketIOClient?) {
        log.error(e.message, e)
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext?, e: Throwable): Boolean {
        log.error(e.message, e)
        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(DefaultExceptionListener::class.java)
    }
}