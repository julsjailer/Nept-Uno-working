package com.example.neptuno.app

import android.content.Context
import com.example.neptuno.ai.api.AiProviderRegistry
import com.example.neptuno.ai.gemini.GeminiFirebaseProvider
import com.example.neptuno.ai.gemini.GeminiPendingProvider
import com.example.neptuno.ai.local.LocalEnginePendingProvider
import com.example.neptuno.android.services.BluetoothDeviceController
import com.example.neptuno.audio.engine.AndroidAudioCaptureEngine
import com.example.neptuno.audio.engine.AndroidAudioPlaybackEngine
import com.example.neptuno.audio.engine.AndroidAudioRouteManager
import com.example.neptuno.audio.engine.AndroidSpeechToTextEngine
import com.example.neptuno.audio.engine.AndroidTextToSpeechEngine
import com.example.neptuno.audio.engine.AudioCaptureEngine
import com.example.neptuno.audio.engine.AudioPlaybackEngine
import com.example.neptuno.audio.engine.AudioRoutingController
import com.example.neptuno.audio.engine.BidirectionalAudioCoordinator
import com.example.neptuno.audio.engine.PendingAudioRoutingController
import com.example.neptuno.audio.engine.SpeechToTextEngine
import com.example.neptuno.audio.engine.TextToSpeechEngine
import com.example.neptuno.core.DefaultDispatcherProvider
import com.example.neptuno.core.DispatcherProvider
import com.example.neptuno.core.NetworkMonitor
import com.example.neptuno.data.local.ConversationRepository
import com.example.neptuno.data.local.LocalConversationRepository
import com.example.neptuno.data.local.LocalMemoryRepository
import com.example.neptuno.data.local.MemoryRepository
import com.example.neptuno.data.local.NeptunoDatabase
import com.example.neptuno.data.local.UserPreferencesRepository
import com.example.neptuno.feature.tools.ToolRegistry
import com.example.neptuno.voice.wake.VoskWakeWordEngine
import com.example.neptuno.voice.wake.WakeActivationBus
import com.example.neptuno.voice.wake.WakeWordEngine

/**
 * Contenedor manual de dependencias de NEPTUNO.
 * Centraliza repositorios, motores reales de voz (STT/TTS), audio PCM, controladores del sistema y proveedores de IA.
 */
class NeptunoAppContainer(context: Context) {

    private val appContext = context.applicationContext

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    val database: NeptunoDatabase by lazy {
        NeptunoDatabase.getInstance(appContext)
    }

    val conversationRepository: ConversationRepository by lazy {
        LocalConversationRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao()
        )
    }

    val memoryRepository: MemoryRepository by lazy {
        LocalMemoryRepository(
            memoryDao = database.memoryDao()
        )
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(appContext)
    }

    val bluetoothController: BluetoothDeviceController by lazy {
        BluetoothDeviceController(appContext)
    }

    val audioRouteManager: AndroidAudioRouteManager by lazy {
        AndroidAudioRouteManager(appContext)
    }

    val audioRoutingController: AudioRoutingController by lazy {
        PendingAudioRoutingController()
    }

    val audioCaptureEngine: AudioCaptureEngine by lazy {
        AndroidAudioCaptureEngine(appContext)
    }

    val audioPlaybackEngine: AudioPlaybackEngine by lazy {
        AndroidAudioPlaybackEngine()
    }

    val speechToTextEngine: SpeechToTextEngine by lazy {
        AndroidSpeechToTextEngine(appContext, preferencesRepository)
    }

    val textToSpeechEngine: TextToSpeechEngine by lazy {
        AndroidTextToSpeechEngine(appContext)
    }

    val audioCoordinator: BidirectionalAudioCoordinator by lazy {
        BidirectionalAudioCoordinator()
    }

    val aiProviderRegistry: AiProviderRegistry by lazy {
        AiProviderRegistry(
            providers = listOf(
                GeminiFirebaseProvider(usageStore = com.example.neptuno.ai.gemini.SharedPrefsGeminiUsageStore(appContext)),
                GeminiPendingProvider(),
                LocalEnginePendingProvider()
            )
        )
    }

    val wakeWordEngine: WakeWordEngine by lazy {
        // Motor Vosk (local, español). Sin probar en teléfono todavía (ver UNANZE.md).
        VoskWakeWordEngine(appContext, preferencesRepository)
    }

    val wakeActivationBus: WakeActivationBus by lazy { WakeActivationBus() }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry()
    }

    fun releaseAudioEngines() {
        speechToTextEngine.release()
        textToSpeechEngine.release()
        audioCaptureEngine.release()
        audioPlaybackEngine.release()
    }
}
