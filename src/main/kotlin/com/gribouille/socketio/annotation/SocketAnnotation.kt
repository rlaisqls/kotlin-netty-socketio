package com.gribouille.socketio.annotation

import com.gribouille.socketio.AckRequest
import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.handler.SocketIOException
import com.gribouille.socketio.listener.ConnectListener
import com.gribouille.socketio.listener.DataListener
import com.gribouille.socketio.namespace.Namespace
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnConnect

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnDisconnect

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnEvent(
    val value: String
)

object SocketAnnotation {
    val annotations = listOf(OnConnect::class, OnDisconnect::class, OnEvent::class)
}