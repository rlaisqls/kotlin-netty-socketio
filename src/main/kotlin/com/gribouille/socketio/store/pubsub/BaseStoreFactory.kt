
package com.gribouille.socketio.store.pubsub

import com.gribouille.socketio.handler.AuthorizeHandler
import org.slf4j.LoggerFactory

abstract class BaseStoreFactory : StoreFactory {
    private val log = LoggerFactory.getLogger(javaClass)
    protected val nodeId = (Math.random() * 1000000).toLong()
    fun init(namespacesHub: NamespacesHub, authorizeHandler: AuthorizeHandler, jsonSupport: JsonSupport?) {
        pubSubStore().subscribe(
            PubSubType.DISCONNECT,
            { msg -> log.debug("{} sessionId: {}", PubSubType.DISCONNECT, msg.sessionId) },
            DisconnectMessage::class.java
        )
        pubSubStore().subscribe(PubSubType.CONNECT, { msg ->
            authorizeHandler.connect(msg.sessionId)
            log.debug("{} sessionId: {}", PubSubType.CONNECT, msg.sessionId)
        }, ConnectMessage::class.java)
        pubSubStore().subscribe(PubSubType.DISPATCH, { msg ->
            val name = msg.room
            namespacesHub.get(msg.namespace).dispatch(name, msg.packet)
            log.debug("{} packet: {}", PubSubType.DISPATCH, msg.packet)
        }, DispatchMessage::class.java)
        pubSubStore().subscribe(PubSubType.JOIN, { msg ->
            val name = msg.room
            namespacesHub.get(msg.namespace).join(name, msg.sessionId)
            log.debug("{} sessionId: {}", PubSubType.JOIN, msg.sessionId)
        }, JoinLeaveMessage::class.java)
        pubSubStore().subscribe(PubSubType.BULK_JOIN, { msg ->
            val rooms = msg.rooms
            for (room in rooms!!) {
                namespacesHub.get(msg.namespace).join(room, msg.sessionId)
            }
            log.debug("{} sessionId: {}", PubSubType.BULK_JOIN, msg.sessionId)
        }, BulkJoinLeaveMessage::class.java)
        pubSubStore().subscribe(PubSubType.LEAVE, { msg ->
            val name = msg.room
            namespacesHub.get(msg.namespace).leave(name, msg.sessionId)
            log.debug("{} sessionId: {}", PubSubType.LEAVE, msg.sessionId)
        }, JoinLeaveMessage::class.java)
        pubSubStore().subscribe(PubSubType.BULK_LEAVE, { msg ->
            val rooms = msg.rooms
            for (room in rooms!!) {
                namespacesHub.get(msg.namespace).leave(room, msg.sessionId)
            }
            log.debug("{} sessionId: {}", PubSubType.BULK_LEAVE, msg.sessionId)
        }, BulkJoinLeaveMessage::class.java)
    }

    abstract fun pubSubStore(): PubSubStore
    fun onDisconnect(client: ClientHead?) {}
    override fun toString(): String {
        return javaClass.simpleName + " (distributed session store, distributed publish/subscribe)"
    }
}