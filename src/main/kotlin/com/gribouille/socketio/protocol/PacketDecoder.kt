
package com.gribouille.socketio.protocol

import com.gribouille.socketio.ack.ackManager
import com.gribouille.socketio.handler.ClientHead
import com.gribouille.socketio.jsonSupport
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.handler.codec.base64.Base64
import io.netty.util.CharsetUtil
import java.io.IOException
import java.net.URLDecoder
import java.util.LinkedList
import kotlin.math.min

interface PackerDecoder {
    fun preprocessJson(jsonIndex: Int?, content: ByteBuf): ByteBuf
    fun decodePackets(buffer: ByteBuf, client: ClientHead): Packet
}

internal val packetDecoder = object : PackerDecoder {

    private fun isStringPacket(content: ByteBuf): Boolean {
        return content.getByte(content.readerIndex()).toInt() == 0x0
    }

    // TODO optimize
    @Throws(IOException::class)
    override fun preprocessJson(jsonIndex: Int?, content: ByteBuf): ByteBuf {
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
        val result = (chars.readerIndex() until chars.readerIndex() + length)
            .sumOf { i ->
                var digit = chars.getByte(i).toInt() and 0xF
                for (j in 0 until chars.readerIndex() + length - 1 - i) {
                    digit *= 10
                }
                digit.toLong()
            }

        chars.readerIndex(chars.readerIndex() + length)
        return result
    }

    private fun readType(buffer: ByteBuf): PacketType {
        val typeId = buffer.readByte().toInt() and 0xF
        return PacketType.valueOf(typeId)
    }

    private fun readInnerType(buffer: ByteBuf): PacketType {
        val typeId = buffer.readByte().toInt() and 0xF
        return PacketType.valueOfInner(typeId)
    }

    private fun hasLengthHeader(buffer: ByteBuf): Boolean {
        for (i in 0 until min(buffer.readableBytes(), 10)) {
            val b = buffer.getByte(buffer.readerIndex() + i)
            if (b == ':'.code.toByte() && i > 0) {
                return true
            } else if (b > 57 || b < 48) {
                return false
            }
        }
        return false
    }

    @Throws(IOException::class)
    override fun decodePackets(buffer: ByteBuf, client: ClientHead): Packet {

        if (isStringPacket(buffer)) {

            val maxLength = buffer.readableBytes().coerceAtMost(10)
            val headEndIndex = buffer.bytesBefore(maxLength, (-1).toByte()).run {
                if (this == -1) {
                    buffer.bytesBefore(maxLength, 0x3f.toByte())
                } else this
            }

            val len = readLong(buffer, headEndIndex).toInt()
            val frame = buffer.slice(buffer.readerIndex() + 1, len)
            // skip this frame
            buffer.readerIndex(buffer.readerIndex() + 1 + len)
            return decode(client, frame)
        } else if (hasLengthHeader(buffer)) {

            val lenHeader = readLong(
                chars = buffer,
                length = buffer.bytesBefore(':'.code.toByte())
            ).toInt()
            val len = UTF8CharsScanner.getActualLength(buffer, lenHeader)
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
        if (frame.getByte(0) == 'b'.code.toByte() &&
            frame.getByte(1) == '4'.code.toByte() ||
            frame.getByte(0).toInt() == 4 ||
            frame.getByte(0).toInt() == 1
        ) {
            return parseBinary(head, frame)
        }
        val type = readType(frame)
        val packet = Packet(type)
        if (type == PacketType.PING) {
            packet.data = readString(frame)
            return packet
        }
        if (!frame.isReadable) {
            return packet
        }
        val innerType = readInnerType(frame)
        packet.subType = innerType
        parseHeader(frame, packet, innerType)
        parseBody(head, frame, packet)

        return packet
    }

    private fun parseHeader(frame: ByteBuf, packet: Packet, innerType: PacketType) {
        var endIndex = frame.bytesBefore('['.code.toByte()).apply {
            if (this <= 0) {
                return
            }
        }
        val attachmentsDividerIndex = frame.bytesBefore(endIndex, '-'.code.toByte())
        val hasAttachments = attachmentsDividerIndex != -1

        if (hasAttachments && (innerType == PacketType.BINARY_EVENT || innerType == PacketType.BINARY_ACK)) {
            packet.initAttachments(
                attachmentsCount = readLong(frame, attachmentsDividerIndex).toInt()
            )
            frame.readerIndex(frame.readerIndex() + 1)
            endIndex -= attachmentsDividerIndex + 1
        }
        if (endIndex == 0) {
            return
        }

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
            packet.ackId = readLong(frame, endIndex)
        }
    }

    private fun parseBinary(head: ClientHead, f: ByteBuf): Packet {

        var frame: ByteBuf = f
        if (frame.getByte(0).toInt() == 1) {
            val len = frame.run {
                readByte()
                val headEndIndex: Int = bytesBefore((-1).toByte())
                readLong(this, headEndIndex).toInt()
            }
            val oldFrame: ByteBuf = frame
            frame = frame.slice(oldFrame.readerIndex() + 1, len)
            oldFrame.readerIndex(oldFrame.readerIndex() + 1 + len)
        }

        if (frame.getByte(0) == 'b'.code.toByte() && frame.getByte(1) == '4'.code.toByte()) {
            frame.readShort()
        } else if (frame.getByte(0).toInt() == 4) {
            frame.readByte()
        }

        head.lastBinaryPacket?.let { binaryPacket ->

            if (frame.getByte(0) == 'b'.code.toByte() && frame.getByte(1) == '4'.code.toByte()) {
                binaryPacket.addAttachment(Unpooled.copiedBuffer(frame))
            } else {
                val attachBuf: ByteBuf = Base64.encode(frame)
                binaryPacket.addAttachment(Unpooled.copiedBuffer(attachBuf))
                attachBuf.release()
            }
            frame.readerIndex(frame.readerIndex() + frame.readableBytes())

            if (binaryPacket.isAttachmentsNotLoaded) {
                val slices = LinkedList<ByteBuf>()
                val source = binaryPacket.dataSource!!

                for (i in binaryPacket.attachments.indices) {
                    val attachment = binaryPacket.attachments[i]!!
                    var scanValue = Unpooled.copiedBuffer("{\"_placeholder\":true,\"num\":$i}", CharsetUtil.UTF_8)
                    var pos = packetEncoder.find(source, scanValue)
                    if (pos == -1) {
                        scanValue = Unpooled.copiedBuffer("{\"num\":$i,\"_placeholder\":true}", CharsetUtil.UTF_8)
                        pos = packetEncoder.find(source, scanValue)
                        check(pos != -1) { "Can't find attachment by index: $i in packet source" }
                    }
                    val prefixBuf = source.slice(source.readerIndex(), pos - source.readerIndex())
                    slices.add(prefixBuf)
                    slices.add(QUOTES)
                    slices.add(attachment)
                    slices.add(QUOTES)
                    source.readerIndex(pos + scanValue.readableBytes())
                }
                slices.add(source.slice())
                parseBody(
                    head = head,
                    frame = Unpooled.wrappedBuffer(*slices.toTypedArray()),
                    packet = binaryPacket
                )
                head.lastBinaryPacket = null
                return binaryPacket
            }
        }
        return Packet(PacketType.MESSAGE)
    }

    private fun parseBody(head: ClientHead, frame: ByteBuf, packet: Packet) {
        if (packet.type == PacketType.MESSAGE) {

            if (packet.subType == PacketType.CONNECT || packet.subType == PacketType.DISCONNECT) {
                packet.nsp = readNamespace(frame)
            }
            if (packet.isAttachmentsNotLoaded) {
                packet.dataSource = Unpooled.copiedBuffer(frame)
                frame.readerIndex(frame.readableBytes())
                head.lastBinaryPacket = packet
                if (packet.isAttachmentsNotLoaded) {
                    return
                }
            }

            when (packet.subType) {
                PacketType.ACK, PacketType.BINARY_ACK -> {
                    val inputStream = ByteBufInputStream(frame)
                    val callback = ackManager.getCallback(head.sessionId!!, packet.ackId!!)!!
                    val args = jsonSupport.readAckArgs(inputStream, callback)
                    packet.data = args.args
                }
                PacketType.EVENT, PacketType.BINARY_EVENT -> {
                    val inputStream = ByteBufInputStream(frame)
                    val event = jsonSupport.readValue(packet.nsp, inputStream, Event::class.java)
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
        val buffer = frame.slice()
        // skip this frame
        frame.readerIndex(frame.readerIndex() + frame.readableBytes())
        var endIndex = buffer.bytesBefore('?'.code.toByte())
        if (endIndex > 0) {
            return readString(buffer, endIndex)
        }
        endIndex = buffer.bytesBefore(','.code.toByte())
        return if (endIndex > 0) {
            readString(buffer, endIndex)
        } else readString(buffer)
    }

    private val QUOTES: ByteBuf = Unpooled.copiedBuffer("\"", CharsetUtil.UTF_8)
}