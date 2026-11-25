package com.example.neptuno.audio.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Último aviso sobre qué micrófono se está usando y por qué (se muestra en el chat). */
object AudioRouteNotice {
    private val _state = MutableStateFlow<String?>(null)
    val state: StateFlow<String?> = _state

    fun set(message: String?) {
        _state.value = message
    }
}

/**
 * Memoria de si el micrófono Bluetooth ha enviado audio. Si falla (silencio), el modo automático lo evita unos minutos
 * y usa el micrófono del teléfono; una elección manual explícita del usuario siempre se respeta.
 */
object BluetoothMicHealth {
    const val COOLDOWN_MS = 15 * 60 * 1000L
    @Volatile private var failedAtMillis: Long = 0L

    fun markFailed(nowMillis: Long = System.currentTimeMillis()) {
        failedAtMillis = nowMillis
    }

    fun reset() {
        failedAtMillis = 0L
        consecutiveSilentTurns = 0
    }

    /** Turnos seguidos con micrófono Bluetooth sin voz ni nivel (puede ser el usuario callado o un micrófono mudo). */
    @Volatile private var consecutiveSilentTurns = 0
    const val SILENT_TURNS_TO_BLOCK = 3

    /** Devuelve true cuando ya son suficientes turnos seguidos mudos como para bloquear el Bluetooth. */
    fun noteSilentBluetoothTurn(): Boolean {
        consecutiveSilentTurns++
        return consecutiveSilentTurns >= SILENT_TURNS_TO_BLOCK
    }

    fun noteSpeechHeard() {
        consecutiveSilentTurns = 0
    }

    fun isBlocked(nowMillis: Long = System.currentTimeMillis()): Boolean =
        failedAtMillis != 0L && nowMillis - failedAtMillis < COOLDOWN_MS
}

/** Estado en vivo del asistente (escuchando, pensando, hablando…) compartido entre el chat y la pantalla de inicio. */
object LiveAssistantState {
    private val _state = MutableStateFlow<com.example.neptuno.core.model.AssistantState?>(null)
    val state: StateFlow<com.example.neptuno.core.model.AssistantState?> = _state

    fun set(value: com.example.neptuno.core.model.AssistantState?) {
        _state.value = value
    }
}
