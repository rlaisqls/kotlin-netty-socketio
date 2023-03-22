package test

import com.gribouille.socketio.SocketIOClient
import com.gribouille.socketio.annotation.OnEvent

class TestClass() {

    @OnEvent("test")
    fun test(client: SocketIOClient, request: TestRequest) {
        println("<top>.test")
        client.sendEvent("test", "${request.data} was sent")
    }

    data class TestRequest(
        val data: String
    )

}
