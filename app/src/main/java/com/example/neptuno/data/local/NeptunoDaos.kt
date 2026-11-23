package com.example.neptuno.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ConversationWithCount(
    val id: Long,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val messageCount: Int
)

@Dao
interface ConversationDao {

    @Query(
        """
        SELECT c.id, c.title, c.createdAtMillis, c.updatedAtMillis,
               COUNT(m.id) AS messageCount
        FROM conversations c
        LEFT JOIN messages m ON c.id = m.conversationId
        GROUP BY c.id
        ORDER BY c.updatedAtMillis DESC
        """
    )
    fun observeAllConversations(): Flow<List<ConversationWithCount>>

    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC")
    suspend fun getAllConversationsOnce(): List<ConversationEntity>

    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getLatestConversation(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(entity: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateConversationHeader(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: Long, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMillis ASC, id ASC")
    fun observeMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMillis ASC, id ASC")
    suspend fun getMessagesForConversationOnce(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: MessageEntity): Long

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)
}

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memory_entries ORDER BY updatedAtMillis DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entries ORDER BY updatedAtMillis DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entity: MemoryEntity): Long

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAllMemories()
}
