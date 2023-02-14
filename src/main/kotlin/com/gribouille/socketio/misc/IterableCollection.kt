
package com.gribouille.socketio.misc

import java.util.*

class IterableCollection<T>(iterable: CompositeIterable<T?>) : AbstractCollection<T?>() {
    private val iterable: CompositeIterable<T>

    constructor(iterable: Iterable<T>?) : this(CompositeIterable<Any?>(iterable))

    init {
        this.iterable = iterable
    }

    override fun iterator(): MutableIterator<T?> {
        return CompositeIterable(iterable).iterator()
    }

    override fun size(): Int {
        val iterator: Iterator<T?> = CompositeIterable(iterable).iterator()
        var count = 0
        while (iterator.hasNext()) {
            iterator.next()
            count++
        }
        return count
    }
}