
package com.gribouille.socketio.protocol

import com.gribouille.socketio.namespace.Namespace
import io.netty.buffer.ByteBuf
import java.io.Serializable

class Packet(
    var type: PacketType? = null
) : Serializable {
    var subType: PacketType? = null
    var ackId: Long? = null
    var name: String? = null
    var nsp: String = Namespace.DEFAULT_NAME
    var data: Any? = null
    var attachments: MutableList<ByteBuf?> = mutableListOf()
    private var attachmentsCount = 0
    var dataSource: ByteBuf? = null

    val isAttachmentsLoaded: Boolean
        get() = attachments.size == attachmentsCount

    @JvmName("getTypedData")
    fun <T> getData(): T {
        return data as T
    }

    fun withNsp(namespace: String): Packet {
        return if (nsp.equals(namespace, ignoreCase = true)) {
            this
        } else {
            copyWithNsp(namespace)
        }
    }

    private fun copyWithNsp(namespace: String): Packet {
        val newPacket = Packet(type)
        newPacket.ackId = ackId
        newPacket.data = data
        newPacket.dataSource = dataSource
        newPacket.name = name
        newPacket.subType = subType
        newPacket.nsp = namespace
        newPacket.attachments = attachments
        newPacket.attachmentsCount = attachmentsCount
        return newPacket
    }

    val isAckRequested: Boolean
        get() = ackId != null

    fun initAttachments(attachmentsCount: Int) {
        this.attachmentsCount = attachmentsCount
    }

    fun addAttachment(attachment: ByteBuf?) {
        if (attachments.size < attachmentsCount) {
            attachments.add(attachment)
        }
    }

    fun hasAttachments(): Boolean {
        return attachmentsCount != 0
    }

    override fun toString(): String {
        return "Packet [type=$type, ackId=$ackId]"
    }

    companion object {
        private const val serialVersionUID = 4560159536486711426L
    }
}