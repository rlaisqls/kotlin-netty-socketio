
package com.gribouille.socketio

import com.gribouille.socketio.AckCallback

/**
 * Base ack callback with [Void] class as type.
 *
 */
abstract class VoidAckCallback : AckCallback<Void> {
    constructor() : super(Void::class.java)
    constructor(timeout: Int) : super(Void::class.java, timeout)

    override fun onSuccess(result: Void) {
        onSuccess()
    }

    protected abstract fun onSuccess()
}