
package com.gribouille.socketio.namespace

import com.gribouille.socketio.listener.DataListener
import java.util.concurrent.ConcurrentLinkedQueue

class EventEntry {
    val listeners = ConcurrentLinkedQueue<DataListener>()
    fun addListener(listener: DataListener) {
        listeners.add(listener)
    }
}