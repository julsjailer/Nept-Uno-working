package com.example.neptuno.data.local

import com.example.neptuno.core.model.ChatMessage
import com.example.neptuno.core.model.ConversationSession
import com.example.neptuno.core.model.MemoryCategory
import com.example.neptuno.core.model.MemoryEntry
import com.example.neptuno.core.model.MessageSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    fun observeConversations(): Flow<List<ConversationSession>>
    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>
    suspend fun getConversationsSnapshot(): List<ConversationSession>
    suspend fun getConversationsNeedingCloudSyncSnapshot(): List<ConversationSession>
    suspend fun getMessagesSnapshot(conversationId: Long): List<ChatMessage>
    suspend fun getUnsyncedMessagesSnapshot(conversationId: Long): List<ChatMessage>
    suspend fun markConversationSynced(id: Long, expectedUpdatedAtMillis: Long)
    suspend fun markMessagesSynced(ids: List<Long>)
    suspend fun getOrCreateActiveConversation(defaultTitle: String): Long
    suspend fun ensureConversationExists(conversationId: Long?, defaultTitle: String): Long
    suspend fun createConversation(title: String): Long
    suspend fun addMessage(message: ChatMessage): Long
    suspend fun updateConversationTitle(conversationId: Long, title: String)
    suspend fun deleteConversation(conversationId: Long)
    suspend fun clearAllConversations()
}

interface MemoryRepository {
    fun observeMemories(): Flow<List<MemoryEntry>>
    suspend fun getMemoriesSnapshot(): List<MemoryEntry>
    suspend fun getUnsyncedMemoriesSnapshot(): List<MemoryEntry>
    suspend fun markMemorySynced(id: Long, expectedUpdatedAtMillis: Long)
    suspend fun saveMemory(entry: MemoryEntry): Long
    suspend fun deleteMemory(id: Long)
    suspend fun clearAllMemories()
}

class LocalConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ConversationRepository {

    override fun observeConversations(): Flow<List<ConversationSession>> {
        return conversationDao.observeAllConversations().map { list ->
            list.map { item ->
                ConversationSession(
                    id = item.id,
                    title = item.title,
                    createdAtMillis = item.createdAtMillis,
                    updatedAtMillis = item.updatedAtMillis,
                    messageCount = item.messageCount
                )
            }
        }
    }

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> {
        return messageDao.observeMessagesForConversation(conversationId).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    sender = runCatching { MessageSender.valueOf(entity.sender) }
                        .getOrDefault(MessageSender.SYSTEM_NOTICE),
                    content = entity.content,
                    timestampMillis = entity.timestampMillis,
                    providerId = entity.providerId,
                    isInterrupted = entity.isInterrupted
                )
            }
        }
    }

    override suspend fun getConversationsSnapshot(): List<ConversationSession> {
        return conversationDao.getAllConversationsOnce().map { item ->
            ConversationSession(
                id = item.id,
                title = item.title,
                createdAtMillis = item.createdAtMillis,
                updatedAtMillis = item.updatedAtMillis
            )
        }
    }

    override suspend fun getConversationsNeedingCloudSyncSnapshot(): List<ConversationSession> {
        return conversationDao.getConversationsNeedingCloudSync().map { item ->
            ConversationSession(
                id = item.id,
                title = item.title,
                createdAtMillis = item.createdAtMillis,
                updatedAtMillis = item.updatedAtMillis
            )
        }
    }

    override suspend fun getMessagesSnapshot(conversationId: Long): List<ChatMessage> =
        messageDao.getMessagesForConversationOnce(conversationId).map(::toChatMessage)

    override suspend fun getUnsyncedMessagesSnapshot(conversationId: Long): List<ChatMessage> =
        messageDao.getUnsyncedMessagesForConversation(conversationId).map(::toChatMessage)

    private fun toChatMessage(entity: MessageEntity): ChatMessage = ChatMessage(
        id = entity.id,
        conversationId = entity.conversationId,
        sender = runCatching { MessageSender.valueOf(entity.sender) }
            .getOrDefault(MessageSender.SYSTEM_NOTICE),
        content = entity.content,
        timestampMillis = entity.timestampMillis,
        providerId = entity.providerId,
        isInterrupted = entity.isInterrupted
    )

    override suspend fun markConversationSynced(id: Long, expectedUpdatedAtMillis: Long) {
        conversationDao.markCloudSynced(id, expectedUpdatedAtMillis, System.currentTimeMillis())
    }

    override suspend fun markMessagesSynced(ids: List<Long>) {
        if (ids.isNotEmpty()) messageDao.markCloudSynced(ids, System.currentTimeMillis())
    }

    override suspend fun getOrCreateActiveConversation(defaultTitle: String): Long {
        val latest = conversationDao.getLatestConversation()
        if (latest != null) return latest.id
        return createConversation(defaultTitle)
    }

    override suspend fun ensureConversationExists(conversationId: Long?, defaultTitle: String): Long {
        if (conversationId != null && conversationDao.getConversationById(conversationId) != null) {
            return conversationId
        }
        return createConversation(defaultTitle)
    }

    override suspend fun createConversation(title: String): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insertConversation(
            ConversationEntity(
                title = title,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }

    override suspend fun addMessage(message: ChatMessage): Long {
        val id = messageDao.insertMessage(
            MessageEntity(
                conversationId = message.conversationId,
                sender = message.sender.name,
                content = message.content,
                timestampMillis = message.timestampMillis,
                providerId = message.providerId,
                isInterrupted = message.isInterrupted
            )
        )
        conversationDao.touchConversation(message.conversationId, message.timestampMillis)
        return id
    }

    override suspend fun updateConversationTitle(conversationId: Long, title: String) {
        conversationDao.updateConversationHeader(
            id = conversationId,
            title = title,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteConversationById(conversationId)
    }

    override suspend fun clearAllConversations() {
        conversationDao.deleteAllConversations()
    }
}

class LocalMemoryRepository(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override fun observeMemories(): Flow<List<MemoryEntry>> {
        return memoryDao.observeAllMemories().map { entities ->
            entities.map { entity ->
                MemoryEntry(
                    id = entity.id,
                    title = entity.title,
                    content = entity.content,
                    category = runCatching { MemoryCategory.valueOf(entity.category) }
                        .getOrDefault(MemoryCategory.GENERAL),
                    authorizedByUser = entity.authorizedByUser,
                    createdAtMillis = entity.createdAtMillis,
                    updatedAtMillis = entity.updatedAtMillis
                )
            }
        }
    }

    override suspend fun getMemoriesSnapshot(): List<MemoryEntry> =
        memoryDao.getAllMemoriesOnce().map(::toMemoryEntry)

    override suspend fun getUnsyncedMemoriesSnapshot(): List<MemoryEntry> =
        memoryDao.getUnsyncedAuthorizedMemories().map(::toMemoryEntry)

    private fun toMemoryEntry(entity: MemoryEntity): MemoryEntry = MemoryEntry(
        id = entity.id,
        title = entity.title,
        content = entity.content,
        category = runCatching { MemoryCategory.valueOf(entity.category) }
            .getOrDefault(MemoryCategory.GENERAL),
        authorizedByUser = entity.authorizedByUser,
        createdAtMillis = entity.createdAtMillis,
        updatedAtMillis = entity.updatedAtMillis
    )

    override suspend fun markMemorySynced(id: Long, expectedUpdatedAtMillis: Long) {
        memoryDao.markCloudSynced(id, expectedUpdatedAtMillis, System.currentTimeMillis())
    }

    override suspend fun saveMemory(entry: MemoryEntry): Long {
        val now = System.currentTimeMillis()
        return memoryDao.insertMemory(
            MemoryEntity(
                id = entry.id,
                title = entry.title.trim(),
                content = entry.content.trim(),
                category = entry.category.name,
                authorizedByUser = entry.authorizedByUser,
                createdAtMillis = if (entry.id == 0L) now else entry.createdAtMillis,
                updatedAtMillis = now,
                cloudSyncedAtMillis = null
            )
        )
    }

    override suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    override suspend fun clearAllMemories() {
        memoryDao.deleteAllMemories()
    }
}
