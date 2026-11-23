package com.example.neptuno.voice.wake

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canal entre el servicio de escucha de fondo y el chat: el servicio pide "empieza a escuchar y envía" y el chat
 * avisa cuando terminó el dictado para que el servicio reanude la escucha de la frase de activación.
 */
class WakeActivationBus {
    private val _pending = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    private val _turnEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val turnEnded: SharedFlow<Unit> = _turnEnded

    /** Un servicio pide que el chat empiece a escuchar. */
    fun request(nowMillis: Long = System.currentTimeMillis()) {
        _pending.value = nowMillis
    }

    /** El chat consume la petición una sola vez; ignora las que tienen más de [maxAgeMillis]. */
    fun consume(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = 15_000L): Boolean {
        val requestedAt = _pending.value ?: return false
        _pending.value = null
        return nowMillis - requestedAt <= maxAgeMillis
    }

    fun notifyTurnEnded() {
        _turnEnded.tryEmit(Unit)
    }
}
