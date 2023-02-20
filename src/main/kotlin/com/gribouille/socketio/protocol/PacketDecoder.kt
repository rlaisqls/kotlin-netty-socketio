
package com.gribouille.socketio.protocol

import com.gribouille.socketio.AckCallback
import com.gribouille.socketio.ack.AckManager
import com.gribouille.socketio.handler.ClientHead
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.handler.codec.base64.Base64
import io.netty.util.CharsetUtil
import java.io.IOException
import java.net.URLDecoder
import java.util.LinkedList

class PacketDecoder(private val jsonSupport: JsonSupport, ackManager: AckManager) {

    private val utf8scanner = UTF8CharsScanner()
    private val QUOTES: ByteBuf = Unpooled.copiedBuffer("\"", CharsetUtil.UTF_8)
    private val ackManager: AckManager

    init {
        this.ackManager = ackManager
    }

    private fun isStringPacket(content: ByteBuf): Boolean {
        return content.getByte(content.readerIndex()).toInt() == 0x0
    }

    // TODO optimize
    @Throws(IOException::class)
    fun preprocessJson(jsonIndex: Int?, content: ByteBuf): ByteBuf {
        var packet: String = URLDecoder.decode(content.toString(CharsetUtil.UTF_8), CharsetUtil.UTF_8.name())
        if (jsonIndex != null) {
            /**
             * double escaping is required for escaped new lines because unescaping of new lines can be done safely on server-side
             * (c) socket.io.js
             *
             * @see https://github.com/Automattic/socket.io-client/blob/1.3.3/socket.io.js.L2682
             */
            packet = packet.replace("\\\\n", "\\n")

            // skip "d="
            packet = packet.substring(2)
        }
        return Unpooled.wrappedBuffer(packet.toByteArray(CharsetUtil.UTF_8))
    }

    // fastest way to parse chars to int
    private fun readLong(chars: ByteBuf, length: Int): Long {
        var result: Long = 0
        for (i in chars.readerIndex() until chars.readerIndex() + length) {
            var digit: Int = chars.getByte(i).toInt() and 0xF
            for (j in 0 until chars.readerIndex() + length - 1 - i) {
                digit *= 10
            }
            result += digit.toLong()
        }
        chars.readerIndex(chars.readerIndex() + length)
        return result
    }

    private fun readType(buffer: ByteBuf): PacketType {
        val typeId: Int = buffer.readByte().toInt() and 0xF
        return PacketType.Companion.valueOf(typeId)
    }

    private fun readInnerType(buffer: ByteBuf): PacketType {
        val typeId: Int = buffer.readByte().toInt() and 0xF
        return PacketType.Companion.valueOfInner(typeId)
    }

    private fun hasLengthHeader(buffer: ByteBuf): Boolean {
        for (i in 0 until Math.min(buffer.readableBytes(), 10)) {
            val b: Byte = buffer.getByte(buffer.readerIndex() + i)
            if (b == ':'.code.toByte() && i > 0) {
                return true
            }
            if (b > 57 || b < 48) {
                return false
            }
        }
        return false
    }

    @Throws(IOException::class)
    fun decodePackets(buffer: ByteBuf, client: ClientHead): Packet {
        if (isStringPacket(buffer)) {
            // TODO refactor
            val maxLength = Math.min(buffer.readableBytes(), 10)
            var headEndIndex: Int = buffer.bytesBefore(maxLength, (-1).toByte())
            if (headEndIndex == -1) {
                headEndIndex = buffer.bytesBefore(maxLength, 0x3f.toByte())
            }
            val len = readLong(buffer, headEndIndex).toInt()
            val frame: ByteBuf = buffer.slice(buffer.readerIndex() + 1, len)
            // skip this frame
            buffer.readerIndex(buffer.readerIndex() + 1 + len)
            return decode(client, frame)
        } else if (hasLengthHeader(buffer)) {
            // TODO refactor
            val lengthEndIndex: Int = buffer.bytesBefore(':'.code.toByte())
            val lenHeader = readLong(buffer, lengthEndIndex).toInt()
            val len = utf8scanner.getActualLength(buffer, lenHeader)
            val frame: ByteBuf = buffer.slice(buffer.readerIndex() + 1, len)
            // skip this frame
            buffer.readerIndex(buffer.readerIndex() + 1 + len)
            return decode(client, frame)
        }
        return decode(client, buffer)
    }

    private fun readString(frame: ByteBuf, size: Int = frame.readableBytes()): String {
        val bytes = ByteArray(size)
        frame.readBytes(bytes)
        return String(bytes, CharsetUtil.UTF_8)
    }

    private fun decode(head: ClientHead, frame: ByteBuf): Packet {
        if (frame.getByte(0) == 'b'.code.toByte() && frame.getByte(1) == '4'.code.toByte() || frame.getByte(0)
                .toInt() == 4 || frame.getByte(0).toInt() == 1
        ) {
            return parseBinary(head, frame)
        }
        val type = readType(frame)
        val packet = Packet(type)
        if (type == PacketType.PING) {
            packet.data = readString(frame)
            return packet
        }
        if (!frame.isReadable()) {
            return packet
        }
        val innerType = readInnerType(frame)
        packet.subType = innerType
        parseHeader(frame, packet, innerType)
        parseBody(head, frame, packet)
        return packet
    }

    private fun parseHeader(frame: ByteBuf, packet: Packet, innerType: PacketType) {
        var endIndex: Int = frame.bytesBefore('['.code.toByte())
        if (endIndex <= 0) {
            return
        }
        val attachmentsDividerIndex: Int = frame.bytesBefore(endIndex, '-'.code.toByte())
        val hasAttachments = attachmentsDividerIndex != -1
        if (hasAttachments && (PacketType.BINARY_EVENT == innerType || PacketType.BINARY_ACK == innerType)) {
            val attachments = readLong(frame, attachmentsDividerIndex).toInt()
            packet.initAttachments(attachments)
            frame.readerIndex(frame.readerIndex() + 1)
            endIndex -= attachmentsDividerIndex + 1
        }
        if (endIndex == 0) {
            return
        }

        // TODO optimize
        val hasNsp = frame.bytesBefore(endIndex, ','.code.toByte()) != -1
        if (hasNsp) {
            val nspAckId = readString(frame, endIndex)
            val parts = nspAckId.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val nsp = parts[0]
            packet.nsp = nsp
            if (parts.size > 1) {
                val ackId = parts[1]
                packet.ackId = java.lang.Long.valueOf(ackId)
            }
        } else {
            val ackId = readLong(frame, endIndex)
            packet.ackId = ackId
        }
    }

    private fun parseBinary(head: ClientHead, frame: ByteBuf): Packet {
        var frame: ByteBuf = frame
        if (frame.getByte(0).toInt() == 1) {
            frame.readByte()
            val headEndIndex: Int = frame.bytesBefore((-1).toByte())
            val len = readLong(frame, headEndIndex).toInt()
            val oldFrame: ByteBuf = frame
            frame = frame.slice(oldFrame.readerIndex() + 1, len)
            oldFrame.readerIndex(oldFrame.readerIndex() + 1 + len)
        }
        if (frame.getByte(0) == 'b'.code.toByte() && frame.getByte(1) == '4'.code.toByte()) {
            frame.readShort()
        } else if (frame.getByte(0).toInt() == 4) {
            frame.readByte()
        }
        val binaryPacket = head.lastBinaryPacket
        if (binaryPacket != null) {
            if (frame.getByte(0) == 'b'.code.toByte() && frame.getByte(1) == '4'.code.toByte()) {
                binaryPacket.addAttachment(Unpooled.copiedBuffer(frame))
            } else {
                val attachBuf: ByteBuf = Base64.encode(frame)
                binaryPacket.addAttachment(Unpooled.copiedBuffer(attachBuf))
                attachBuf.release()
            }
            frame.readerIndex(frame.readerIndex() + frame.readableBytes())
            if (binaryPacket.isAttachmentsLoaded) {
                val slices = LinkedList<ByteBuf>()
                val source: ByteBuf = binaryPacket.dataSource!!
                for (i in binaryPacket.attachments.indices) {
                    val attachment: ByteBuf = binaryPacket.attachments[i]!!
                    var scanValue: ByteBuf =
                        Unpooled.copiedBuffer("{\"_placeholder\":true,\"num\":$i}", CharsetUtil.UTF_8)
                    var pos: Int = PacketEncoder.Companion.find(source, scanValue)
                    if (pos == -1) {
                        scanValue = Unpooled.copiedBuffer("{\"num\":$i,\"_placeholder\":true}", CharsetUtil.UTF_8)
                        pos = PacketEncoder.Companion.find(source, scanValue)
                        check(pos != -1) { "Can't find attachment by index: $i in packet source" }
                    }
                    val prefixBuf: ByteBuf = source.slice(source.readerIndex(), pos - source.readerIndex())
                    slices.add(prefixBuf)
                    slices.add(QUOTES)
                    slices.add(attachment)
                    slices.add(QUOTES)
                    source.readerIndex(pos + scanValue.readableBytes())
                }
                slices.add(source.slice())
                val compositeBuf: ByteBuf = Unpooled.wrappedBuffer(*slices.toTypedArray<ByteBuf>())
                parseBody(head, compositeBuf, binaryPacket)
                head.lastBinaryPacket = null
                return binaryPacket
            }
        }
        return Packet(PacketType.MESSAGE)
    }

    private fun parseBody(head: ClientHead, frame: ByteBuf, packet: Packet) {
        if (packet.type == PacketType.MESSAGE) {
            if (packet.subType == PacketType.CONNECT
                || packet.subType == PacketType.DISCONNECT
            ) {
                packet.nsp = readNamespace(frame)
            }
            if (packet.hasAttachments() && !packet.isAttachmentsLoaded) {
                packet.dataSource = Unpooled.copiedBuffer(frame)
                frame.readerIndex(frame.readableBytes())
                head.lastBinaryPacket = packet
            }
            if (packet.hasAttachments() && !packet.isAttachmentsLoaded) {
                return
            }
            when (packet.subType) {
                PacketType.ACK, PacketType.BINARY_ACK -> {
                    val `in` = ByteBufInputStream(frame)
                    val callback = ackManager.getCallback(head.sessionId!!, packet.ackId!!)!!
                    val args = jsonSupport.readAckArgs(`in`, callback)
                    packet.data = args.args
                }
                PacketType.EVENT, PacketType.BINARY_EVENT -> {
                    val `in` = ByteBufInputStream(frame)
                    val event = jsonSupport.readValue(packet.nsp, `in`, Event::class.java)
                    packet.name = event.name
                    packet.data = event.args
                }
                else -> {}
            }
        }
    }

    private fun readNamespace(frame: ByteBuf): String {
        /**
         * namespace post request with url queryString, like
         * /message?a=1,
         * /message,
         */
        val buffer: ByteBuf = frame.slice()
        // skip this frame
        frame.readerIndex(frame.readerIndex() + frame.readableBytes())
        var endIndex: Int = buffer.bytesBefore('?'.code.toByte())
        if (endIndex > 0) {
            return readString(buffer, endIndex)
        }
        endIndex = buffer.bytesBefore(','.code.toByte())
        return if (endIndex > 0) {
            readString(buffer, endIndex)
        } else readString(buffer)
    }
}