
package com.gribouille.socketio

import com.gribouille.socketio.protocol.AckArgs
import org.slf4j.LoggerFactory

internal class JsonSupportWrapper(delegate: JsonSupport?) : JsonSupport {
    private val delegate: JsonSupport?

    init {
        this.delegate = delegate
    }

    @Throws(IOException::class)
    fun readAckArgs(src: ByteBufInputStream, callback: com.gribouille.socketio.AckCallback<*>): AckArgs {
        return try {
            delegate.readAckArgs(src, callback)
        } catch (e: Exception) {
            src.reset()
            log.error("Can't read ack args: " + src.readLine() + " for type: " + callback.getResultClass(), e)
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    fun <T> readValue(namespaceName: String?, src: ByteBufInputStream, valueType: Class<T>): T {
        return try {
            delegate.readValue(namespaceName, src, valueType)
        } catch (e: Exception) {
            src.reset()
            log.error("Can't read value: " + src.readLine() + " for type: " + valueType, e)
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    fun writeValue(out: ByteBufOutputStream?, value: Any) {
        try {
            delegate.writeValue(out, value)
        } catch (e: Exception) {
            log.error("Can't write value: $value", e)
            throw IOException(e)
        }
    }

    fun addEventMapping(namespaceName: String?, eventName: String?, vararg eventClass: Class<*>?) {
        delegate.addEventMapping(namespaceName, eventName, eventClass)
    }

    fun removeEventMapping(namespaceName: String?, eventName: String?) {
        delegate.removeEventMapping(namespaceName, eventName)
    }

    val arrays: List<ByteArray>
        get() = delegate.getArrays()

    companion object {
        private val log = LoggerFactory.getLogger(JsonSupportWrapper::class.java)
    }
}