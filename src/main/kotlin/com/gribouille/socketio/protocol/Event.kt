
package com.gribouille.socketio.protocol

class Event {
    var name: String? = null
        private set
    var args: List<Any>? = null
        private set

    constructor()
    constructor(name: String?, args: List<Any>?) : super() {
        this.name = name
        this.args = args
    }
}