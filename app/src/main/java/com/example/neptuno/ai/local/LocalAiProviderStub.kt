package com.example.neptuno.ai.local

import com.example.neptuno.ai.api.AiProvider
import com.example.neptuno.ai.api.AiProviderCapabilities
import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.core.model.AiProviderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Contrato preparado para motores de inferencia local en el dispositivo (por ejemplo, LiteRT / MediaPipe / GGUF).
 * En esta primera versión informa de forma transparente que el motor aún no está instalado.
 */
class LocalEnginePendingProvider : AiProvider {
    override val id: String = "local_engine_pending"
    override val displayName: String = "Motor Local (On-Device)"
    override val capabilities: AiProviderCapabilities = AiProviderCapabilities(
        supportsStreamingText = true,
        supportsBidirectionalAudio = false,
        supportsOfflineExecution = true,
        supportsToolCalling = false
    )
    override val status: AiProviderStatus = AiProviderStatus.PendingIntegration

    override fun streamResponse(request: AiRequest): Flow<AiResponseEvent> = flow {
        emit(
            AiResponseEvent.IntegrationPending(
                providerName = displayName,
                message = "Motor local pendiente de integración. El mensaje quedó registrado en Room; cuando se integre un modelo local podrás conversar sin conexión."
            )
        )
    }

    override suspend fun interruptActiveResponse() {
        // Listo para detener la decodificación local en futuras fases.
    }
}
