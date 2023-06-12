
package com.gribouille.socketio.ack

enum class AckMode {
    /**
     * Send ack-response automatically on each ack-request
     * **skip** exceptions during packet handling
     */
    AUTO,

    /**
     * Send ack-response automatically on each ack-request
     * only after **success** packet handling
     */
    AUTO_SUCCESS_ONLY,

    /**
     * Turn off auto ack-response sending.
     * Use AckRequest.sendAckData to send ack-response each time.
     *
     */
    MANUAL
}