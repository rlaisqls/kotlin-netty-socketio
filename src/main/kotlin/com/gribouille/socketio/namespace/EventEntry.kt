
package com.gribouille.socketio.namespace

import com.gribouille.socketio.listener.DataListener
import java.util.*

class EventEntry<T> {
    private val listeners: Queue<DataListener<T>> = ConcurrentLinkedQueue<DataListener<T>>()
    fun addListener(listener: DataListener<T>) {
        listeners.add(listener)
    }

    fun getListeners(): Queue<DataListener<T>> {
        return listeners
    }
}