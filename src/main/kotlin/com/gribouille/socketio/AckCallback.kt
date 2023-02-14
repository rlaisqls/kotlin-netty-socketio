
package com.gribouille.socketio

import com.gribouille.socketio.SocketIOClient

/**
 * Base ack callback class.
 *
 * Notifies about acknowledgement received from client
 * via [.onSuccess] callback method.
 *
 * By default it may wait acknowledgement from client
 * while [SocketIOClient] is alive. Timeout can be
 * defined [.timeout] as constructor argument.
 *
 * This object is NOT actual anymore if [.onSuccess] or
 * [.onTimeout] was executed.
 *
 * @param <T> - any serializable type
 *
 * @see com.gribouille.socketio.VoidAckCallback
 *
 * @see com.gribouille.socketio.MultiTypeAckCallback
</T> */
abstract class AckCallback<T>
/**
 * Create AckCallback
 *
 * @param resultClass - result class
 */ @JvmOverloads constructor(
    /**
     * Returns class of argument in [.onSuccess] method
     *
     * @return - result class
     */
    val resultClass: Class<T>, val timeout: Int = -1
) {
    /**
     * Creates AckCallback with timeout
     *
     * @param resultClass - result class
     * @param timeout - callback timeout in seconds
     */
    /**
     * Executes only once when acknowledgement received from client.
     *
     * @param result - object sended by client
     */
    abstract fun onSuccess(result: T)

    /**
     * Invoked only once then `timeout` defined
     *
     */
    open fun onTimeout() {}
}