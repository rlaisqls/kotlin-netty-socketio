
package com.gribouille.socketio

class MultiTypeArgs(val args: List<Any>) : Iterable<Any?> {

    val isEmpty: Boolean
        get() = size() == 0
    fun size(): Int = args.size
    fun <T> first(): T = get(0)
    fun <T> second(): T = get(1)
    operator fun <T> get(index: Int): T = args[index] as T
    override fun iterator(): Iterator<Any> = args.iterator()

}