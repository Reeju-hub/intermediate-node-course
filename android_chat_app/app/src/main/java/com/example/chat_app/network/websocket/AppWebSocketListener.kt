package com.example.chat_app.network.websocket

import android.util.Log
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

interface WebSocketCallback {
    fun onWebSocketOpen()
    fun onNewMessage(message: String)
    fun onWebSocketClose(reason: String)
    fun onWebSocketError(error: String)
}

class AppWebSocketListener(private val callback: WebSocketCallback) : WebSocketListener() {
    companion object {
        private const val TAG = "AppWebSocketListener"
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "WebSocket Opened")
        callback.onWebSocketOpen()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "Receiving text: $text")
        // This will be raw Socket.IO protocol messages. We need to parse them.
        // E.g., "42["new_private_message",{"sender_id":1,"content":"Hello","timestamp":"..."}]"
        // For now, pass the raw message. Parsing will be handled in ChatActivity or ViewModel.
        callback.onNewMessage(text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "Receiving bytes: ${bytes.hex()}")
        // We typically expect text messages for Socket.IO
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket Closing: $code / $reason")
        webSocket.close(1000, null)
        callback.onWebSocketClose(reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket Closed: $code / $reason")
        // callback.onWebSocketClose(reason) // Already called in onClosing
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket Failure: ${t.message}", t)
        callback.onWebSocketError(t.message ?: "Unknown WebSocket error")
    }
}
