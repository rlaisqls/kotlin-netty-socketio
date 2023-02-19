
package com.gribouille.socketio

import io.netty.handler.codec.http.HttpHeaders
import java.io.Serializable
import java.net.InetSocketAddress
import java.util.*

class HandshakeData (
    val httpHeaders: HttpHeaders,
    val urlParams: Map<String, List<String>>,
    val address: InetSocketAddress,
    val local: InetSocketAddress?,
    val url: String,
    val xdomain: Boolean
) : Serializable {

    fun getSingleUrlParam(name: String): String? {
        val values = urlParams[name]
        if (values != null && values.size == 1)
            return values.iterator().next()

        return null
    }

    companion object {
        private const val serialVersionUID = 1196350300161819978L
    }
}