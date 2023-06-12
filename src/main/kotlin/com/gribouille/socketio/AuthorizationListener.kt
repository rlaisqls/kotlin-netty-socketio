
package com.gribouille.socketio

interface AuthorizationListener {
    fun isAuthorized(data: HandshakeData): Boolean
}

/**
 * Default implementation of @see com.gribouille.socketio.AuthorizationListener
 */
class SuccessAuthorizationListener : AuthorizationListener {
    override fun isAuthorized(data: HandshakeData): Boolean {
        return true
    }
}