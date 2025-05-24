package com.example.chat_app.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "messages",
    indices = [Index(value = ["senderId", "receiverId", "timestamp"])] // Index for querying by chat participants & time
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // Auto-generated primary key
    val messageIdBackend: String, // ID from backend, if available, or client-generated unique ID
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val timestamp: Long,
    val channelId: String // Composite key: e.g., "user1_user2" (sorted IDs)
)
