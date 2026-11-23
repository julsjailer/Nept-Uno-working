package com.example.neptuno.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neptuno.ai.api.AiProvider
import com.example.neptuno.ai.api.AiProviderRegistry
import com.example.neptuno.audio.engine.TextToSpeechEngine
import com.example.neptuno.core.model.AppLanguage
import com.example.neptuno.core.model.AvatarStyle
import com.example.neptuno.core.model.DeviceVoiceInfo
import com.example.neptuno.core.model.PersonalityMode
import com.example.neptuno.core.model.ThemeMode
import com.example.neptuno.core.model.UserPreferences
import com.example.neptuno.core.model.VoiceProfile
import com.example.neptuno.data.local.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val aiProviderRegistry: AiProviderRegistry,
    private val textToSpeechEngine: TextToSpeechEngine
) : ViewModel() {

    private val _ttsRefreshTick = MutableStateFlow(0)
    private val _previewError = MutableStateFlow<String?>(null)
    private var previewJob: Job? = null

    val preferences: StateFlow<UserPreferences> = preferencesRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    val voiceProfiles: StateFlow<List<VoiceProfile>> = combine(
        preferencesRepository.userPreferences,
        textToSpeechEngine.availableVoices,
        textToSpeechEngine.availableLanguages,
        textToSpeechEngine.lastError,
        _ttsRefreshTick
    ) { prefs, _, _, _, _ ->
        textToSpeechEngine.resolveVoiceProfiles(prefs.language.code)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = textToSpeechEngine.resolveVoiceProfiles(AppLanguage.SPANISH.code)
    )

    val deviceVoices: StateFlow<List<DeviceVoiceInfo>> = combine(
        preferencesRepository.userPreferences,
        textToSpeechEngine.availableVoices
    ) { prefs, voices ->
        val langCode = prefs.language.code.lowercase()
        val matching = voices.filter { it.localeTag.lowercase().startsWith(langCode) }
        val others = voices.filterNot { it.localeTag.lowercase().startsWith(langCode) }
        matching + others
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val isTtsSpeaking: StateFlow<Boolean> = textToSpeechEngine.isSpeaking

    val ttsError: StateFlow<String?> = combine(
        _previewError,
        textToSpeechEngine.lastError
    ) { preview, engineErr ->
        preview ?: engineErr
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val aiProviders: List<AiProvider> = aiProviderRegistry.getAllProviders()

    init {
        refreshVoiceCatalog()
    }

    fun refreshVoiceCatalog() {
        viewModelScope.launch {
            textToSpeechEngine.initialize()
            _ttsRefreshTick.value += 1
        }
    }

    fun setVoice(voiceId: String) = viewModelScope.launch {
        preferencesRepository.setSelectedVoice(voiceId)
    }

    fun setDeviceVoiceName(voiceName: String?) = viewModelScope.launch {
        preferencesRepository.setSelectedDeviceVoiceName(voiceName)
    }

    fun setTtsSpeechRate(rate: Float) = viewModelScope.launch {
        preferencesRepository.setTtsSpeechRate(rate)
    }

    fun setTtsPitch(pitch: Float) = viewModelScope.launch {
        preferencesRepository.setTtsPitch(pitch)
    }

    fun resetVoiceTuning() = viewModelScope.launch {
        preferencesRepository.setTtsSpeechRate(1.0f)
        preferencesRepository.setTtsPitch(1.0f)
        preferencesRepository.setSelectedDeviceVoiceName(null)
    }

    fun setAutoSpeakResponses(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setAutoSpeakResponses(enabled)
    }

    fun testVoicePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _previewError.value = null
            val prefs = preferencesRepository.userPreferences.first()
            val sampleText = when (prefs.language) {
                AppLanguage.SPANISH -> "Hola, soy Neptuno. Así suena la voz configurada en este dispositivo."
                AppLanguage.ENGLISH -> "Hello, I am Neptuno. This is a preview of your configured voice profile."
                AppLanguage.PORTUGUESE -> "Olá, eu sou o Neptuno. Esta é uma prévia da voz configurada neste dispositivo."
            }
            val result = textToSpeechEngine.speak(
                text = sampleText,
                voiceId = prefs.selectedVoiceId,
                languageCode = prefs.language.code,
                speechRate = prefs.ttsSpeechRate,
                pitch = prefs.ttsPitch,
                preferredDeviceVoiceName = prefs.selectedDeviceVoiceName
            )
            if (result.isFailure) {
                _previewError.value = result.exceptionOrNull()?.localizedMessage
                    ?: "No se pudo reproducir la prueba de voz."
            }
        }
    }

    fun stopVoicePreview() {
        previewJob?.cancel()
        viewModelScope.launch {
            textToSpeechEngine.interrupt()
        }
    }

    fun clearTtsError() {
        _previewError.value = null
    }

    fun setPersonality(mode: PersonalityMode) = viewModelScope.launch {
        preferencesRepository.setPersonalityMode(mode)
    }

    fun setAvatarStyle(style: AvatarStyle) = viewModelScope.launch {
        preferencesRepository.setAvatarStyle(style)
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        preferencesRepository.setLanguage(language)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        preferencesRepository.setThemeMode(mode)
    }

    fun setOfflineMode(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setOfflineMode(enabled)
    }

    fun setBargeInEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setBargeInEnabled(enabled)
    }

    fun setPreferBluetoothAudio(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setPreferBluetoothAudio(enabled)
    }

    fun setMemoryAuthorized(authorized: Boolean) = viewModelScope.launch {
        preferencesRepository.setMemoryAuthorized(authorized)
    }

    fun setPersistConversationHistory(persist: Boolean) = viewModelScope.launch {
        preferencesRepository.setPersistConversationHistory(persist)
    }

    fun setPreferredAiProvider(providerId: String) = viewModelScope.launch {
        preferencesRepository.setPreferredAiProvider(providerId)
    }

    companion object {
        fun factory(
            preferencesRepository: UserPreferencesRepository,
            aiProviderRegistry: AiProviderRegistry,
            textToSpeechEngine: TextToSpeechEngine
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    preferencesRepository = preferencesRepository,
                    aiProviderRegistry = aiProviderRegistry,
                    textToSpeechEngine = textToSpeechEngine
                ) as T
            }
        }
    }
}
