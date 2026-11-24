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
    }

    fun isBlocked(nowMillis: Long = System.currentTimeMillis()): Boolean =
        failedAtMillis != 0L && nowMillis - failedAtMillis < COOLDOWN_MS
}
