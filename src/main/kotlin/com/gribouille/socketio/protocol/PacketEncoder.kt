
package com.gribouille.socketio.protocol

import com.gribouille.socketio.configuration
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import io.netty.handler.codec.base64.Base64
import io.netty.handler.codec.base64.Base64Dialect
import io.netty.util.CharsetUtil
import java.io.IOException
import java.lang.Math.log10
import java.util.*

interface PacketEncoder {
    fun allocateBuffer(allocator: ByteBufAllocator): ByteBuf
    fun encodeJsonP(jsonpIndex: Int?, packets: Queue<Packet?>, out: ByteBuf, allocator: ByteBufAllocator, limit: Int)
    fun encodePackets(packets: Queue<Packet?>, buffer: ByteBuf, allocator: ByteBufAllocator, limit: Int)
    fun encodePacket(packet: Packet, buffer: ByteBuf, allocator: ByteBufAllocator, binary: Boolean)
    fun find(buffer: ByteBuf, searchValue: ByteBuf): Int
}

internal val packetEncoder = object : PacketEncoder {
    private val jsonSupport = configuration.jsonSupport

    override fun allocateBuffer(allocator: ByteBufAllocator): ByteBuf {
        return if (configuration.isPreferDirectBuffer) {
            allocator.ioBuffer()
        } else allocator.heapBuffer()
    }

    @Throws(IOException::class)
    override fun encodeJsonP(jsonpIndex: Int?, packets: Queue<Packet?>, out: ByteBuf, allocator: ByteBufAllocator, limit: Int) {
        val jsonpMode = jsonpIndex != null
        val buf: ByteBuf = allocateBuffer(allocator)
        var i = 0
        while (true) {
            val packet = packets.poll()
            if (packet == null || i == limit) {
                break
            }
            val packetBuf = allocateBuffer(allocator)
            encodePacket(packet, packetBuf, allocator, true)
            val packetSize: Int = packetBuf.writerIndex()
            with(buf) {
                writeBytes(toChars(packetSize.toLong()))
                writeBytes(B64_DELIMITER)
                writeBytes(packetBuf)
            }
            packetBuf.release()
            i++
            for (attachment in packet.attachments) {
                val encodedBuf = Base64.encode(attachment, Base64Dialect.URL_SAFE)
                with(buf) {
                    writeBytes(toChars((encodedBuf.readableBytes() + 2).toLong()))
                    writeBytes(B64_DELIMITER)
                    writeBytes(BINARY_HEADER)
                    writeBytes(encodedBuf)
                }
            }
        }
        if (jsonpMode) {
            with(out) {
                writeBytes(JSONP_HEAD)
                writeBytes(toChars(jsonpIndex!!.toLong()))
                writeBytes(JSONP_START)
            }
        }
        processUtf8(buf, out, jsonpMode)
        buf.release()
        if (jsonpMode) {
            out.writeBytes(JSONP_END)
        }
    }

    private fun processUtf8(`in`: ByteBuf, out: ByteBuf, jsonpMode: Boolean) {
        while (`in`.isReadable) {
            val value: Short = (`in`.readByte().toInt() and 0xFF).toShort()
            if (value.toInt() ushr 7 == 0) {
                if (jsonpMode && (value == '\\'.code.toShort() || value == '\''.code.toShort())) {
                    out.writeByte('\\'.code)
                }
                out.writeByte(value.toInt())
            } else {
                out.writeByte(value.toInt() ushr 6 or 0xC0)
                out.writeByte(value.toInt() and 0x3F or 0x80)
            }
        }
    }

    @Throws(IOException::class)
    override fun encodePackets(packets: Queue<Packet?>, buffer: ByteBuf, allocator: ByteBufAllocator, limit: Int) {
        var i = 0
        while (true) {
            val packet = packets.poll()
            if (packet == null || i == limit) {
                break
            }
            encodePacket(packet, buffer, allocator, false)
            i++
            for (attachment in packet.attachments) {
                buffer.writeByte(1)
                buffer.writeBytes(longToBytes((attachment!!.readableBytes() + 1).toLong()))
                buffer.writeByte(0xff)
                buffer.writeByte(4)
                buffer.writeBytes(attachment)
            }
        }
    }

    private fun toChar(number: Int): Byte {
        return (number xor 0x30).toByte()
    }

    @Throws(IOException::class)
    override fun encodePacket(packet: Packet, buffer: ByteBuf, allocator: ByteBufAllocator, binary: Boolean) {
        var buf: ByteBuf = buffer
        if (!binary) {
            buf = allocateBuffer(allocator)
        }
        val type = toChar(packet.type!!.value)
        buf.writeByte(type.toInt())
        try {
            when (packet.type) {
                PacketType.PONG -> {
                    buf.writeBytes(packet.getData<Any>().toString().toByteArray(CharsetUtil.UTF_8))
                }

                PacketType.OPEN -> {
                    val out = ByteBufOutputStream(buf)
                    jsonSupport.writeValue(out, packet.getData())
                }

                PacketType.MESSAGE -> {
                    var encBuf: ByteBuf? = null
                    if (packet.subType == PacketType.ERROR) {
                        encBuf = allocateBuffer(allocator)
                        val out = ByteBufOutputStream(encBuf)
                        jsonSupport.writeValue(out, packet.getData())
                    }
                    if (packet.subType == PacketType.EVENT
                        || packet.subType == PacketType.ACK
                    ) {
                        val values: MutableList<Any?> = ArrayList()
                        if (packet.subType == PacketType.EVENT) {
                            values.add(packet.name)
                        }
                        encBuf = allocateBuffer(allocator)
                        val arg = packet.getData<Any>()
                        values.add(arg)
                        val out = ByteBufOutputStream(encBuf)
                        jsonSupport.writeValue(out, values)
                        if (jsonSupport.arrays.isNotEmpty()) {
                            packet.initAttachments(jsonSupport.arrays.size)
                            for (array in jsonSupport.arrays) {
                                packet.addAttachment(Unpooled.wrappedBuffer(array))
                            }
                            packet.subType =
                                if (packet.subType == PacketType.ACK) PacketType.BINARY_ACK else PacketType.BINARY_EVENT
                        }
                    }
                    val subType = toChar(packet.subType!!.value)
                    buf.writeByte(subType.toInt())
                    if (packet.hasAttachments) {
                        val ackId = toChars(packet.attachments.size.toLong())
                        buf.writeBytes(ackId)
                        buf.writeByte('-'.code)
                    }
                    if (packet.subType == PacketType.CONNECT) {
                        if (packet.nsp.isNotEmpty()) {
                            buf.writeBytes(packet.nsp.toByteArray(CharsetUtil.UTF_8))
                        }
                    } else {
                        if (packet.nsp.isNotEmpty()) {
                            buf.writeBytes(packet.nsp.toByteArray(CharsetUtil.UTF_8))
                            buf.writeByte(','.code)
                        }
                    }
                    if (packet.ackId != null) {
                        val ackId = toChars(packet.ackId!!)
                        buf.writeBytes(ackId)
                    }
                    if (encBuf != null) {
                        buf.writeBytes(encBuf)
                        encBuf.release()
                    }
                }

                else -> {}
            }
        } finally {
            // we need to write a buffer in any case
            if (!binary) {
                buffer.writeByte(0)
                val length: Int = buf.writerIndex()
                buffer.writeBytes(longToBytes(length.toLong()))
                buffer.writeByte(0xff)
                buffer.writeBytes(buf)
                buf.release()
            }
        }
    }


    private val BINARY_HEADER = "b4".toByteArray(CharsetUtil.UTF_8)
    private val B64_DELIMITER = byteArrayOf(':'.code.toByte())
    private val JSONP_HEAD = "___eio[".toByteArray(CharsetUtil.UTF_8)
    private val JSONP_START = "]('".toByteArray(CharsetUtil.UTF_8)
    private val JSONP_END = "');".toByteArray(CharsetUtil.UTF_8)
    private val DigitTens = charArrayOf(
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1',
        '1', '1', '1', '1', '1', '1', '2', '2', '2', '2', '2', '2', '2', '2', '2', '2', '3', '3', '3',
        '3', '3', '3', '3', '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4', '4', '4', '5', '5',
        '5', '5', '5', '5', '5', '5', '5', '5', '6', '6', '6', '6', '6', '6', '6', '6', '6', '6', '7',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9'
    )
    private val DigitOnes = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3',
        '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2',
        '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1',
        '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
        '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    )
    private val digits = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
        'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
        'y', 'z'
    )
    private val sizeTable = intArrayOf(9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Int.MAX_VALUE)

    // Requires positive x
    private fun stringSize(x: Long): Int {
        var i = 0
        while (true) {
            if (x <= sizeTable[i]) return i + 1
            i++
        }
    }

    private fun getChars(i: Long, index: Int, buf: ByteArray) {
        var i = i
        var q: Long
        var r: Long
        var charPos = index
        var sign: Byte = 0
        if (i < 0) {
            sign = '-'.code.toByte()
            i = -i
        }

        // Generate two digits per iteration
        while (i >= 65536) {
            q = i / 100
            // really: r = i - (q * 100);
            r = i - ((q shl 6) + (q shl 5) + (q shl 2))
            i = q
            buf[--charPos] = DigitOnes[r.toInt()].code.toByte()
            buf[--charPos] = DigitTens[r.toInt()].code.toByte()
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        while (true) {
            q = i * 52429 ushr 16 + 3
            r = i - ((q shl 3) + (q shl 1)) // r = i-(q*10) ...
            buf[--charPos] = digits[r.toInt()].code.toByte()
            i = q
            if (i == 0L) break
        }
        if (sign.toInt() != 0) {
            buf[--charPos] = sign
        }
    }

    private fun toChars(i: Long): ByteArray {
        val size = if (i < 0) stringSize(-i) + 1 else stringSize(i)
        val buf = ByteArray(size)
        getChars(i, size, buf)
        return buf
    }

    private fun longToBytes(number: Long): ByteArray {
        // TODO optimize
        var number = number
        val length = (log10(number.toDouble()) + 1).toInt()
        val res = ByteArray(length)
        var i = length
        while (number > 0) {
            res[--i] = (number % 10).toByte()
            number /= 10
        }
        return res
    }

    override fun find(buffer: ByteBuf, searchValue: ByteBuf): Int {
        for (i in buffer.readerIndex() until buffer.readerIndex() + buffer.readableBytes()) {
            if (isValueFound(buffer, i, searchValue)) {
                return i
            }
        }
        return -1
    }

    private fun isValueFound(buffer: ByteBuf, index: Int, search: ByteBuf): Boolean {
        for (i in 0 until search.readableBytes()) {
            if (buffer.getByte(index + i) != search.getByte(i)) {
                return false
            }
        }
        return true
    }
}