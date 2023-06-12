package com.gribouille.socketio.annotation

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.ack.AckRequest
import com.gribouille.socketio.handler.SocketIOException
import com.gribouille.socketio.listener.ConnectListener
import com.gribouille.socketio.listener.DataListener
import com.gribouille.socketio.namespace.Namespace
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

interface SocketAnnotationScanner {
    fun addListener(namespace: Namespace, obj: Any, method: Method, annotation: Annotation)
}

internal val socketAnnotationScanner = object : SocketAnnotationScanner {

    override fun addListener(
        namespace: Namespace,
        obj: Any,
        method: Method,
        annotation: Annotation
    ) {
        when (annotation.annotationClass) {
            OnConnect::class -> addConnectionListener(namespace, obj, method, annotation as OnConnect)
            OnDisconnect::class -> addConnectionListener(namespace, obj, method, annotation as OnDisconnect)
            OnEvent::class -> addOnEventListener(namespace, obj, method, annotation as OnEvent)
        }
    }

    private fun addConnectionListener(namespace: Namespace, obj: Any, method: Method, annotation: Annotation) {
        namespace.addConnectListener(object : ConnectListener {
            override fun onConnect(client: SocketIOClient?) {
                try {
                    method.invoke(obj, client)
                } catch (e: InvocationTargetException) {
                    throw SocketIOException(e.cause)
                } catch (e: Exception) {
                    throw SocketIOException(e)
                }
            }
        })
    }

    private fun addOnEventListener(namespace: Namespace, obj: Any?, method: Method, annotation: OnEvent) {

        require(annotation.value.trim { it <= ' ' }.isNotEmpty()) { "OnEvent \"value\" parameter is required" }

        val requestType = getDataType(method)
        namespace.addEventListener(annotation.value, requestType, object : DataListener {
            override fun onData(client: SocketIOClient, data: Any, ackSender: AckRequest) {
                try {
                    val args: MutableList<Any> = ArrayList()
                    for (params in method.parameterTypes) {
                        if (params == SocketIOClient::class.java) {
                            args.add(client)
                        } else if (params == requestType) {
                            args.add(data)
                        }
                    }
                    method.invoke(obj, *args.toTypedArray())
                } catch (e: InvocationTargetException) {
                    throw SocketIOException(e.cause)
                } catch (e: Exception) {
                    throw SocketIOException(e)
                }
            }
        })
    }

    private fun getDataType(method: Method): Class<*> {
        for (type in method.parameterTypes) {
            if (type != AckRequest::class.java && type != SocketIOClient::class.java) {
                return type
            }
        }
        return Void::class.java
    }
}