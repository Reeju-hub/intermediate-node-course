package com.example.chat_app

package com.example.chat_app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View // Import View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chat_app.databinding.ActivityChatBinding
import com.example.chat_app.db.AppDatabase
import com.example.chat_app.db.dao.MessageDao
import com.example.chat_app.db.entity.MessageEntity
import com.example.chat_app.network.websocket.AppWebSocketListener
import com.example.chat_app.network.websocket.WebSocketCallback
import com.example.chat_app.network.websocket.WebSocketManager
import com.example.chat_app.ui.adapter.ChatMessage
import com.example.chat_app.ui.adapter.MessageAdapter
import com.example.chat_app.util.UserSessionManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.*
import org.json.JSONArray

class ChatActivity : AppCompatActivity(), WebSocketCallback {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter
    private var currentUserId: Int = -1
    private var recipientId: Int = -1
    private var recipientUsername: String? = null
    private val gson = Gson()

    private lateinit var messageDao: MessageDao
    private var channelId: String = ""

    companion object {
        private const val TAG = "ChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        messageDao = AppDatabase.getDatabase(applicationContext).messageDao()

        currentUserId = UserSessionManager.getUserId(this) ?: -1
        if (currentUserId == -1) {
            Toast.makeText(this, "Error: User not logged in.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        recipientId = intent.getIntExtra("RECIPIENT_ID", -1)
        recipientUsername = intent.getStringExtra("RECIPIENT_USERNAME")

        if (recipientId == -1 || recipientUsername == null) {
            Toast.makeText(this, "Error: Recipient details missing.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        binding.tvChatRecipientName.text = recipientUsername
        channelId = generateChannelId(currentUserId, recipientId)

        setupRecyclerView()
        loadMessagesFromDb()
        setupSendButton()

        WebSocketManager.connect(AppWebSocketListener(this))
    }

    private fun generateChannelId(id1: Int, id2: Int): String {
        return if (id1 < id2) "${id1}_${id2}" else "${id2}_${id1}"
    }

    private fun loadMessagesFromDb() {
        binding.pbChatLoading.visibility = View.VISIBLE // Start loading
        CoroutineScope(Dispatchers.IO).launch {
            val messageEntities = messageDao.getMessagesForChannel(channelId)
            val chatMessages = messageEntities.map { entity ->
                ChatMessage(
                    messageId = entity.messageIdBackend,
                    senderId = entity.senderId,
                    receiverId = entity.receiverId,
                    content = entity.content,
                    timestamp = entity.timestamp
                )
            }
            withContext(Dispatchers.Main) {
                messageAdapter.setMessages(chatMessages)
                if (messageAdapter.itemCount > 0) {
                     binding.rvMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
                }
                binding.pbChatLoading.visibility = View.GONE // Stop loading
            }
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(mutableListOf(), currentUserId)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupSendButton() {
        binding.btnSendMessage.setOnClickListener {
            val messageText = binding.etMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                val messagePayload = mapOf(
                    "recipient_id" to recipientId,
                    "message" to messageText
                )
                val socketIoMessage = "42[\"private_message\",${gson.toJson(messagePayload)}]"
                
                val sentMessage = ChatMessage(
                    messageId = System.currentTimeMillis().toString(),
                    senderId = currentUserId,
                    receiverId = recipientId,
                    content = messageText,
                    timestamp = System.currentTimeMillis()
                )

                if (WebSocketManager.sendMessage(socketIoMessage)) {
                    binding.etMessageInput.text.clear()
                    CoroutineScope(Dispatchers.IO).launch {
                        messageDao.insertMessage(
                            MessageEntity(
                                messageIdBackend = sentMessage.messageId,
                                senderId = sentMessage.senderId,
                                receiverId = sentMessage.receiverId,
                                content = sentMessage.content,
                                timestamp = sentMessage.timestamp,
                                channelId = channelId
                            )
                        )
                        // Server will echo message back, which gets added to DB and UI
                        // So, no direct UI add here for sent messages to avoid duplication if server echoes
                    }
                } else {
                    Toast.makeText(this, "Failed to send. Not connected?", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.disconnect()
    }

    override fun onWebSocketOpen() {
        runOnUiThread {
            Toast.makeText(this, "Connected to chat server", Toast.LENGTH_SHORT).show()
            val identifyPayload = mapOf("user_id" to currentUserId)
            val identifyMsg = "42[\"identify_user\",${gson.toJson(identifyPayload)}]"
            WebSocketManager.sendMessage(identifyMsg)
        }
    }

    private fun parseSocketIOMessage(rawMessage: String): Pair<String, String?>? {
        Log.d(TAG, "Attempting to parse: $rawMessage")
        if (rawMessage.startsWith("42")) {
            try {
                val jsonArrayString = rawMessage.substring(2)
                val jsonArray = JSONArray(jsonArrayString)
                if (jsonArray.length() >= 1) {
                    val eventName = jsonArray.getString(0)
                    val data = if (jsonArray.length() > 1) jsonArray.getJSONObject(1).toString() else null
                    Log.d(TAG, "Parsed event: $eventName, data: $data")
                    return Pair(eventName, data)
                }
            } catch (e: Exception){
                 Log.e(TAG, "Error parsing Socket.IO message: $rawMessage", e)
            }
        } else if (rawMessage == "2") {
             Log.d(TAG, "Received PING, sending PONG")
             WebSocketManager.sendMessage("3")
        } else if (rawMessage.startsWith("0{")) {
            Log.d(TAG, "Engine.IO Open packet received: $rawMessage")
        } else if (rawMessage == "40") {
             Log.d(TAG, "Socket.IO Namespace connected.")
        }
        return null
    }
    
    data class NewMessagePayload(
        val sender_id: Int, 
        val content: String, 
        val timestamp: String, 
        val message_id: String? = null,
        val receiver_id: Int
    )

    override fun onNewMessage(message: String) {
        runOnUiThread {
            Log.d(TAG, "Raw message from server: $message")
            val parsed = parseSocketIOMessage(message)
            if (parsed != null) {
                val (eventName, dataJson) = parsed
                if (eventName == "new_private_message" && dataJson != null) {
                    try {
                        val msgData = gson.fromJson(dataJson, NewMessagePayload::class.java)
                        
                        val relevantToCurrentChat = 
                            (msgData.sender_id == currentUserId && msgData.receiver_id == recipientId) ||
                            (msgData.sender_id == recipientId && msgData.receiver_id == currentUserId)

                        if (relevantToCurrentChat) {
                            val chatMsg = ChatMessage(
                                messageId = msgData.message_id ?: System.currentTimeMillis().toString(),
                                senderId = msgData.sender_id,
                                receiverId = msgData.receiver_id,
                                content = msgData.content,
                                timestamp = System.currentTimeMillis() 
                            )
                            
                            CoroutineScope(Dispatchers.IO).launch {
                                messageDao.insertMessage(
                                    MessageEntity(
                                        messageIdBackend = chatMsg.messageId,
                                        senderId = chatMsg.senderId,
                                        receiverId = chatMsg.receiverId,
                                        content = chatMsg.content,
                                        timestamp = chatMsg.timestamp,
                                        channelId = channelId
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    messageAdapter.addMessage(chatMsg)
                                    binding.rvMessages.smoothScrollToPosition(messageAdapter.itemCount - 1) // Smooth scroll
                                }
                            }
                        } else {
                            Log.d(TAG, "Received message not for current chat context. Sender: ${msgData.sender_id}, Recipient: ${msgData.receiver_id}")
                        }
                    } catch (e: JsonSyntaxException) {
                        Log.e(TAG, "Error deserializing new_private_message data: $dataJson", e)
                    }
                } else if (eventName == "user_identified") {
                    Log.d(TAG, "User identified by server. SID: ${dataJson}")
                }
            }
        }
    }

    override fun onWebSocketClose(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Disconnected: $reason", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "WebSocket Closed: $reason")
        }
    }

    override fun onWebSocketError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Chat Error: $error", Toast.LENGTH_LONG).show()
            Log.e(TAG, "WebSocket Error: $error")
        }
    }
}
