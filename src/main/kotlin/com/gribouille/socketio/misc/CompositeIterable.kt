
package com.gribouille.socketio.misc

class CompositeIterable<T> : Iterable<T> {
    private var iterablesList: List<Iterable<T>>? = null
    private var iterables: Array<Iterable<T>>?

    constructor(iterables: List<Iterable<T>>?) {
        iterablesList = iterables
    }

    constructor(vararg iterables: Iterable<T>) {
        this.iterables = iterables
    }

    constructor(iterable: CompositeIterable<T>) {
        iterables = iterable.iterables
        iterablesList = iterable.iterablesList
    }

    override fun iterator(): MutableIterator<T> {
        val iterators: MutableList<Iterator<T>> = ArrayList()
        if (iterables != null) {
            for (iterable in iterables!!) {
                iterators.add(iterable.iterator())
            }
        } else {
            for (iterable in iterablesList!!) {
                iterators.add(iterable.iterator())
            }
        }
        return CompositeIterator(iterators.iterator())
    }
}