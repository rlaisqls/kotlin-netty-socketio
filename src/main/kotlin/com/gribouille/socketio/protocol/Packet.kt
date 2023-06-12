
package com.gribouille.socketio.protocol

import com.gribouille.socketio.namespace.Namespace
import io.netty.buffer.ByteBuf
import java.io.Serializable

class Packet(
    var type: PacketType? = null,
    var nsp: String = Namespace.DEFAULT_NAME,
    var data: Any? = null
) : Serializable {

    var subType: PacketType? = null
    var ackId: Long? = null
    var name: String? = null
    var attachments: MutableList<ByteBuf?> = mutableListOf()
    var dataSource: ByteBuf? = null

    val isAttachmentsNotLoaded: Boolean
        get() = hasAttachments && attachments.size != attachmentsCount

    private var attachmentsCount = 0
    val hasAttachments: Boolean
        get() = attachmentsCount != 0

    @JvmName("getTypedData")
    fun <T> getData(): T {
        return data as T
    }

    fun withNsp(namespace: String?): Packet? {
        return if (nsp.equals(namespace, ignoreCase = true)) {
            this
        } else {
            Packet(type).also {
                it.ackId = this.ackId
                it.data = this.data
                it.dataSource = this.dataSource
                it.name = this.name
                it.subType = this.subType
                it.nsp = this.nsp
                it.attachments = this.attachments
                it.attachmentsCount = this.attachmentsCount
            }
        }
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

    override fun toString(): String {
        return "Packet [type=$type, ackId=$ackId, data=$data]"
    }
}