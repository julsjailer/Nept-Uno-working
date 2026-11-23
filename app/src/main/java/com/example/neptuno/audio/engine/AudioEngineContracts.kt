package com.example.neptuno.audio.engine

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.example.neptuno.core.model.AudioRouteDirection
import com.example.neptuno.core.model.AudioRouteInfo
import com.example.neptuno.core.model.DeviceVoiceInfo
import com.example.neptuno.core.model.VoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SttEvent {
    data object ListeningStarted : SttEvent
    data class PartialTranscript(val text: String) : SttEvent
    data class FinalTranscript(val text: String) : SttEvent
    data class Error(
        val message: String,
        val isServiceUnavailable: Boolean = false,
        val isNetworkError: Boolean = false
    ) : SttEvent
    data object Stopped : SttEvent
}

data class SttAvailability(
    val isServiceAvailable: Boolean,
    val isOnDeviceSupported: Boolean,
    val note: String
)

/**
 * Contratos de la capa de audio bidireccional de Neptuno.
 * Separa captura PCM, reproducción, reconocimiento de voz (STT) y síntesis de voz (TTS).
 */
interface AudioCaptureEngine {
    val isCapturing: StateFlow<Boolean>
    suspend fun startCapture(): Result<Unit>
    suspend fun stopCapture()
    fun release() {}
}

interface AudioPlaybackEngine {
    val isPlaying: StateFlow<Boolean>
    suspend fun playPcmChunk(pcmData: ByteArray)
    suspend fun stopImmediately()
    fun release() {}
}

interface SpeechToTextEngine {
    val isIntegrationReady: Boolean
    val isListening: StateFlow<Boolean>
    fun checkAvailability(): SttAvailability
    fun observePartialTranscripts(): Flow<String>
    fun observeEvents(): Flow<SttEvent>
    suspend fun startListening(languageCode: String = "es", preferOffline: Boolean = false): Result<Unit>
    suspend fun stopListening()
    fun cancel()
    fun release()
}

interface TextToSpeechEngine {
    val isIntegrationReady: Boolean
    val isSpeaking: StateFlow<Boolean>
    val availableVoices: StateFlow<List<DeviceVoiceInfo>>
    val availableLanguages: StateFlow<List<String>>
    val lastError: StateFlow<String?>

    suspend fun initialize(): Boolean
    fun resolveVoiceProfiles(languageCode: String): List<VoiceProfile>
    suspend fun speak(
        text: String,
        voiceId: String,
        languageCode: String = "es",
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        preferredDeviceVoiceName: String? = null
    ): Result<Unit>
    suspend fun interrupt()
    fun release()
}

/**
 * Coordinador de audio bidireccional e interrupción inmediata (barge-in).
 */
class BidirectionalAudioCoordinator {
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _lastInterruptionMillis = MutableStateFlow<Long?>(null)
    val lastInterruptionMillis: StateFlow<Long?> = _lastInterruptionMillis.asStateFlow()

    fun markSessionActive(active: Boolean) {
        _isSessionActive.value = active
    }

    fun interruptCurrentOutput() {
        _isSessionActive.value = false
        _lastInterruptionMillis.value = System.currentTimeMillis()
    }
}

/**
 * Catálogo extensible de perfiles de voz.
 * Se enlaza dinámicamente con Android TextToSpeech para determinar disponibilidad real en el dispositivo.
 */
object VoiceProfileCatalog {
    const val ID_OCEAN_NEUTRAL = "neptuno_ocean_neutral"
    const val ID_ABYSSAL_DEEP = "neptuno_abyssal_deep"
    const val ID_SONAR_CRISP = "neptuno_sonar_crisp"
    const val ID_ANDROID_SYSTEM = "android_system_tts"

    val profiles: List<VoiceProfile> = listOf(
        VoiceProfile(
            id = ID_OCEAN_NEUTRAL,
            displayName = "Neptuno · Base Oceánica",
            description = "Timbre sereno y equilibrado sobre el motor TextToSpeech del dispositivo.",
            engineType = "Android TextToSpeech",
            isAvailableNow = false,
            availabilityNote = "Verificando motor TTS del dispositivo…",
            basePitch = 0.98f,
            baseRate = 1.0f
        ),
        VoiceProfile(
            id = ID_ABYSSAL_DEEP,
            displayName = "Abisal · Profunda",
            description = "Registro más grave y pausado mediante ajuste acústico sobre una voz compatible.",
            engineType = "Android TextToSpeech",
            isAvailableNow = false,
            availabilityNote = "Verificando motor TTS del dispositivo…",
            basePitch = 0.84f,
            baseRate = 0.93f
        ),
        VoiceProfile(
            id = ID_SONAR_CRISP,
            displayName = "Sonar · Precisión",
            description = "Articulación ágil para respuestas rápidas (síntesis local TTS; Gemini Live sigue pendiente).",
            engineType = "Android TTS · Gemini Live pendiente",
            isAvailableNow = false,
            availabilityNote = "Verificando motor TTS del dispositivo…",
            basePitch = 1.04f,
            baseRate = 1.12f
        ),
        VoiceProfile(
            id = ID_ANDROID_SYSTEM,
            displayName = "Voz del sistema Android",
            description = "Utiliza directamente la voz predeterminada instalada en Android.",
            engineType = "Android TextToSpeech",
            isAvailableNow = false,
            availabilityNote = "Verificando motor TTS del dispositivo…",
            basePitch = 1.0f,
            baseRate = 1.0f
        )
    )
}

/**
 * Contrato preparado para la fase de enrutamiento activo de audio (AudioManager.setCommunicationDevice / SCO / A2DP).
 * En esta fase el cambio activo aún no se ejecuta; solo se detectan dispositivos y se guarda la preferencia local.
 */
interface AudioRoutingController {
    val isActiveRoutingSupported: Boolean get() = false
    suspend fun applyPreferredInputRoute(deviceId: Int): Result<Unit>
    suspend fun applyPreferredOutputRoute(deviceId: Int): Result<Unit>
}

class PendingAudioRoutingController : AudioRoutingController {
    override suspend fun applyPreferredInputRoute(deviceId: Int): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("El cambio activo de ruta de entrada se implementará en la fase de Bluetooth y audio.")
        )
    }

    override suspend fun applyPreferredOutputRoute(deviceId: Int): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("El cambio activo de ruta de salida se implementará en la fase de Bluetooth y audio.")
        )
    }
}

/**
 * Inspector real de rutas de entrada y salida de audio del sistema mediante AudioManager (API 29+).
 * No inventa dispositivos: solo reporta los que Android expone realmente.
 */
class AndroidAudioRouteManager(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun getAvailableInputRoutes(): List<AudioRouteInfo> {
        val manager = audioManager ?: return emptyList()
        return manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .map { device -> device.toAudioRouteInfo(AudioRouteDirection.INPUT) }
            .distinctBy { it.id }
    }

    fun getAvailableOutputRoutes(): List<AudioRouteInfo> {
        val manager = audioManager ?: return emptyList()
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { device -> device.toAudioRouteInfo(AudioRouteDirection.OUTPUT) }
            .distinctBy { it.id }
    }

    private fun AudioDeviceInfo.toAudioRouteInfo(direction: AudioRouteDirection): AudioRouteInfo {
        val isBt = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                ))

        val label = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Micrófono integrado"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz integrado"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricular interno"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con cable"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Audífonos con cable"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (Voz SCO)"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (Audio A2DP)"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "Audio USB"
            else -> if (isBt) "Audio Bluetooth LE" else "Dispositivo de sistema"
        }

        val name = productName?.toString()?.takeIf { it.isNotBlank() } ?: label
        return AudioRouteInfo(
            id = id,
            productName = name,
            typeLabel = label,
            direction = direction,
            isBluetooth = isBt
        )
    }
}
