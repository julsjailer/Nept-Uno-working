package com.example.neptuno.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neptuno.ai.api.AiProviderRegistry
import com.example.neptuno.android.services.BluetoothDeviceController
import com.example.neptuno.core.NetworkMonitor
import com.example.neptuno.core.NetworkStatus
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.AssistantState
import com.example.neptuno.core.model.AvatarStyle
import com.example.neptuno.core.model.BluetoothAdapterState
import com.example.neptuno.data.local.ConversationRepository
import com.example.neptuno.data.local.MemoryRepository
import com.example.neptuno.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val assistantState: AssistantState = AssistantState.IDLE,
    val avatarStyle: AvatarStyle = AvatarStyle.ABYSSAL_CORE,
    val networkStatus: NetworkStatus = NetworkStatus(false, "Verificando…"),
    val offlineModeForced: Boolean = false,
    val bluetoothState: BluetoothAdapterState = BluetoothAdapterState.OFF,
    val activeProviderName: String = "Gemini (Texto y Streaming)",
    val providerStatus: AiProviderStatus = AiProviderStatus.Configured("Configurado vía Firebase AI Logic"),
    val isProviderReady: Boolean = false,
    val memoryCount: Int = 0,
    val conversationCount: Int = 0
)

class HomeViewModel(
    networkMonitor: NetworkMonitor,
    bluetoothController: BluetoothDeviceController,
    preferencesRepository: UserPreferencesRepository,
    memoryRepository: MemoryRepository,
    conversationRepository: ConversationRepository,
    aiProviderRegistry: AiProviderRegistry
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        networkMonitor.observeNetworkStatus(),
        bluetoothController.observeAdapterState(),
        preferencesRepository.userPreferences,
        memoryRepository.observeMemories(),
        conversationRepository.observeConversations()
    ) { network, btState, prefs, memories, conversations ->
        val effectiveOffline = prefs.offlineMode || !network.isConnected
        val provider = aiProviderRegistry.getActiveProvider(prefs.preferredAiProviderId)
        val status = provider.status
        HomeUiState(
            assistantState = if (effectiveOffline) AssistantState.OFFLINE else AssistantState.IDLE,
            avatarStyle = prefs.avatarStyle,
            networkStatus = network,
            offlineModeForced = prefs.offlineMode,
            bluetoothState = btState,
            activeProviderName = provider.displayName,
            providerStatus = status,
            isProviderReady = status is AiProviderStatus.Ready,
            memoryCount = memories.size,
            conversationCount = conversations.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    companion object {
        fun factory(
            networkMonitor: NetworkMonitor,
            bluetoothController: BluetoothDeviceController,
            preferencesRepository: UserPreferencesRepository,
            memoryRepository: MemoryRepository,
            conversationRepository: ConversationRepository,
            aiProviderRegistry: AiProviderRegistry
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    networkMonitor = networkMonitor,
                    bluetoothController = bluetoothController,
                    preferencesRepository = preferencesRepository,
                    memoryRepository = memoryRepository,
                    conversationRepository = conversationRepository,
                    aiProviderRegistry = aiProviderRegistry
                ) as T
            }
        }
    }
}
