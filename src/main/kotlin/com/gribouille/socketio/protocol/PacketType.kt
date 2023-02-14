
package com.gribouille.socketio.protocol

enum class PacketType @JvmOverloads constructor(val value: Int, private val inner: Boolean = false) {
    OPEN(0), CLOSE(1), PING(2), PONG(3), MESSAGE(4), UPGRADE(5), NOOP(6), CONNECT(0, true), DISCONNECT(
        1,
        true
    ),
    EVENT(2, true), ACK(3, true), ERROR(4, true), BINARY_EVENT(5, true), BINARY_ACK(6, true);

    companion object {
        val VALUES = values()
        fun valueOf(value: Int): PacketType {
            for (type in VALUES) {
                if (type.value == value && !type.inner) {
                    return type
                }
            }
            throw IllegalStateException()
        }

        fun valueOfInner(value: Int): PacketType {
            for (type in VALUES) {
                if (type.value == value && type.inner) {
                    return type
                }
            }
            throw IllegalArgumentException("Can't parse $value")
        }
    }
}