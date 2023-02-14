
package com.gribouille.socketio.protocol

import io.netty.buffer.ByteBuf

class UTF8CharsScanner {
    private fun getCharTailIndex(inputBuffer: ByteBuf, i: Int): Int {
        var i = i
        val c = inputBuffer.getByte(i).toInt() and 0xFF
        when (sInputCodesUtf8[c]) {
            2 -> i += 2
            3 -> i += 3
            4 -> i += 4
            else -> i++
        }
        return i
    }

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

    companion object {
        /**
         * Lookup table used for determining which input characters need special
         * handling when contained in text segment.
         */
        val sInputCodes: IntArray

        init {
            /*
         * 96 would do for most cases (backslash is ascii 94) but if we want to
         * do lookups by raw bytes it's better to have full table
         */
            val table = IntArray(256)
            // Control chars and non-space white space are not allowed unquoted
            for (i in 0..31) {
                table[i] = -1
            }
            // And then string end and quote markers are special too
            table['"'.code] = 1
            table['\\'.code] = 1
            sInputCodes = table
        }

        /**
         * Additionally we can combine UTF-8 decoding info into similar data table.
         */
        val sInputCodesUtf8: IntArray

        init {
            val table = IntArray(sInputCodes.size)
            System.arraycopy(sInputCodes, 0, table, 0, sInputCodes.size)
            for (c in 128..255) {
                var code: Int

                // We'll add number of bytes needed for decoding
                code = if (c and 0xE0 == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                    2
                } else if (c and 0xF0 == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                    3
                } else if (c and 0xF8 == 0xF0) {
                    // 4 bytes; double-char with surrogates and all...
                    4
                } else {
                    // And -1 seems like a good "universal" error marker...
                    -1
                }
                table[c] = code
            }
            sInputCodesUtf8 = table
        }
    }
}