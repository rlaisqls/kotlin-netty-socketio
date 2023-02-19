
package com.gribouille.socketio

interface AuthorizationListener {
    /**
     * Checks is client with handshake data is authorized
     *
     * @param data - handshake data
     * @return - **true** if client is authorized of **false** otherwise
     */
    fun isAuthorized(data: HandshakeData): Boolean
}