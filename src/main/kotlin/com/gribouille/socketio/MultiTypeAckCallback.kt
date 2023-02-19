
package com.gribouille.socketio

import com.gribouille.socketio.MultiTypeArgs

/**
 * Multi type ack callback used in case of multiple ack arguments
 *
 */
abstract class MultiTypeAckCallback(
    val resultClasses: Array<Class<*>>
) : AckCallback(MultiTypeArgs::class.java)