
package com.gribouille.socketio

import com.gribouille.socketio.protocol.AckArgs
import com.gribouille.socketio.protocol.JsonSupport
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import org.slf4j.LoggerFactory
import java.io.IOException

internal class JsonSupportWrapper(
    private val delegate: JsonSupport
) : JsonSupport {

    override val arrays: List<ByteArray>
        get() = delegate.arrays

    @Throws(IOException::class)
    override fun readAckArgs(
        src: ByteBufInputStream,
        callback: AckCallback
    ): AckArgs {
        return try {
            delegate.readAckArgs(src, callback)
        } catch (e: Exception) {
            src.reset()
            log.error("Can't read ack args: " + src.readLine() + " for type: " + callback.resultClass, e)
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun <T> readValue(
        namespaceName: String,
        src: ByteBufInputStream,
        valueType: Class<T>,
    ): T {
        return try {
            delegate.readValue(namespaceName, src, valueType)
        } catch (e: Exception) {
            src.reset()
            log.error("Can't read value: " + src.readLine() + " for type: " + valueType, e)
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun writeValue(
        out: ByteBufOutputStream,
        value: Any,
    ) {
        try {
            delegate.writeValue(out, value)
        } catch (e: Exception) {
            log.error("Can't write value: $value", e)
            throw IOException(e)
        }
    }

    override fun addEventMapping(namespaceName: String, eventName: String, vararg eventClass: Class<*>) {
        delegate.addEventMapping(namespaceName, eventName, *eventClass)
    }

    override fun removeEventMapping(namespaceName: String, eventName: String) {
        delegate.removeEventMapping(namespaceName, eventName)
    }

    companion object {
        private val log = LoggerFactory.getLogger(JsonSupportWrapper::class.java)
    }
}