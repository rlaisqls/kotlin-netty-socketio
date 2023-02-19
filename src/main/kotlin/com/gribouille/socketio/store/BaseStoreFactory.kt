
package com.gribouille.socketio.store

import org.slf4j.LoggerFactory

abstract class BaseStoreFactory : StoreFactory {
    private val log = LoggerFactory.getLogger(javaClass)
    protected val nodeId = (Math.random() * 1000000).toLong()

    override fun toString(): String {
        return javaClass.simpleName + " (distributed session store, distributed publish/subscribe)"
    }
}