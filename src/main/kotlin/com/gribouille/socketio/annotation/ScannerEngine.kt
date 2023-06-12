package com.gribouille.socketio.annotation

import com.gribouille.socketio.namespace.Namespace
import org.slf4j.LoggerFactory

internal interface ScannerEngine {
    fun scan(namespace: Namespace, obj: Any, clazz: Class<*>)
}

internal val scannerEngine = object : ScannerEngine {

    @Throws(IllegalArgumentException::class)
    override fun scan(namespace: Namespace, obj: Any, clazz: Class<*>) {
        val methods = clazz.declaredMethods
        for (method in methods) {
            for (annotation in SocketAnnotation.annotations) {
                method.getAnnotation(annotation.java)?.let {
                    socketAnnotationScanner.addListener(
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

    private val log = LoggerFactory.getLogger(ScannerEngine::class.java)
}
