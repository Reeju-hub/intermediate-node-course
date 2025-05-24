package com.example.chat_app.network.websocket

import com.example.chat_app.network.RetrofitClient // For BASE_URL, or define a new one for WS
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit

object WebSocketManager {
    private const val TAG = "WebSocketManager"
    // Adjust the base URL for WebSocket: ws:// instead of http://
    // And ensure the path is correct for Flask-SocketIO, often just /socket.io/
    // The EIO and transport parameters are usually handled by Socket.IO client libraries,
    // with OkHttp we might need to ensure the server supports plain WebSocket upgrade on this path.
    // For Flask-SocketIO, direct WebSocket connection needs to hit /socket.io/ with transport=websocket
    private val WS_BASE_URL = RetrofitClient.BASE_URL.replace("http://", "ws://") + "socket.io/?EIO=4&transport=websocket"
    // Simpler alternative if server is configured for direct ws on a path:
    // private val WS_BASE_URL = RetrofitClient.BASE_URL.replace("http://", "ws://") + "ws" 


    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read timeout for WebSocket
        .pingInterval(20, TimeUnit.SECONDS) // Keep connection alive, server should pong
        .build()

    fun connect(listener: AppWebSocketListener) {
        if (webSocket != null && (webSocket?.send("") == true || webSocket?.send("2") == true)) { // Quick check if still active
             // Already connected or attempting to connect and possibly still active
            return
        }
        val request = Request.Builder()
            .url(WS_BASE_URL)
            .build()
        webSocket = client.newWebSocket(request, listener)
    }

    fun sendMessage(messageJson: String): Boolean {
        return webSocket?.send(messageJson) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }
    
    fun isConnected(): Boolean {
        // This doesn't guarantee the connection is active, just that we have an instance.
        // True status comes from listener callbacks.
        return webSocket != null 
    }
}
