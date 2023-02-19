
package com.gribouille.socketio.handler

import com.gribouille.socketio.AuthorizationListener
import com.gribouille.socketio.HandshakeData

class SuccessAuthorizationListener : AuthorizationListener {
    override fun isAuthorized(data: HandshakeData): Boolean {
        return true
    }
}