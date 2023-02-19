package com.gribouille.socketio.misc

import java.util.*

class IterableCollection<T>(
    private val iterable: CompositeIterable<T>
) : AbstractCollection<T>() {

    override fun iterator(): MutableIterator<T> {
        return CompositeIterable(iterable).iterator()
    }

    override val size: Int
        get() = let {
            val iterator: Iterator<T> = CompositeIterable(iterable).iterator()
            var count = 0
            while (iterator.hasNext()) {
                iterator.next()
                count++
            }
            return count
        }
}