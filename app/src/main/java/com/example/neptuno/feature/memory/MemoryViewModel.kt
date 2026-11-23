package com.example.neptuno.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neptuno.core.model.ConversationSession
import com.example.neptuno.core.model.MemoryCategory
import com.example.neptuno.core.model.MemoryEntry
import com.example.neptuno.data.local.ConversationRepository
import com.example.neptuno.data.local.MemoryRepository
import com.example.neptuno.data.local.UserPreferencesRepository
import com.example.neptuno.data.remote.CloudSyncGateway
import com.example.neptuno.data.remote.NoOpCloudSyncGateway
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoryUiState(
    val memories: List<MemoryEntry> = emptyList(),
    val conversations: List<ConversationSession> = emptyList(),
    val isMemoryAuthorized: Boolean = true
)

class MemoryViewModel(
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    preferencesRepository: UserPreferencesRepository,
    private val cloudSyncGateway: CloudSyncGateway = NoOpCloudSyncGateway
) : ViewModel() {

    val uiState: StateFlow<MemoryUiState> = combine(
        memoryRepository.observeMemories(),
        conversationRepository.observeConversations(),
        preferencesRepository.userPreferences
    ) { memories, conversations, prefs ->
        MemoryUiState(
            memories = memories,
            conversations = conversations,
            isMemoryAuthorized = prefs.memoryAuthorized
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoryUiState()
    )

    fun saveAuthorizedMemory(title: String, content: String, category: MemoryCategory) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            val entry = MemoryEntry(
                title = title.trim(),
                content = content.trim(),
                category = category,
                authorizedByUser = true
            )
            val savedId = memoryRepository.saveMemory(entry)
            cloudSyncGateway.syncMemorySaved(entry.copy(id = savedId))
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
            cloudSyncGateway.syncMemoryDeleted(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
            cloudSyncGateway.syncAllMemoriesCleared()
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            cloudSyncGateway.syncConversationDeleted(id)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            conversationRepository.clearAllConversations()
            cloudSyncGateway.syncAllConversationsCleared()
        }
    }

    companion object {
        fun factory(
            memoryRepository: MemoryRepository,
            conversationRepository: ConversationRepository,
            preferencesRepository: UserPreferencesRepository,
            cloudSyncGateway: CloudSyncGateway = NoOpCloudSyncGateway
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MemoryViewModel(
                    memoryRepository = memoryRepository,
                    conversationRepository = conversationRepository,
                    preferencesRepository = preferencesRepository,
                    cloudSyncGateway = cloudSyncGateway
                ) as T
            }
        }
    }
}
