
package com.gribouille.socketio.protocol

import io.netty.buffer.ByteBuf

object UTF8CharsScanner {

    fun getActualLength(inputBuffer: ByteBuf, length: Int): Int {
        var len = 0
        val start = inputBuffer.readerIndex()

        var i = inputBuffer.readerIndex()
        while (i < inputBuffer.readableBytes() + inputBuffer.readerIndex()) {
            i = getCharTailIndex(inputBuffer, i)
            len++
            if (length == len) {
                return i - start
            }
        }
        throw IllegalStateException()
    }

    private fun getCharTailIndex(inputBuffer: ByteBuf, i: Int): Int {
        var idx = i
        val c = inputBuffer.getByte(i).toInt() and 0xFF
        when (sInputCodesUtf8[c]) {
            2 -> idx += 2
            3 -> idx += 3
            4 -> idx += 4
            else -> idx++
        }
        return idx
    }

    /**
     * 텍스트 세그먼트에 특수 처리가 필요한 입력 문자가 포함될때 사용하는 lockup table
     */
    private val sInputCodes: IntArray = let {
        val table = IntArray(256)
        // Control chars와 space가 아닌 공백은 따옴표 없이 사용할 수 없다.
        for (i in 0..31) {
            table[i] = -1
        }
        // string end와 따옴표 처리
        table['"'.code] = 1
        table['\\'.code] = 1
        table
    }

    /**
     * UTF-8 decoding에 추가적으로 필요한 lockup table
     */
    private val sInputCodesUtf8: IntArray

    init {
        val table = IntArray(sInputCodes.size)
        System.arraycopy(sInputCodes, 0, table, 0, sInputCodes.size)
        for (c in 128..255) {

            // decoding에 필요한 바이트 수를 나타낸다.
            val code = if (c and 0xE0 == 0xC0) 2 // 2 bytes (0x0080 - 0x07FF)
            else if (c and 0xF0 == 0xE0) 3 // 3 bytes (0x0800 - 0xFFFF)
            else if (c and 0xF8 == 0xF0) 4 // 4 bytes; double-char with surrogates and all...
            else -1 // error marker

            table[c] = code
        }
        sInputCodesUtf8 = table
    }
}