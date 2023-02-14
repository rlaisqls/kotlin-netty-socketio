
package com.gribouille.socketio

import com.gribouille.socketio.MultiTypeArgs

/**
 * Multi type ack callback used in case of multiple ack arguments
 *
 */
abstract class MultiTypeAckCallback(vararg resultClasses: Class<*>) : com.gribouille.socketio.AckCallback<MultiTypeArgs?>(
    MultiTypeArgs::class.java
) {
    val resultClasses: Array<Class<*>>

    init {
        this.resultClasses = resultClasses
    }
}