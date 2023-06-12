
package com.gribouille.socketio.protocol

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonGenerator.Feature
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper
import com.fasterxml.jackson.databind.jsontype.TypeSerializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.type.ArrayType
import com.gribouille.socketio.ack.AckArgs
import com.gribouille.socketio.ack.AckCallback
import com.gribouille.socketio.namespace.Namespace
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

open class JacksonJsonSupport(vararg modules: Module) : JsonSupport {

    protected val modifier = ExBeanSerializerModifier()
    protected val namespaceClass = ThreadLocal<String?>()
    protected val currentAckClass: ThreadLocal<AckCallback> = ThreadLocal<AckCallback>()
    protected val objectMapper: ObjectMapper = ObjectMapper()
    protected val eventDeserializer = EventDeserializer()
    protected val ackArgsDeserializer = AckArgsDeserializer()

    init {
        if (modules != null && modules.size > 0) {
            objectMapper.registerModules(modules.toList())
        }
        val module = SimpleModule()
        module.setSerializerModifier(modifier)
        module.addDeserializer(Event::class.java, eventDeserializer)
        module.addDeserializer(AckArgs::class.java, ackArgsDeserializer)
        objectMapper.registerModule(module)
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.configure(Feature.WRITE_BIGDECIMAL_AS_PLAIN, true)
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    }

    inner class AckArgsDeserializer : StdDeserializer<AckArgs?>(AckArgs::class.java) {

        override fun deserialize(jp: JsonParser, ctxt: DeserializationContext?): AckArgs {
            val args: MutableList<Any> = ArrayList()
            val result = AckArgs(args)
            val mapper = jp.codec as ObjectMapper
            val root = mapper.readTree<JsonNode>(jp)
            val callback = currentAckClass.get()
            val iter = root.iterator()
            var i = 0
            while (iter.hasNext()) {
                var value: Any
                var clazz: Class<*> = callback.resultClass

                val arg = iter.next()
                if (arg.isTextual || arg.isBoolean) {
                    clazz = Any::class.java
                }
                value = mapper.treeToValue(arg, clazz)
                args.add(value)
                i++
            }
            return result
        }
    }

    data class EventKey(
        private val namespaceName: String?,
        private val eventName: String?
    )

    inner class EventDeserializer : StdDeserializer<Event?>(Event::class.java) {
        val eventMapping = ConcurrentHashMap<EventKey, List<Class<*>>>()

        override fun deserialize(jp: JsonParser, ctxt: DeserializationContext?): Event {
            val mapper = jp.codec as ObjectMapper
            val eventName: String? = jp.nextTextValue()

            var ek = EventKey(namespaceClass.get(), eventName)
            if (!eventMapping.containsKey(ek)) {
                ek = EventKey(Namespace.DEFAULT_NAME, eventName)

                if (!eventMapping.containsKey(ek)) {
                    return Event(eventName, emptyList())
                }
            }
            val eventArgs = ArrayList<Any>()
            val event = Event(eventName, eventArgs)
            val eventClasses = eventMapping[ek]!!
            var i = 0
            while (true) {
                val token = jp.nextToken()
                if (token == JsonToken.END_ARRAY) {
                    break
                }
                if (i > eventClasses.size - 1) {
                    log.debug("Event {} has more args than declared in handler: {}", eventName, null)
                    break
                }
                val eventClass = eventClasses[i]
                val arg = mapper.readValue(jp, eventClass)
                eventArgs.add(arg)
                i++
            }
            return event
        }
    }

    class ByteArraySerializer : StdSerializer<ByteArray>(ByteArray::class.java) {

        private val arrays = object : ThreadLocal<MutableList<ByteArray>>() {
            override fun initialValue(): MutableList<ByteArray> = ArrayList()
        }

        override fun isEmpty(value: ByteArray?): Boolean {
            return value == null || value.isEmpty()
        }

        override fun serialize(value: ByteArray?, jgen: JsonGenerator?, provider: SerializerProvider?) {
            val map: MutableMap<String, Any> = HashMap()
            map["num"] = arrays.get().size
            map["_placeholder"] = true
            jgen!!.writeObject(map)
            arrays.get().add(value!!)
        }

        override fun serializeWithType(
            value: ByteArray,
            jgen: JsonGenerator,
            provider: SerializerProvider?,
            typeSer: TypeSerializer?
        ) {
            serialize(value, jgen, provider)
        }

        override fun getSchema(provider: SerializerProvider?, typeHint: Type?): JsonNode {
            val o = createSchemaNode("array", true)
            val itemSchema = createSchemaNode("string") //binary values written as strings?
            return o.set("items", itemSchema)
        }

        override fun acceptJsonFormatVisitor(
            visitor: JsonFormatVisitorWrapper?,
            typeHint: JavaType?
        ) {
            if (visitor != null) {
                visitor.expectArrayFormat(typeHint)?.itemsFormat(JsonFormatTypes.STRING)
            }
        }

        fun getArrays(): List<ByteArray> {
            return arrays.get()
        }

        fun clear() {
            arrays.set(ArrayList())
        }

        companion object {
            private const val serialVersionUID = 3420082888596468148L
        }
    }

    inner class ExBeanSerializerModifier : BeanSerializerModifier() {
        val serializer = ByteArraySerializer()
        override fun modifyArraySerializer(
            config: SerializationConfig?,
            valueType: ArrayType,
            beanDesc: BeanDescription?,
            serializer: JsonSerializer<*>?
        ): JsonSerializer<*> {
            return if (valueType.rawClass.equals(ByteArray::class.java)) {
                this.serializer
            } else super.modifyArraySerializer(config, valueType, beanDesc, serializer)
        }
    }

    override fun addEventMapping(
        namespaceName: String,
        eventName: String,
        vararg eventClass: Class<*>
    ) {
        eventDeserializer.eventMapping[EventKey(namespaceName, eventName)] = listOf(*eventClass)
    }

    override fun removeEventMapping(namespaceName: String, eventName: String) {
        eventDeserializer.eventMapping.remove(EventKey(namespaceName, eventName))
    }

    override fun <T> readValue(
        namespaceName: String,
        src: ByteBufInputStream,
        valueType: Class<T>
    ): T {
        namespaceClass.set(namespaceName)
        return objectMapper.readValue(src as InputStream, valueType)
    }

    override fun readAckArgs(
        src: ByteBufInputStream,
        callback: AckCallback
    ): AckArgs {
        currentAckClass.set(callback)
        return objectMapper.readValue(src as InputStream?, AckArgs::class.java)
    }

    override fun writeValue(
        out: ByteBufOutputStream,
        value: Any
    ) {
        modifier.serializer.clear()
        objectMapper.writeValue(out as OutputStream?, value)
    }

    override val arrays: List<ByteArray>
        get() = modifier.serializer.getArrays()

    companion object {
        protected val log = LoggerFactory.getLogger(JacksonJsonSupport::class.java)
    }
}
