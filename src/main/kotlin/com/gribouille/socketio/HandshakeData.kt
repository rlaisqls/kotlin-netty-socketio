
package com.gribouille.socketio

import io.netty.handler.codec.http.HttpHeaders
import java.io.Serializable
import java.net.InetSocketAddress
import java.util.*

class HandshakeData : Serializable {
    /**
     * Http headers sent during first client request
     *
     * @return headers
     */
    var httpHeaders: HttpHeaders? = null
        private set

    /**
     * Client network address
     *
     * @return network address
     */
    var address: InetSocketAddress? = null
        private set

    /**
     * Client connection date
     *
     * @return date
     */
    val time = Date()

    /**
     * Connection local address
     *
     * @return local address
     */
    var local: InetSocketAddress? = null
        private set

    /**
     * Url used by client during first request
     *
     * @return url
     */
    var url: String? = null
        private set

    /**
     * Url params stored in url used by client during first request
     *
     * @return map
     */
    var urlParams: Map<String?, List<String?>?>? = null
        private set
    var isXdomain = false
        private set

    // needed for correct deserialization
    constructor()
    constructor(
        headers: HttpHeaders?,
        urlParams: Map<String?, List<String?>?>?,
        address: InetSocketAddress?,
        url: String?,
        xdomain: Boolean
    ) : this(headers, urlParams, address, null, url, xdomain)

    constructor(
        headers: HttpHeaders?,
        urlParams: Map<String?, List<String?>?>?,
        address: InetSocketAddress?,
        local: InetSocketAddress?,
        url: String?,
        xdomain: Boolean
    ) : super() {
        httpHeaders = headers
        this.urlParams = urlParams
        this.address = address
        this.local = local
        this.url = url
        isXdomain = xdomain
    }

    fun getSingleUrlParam(name: String?): String? {
        val values = urlParams!![name]
        return if (values != null && values.size == 1) {
            values.iterator().next()
        } else null
    }

    companion object {
        private const val serialVersionUID = 1196350300161819978L
    }
}