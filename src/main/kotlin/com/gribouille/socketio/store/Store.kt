
package com.gribouille.socketio.store

interface Store {
    operator fun set(key: String, `val`: Any)
    operator fun <T> get(key: String): T?
    fun has(key: String): Boolean
    fun del(key: String)
}