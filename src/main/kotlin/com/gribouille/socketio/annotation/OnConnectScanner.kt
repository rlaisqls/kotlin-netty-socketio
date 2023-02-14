package com.gribouille.socketio.annotation

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.handler.SocketIOException
import com.gribouille.socketio.listener.ConnectListener
import com.gribouille.socketio.namespace.Namespace
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class OnConnectScanner : AnnotationScanner {
    override val scanAnnotation: Class<out Annotation?>?
        get() = OnConnect::class.java

    override fun addListener(namespace: Namespace, obj: Any?, method: Method, annotation: Annotation) {
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

    override fun validate(method: Method, clazz: Class<*>?) {
        require(method.parameterTypes.size == 1) { "Wrong OnConnect listener signature: " + clazz + "." + method.name }
        var valid = false
        for (eventType in method.parameterTypes) {
            if (eventType == SocketIOClient::class.java) {
                valid = true
            }
        }
        require(valid) { "Wrong OnConnect listener signature: " + clazz + "." + method.name }
    }
}