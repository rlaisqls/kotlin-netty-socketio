
package com.gribouille.socketio.ack

import java.util.UUID

class AuthPacket(
    val sid: UUID,
    val upgrades: Array<String>,
    val pingInterval: Int,
    val pingTimeout: Int
)