
package com.gribouille.socketio.protocol

import com.gribouille.socketio.AckCallback
import io.netty.util.internal.PlatformDependent
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import java.util.*

class JacksonJsonSupport(vararg modules: Module?) : JsonSupport {
    private inner class AckArgsDeserializer : StdDeserializer<AckArgs?>(AckArgs::class.java) {
        @Throws(IOException::class, JsonProcessingException::class)
        fun deserialize(jp: JsonParser, ctxt: DeserializationContext?): AckArgs {
            val args: MutableList<Any> = ArrayList()
            val result = AckArgs(args)
            val mapper: ObjectMapper = jp.getCodec() as ObjectMapper
            val root: JsonNode = mapper.readTree(jp)
            val callback: AckCallback<*> = currentAckClass.get()
            val iter: Iterator<JsonNode> = root.iterator()
            var i = 0
            while (iter.hasNext()) {
                var `val`: Any
                var clazz: Class<*> = callback.getResultClass()
                if (callback is MultiTypeAckCallback) {
                    val multiTypeAckCallback: MultiTypeAckCallback = callback as MultiTypeAckCallback
                    clazz = multiTypeAckCallback.getResultClasses().get(i)
                }
                val arg: JsonNode = iter.next()
                if (arg.isTextual() || arg.isBoolean()) {
                    clazz = Any::class.java
                }
                `val` = mapper.treeToValue(arg, clazz)
                args.add(`val`)
                i++
            }
            return result
        }

        companion object {
            private const val serialVersionUID = 7810461017389946707L
        }
    }

    class EventKey(private val namespaceName: String?, private val eventName: String?) {
        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + (eventName?.hashCode() ?: 0)
            result = prime * result + (namespaceName?.hashCode() ?: 0)
            return result
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) return true
            if (obj == null) return false
            if (javaClass != obj.javaClass) return false
            val other = obj as EventKey
            if (eventName == null) {
                if (other.eventName != null) return false
            } else if (eventName != other.eventName) return false
            if (namespaceName == null) {
                if (other.namespaceName != null) return false
            } else if (namespaceName != other.namespaceName) return false
            return true
        }
    }

    private inner class EventDeserializer : StdDeserializer<Event?>(
        Event::class.java
    ) {
        val eventMapping: MutableMap<EventKey, List<Class<*>>> = ConcurrentHashMap()
        @Throws(IOException::class, JsonProcessingException::class)
        fun deserialize(jp: JsonParser, ctxt: DeserializationContext?): Event {
            val mapper: ObjectMapper = jp.getCodec() as ObjectMapper
            val eventName: String = jp.nextTextValue()
            var ek = EventKey(namespaceClass.get(), eventName)
            if (!eventMapping.containsKey(ek)) {
                ek = EventKey(Namespace.DEFAULT_NAME, eventName)
                if (!eventMapping.containsKey(ek)) {
                    return Event(eventName, emptyList())
                }
            }
            val eventArgs: MutableList<Any> = ArrayList()
            val event = Event(eventName, eventArgs)
            val eventClasses = eventMapping[ek]!!
            var i = 0
            while (true) {
                val token: JsonToken = jp.nextToken()
                if (token === JsonToken.END_ARRAY) {
                    break
                }
                if (i > eventClasses.size - 1) {
                    log.debug("Event {} has more args than declared in handler: {}", eventName, null)
                    break
                }
                val eventClass = eventClasses[i]
                val arg: Any = mapper.readValue(jp, eventClass)
                eventArgs.add(arg)
                i++
            }
            return event
        }

        companion object {
            private const val serialVersionUID = 8178797221017768689L
        }
    }

    class ByteArraySerializer : StdSerializer<ByteArray?>(ByteArray::class.java) {
        private val arrays: ThreadLocal<MutableList<ByteArray>> = object : ThreadLocal<List<ByteArray>>() {
            override fun initialValue(): List<ByteArray> {
                return ArrayList()
            }
        }

        fun isEmpty(value: ByteArray?): Boolean {
            return value == null || value.size == 0
        }

        @Throws(IOException::class, JsonGenerationException::class)
        fun serialize(value: ByteArray, jgen: JsonGenerator, provider: SerializerProvider?) {
            val map: MutableMap<String, Any> = HashMap()
            map["num"] = arrays.get().size
            map["_placeholder"] = true
            jgen.writeObject(map)
            arrays.get().add(value)
        }

        @Throws(IOException::class, JsonGenerationException::class)
        fun serializeWithType(
            value: ByteArray, jgen: JsonGenerator, provider: SerializerProvider?,
            typeSer: TypeSerializer?
        ) {
            serialize(value, jgen, provider)
        }

        fun getSchema(provider: SerializerProvider?, typeHint: Type?): JsonNode {
            val o: ObjectNode = createSchemaNode("array", true)
            val itemSchema: ObjectNode = createSchemaNode("string") //binary values written as strings?
            return o.set("items", itemSchema)
        }

        @Throws(JsonMappingException::class)
        fun acceptJsonFormatVisitor(visitor: JsonFormatVisitorWrapper?, typeHint: JavaType?) {
            if (visitor != null) {
                val v2: JsonArrayFormatVisitor = visitor.expectArrayFormat(typeHint)
                if (v2 != null) {
                    v2.itemsFormat(JsonFormatTypes.STRING)
                }
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

    private inner class ExBeanSerializerModifier : BeanSerializerModifier() {
        val serializer = ByteArraySerializer()
        fun modifyArraySerializer(
            config: SerializationConfig?, valueType: ArrayType,
            beanDesc: BeanDescription?, serializer: JsonSerializer<*>?
        ): JsonSerializer<*> {
            return if (valueType.getRawClass().equals(ByteArray::class.java)) {
                this.serializer
            } else super.modifyArraySerializer(config, valueType, beanDesc, serializer)
        }
    }

    protected val modifier = ExBeanSerializerModifier()
    protected val namespaceClass = ThreadLocal<String?>()
    protected val currentAckClass: ThreadLocal<AckCallback<*>> = ThreadLocal<AckCallback<*>>()
    protected val objectMapper: ObjectMapper = ObjectMapper()
    protected val eventDeserializer = EventDeserializer()
    protected val ackArgsDeserializer = AckArgsDeserializer()

    constructor() : this(*arrayOf<Module>())

    init {
        if (modules != null && modules.size > 0) {
            objectMapper.registerModules(modules)
        }
        init(objectMapper)
    }

    protected fun init(objectMapper: ObjectMapper) {
        val module = SimpleModule()
        module.setSerializerModifier(modifier)
        module.addDeserializer(Event::class.java, eventDeserializer)
        module.addDeserializer(AckArgs::class.java, ackArgsDeserializer)
        objectMapper.registerModule(module)
        objectMapper.setSerializationInclusion(Include.NON_NULL)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    }

    override fun addEventMapping(namespaceName: String?, eventName: String?, vararg eventClass: Class<*>?) {
        eventDeserializer.eventMapping[EventKey(namespaceName, eventName)] = Arrays.asList(*eventClass)
    }

    override fun removeEventMapping(namespaceName: String?, eventName: String?) {
        eventDeserializer.eventMapping.remove(EventKey(namespaceName, eventName))
    }

    @Throws(IOException::class)
    override fun <T> readValue(namespaceName: String?, src: ByteBufInputStream?, valueType: Class<T>?): T {
        namespaceClass.set(namespaceName)
        return objectMapper.readValue(src as InputStream?, valueType)
    }

    @Throws(IOException::class)
    override fun readAckArgs(src: ByteBufInputStream?, callback: AckCallback<*>): AckArgs {
        currentAckClass.set(callback)
        return objectMapper.readValue(src as InputStream?, AckArgs::class.java)
    }

    @Throws(IOException::class)
    override fun writeValue(out: ByteBufOutputStream?, value: Any?) {
        modifier.getSerializer().clear()
        objectMapper.writeValue(out as OutputStream?, value)
    }

    override val arrays: List<ByteArray>
        get() = modifier.getSerializer().getArrays()

    companion object {
        protected val log = LoggerFactory.getLogger(JacksonJsonSupport::class.java)
    }
}