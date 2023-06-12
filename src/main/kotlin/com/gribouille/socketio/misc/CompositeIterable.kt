
package com.gribouille.socketio.misc

class CompositeIterable<T>(
    private val iterables: List<MutableIterable<T>>
) : Iterable<T> {

    override fun iterator(): MutableIterator<T> =
        CompositeIterator(
            iterables.map { it.iterator() }.iterator()
        )
}

class CompositeIterator<T>(
    private val listIterator: Iterator<MutableIterator<T>>
) : MutableIterator<T> {

    private var currentIterator: MutableIterator<T>? = null

    override fun hasNext(): Boolean {
        if (currentIterator == null || !currentIterator!!.hasNext()) {
            while (listIterator.hasNext()) {
                val iterator = listIterator.next()
                if (iterator.hasNext()) {
                    currentIterator = iterator
                    return true
                }
            }
            return false
        }
        return currentIterator!!.hasNext()
    }

    override fun next(): T {
        hasNext()
        return currentIterator!!.next()
    }

    override fun remove() {
        currentIterator!!.remove()
    }
}