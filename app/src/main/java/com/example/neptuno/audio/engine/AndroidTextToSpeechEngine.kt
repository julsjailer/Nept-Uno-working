package com.example.neptuno.audio.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.neptuno.core.model.DeviceVoiceInfo
import com.example.neptuno.core.model.VoiceProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Implementación real de TextToSpeechEngine usando android.speech.tts.TextToSpeech.
 * Consulta voces e idiomas instalados realmente, aplica velocidad/tono/voz, soporta interrupción
 * inmediata (barge-in) y refleja con honestidad cuándo una voz o idioma no está disponible.
 */
class AndroidTextToSpeechEngine(
    context: Context
) : TextToSpeechEngine {

    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    private var tts: TextToSpeech? = null
    private var initReady: Boolean = false
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private var activeUtteranceDeferred: CompletableDeferred<Result<Unit>>? = null
    private var activeUtteranceId: String? = null

    override val isIntegrationReady: Boolean
        get() = initReady

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<DeviceVoiceInfo>>(emptyList())
    override val availableVoices: StateFlow<List<DeviceVoiceInfo>> = _availableVoices.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<String>>(emptyList())
    override val availableLanguages: StateFlow<List<String>> = _availableLanguages.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override suspend fun initialize(): Boolean {
        if (initReady && tts != null) return true

        val deferred = initMutex.withLock {
            if (initReady && tts != null) return true
            initDeferred?.let { return@withLock it }

            val created = CompletableDeferred<Boolean>()
            initDeferred = created

            runCatching {
                val instance = TextToSpeech(appContext) { status ->
                    val ok = status == TextToSpeech.SUCCESS
                    initReady = ok
                    if (ok) {
                        _lastError.value = null
                        refreshCatalogFromEngine()
                    } else {
                        _lastError.value = "No se pudo inicializar el motor TextToSpeech del dispositivo."
                    }
                    created.complete(ok)
                }
                instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            activeUtteranceDeferred?.complete(Result.success(Unit))
                            activeUtteranceDeferred = null
                            activeUtteranceId = null
                        }
                    }

                    @Deprecated("Deprecated in API 21")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            val err = IllegalStateException("Error durante la síntesis de voz TTS.")
                            _lastError.value = err.message
                            activeUtteranceDeferred?.complete(Result.failure(err))
                            activeUtteranceDeferred = null
                            activeUtteranceId = null
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            val err = IllegalStateException("Error de síntesis TTS (código $errorCode).")
                            _lastError.value = err.message
                            activeUtteranceDeferred?.complete(Result.failure(err))
                            activeUtteranceDeferred = null
                            activeUtteranceId = null
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = false
                            activeUtteranceDeferred?.complete(Result.success(Unit))
                            activeUtteranceDeferred = null
                            activeUtteranceId = null
                        }
                    }
                })
                tts = instance
            }.onFailure { throwable ->
                initReady = false
                _lastError.value = throwable.localizedMessage ?: "Motor TTS no disponible."
                created.complete(false)
            }

            created
        }

        val result = withTimeoutOrNull(5_000L) { deferred.await() } ?: false
        if (result) {
            refreshCatalogFromEngine()
        }
        return result
    }

    override fun resolveVoiceProfiles(languageCode: String): List<VoiceProfile> {
        val engine = tts
        val targetLocale = localeForCode(languageCode)
        val langSupported = if (initReady && engine != null) {
            isLocaleSupported(engine, targetLocale)
        } else {
            false
        }

        val voices = _availableVoices.value
        val matchingLangVoices = voices.filter {
            it.localeTag.lowercase().startsWith(languageCode.lowercase())
        }

        return VoiceProfileCatalog.profiles.map { base ->
            if (!initReady || engine == null) {
                base.copy(
                    isAvailableNow = false,
                    availabilityNote = _lastError.value
                        ?: "Motor TextToSpeech no inicializado en este dispositivo."
                )
            } else if (!langSupported && matchingLangVoices.isEmpty()) {
                base.copy(
                    isAvailableNow = false,
                    availabilityNote = "Sin datos de voz TTS compatibles para '${targetLocale.displayLanguage}'."
                )
            } else {
                val detail = when (base.id) {
                    VoiceProfileCatalog.ID_ABYSSAL_DEEP ->
                        "Operativo con ajuste grave (${matchingLangVoices.size} voces locales en ${targetLocale.language.uppercase()})."
                    VoiceProfileCatalog.ID_SONAR_CRISP ->
                        "Operativo con Android TTS ágil (Gemini Live en tiempo real aún pendiente)."
                    VoiceProfileCatalog.ID_ANDROID_SYSTEM ->
                        "Operativo con la voz nativa del sistema (${targetLocale.language.uppercase()})."
                    else ->
                        "Operativo sobre Android TTS (${matchingLangVoices.size} voces compatibles en ${targetLocale.language.uppercase()})."
                }
                base.copy(
                    isAvailableNow = true,
                    availabilityNote = detail
                )
            }
        }
    }

    override suspend fun speak(
        text: String,
        voiceId: String,
        languageCode: String,
        speechRate: Float,
        pitch: Float,
        preferredDeviceVoiceName: String?
    ): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return Result.success(Unit)

        if (!initialize()) {
            val msg = _lastError.value ?: "Motor TextToSpeech no disponible en este dispositivo."
            return Result.failure(IllegalStateException(msg))
        }

        val engine = tts ?: return Result.failure(IllegalStateException("Motor TextToSpeech no inicializado."))

        // Detener cualquier locución anterior antes de iniciar la nueva
        interrupt()

        val targetLocale = localeForCode(languageCode)
        val langResult = runCatching { engine.setLanguage(targetLocale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val msg = "Idioma '${targetLocale.displayLanguage}' sin datos de voz TTS instalados."
            _lastError.value = msg
            return Result.failure(IllegalStateException(msg))
        }

        val profile = VoiceProfileCatalog.profiles.find { it.id == voiceId }
        val finalRate = ((profile?.baseRate ?: 1.0f) * speechRate).coerceIn(0.5f, 2.0f)
        val finalPitch = ((profile?.basePitch ?: 1.0f) * pitch).coerceIn(0.5f, 2.0f)

        runCatching {
            engine.setSpeechRate(finalRate)
            engine.setPitch(finalPitch)
        }

        // Aplicar una voz específica instalada si el usuario eligió una y sigue disponible
        val installedVoices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val selectedVoice = selectBestVoice(
            installedVoices = installedVoices,
            preferredDeviceVoiceName = preferredDeviceVoiceName,
            voiceProfileId = voiceId,
            languageCode = languageCode
        )
        if (selectedVoice != null) {
            runCatching { engine.voice = selectedVoice }
        }

        val utteranceId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Result<Unit>>()
        activeUtteranceId = utteranceId
        activeUtteranceDeferred = completion

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        _isSpeaking.value = true
        val status = runCatching {
            engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        if (status == TextToSpeech.ERROR) {
            _isSpeaking.value = false
            activeUtteranceId = null
            activeUtteranceDeferred = null
            val msg = "No se pudo iniciar la reproducción TTS."
            _lastError.value = msg
            return Result.failure(IllegalStateException(msg))
        }

        return withTimeoutOrNull(120_000L) { completion.await() } ?: run {
            interrupt()
            Result.failure(IllegalStateException("Tiempo de espera de reproducción TTS agotado."))
        }
    }

    override suspend fun interrupt() {
        _isSpeaking.value = false
        runCatching { tts?.stop() }
        activeUtteranceDeferred?.complete(Result.success(Unit))
        activeUtteranceDeferred = null
        activeUtteranceId = null
    }

    override fun release() {
        _isSpeaking.value = false
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        initReady = false
        initDeferred = null
        activeUtteranceDeferred?.complete(Result.success(Unit))
        activeUtteranceDeferred = null
        activeUtteranceId = null
    }

    private fun refreshCatalogFromEngine() {
        val engine = tts ?: return
        val rawVoices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val mappedVoices = rawVoices
            .filter { !it.isNetworkConnectionRequired || it.features.isNotEmpty() }
            .sortedWith(compareBy<Voice>({ it.locale.language }, { it.isNetworkConnectionRequired }, { it.name }))
            .map { voice ->
                val qualityText = when {
                    voice.quality >= Voice.QUALITY_VERY_HIGH -> "Muy alta"
                    voice.quality >= Voice.QUALITY_HIGH -> "Alta"
                    voice.quality >= Voice.QUALITY_NORMAL -> "Normal"
                    else -> "Básica"
                }
                val networkTag = if (voice.isNetworkConnectionRequired) "Red" else "Local"
                DeviceVoiceInfo(
                    name = voice.name,
                    displayName = "${voice.locale.displayLanguage} (${voice.locale.country.ifBlank { voice.locale.language.uppercase() }}) · $networkTag",
                    localeTag = voice.locale.toLanguageTag(),
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    qualityLabel = qualityText
                )
            }
        _availableVoices.value = mappedVoices

        val langs = runCatching {
            engine.availableLanguages.orEmpty()
                .map { it.toLanguageTag() }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())
        _availableLanguages.value = langs
    }

    private fun selectBestVoice(
        installedVoices: Set<Voice>,
        preferredDeviceVoiceName: String?,
        voiceProfileId: String,
        languageCode: String
    ): Voice? {
        if (!preferredDeviceVoiceName.isNullOrBlank()) {
            installedVoices.find { it.name == preferredDeviceVoiceName }?.let { return it }
        }

        val matchingLanguage = installedVoices.filter {
            it.locale.language.equals(languageCode, ignoreCase = true)
        }
        if (matchingLanguage.isEmpty()) return null

        return when (voiceProfileId) {
            VoiceProfileCatalog.ID_ABYSSAL_DEEP -> {
                matchingLanguage.firstOrNull { voice ->
                    !voice.isNetworkConnectionRequired &&
                        (voice.name.contains("male", ignoreCase = true) ||
                            voice.name.contains("-b-", ignoreCase = true) ||
                            voice.name.contains("-d-", ignoreCase = true))
                } ?: matchingLanguage.firstOrNull { !it.isNetworkConnectionRequired }
                    ?: matchingLanguage.first()
            }
            VoiceProfileCatalog.ID_SONAR_CRISP -> {
                matchingLanguage.firstOrNull {
                    !it.isNetworkConnectionRequired && it.quality >= Voice.QUALITY_NORMAL
                } ?: matchingLanguage.first()
            }
            else -> {
                matchingLanguage.firstOrNull { !it.isNetworkConnectionRequired }
                    ?: matchingLanguage.first()
            }
        }
    }

    private fun isLocaleSupported(engine: TextToSpeech, locale: Locale): Boolean {
        val code = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        return code >= TextToSpeech.LANG_AVAILABLE
    }

    private fun localeForCode(code: String): Locale = when (code.lowercase()) {
        "es" -> Locale("es", "ES")
        "en" -> Locale.US
        "pt" -> Locale("pt", "BR")
        else -> Locale.forLanguageTag(code)
    }
}
