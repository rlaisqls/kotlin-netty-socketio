
package com.gribouille.socketio.handler

import com.gribouille.socketio.HandshakeData

class SuccessAuthorizationListener : AuthorizationListener {
    fun isAuthorized(data: HandshakeData?): Boolean {
        return true
    }
}