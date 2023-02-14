
package com.gribouille.socketio.listener

interface ClientListeners {
    fun addMultiTypeEventListener(eventName: String?, listener: MultiTypeEventListener?, vararg eventClass: Class<*>?)
    fun <T> addEventListener(eventName: String?, eventClass: Class<T>?, listener: DataListener<T>?)
    fun addEventInterceptor(eventInterceptor: EventInterceptor?)
    fun addDisconnectListener(listener: DisconnectListener?)
    fun addConnectListener(listener: ConnectListener?)

    /**
     * from v4, ping will always be sent by server except probe ping packet sent from client,
     * and pong will always be responded by client while receiving ping except probe pong packet responded from server
     * it makes no more sense to listen to ping packet, instead you can listen to pong packet
     * @param listener
     */
    @Deprecated(
        """use addPongListener instead
      """
    )
    fun addPingListener(listener: PingListener?)
    fun addPongListener(listener: PongListener?)
    fun addListeners(listeners: Any?)
    fun addListeners(listeners: Any?, listenersClass: Class<*>?)
    fun removeAllListeners(eventName: String?)
}