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
