
package com.gribouille.socketio.annotation

import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.MultiTypeArgs
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.handler.SocketIOException
import com.gribouille.socketio.listener.DataListener
import com.gribouille.socketio.listener.MultiTypeEventListener
import com.gribouille.socketio.namespace.Namespace
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class OnEventScanner : AnnotationScanner {
    override val scanAnnotation: Class<out Annotation?>?
        get() = OnEvent::class.java

    override fun addListener(namespace: Namespace, obj: Any?, method: Method, annot: Annotation) {
        val annotation = annot as OnEvent
        require(!(annotation.value == null || annotation.value.trim { it <= ' ' }.length == 0)) { "OnEvent \"value\" parameter is required" }
        val socketIOClientIndex = paramIndex(method, SocketIOClient::class.java)
        val ackRequestIndex = paramIndex(method, AckRequest::class.java)
        val dataIndexes = dataIndexes(method)
        if (dataIndexes.size > 1) {
            val classes: MutableList<Class<*>> = ArrayList()
            for (index in dataIndexes) {
                val param = method.parameterTypes[index]
                classes.add(param)
            }
            namespace.addMultiTypeEventListener(annotation.value, object : MultiTypeEventListener {
                override fun onMultiTypeArgs(client: SocketIOClient, data: MultiTypeArgs, ackSender: AckRequest) {
                    try {
                        val args = arrayOfNulls<Any>(method.parameterTypes.size)
                        if (socketIOClientIndex != -1) {
                            args[socketIOClientIndex] = client
                        }
                        if (ackRequestIndex != -1) {
                            args[ackRequestIndex] = ackSender
                        }
                        var i = 0
                        for (index in dataIndexes) {
                            args[index] = data.get<Any>(i)
                            i++
                        }
                        method.invoke(obj, *args)
                    } catch (e: InvocationTargetException) {
                        throw SocketIOException(e.cause)
                    } catch (e: Exception) {
                        throw SocketIOException(e)
                    }
                }
            }, *classes.toTypedArray())
        } else {
            var objectType: Class<*>? = Void::class.java
            if (dataIndexes.isNotEmpty()) {
                objectType = method.parameterTypes[dataIndexes.iterator().next()]
            }
            namespace.addEventListener(annotation.value, objectType!!, object : DataListener {
                override fun onData(client: SocketIOClient, data: Any, ackSender: AckRequest) {
                    try {
                        val args = arrayOfNulls<Any>(method.parameterTypes.size)
                        if (socketIOClientIndex != -1) {
                            args[socketIOClientIndex] = client
                        }
                        if (ackRequestIndex != -1) {
                            args[ackRequestIndex] = ackSender
                        }
                        if (dataIndexes.isNotEmpty()) {
                            val dataIndex = dataIndexes.iterator().next()
                            args[dataIndex] = data
                        }
                        method.invoke(obj, *args)
                    } catch (e: InvocationTargetException) {
                        throw SocketIOException(e.cause)
                    } catch (e: Exception) {
                        throw SocketIOException(e)
                    }
                }
            })
        }
    }


    private fun dataIndexes(method: Method): List<Int> {
        val result: MutableList<Int> = ArrayList()
        var index = 0
        for (type in method.parameterTypes) {
            if (type != AckRequest::class.java && type != SocketIOClient::class.java) {
                result.add(index)
            }
            index++
        }
        return result
    }

    private fun paramIndex(method: Method, clazz: Class<*>): Int {
        var index = 0
        for (type in method.parameterTypes) {
            if (type == clazz) {
                return index
            }
            index++
        }
        return -1
    }

    override fun validate(method: Method, clazz: Class<*>?) {
        var paramsCount = method.parameterTypes.size
        val socketIOClientIndex = paramIndex(method, SocketIOClient::class.java)
        val ackRequestIndex = paramIndex(method, AckRequest::class.java)
        val dataIndexes = dataIndexes(method)
        paramsCount -= dataIndexes.size
        if (socketIOClientIndex != -1) {
            paramsCount--
        }
        if (ackRequestIndex != -1) {
            paramsCount--
        }
        require(paramsCount == 0) { "Wrong OnEvent listener signature: " + clazz + "." + method.name }
    }

}