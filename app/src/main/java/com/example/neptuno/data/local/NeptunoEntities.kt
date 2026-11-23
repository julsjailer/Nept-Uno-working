package com.example.neptuno.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val cloudSyncedAtMillis: Long? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val sender: String,
    val content: String,
    val timestampMillis: Long,
    val providerId: String? = null,
    val isInterrupted: Boolean = false,
    val cloudSyncedAtMillis: Long? = null
)

@Entity(tableName = "memory_entries")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val category: String,
    val authorizedByUser: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val cloudSyncedAtMillis: Long? = null
)
