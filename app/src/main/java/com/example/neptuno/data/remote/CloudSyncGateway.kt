package com.example.neptuno.data.remote

import android.util.Log
import com.example.neptuno.core.NetworkMonitor
import com.example.neptuno.core.model.ChatMessage
import com.example.neptuno.core.model.MemoryEntry
import com.example.neptuno.core.model.UserPreferences
import com.example.neptuno.data.local.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

private const val GATEWAY_TAG = "CloudSyncGateway"
private const val DEFAULT_GATEWAY_TIMEOUT_MS = 8_000L

/**
 * Puente opcional de sincronización hacia Cloud Firestore.
 * Mantiene Room y DataStore operativos localmente y sincroniza cambios autorizados
 * cuando existe sesión autenticada y conectividad validada.
 */
interface CloudSyncGateway {
    suspend fun syncProfile(displayName: String, email: String?, preferences: UserPreferences)
    suspend fun syncMemorySaved(entry: MemoryEntry)
    suspend fun syncMemoryDeleted(localId: Long)
    suspend fun syncAllMemoriesCleared()
    suspend fun syncConversationTitle(localConversationId: Long, title: String)
    suspend fun syncMessageAdded(conversationId: Long, conversationTitle: String, message: ChatMessage)
    suspend fun syncConversationDeleted(localConversationId: Long)
    suspend fun syncAllConversationsCleared()
}

object NoOpCloudSyncGateway : CloudSyncGateway {
    override suspend fun syncProfile(displayName: String, email: String?, preferences: UserPreferences) = Unit
    override suspend fun syncMemorySaved(entry: MemoryEntry) = Unit
    override suspend fun syncMemoryDeleted(localId: Long) = Unit
    override suspend fun syncAllMemoriesCleared() = Unit
    override suspend fun syncConversationTitle(localConversationId: Long, title: String) = Unit
    override suspend fun syncMessageAdded(conversationId: Long, conversationTitle: String, message: ChatMessage) = Unit
    override suspend fun syncConversationDeleted(localConversationId: Long) = Unit
    override suspend fun syncAllConversationsCleared() = Unit
}

/**
 * Delegador conmutable entre NoOpCloudSyncGateway (modo local sin sesión)
 * y FirestoreCloudSyncGateway (con sesión Firebase), sin reiniciar ViewModels ni perder datos de Room.
 */
class SwitchableCloudSyncGateway(
    initialDelegate: CloudSyncGateway = NoOpCloudSyncGateway
) : CloudSyncGateway {

    @Volatile
    private var delegate: CloudSyncGateway = initialDelegate

    fun setDelegate(newDelegate: CloudSyncGateway) {
        delegate = newDelegate
    }

    fun resetToLocal() {
        delegate = NoOpCloudSyncGateway
    }

    fun isCloudEnabled(): Boolean = delegate !== NoOpCloudSyncGateway

    override suspend fun syncProfile(displayName: String, email: String?, preferences: UserPreferences) {
        delegate.syncProfile(displayName, email, preferences)
    }

    override suspend fun syncMemorySaved(entry: MemoryEntry) {
        delegate.syncMemorySaved(entry)
    }

    override suspend fun syncMemoryDeleted(localId: Long) {
        delegate.syncMemoryDeleted(localId)
    }

    override suspend fun syncAllMemoriesCleared() {
        delegate.syncAllMemoriesCleared()
    }

    override suspend fun syncConversationTitle(localConversationId: Long, title: String) {
        delegate.syncConversationTitle(localConversationId, title)
    }

    override suspend fun syncMessageAdded(conversationId: Long, conversationTitle: String, message: ChatMessage) {
        delegate.syncMessageAdded(conversationId, conversationTitle, message)
    }

    override suspend fun syncConversationDeleted(localConversationId: Long) {
        delegate.syncConversationDeleted(localConversationId)
    }

    override suspend fun syncAllConversationsCleared() {
        delegate.syncAllConversationsCleared()
    }
}

class FirestoreCloudSyncGateway(
    private val userId: String,
    private val repository: CloudRemoteStore,
    private val preferencesRepository: UserPreferencesRepository,
    private val networkMonitor: NetworkMonitor,
    private val operationTimeoutMs: Long = DEFAULT_GATEWAY_TIMEOUT_MS
) : CloudSyncGateway {

    private suspend fun isCloudSyncAllowed(): Boolean {
        val prefs = preferencesRepository.userPreferences.first()
        if (prefs.offlineMode) return false
        return networkMonitor.observeNetworkStatus().first().isConnected
    }

    private suspend inline fun runBounded(actionName: String, crossinline block: suspend () -> Unit) {
        try {
            if (!isCloudSyncAllowed()) return
            withTimeout(operationTimeoutMs) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(GATEWAY_TAG, "Cloud sync timed out ($actionName); local Room state preserved.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(GATEWAY_TAG, "Cloud sync failed ($actionName); local Room state preserved.", e)
        }
    }

    override suspend fun syncProfile(
        displayName: String,
        email: String?,
        preferences: UserPreferences
    ) = runBounded("syncProfile") {
        repository.upsertUserProfile(
            userId = userId,
            displayName = displayName,
            email = email,
            languageCode = preferences.language.code,
            personalityId = preferences.personalityMode.id,
            avatarStyleId = preferences.avatarStyle.id
        )
    }

    override suspend fun syncMemorySaved(entry: MemoryEntry) {
        if (!entry.authorizedByUser) return
        runBounded("syncMemorySaved") {
            repository.saveMemory(
                userId = userId,
                title = entry.title,
                content = entry.content,
                category = entry.category,
                localId = entry.id.takeIf { it > 0L }
            )
        }
    }

    override suspend fun syncMemoryDeleted(localId: Long) {
        if (localId <= 0L) return
        runBounded("syncMemoryDeleted") {
            repository.deleteMemory(userId = userId, memoryDocId = "local_$localId")
        }
    }

    override suspend fun syncAllMemoriesCleared() = runBounded("syncAllMemoriesCleared") {
        repository.clearAllMemories(userId = userId)
    }

    override suspend fun syncConversationTitle(localConversationId: Long, title: String) =
        runBounded("syncConversationTitle") {
            repository.ensureCloudConversation(
                userId = userId,
                localConversationId = localConversationId,
                title = title
            )
        }

    override suspend fun syncMessageAdded(
        conversationId: Long,
        conversationTitle: String,
        message: ChatMessage
    ) = runBounded("syncMessageAdded") {
        repository.appendCloudMessage(
            userId = userId,
            localConversationId = conversationId,
            conversationTitle = conversationTitle,
            sender = message.sender,
            content = message.content,
            providerId = message.providerId,
            interrupted = message.isInterrupted,
            localMessageId = message.id.takeIf { it > 0L }
        )
    }

    override suspend fun syncConversationDeleted(localConversationId: Long) =
        runBounded("syncConversationDeleted") {
            repository.deleteCloudConversation(
                userId = userId,
                conversationDocId = "conv_$localConversationId"
            )
        }

    override suspend fun syncAllConversationsCleared() =
        runBounded("syncAllConversationsCleared") {
            repository.clearAllCloudConversations(userId = userId)
        }
}
