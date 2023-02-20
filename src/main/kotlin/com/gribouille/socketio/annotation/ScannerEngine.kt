package com.gribouille.socketio.annotation

import com.gribouille.socketio.namespace.Namespace
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

class ScannerEngine {

    @Throws(IllegalArgumentException::class)
    fun scan(namespace: Namespace, obj: Any, clazz: Class<*>) {
        val methods = clazz.declaredMethods
        for (method in methods) {
            for (annotation in SocketAnnotation.annotations) {
                method.getAnnotation(annotation.java)?.let {
                    SocketAnnotationScanner.addListener(
                        namespace = namespace,
                        obj = obj,
                        method = method,
                        annotation = it
                    )
                    log.info("add {} Listener class: {} method: {}", annotation, obj.javaClass, method.name)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScannerEngine::class.java)
    }
}