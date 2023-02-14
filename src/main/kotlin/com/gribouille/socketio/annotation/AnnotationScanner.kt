
package com.gribouille.socketio.annotation

import com.gribouille.socketio.namespace.Namespace
import java.lang.reflect.Method

interface AnnotationScanner {
    val scanAnnotation: Class<out Annotation?>?
    fun addListener(namespace: Namespace, obj: Any?, method: Method, annotation: Annotation)
    fun validate(method: Method, clazz: Class<*>?)
}