
package com.gribouille.socketio.listener

interface ClientListeners {
    fun addMultiTypeEventListener(eventName: String, listener: MultiTypeEventListener, vararg eventClass: Class<*>)
    fun addEventListener(eventName: String, eventClass: Class<*>, listener: DataListener)
    fun addEventInterceptor(eventInterceptor: EventInterceptor)
    fun addDisconnectListener(listener: DisconnectListener)
    fun addConnectListener(listener: ConnectListener)

    fun addPingListener(listener: PingListener)
    fun addPongListener(listener: PongListener)
    fun addListeners(listeners: Any)
    fun addListeners(listeners: Any, listenersClass: Class<*>)
    fun removeAllListeners(eventName: String)
}