
package com.gribouille.socketio.namespace

import com.gribouille.socketio.listener.DataListener
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class EventEntry {
    val listeners: Queue<DataListener> = ConcurrentLinkedQueue()
    fun addListener(listener: DataListener) {
        listeners.add(listener)
    }
}