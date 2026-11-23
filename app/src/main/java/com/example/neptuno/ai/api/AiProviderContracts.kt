package com.example.neptuno.ai.api

import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.ChatMessage
import com.example.neptuno.core.model.MemoryEntry
import com.example.neptuno.core.model.PersonalityMode
import kotlinx.coroutines.flow.Flow

data class AiProviderCapabilities(
    val supportsStreamingText: Boolean,
    val supportsBidirectionalAudio: Boolean,
    val supportsOfflineExecution: Boolean,
    val supportsToolCalling: Boolean
)

data class AiRequest(
    val conversationId: Long,
    val userInput: String,
    val recentHistory: List<ChatMessage>,
    val authorizedMemories: List<MemoryEntry>,
    val personality: PersonalityMode,
    val languageCode: String
)

sealed interface AiResponseEvent {
    data class TextDelta(val text: String) : AiResponseEvent
    data object Completed : AiResponseEvent
    data class Interrupted(val reason: String) : AiResponseEvent
    data class IntegrationPending(val providerName: String, val message: String) : AiResponseEvent
    data class Failure(val errorMessage: String) : AiResponseEvent
}

/**
 * Contrato desacoplado para proveedores de inteligencia artificial.
 * Permite conectar Gemini Live, APIs remotas o motores locales sin modificar la UI.
 */
interface AiProvider {
    val id: String
    val displayName: String
    val capabilities: AiProviderCapabilities
    val status: AiProviderStatus

    fun streamResponse(request: AiRequest): Flow<AiResponseEvent>

    /**
     * Interrumpe cualquier generación o transmisión de audio en curso (barge-in).
     */
    suspend fun interruptActiveResponse()
}

/**
 * Registro extensible de proveedores de IA disponibles en Neptuno.
 */
class AiProviderRegistry(
    private val providers: List<AiProvider>
) {
    fun getAllProviders(): List<AiProvider> = providers

    fun findById(id: String): AiProvider? = providers.find { it.id == id }

    fun getActiveProvider(preferredId: String): AiProvider {
        return findById(preferredId) ?: providers.first()
    }
}
