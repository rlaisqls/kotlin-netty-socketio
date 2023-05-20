
package com.gribouille.socketio.protocol

import java.util.*

class AuthPacket(
    val sid: UUID,
    val upgrades: Array<String>,
    val pingInterval: Int,
    val pingTimeout: Int,
)