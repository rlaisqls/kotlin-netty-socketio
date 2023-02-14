
package com.gribouille.socketio

class MultiTypeArgs(val args: List<Any>) : Iterable<Any?> {

    val isEmpty: Boolean
        get() = size() == 0

    fun size(): Int {
        return args.size
    }

    fun <T> first(): T {
        return get(0)
    }

    fun <T> second(): T {
        return get(1)
    }

    /**
     * "index out of bounds"-safe method for getting elements
     *
     * @param <T> type of argument
     * @param index to get
     * @return argument
    </T> */
    operator fun <T> get(index: Int): T? {
        return if (size() <= index) {
            null
        } else args[index] as T
    }

    override fun iterator(): Iterator<Any> {
        return args.iterator()
    }
}