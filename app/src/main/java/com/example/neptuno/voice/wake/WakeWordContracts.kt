package com.example.neptuno.voice.wake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

enum class WakeWordState {
    DISABLED,
    UNAVAILABLE_PENDING_MODEL,
    INITIALIZING,
    LISTENING,
    ERROR
}

data class WakeWordConfig(
    val keyword: String = "Hey Neptuno",
    val sensitivity: Float = 0.6f,
    val enabled: Boolean = false
)

/**
 * Contrato independiente para detección local de palabra de activación ("Neptuno").
 * Preparado para integrarse con un servicio en primer plano y un modelo acústico ligero en fases futuras.
 */
interface WakeWordEngine {
    val state: StateFlow<WakeWordState>
    fun detections(): Flow<Long>
    suspend fun configure(config: WakeWordConfig)
    suspend fun start(): Result<Unit>
    suspend fun stop()
}

class PendingWakeWordEngine : WakeWordEngine {
    private val _state = MutableStateFlow(WakeWordState.UNAVAILABLE_PENDING_MODEL)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()

    override fun detections(): Flow<Long> = emptyFlow()

    override suspend fun configure(config: WakeWordConfig) {
        // Configuración almacenada cuando se conecte el motor acústico.
    }

    override suspend fun start(): Result<Unit> {
        return Result.failure(IllegalStateException("Motor de activación por voz pendiente de integración."))
    }

    override suspend fun stop() {
        _state.value = WakeWordState.UNAVAILABLE_PENDING_MODEL
    }
}
