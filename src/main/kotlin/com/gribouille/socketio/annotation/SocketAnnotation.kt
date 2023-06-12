package com.gribouille.socketio.annotation

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