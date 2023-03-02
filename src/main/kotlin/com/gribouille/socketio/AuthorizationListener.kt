
package com.gribouille.socketio

interface AuthorizationListener {
    fun isAuthorized(data: HandshakeData): Boolean
}