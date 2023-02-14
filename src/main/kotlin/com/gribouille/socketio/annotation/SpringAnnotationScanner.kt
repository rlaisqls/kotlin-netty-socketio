
package com.gribouille.socketio.annotation

import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import java.lang.reflect.Method
import java.util.*

class SpringAnnotationScanner(socketIOServer: SocketIOServer) : BeanPostProcessor {
    private val annotations = Arrays.asList(
        OnConnect::class.java, OnDisconnect::class.java, OnEvent::class.java
    )
    private val socketIOServer: SocketIOServer
    private var originalBeanClass: Class<*>? = null
    private var originalBean: Any? = null
    private var originalBeanName: String? = null

    init {
        this.socketIOServer = socketIOServer
    }

    @Throws(BeansException::class)
    fun postProcessAfterInitialization(bean: Any, beanName: String?): Any {
        if (originalBeanClass != null) {
            socketIOServer.addListeners(originalBean, originalBeanClass)
            log.info("{} bean listeners added", originalBeanName)
            originalBeanClass = null
            originalBeanName = null
        }
        return bean
    }

    @Throws(BeansException::class)
    fun postProcessBeforeInitialization(bean: Any, beanName: String?): Any {
        val add = AtomicBoolean()
        ReflectionUtils.doWithMethods(bean.javaClass,
            object : MethodCallback() {
                @Throws(IllegalArgumentException::class, IllegalAccessException::class)
                fun doWith(method: Method?) {
                    add.set(true)
                }
            },
            object : MethodFilter() {
                fun matches(method: Method): Boolean {
                    for (annotationClass in annotations) {
                        if (method.isAnnotationPresent(annotationClass)) {
                            return true
                        }
                    }
                    return false
                }
            })
        if (add.get()) {
            originalBeanClass = bean.javaClass
            originalBean = bean
            originalBeanName = beanName
        }
        return bean
    }

    companion object {
        private val log = LoggerFactory.getLogger(SpringAnnotationScanner::class.java)
    }
}