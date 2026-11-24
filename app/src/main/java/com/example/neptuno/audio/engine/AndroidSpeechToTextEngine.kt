package com.example.neptuno.audio.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.neptuno.core.PermissionHelper
import com.example.neptuno.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementación real de SpeechToTextEngine usando android.speech.SpeechRecognizer.
 * Ejecuta todas las llamadas del reconocedor en Dispatchers.Main.immediate como exige Android SDK,
 * evita sesiones simultáneas y mapea errores reales del servicio de reconocimiento.
 */
class AndroidSpeechToTextEngine(
    context: Context,
    private val preferencesRepository: UserPreferencesRepository
) : SpeechToTextEngine {

    private val appContext = context.applicationContext
    private val bluetoothAudioRouter = AndroidBluetoothAudioRouter(appContext)
    private var activeInputRoute: AudioInputRouteSelection? = null

    override val isIntegrationReady: Boolean
        get() = checkAvailability().isServiceAvailable

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 32)

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeSession = false

    override fun checkAvailability(): SttAvailability {
        val standardAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        val onDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }
                .getOrDefault(false)
        } else {
            false
        }

        val note = when {
            standardAvailable && onDeviceAvailable ->
                "Reconocimiento del sistema y reconocimiento local en dispositivo disponibles."
            standardAvailable ->
                "Reconocimiento disponible mediante servicio del sistema (requiere red si no hay paquete offline)."
            onDeviceAvailable ->
                "Reconocimiento local en dispositivo disponible."
            else ->
                "No hay servicio de reconocimiento de voz instalado o habilitado en este dispositivo."
        }

        return SttAvailability(
            isServiceAvailable = standardAvailable || onDeviceAvailable,
            isOnDeviceSupported = onDeviceAvailable,
            note = note
        )
    }

    override fun observePartialTranscripts(): Flow<String> =
        _events.filterIsInstance<SttEvent.PartialTranscript>().map { it.text }

    override fun observeEvents(): Flow<SttEvent> = _events.asSharedFlow()

    override suspend fun startListening(languageCode: String, preferOffline: Boolean): Result<Unit> {
        if (!PermissionHelper.hasRecordAudioPermission(appContext)) {
            val errorMsg = "Permiso de micrófono (RECORD_AUDIO) no concedido."
            _events.tryEmit(SttEvent.Error(errorMsg))
            return Result.failure(SecurityException(errorMsg))
        }

        val availability = checkAvailability()
        if (!availability.isServiceAvailable) {
            val errorMsg = availability.note
            _events.tryEmit(SttEvent.Error(errorMsg, isServiceUnavailable = true))
            return Result.failure(IllegalStateException(errorMsg))
        }

        val audioPreferences = preferencesRepository.userPreferences.first()

        return withContext(Dispatchers.Main.immediate) {
            if (activeSession || _isListening.value) {
                return@withContext Result.success(Unit)
            }

            runCatching {
                activeInputRoute = bluetoothAudioRouter.configureCommunicationInput(
                    preferredInputId = audioPreferences.preferredAudioInputId,
                    preferBluetooth = audioPreferences.preferBluetoothAudio
                )

                val useOnDeviceOnly = preferOffline &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    availability.isOnDeviceSupported

                val recognizer = ensureRecognizer(useOnDeviceOnly)
                val bcp47 = mapLanguageTag(languageCode)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcp47)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    if (preferOffline) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                activeSession = true
                _isListening.value = true
                recognizer.startListening(intent)
                _events.tryEmit(SttEvent.ListeningStarted)
                Unit
            }.onFailure { throwable ->
                activeSession = false
                _isListening.value = false
                val msg = throwable.localizedMessage ?: "No se pudo iniciar el reconocimiento de voz."
                bluetoothAudioRouter.release()
                activeInputRoute = null
                _events.tryEmit(SttEvent.Error(msg, isServiceUnavailable = true))
            }
        }
    }

    override suspend fun stopListening() {
        withContext(Dispatchers.Main.immediate) {
            if (activeSession) {
                runCatching { speechRecognizer?.stopListening() }
            }
            activeSession = false
            _isListening.value = false
            bluetoothAudioRouter.release()
            activeInputRoute = null
            _events.tryEmit(SttEvent.Stopped)
        }
    }

    override fun cancel() {
        activeSession = false
        _isListening.value = false
        val cancelAction = {
            runCatching { speechRecognizer?.cancel() }
            bluetoothAudioRouter.release()
            activeInputRoute = null
            _events.tryEmit(SttEvent.Stopped)
            Unit
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            cancelAction()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post { cancelAction() }
        }
    }

    override fun release() {
        activeSession = false
        _isListening.value = false
        val destroyAction = {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
            bluetoothAudioRouter.release()
            activeInputRoute = null
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            destroyAction()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post { destroyAction() }
        }
    }

    private fun ensureRecognizer(useOnDevice: Boolean): SpeechRecognizer {
        speechRecognizer?.let { existing ->
            runCatching { existing.destroy() }
            speechRecognizer = null
        }

        val recognizer = if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                activeSession = false
                _isListening.value = false
            }

            override fun onError(error: Int) {
                activeSession = false
                _isListening.value = false
                bluetoothAudioRouter.release()
                activeInputRoute = null
                val mapped = mapRecognitionError(error)
                _events.tryEmit(mapped)
            }

            override fun onResults(results: Bundle?) {
                activeSession = false
                _isListening.value = false
                bluetoothAudioRouter.release()
                activeInputRoute = null
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) {
                    _events.tryEmit(SttEvent.FinalTranscript(text))
                } else {
                    _events.tryEmit(SttEvent.Error("No se detectó texto reconocible en el audio."))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    _events.tryEmit(SttEvent.PartialTranscript(partial))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizer = recognizer
        return recognizer
    }

    private fun mapRecognitionError(errorCode: Int): SttEvent.Error {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO ->
                SttEvent.Error("Error al capturar audio desde el micrófono.")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                SttEvent.Error("Permiso de micrófono insuficiente para reconocer voz.")
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                SttEvent.Error(
                    "El reconocimiento de voz requiere conexión a Internet o un paquete offline instalado.",
                    isNetworkError = true
                )
            SpeechRecognizer.ERROR_SERVER ->
                SttEvent.Error("El servicio de reconocimiento devolvió un error del servidor.", isNetworkError = true)
            SpeechRecognizer.ERROR_NO_MATCH ->
                SttEvent.Error("No se entendió ninguna frase. Inténtalo de nuevo más cerca del micrófono.")
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                SttEvent.Error("Tiempo de espera agotado sin detectar voz.")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                SttEvent.Error("El reconocedor de voz está ocupado. Espera un instante e inténtalo de nuevo.")
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                SttEvent.Error("El idioma seleccionado no está disponible en el reconocedor de este dispositivo.")
            else ->
                SttEvent.Error("Servicio de reconocimiento no disponible (código $errorCode).", isServiceUnavailable = true)
        }
    }

    companion object {
        fun mapLanguageTag(languageCode: String): String = when (languageCode.lowercase()) {
            "es" -> "es-ES"
            "en" -> "en-US"
            "pt" -> "pt-BR"
            else -> languageCode
        }
    }
}
