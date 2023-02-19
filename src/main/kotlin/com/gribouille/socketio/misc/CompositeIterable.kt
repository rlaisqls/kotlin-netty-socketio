
package com.gribouille.socketio.misc

class CompositeIterable<T>(
    private val iterables: List<MutableIterable<T>>
) : Iterable<T> {
    constructor(iterables: CompositeIterable<T>) : this(
        iterables.iterables
    )

    private var iterablesList: List<Iterable<T>>? = null

    override fun iterator(): MutableIterator<T> =
        CompositeIterator(
            iterables.map { it.iterator() }.iterator()
        )

}