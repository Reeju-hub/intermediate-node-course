package com.example.chat_app.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.chat_app.db.entity.MessageEntity

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    // Fetches messages for a specific chat, ordered by time
    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    suspend fun getMessagesForChannel(channelId: String): List<MessageEntity>
}
