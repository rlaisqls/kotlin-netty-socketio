
package com.gribouille.socketio.protocol

import com.gribouille.socketio.ack.AckArgs
import com.gribouille.socketio.ack.AckCallback
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import java.io.IOException

/**
 * JSON infrastructure interface.
 * Allows to implement custom realizations
 * to JSON support operations.
 *
 */
interface JsonSupport {
    @Throws(IOException::class)
    fun readAckArgs(src: ByteBufInputStream, callback: AckCallback): AckArgs

    @Throws(IOException::class)
    fun <T> readValue(namespaceName: String, src: ByteBufInputStream, valueType: Class<T>): T

    @Throws(IOException::class)
    fun writeValue(out: ByteBufOutputStream, value: Any)
    fun addEventMapping(namespaceName: String, eventName: String, vararg eventClass: Class<*>)
    fun removeEventMapping(namespaceName: String, eventName: String)
    val arrays: List<ByteArray>
}