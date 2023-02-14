
package com.gribouille.socketio

/**
 * TCP socket configuration contains configuration for main server channel
 * and client channels
 *
 * @see java.net.SocketOptions
 */
class SocketConfig {
    var isTcpNoDelay = true
    var tcpSendBufferSize = -1
    var tcpReceiveBufferSize = -1
    var isTcpKeepAlive = false
    var soLinger = -1
    var isReuseAddress = false
    var acceptBackLog = 1024
    var writeBufferWaterMarkLow = -1
    var writeBufferWaterMarkHigh = -1
}