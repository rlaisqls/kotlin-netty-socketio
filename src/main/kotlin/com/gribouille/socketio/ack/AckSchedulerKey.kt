
package com.gribouille.socketio.ack

import com.gribouille.socketio.scheduler.SchedulerKey
import java.util.*

class AckSchedulerKey(type: Type?, sessionId: UUID?, val index: Long) : SchedulerKey(type, sessionId) {

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + (index xor (index ushr 32)).toInt()
        return result
    }

    override fun equals(obj: Any): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as AckSchedulerKey
        return if (index != other.index) false else true
    }
}