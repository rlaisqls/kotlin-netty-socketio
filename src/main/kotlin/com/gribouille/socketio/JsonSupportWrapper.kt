/**
 * Copyright (c) 2012-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.gribouille

import com.corundumstudio.socketio.protocol.AckArgs
import org.slf4j.LoggerFactory

internal class JsonSupportWrapper(delegate: JsonSupport?) : JsonSupport {
    private val delegate: JsonSupport?

    init {
        this.delegate = delegate
    }

    @Throws(IOException::class)
    fun readAckArgs(src: ByteBufInputStream, callback: AckCallback<*>): AckArgs {
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