
package com.gribouille.socketio.protocol

import com.gribouille.socketio.namespace.Namespace
import java.io.Serializable

class Packet : Serializable {
    var type: PacketType? = null
        private set
    var subType: PacketType? = null
    var ackId: Long? = null
    var name: String? = null
    var nsp: String = Namespace.DEFAULT_NAME
    private var data: Any? = null
    private var dataSource: ByteBuf? = null
    private var attachmentsCount = 0
    private var attachments: List<ByteBuf?> = emptyList<ByteBuf>()

    protected constructor()
    constructor(type: PacketType?) : super() {
        this.type = type
    }

    fun setData(data: Any?) {
        this.data = data
    }

    /**
     * Get packet data
     *
     * @param <T> the type data
     *
     * <pre>
     * @return **json object** for PacketType.JSON type
     * **message** for PacketType.MESSAGE type
    </pre> *
    </T> */
    fun <T> getData(): T? {
        return data as T?
    }

    /**
     * Creates a copy of #[Packet] with new namespace set
     * if it differs from current namespace.
     * Otherwise, returns original object unchanged
     *
     * @param namespace
     * @return packet
     */
    fun withNsp(namespace: String): Packet {
        return if (nsp.equals(namespace, ignoreCase = true)) {
            this
        } else {
            val newPacket = Packet(type)
            newPacket.ackId = ackId
            newPacket.setData(data)
            newPacket.setDataSource(dataSource)
            newPacket.name = name
            newPacket.subType = subType
            newPacket.nsp = namespace
            newPacket.attachments = attachments
            newPacket.attachmentsCount = attachmentsCount
            newPacket
        }
    }

    val isAckRequested: Boolean
        get() = ackId != null

    fun initAttachments(attachmentsCount: Int) {
        this.attachmentsCount = attachmentsCount
        attachments = ArrayList<ByteBuf>(attachmentsCount)
    }

    fun addAttachment(attachment: ByteBuf?) {
        if (attachments.size < attachmentsCount) {
            attachments.add(attachment)
        }
    }

    fun getAttachments(): List<ByteBuf?> {
        return attachments
    }

    fun hasAttachments(): Boolean {
        return attachmentsCount != 0
    }

    val isAttachmentsLoaded: Boolean
        get() = attachments.size == attachmentsCount

    fun getDataSource(): ByteBuf? {
        return dataSource
    }

    fun setDataSource(dataSource: ByteBuf?) {
        this.dataSource = dataSource
    }

    override fun toString(): String {
        return "Packet [type=$type, ackId=$ackId]"
    }

    companion object {
        private const val serialVersionUID = 4560159536486711426L
    }
}