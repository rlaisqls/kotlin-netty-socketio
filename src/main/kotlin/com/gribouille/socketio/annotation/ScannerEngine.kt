
package com.gribouille.socketio.annotation

import com.gribouille.socketio.namespace.Namespace
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

class ScannerEngine {
    private fun findSimilarMethod(objectClazz: Class<*>, method: Method): Method? {
        val methods = objectClazz.declaredMethods
        for (m in methods) {
            if (isEquals(m, method)) {
                return m
            }
        }
        return null
    }

    @Throws(IllegalArgumentException::class)
    fun scan(namespace: Namespace, obj: Any, clazz: Class<*>) {
        val methods = clazz.declaredMethods
        if (!clazz.isAssignableFrom(obj.javaClass)) {
            for (method in methods) {
                for (annotationScanner in annotations) {
                    val ann = method.getAnnotation(annotationScanner.scanAnnotation)
                    if (ann != null) {
                        annotationScanner.validate(method, clazz)
                        val m = findSimilarMethod(obj.javaClass, method)
                        if (m != null) {
                            annotationScanner.addListener(namespace, obj, m, ann)
                        } else {
                            log.warn("Method similar to " + method.name + " can't be found in " + obj.javaClass)
                        }
                    }
                }
            }
        } else {
            for (method in methods) {
                for (annotationScanner in annotations) {
                    val ann = method.getAnnotation(annotationScanner.scanAnnotation)
                    if (ann != null) {
                        annotationScanner.validate(method, clazz)
                        makeAccessible(method)
                        annotationScanner.addListener(namespace, obj, method, ann)
                    }
                }
            }
            if (clazz.superclass != null) {
                scan(namespace, obj, clazz.superclass)
            } else if (clazz.isInterface) {
                for (superIfc in clazz.interfaces) {
                    scan(namespace, obj, superIfc)
                }
            }
        }
    }

    private fun isEquals(method1: Method, method2: Method): Boolean {
        return if (method1.name != method2.name
            || method1.returnType != method2.returnType
        ) {
            false
        } else Arrays.equals(method1.parameterTypes, method2.parameterTypes)
    }

    private fun makeAccessible(method: Method) {
        if ((!Modifier.isPublic(method.modifiers) || !Modifier.isPublic(method.declaringClass.modifiers))
            && !method.isAccessible
        ) {
            method.isAccessible = true
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScannerEngine::class.java)
        private val annotations = Arrays.asList(OnConnectScanner(), OnDisconnectScanner(), OnEventScanner())
    }
}