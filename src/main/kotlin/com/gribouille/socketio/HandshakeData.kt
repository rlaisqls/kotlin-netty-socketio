/**
 * Copyright (c) 2012-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.gribouille

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