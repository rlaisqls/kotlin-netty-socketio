
package com.gribouille.socketio.ack

import com.gribouille.socketio.scheduler.SchedulerKey
import java.util.*

class AckSchedulerKey(
    type: Type?,
    sessionId: UUID?,
    val index: Long
) : SchedulerKey(type, sessionId)