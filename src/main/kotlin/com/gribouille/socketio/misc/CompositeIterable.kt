
package com.gribouille.socketio.misc

class CompositeIterable<T>(
    private val iterables: List<MutableIterable<T>>
) : Iterable<T> {

    override fun iterator(): MutableIterator<T> =
        CompositeIterator(
            iterables.map { it.iterator() }.iterator()
        )
}