
package com.gribouille.socketio.scheduler

open class SchedulerKey(private val type: Type?, private val sessionId: Any?) {
    enum class Type {
        PING, PING_TIMEOUT, ACK_TIMEOUT, UPGRADE_TIMEOUT
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = (prime * result
                + (sessionId?.hashCode() ?: 0))
        result = prime * result + (type?.hashCode() ?: 0)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as SchedulerKey
        if (sessionId == null) {
            if (other.sessionId != null) return false
        } else if (sessionId != other.sessionId) return false
        return if (type != other.type) false else true
    }
}