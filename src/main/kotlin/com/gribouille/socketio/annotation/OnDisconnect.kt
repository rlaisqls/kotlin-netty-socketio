
package com.gribouille.socketio.annotation

/**
 * Annotation that defines **Disconnect** handler.
 *
 * Arguments in method:
 *
 * - SocketIOClient (required)
 *
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(
    AnnotationRetention.RUNTIME
)
annotation class OnDisconnect 