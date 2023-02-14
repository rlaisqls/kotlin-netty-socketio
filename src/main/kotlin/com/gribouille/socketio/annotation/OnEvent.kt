
package com.gribouille.socketio.annotation

/**
 * Annotation that defines **Event** handler.
 * The value is required, and represents event name.
 *
 * Arguments in method:
 *
 * - SocketIOClient (optional)
 * - AckRequest (optional)
 * - Event data (optional)
 *
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(
    AnnotationRetention.RUNTIME
)
annotation class OnEvent(
    /**
     * Event name
     *
     * @return value
     */
    val value: String
)