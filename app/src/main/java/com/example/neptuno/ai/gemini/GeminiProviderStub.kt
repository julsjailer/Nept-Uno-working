package com.example.neptuno.ai.gemini

import com.example.neptuno.ai.api.AiProvider
import com.example.neptuno.ai.api.AiProviderCapabilities
import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.core.model.AiProviderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Contrato preparado para la integración futura con Gemini / Gemini Live.
 * En esta primera versión no realiza llamadas ni simula respuestas de IA.
 */
interface GeminiLiveSessionContract {
    suspend fun connectRealtimeSession()
    suspend fun sendPcmAudioFrame(frame: ByteArray)
    suspend fun interruptModelSpeech()
    suspend fun disconnect()
}

class GeminiPendingProvider : AiProvider {
    override val id: String = "gemini_live_pending"
    override val displayName: String = "Gemini Live (Audio bidireccional · Pendiente)"
    override val capabilities: AiProviderCapabilities = AiProviderCapabilities(
        supportsStreamingText = true,
        supportsBidirectionalAudio = true,
        supportsOfflineExecution = false,
        supportsToolCalling = true
    )
    override val status: AiProviderStatus = AiProviderStatus.PendingIntegration

    override fun streamResponse(request: AiRequest): Flow<AiResponseEvent> = flow {
        emit(
            AiResponseEvent.IntegrationPending(
                providerName = displayName,
                message = "La sesión en tiempo real de Gemini Live (audio bidireccional WebSocket) está pendiente de implementación. Selecciona 'Gemini (Texto y Streaming)' en Ajustes para conversar con Gemini usando el sistema de voz (STT + TTS)."
            )
        )
    }

    override suspend fun interruptActiveResponse() {
        // Sin sesión en tiempo real activa en esta fase.
    }
}
