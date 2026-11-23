package com.example.neptuno

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.neptuno.ai.api.AiProvider
import com.example.neptuno.ai.api.AiProviderCapabilities
import com.example.neptuno.ai.api.AiProviderRegistry
import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.ai.gemini.GeminiPendingProvider
import com.example.neptuno.audio.engine.AudioPlaybackEngine
import com.example.neptuno.audio.engine.BidirectionalAudioCoordinator
import com.example.neptuno.audio.engine.SpeechToTextEngine
import com.example.neptuno.audio.engine.SttAvailability
import com.example.neptuno.audio.engine.SttEvent
import com.example.neptuno.audio.engine.TextToSpeechEngine
import com.example.neptuno.audio.engine.VoiceProfileCatalog
import com.example.neptuno.core.NetworkMonitor
import com.example.neptuno.core.NetworkStatus
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.AppLanguage
import com.example.neptuno.core.model.AssistantState
import com.example.neptuno.core.model.DeviceVoiceInfo
import com.example.neptuno.core.model.MessageSender
import com.example.neptuno.core.model.PersonalityMode
import com.example.neptuno.core.model.VoiceProfile
import com.example.neptuno.data.local.LocalConversationRepository
import com.example.neptuno.data.local.LocalMemoryRepository
import com.example.neptuno.data.local.NeptunoDatabase
import com.example.neptuno.data.local.UserPreferencesRepository
import com.example.neptuno.feature.chat.ChatViewModel
import com.example.neptuno.feature.settings.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas unitarias y Robolectric de Fase 2 (Voz STT/TTS, streaming TextDelta, barge-in y persistencia).
 * Los dobles de prueba viven exclusivamente en src/test para validar contratos sin simular funciones en producción.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NeptunoVoiceAndChatTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: NeptunoDatabase
    private lateinit var conversationRepo: LocalConversationRepository
    private lateinit var memoryRepo: LocalMemoryRepository
    private lateinit var preferencesRepo: UserPreferencesRepository
    private lateinit var fakeNetworkMonitor: TestNetworkMonitor
    private lateinit var fakeStt: TestSpeechToTextEngine
    private lateinit var fakeTts: TestTextToSpeechEngine
    private lateinit var fakePlayback: TestAudioPlaybackEngine
    private lateinit var audioCoordinator: BidirectionalAudioCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NeptunoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationRepo = LocalConversationRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao()
        )
        memoryRepo = LocalMemoryRepository(database.memoryDao())
        preferencesRepo = UserPreferencesRepository(context)
        runBlocking {
            preferencesRepo.setSelectedVoice(VoiceProfileCatalog.ID_OCEAN_NEUTRAL)
            preferencesRepo.setSelectedDeviceVoiceName(null)
            preferencesRepo.setTtsSpeechRate(1.0f)
            preferencesRepo.setTtsPitch(1.0f)
            preferencesRepo.setAutoSpeakResponses(true)
            preferencesRepo.setPersonalityMode(PersonalityMode.ANALYTICAL)
            preferencesRepo.setLanguage(AppLanguage.SPANISH)
            preferencesRepo.setOfflineMode(false)
            preferencesRepo.setBargeInEnabled(true)
            preferencesRepo.setAutoSendVoice(true)
            preferencesRepo.setContinuousConversation(false)
            preferencesRepo.setMemoryAuthorized(true)
            preferencesRepo.setPersistConversationHistory(true)
            preferencesRepo.setPreferredAiProvider("gemini_firebase")
        }
        fakeNetworkMonitor = TestNetworkMonitor(context)
        fakeStt = TestSpeechToTextEngine()
        fakeTts = TestTextToSpeechEngine()
        fakePlayback = TestAudioPlaybackEngine()
        audioCoordinator = BidirectionalAudioCoordinator()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `continuous conversation preference defaults off and persists changes`() = runTest(testDispatcher) {
        assertFalse(preferencesRepo.userPreferences.first().continuousConversation)
        preferencesRepo.setContinuousConversation(true)
        assertTrue(preferencesRepo.userPreferences.first().continuousConversation)
        preferencesRepo.setContinuousConversation(false)
        assertFalse(preferencesRepo.userPreferences.first().continuousConversation)
    }

    @Test
    fun `stt start stop partial and final transcripts update draft input and listening state`() = runTest(testDispatcher) {
        val provider = TestStreamingAiProvider(id = "gemini_firebase", chunks = listOf("Ok"))
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }

        preferencesRepo.setLanguage(AppLanguage.SPANISH)
        preferencesRepo.setAutoSendVoice(false)
        awaitCondition { vm.uiState.value.activeConversationId != null }

        // Iniciar escucha STT
        vm.onMicrophonePermissionResult(true)
        awaitCondition { fakeStt.isListening.value && vm.uiState.value.assistantState == AssistantState.LISTENING }

        assertEquals("es", fakeStt.lastStartedLanguage)

        // Resultados parciales y resultado final en el campo de entrada
        fakeStt.emitEvent(SttEvent.PartialTranscript("Hola Nep"))
        awaitCondition { vm.draftInput.value == "Hola Nep" }

        fakeStt.emitEvent(SttEvent.FinalTranscript("Hola Neptuno"))
        awaitCondition {
            vm.draftInput.value == "Hola Neptuno" &&
                vm.uiState.value.assistantState == AssistantState.IDLE
        }

        // Detener escucha explícitamente tras reiniciar
        vm.onMicrophonePermissionResult(true)
        awaitCondition { fakeStt.isListening.value }
        vm.onMicrophonePermissionResult(true)
        awaitCondition { !fakeStt.isListening.value }

        collector.cancel()
    }

    @Test
    fun `con envio automatico activado la transcripcion final se envia sola`() = runTest(testDispatcher) {
        val provider = TestStreamingAiProvider(id = "gemini_firebase", chunks = listOf("Ok"))
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }

        preferencesRepo.setLanguage(AppLanguage.SPANISH)
        preferencesRepo.setAutoSendVoice(true)
        awaitCondition { vm.uiState.value.activeConversationId != null }

        vm.onMicrophonePermissionResult(true)
        awaitCondition { fakeStt.isListening.value }

        fakeStt.emitEvent(SttEvent.PartialTranscript("Hola Nep"))
        awaitCondition { vm.draftInput.value == "Hola Nep" }

        // Al llegar el resultado final el mensaje se envía y el borrador queda vacío.
        fakeStt.emitEvent(SttEvent.FinalTranscript("Hola Neptuno"))
        awaitCondition { vm.draftInput.value.isEmpty() }

        collector.cancel()
    }

    @Test
    fun `stt service unavailable and microphone errors surface honest error state`() = runTest(testDispatcher) {
        val provider = TestStreamingAiProvider(id = "gemini_firebase", chunks = listOf("Ok"))
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        awaitCondition { vm.uiState.value.activeConversationId != null }

        // Permiso denegado
        vm.onMicrophonePermissionResult(false)
        awaitCondition {
            vm.uiState.value.assistantState == AssistantState.ERROR &&
                vm.uiState.value.statusDetail == "Error de micrófono"
        }

        // Servicio de reconocimiento ausente
        fakeStt.availability = SttAvailability(
            isServiceAvailable = false,
            isOnDeviceSupported = false,
            note = "No hay servicio de reconocimiento de voz instalado."
        )
        vm.onMicrophonePermissionResult(true)
        awaitCondition {
            vm.uiState.value.assistantState == AssistantState.ERROR &&
                vm.uiState.value.statusDetail == "Servicio de voz no disponible"
        }

        collector.cancel()
    }

    @Test
    fun `streaming text delta accumulates into one final room message and speaks via tts`() = runTest(testDispatcher) {
        val provider = TestStreamingAiProvider(
            id = "gemini_firebase",
            chunks = listOf("Hola ", "desde ", "Neptuno.")
        )
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }

        preferencesRepo.setSelectedVoice(VoiceProfileCatalog.ID_ABYSSAL_DEEP)
        preferencesRepo.setTtsSpeechRate(1.1f)
        preferencesRepo.setTtsPitch(0.9f)
        preferencesRepo.setAutoSpeakResponses(true)
        preferencesRepo.setPersistConversationHistory(true)
        awaitCondition { vm.uiState.value.activeConversationId != null }

        vm.onDraftChanged("Saluda")
        vm.sendMessage()
        awaitCondition {
            val convId = vm.uiState.value.activeConversationId ?: return@awaitCondition false
            val msgs = conversationRepo.observeMessages(convId).first()
            msgs.size == 2 && fakeTts.lastSpokenText != null
        }

        val convId = vm.uiState.value.activeConversationId!!
        val persisted = conversationRepo.observeMessages(convId).first()
        assertEquals(2, persisted.size)
        assertEquals(MessageSender.USER, persisted[0].sender)
        assertEquals(MessageSender.ASSISTANT, persisted[1].sender)
        assertEquals("Hola desde Neptuno.", persisted[1].content)
        assertNull(vm.uiState.value.streamingResponseText)

        // Verifica que TTS recibió la respuesta completa y el perfil de voz configurado
        assertEquals("Hola desde Neptuno.", fakeTts.lastSpokenText)
        assertEquals(VoiceProfileCatalog.ID_ABYSSAL_DEEP, fakeTts.lastVoiceId)
        assertEquals(1.1f, fakeTts.lastRate ?: 0f, 0.001f)
        assertEquals(0.9f, fakeTts.lastPitch ?: 0f, 0.001f)

        collector.cancel()
    }

    @Test
    fun `disabling conversation history keeps streaming messages out of room`() = runTest(testDispatcher) {
        val provider = TestStreamingAiProvider(
            id = "gemini_firebase",
            chunks = listOf("Respuesta ", "efímera")
        )
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }

        awaitCondition { vm.uiState.value.activeConversationId != null }
        val initialConvId = vm.uiState.value.activeConversationId!!
        preferencesRepo.setPersistConversationHistory(false)
        preferencesRepo.setAutoSpeakResponses(false)
        awaitCondition { !vm.uiState.value.persistHistoryEnabled }

        vm.onDraftChanged("Mensaje sin guardar")
        vm.sendMessage()
        awaitCondition { vm.uiState.value.messages.size == 2 }

        assertEquals(2, vm.uiState.value.messages.size)
        assertEquals("Respuesta efímera", vm.uiState.value.messages[1].content)
        assertTrue(conversationRepo.observeMessages(initialConvId).first().isEmpty())

        collector.cancel()
    }

    @Test
    fun `barge-in interrupts active response generation and stops tts and audio playback`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val provider = TestStreamingAiProvider(
            id = "gemini_firebase",
            chunks = listOf("Inicio parcial ", "continuación"),
            holdAfterFirstChunk = gate
        )
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }

        preferencesRepo.setBargeInEnabled(true)
        preferencesRepo.setAutoSpeakResponses(true)
        awaitCondition { vm.uiState.value.activeConversationId != null }

        vm.onDraftChanged("Explícame algo largo")
        vm.sendMessage()
        awaitCondition({
            "Before interrupt: stream=${vm.uiState.value.streamingResponseText}, " +
                "gen=${vm.uiState.value.isGeneratingText}, state=${vm.uiState.value.assistantState}, " +
                "detail=${vm.uiState.value.statusDetail}, msgs=${vm.uiState.value.messages.size}, " +
                "invocations=${provider.invocationCount}"
        }) {
            vm.uiState.value.streamingResponseText == "Inicio parcial " &&
                vm.uiState.value.isGeneratingText
        }

        // Barge-in al iniciar micrófono o pulsar detener
        vm.interruptActiveResponse()
        awaitCondition({
            "After interrupt: interrupted=${provider.interrupted}, ttsStop=${fakeTts.interruptedCount}, " +
                "pcmStop=${fakePlayback.stopCount}, gen=${vm.uiState.value.isGeneratingText}, " +
                "detail=${vm.uiState.value.statusDetail}"
        }) {
            provider.interrupted &&
                fakeTts.interruptedCount > 0 &&
                fakePlayback.stopCount > 0 &&
                !vm.uiState.value.isGeneratingText &&
                vm.uiState.value.statusDetail == "Interrumpido"
        }

        assertNull(vm.uiState.value.streamingResponseText)

        val convId = vm.uiState.value.activeConversationId!!
        awaitCondition({ "Messages size=${conversationRepo.observeMessages(convId).first().size}" }) {
            conversationRepo.observeMessages(convId).first().size == 2
        }
        val messages = conversationRepo.observeMessages(convId).first()
        assertEquals(2, messages.size)
        assertTrue(messages[1].isInterrupted)
        assertEquals("Inicio parcial", messages[1].content)

        collector.cancel()
    }

    @Test
    fun `switching to new session interrupts active tts playback`() = runTest(testDispatcher) {
        val provider = TestStreamingAiProvider(
            id = "gemini_firebase",
            chunks = listOf("Respuesta con audio")
        )
        fakeTts.holdSpeakUntilInterrupted = true
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        awaitCondition { vm.uiState.value.activeConversationId != null }

        vm.onDraftChanged("Habla")
        vm.sendMessage()
        awaitCondition { fakeTts.isSpeaking.value }

        vm.startNewSession()
        awaitCondition {
            !fakeTts.isSpeaking.value &&
                fakeTts.interruptedCount > 0 &&
                fakePlayback.stopCount > 0
        }

        collector.cancel()
    }

    @Test
    fun `offline state prevents online provider call and saves notice locally`() = runTest(testDispatcher) {
        val provider = TestStreamingAiProvider(id = "gemini_firebase", chunks = listOf("No debería llamarse"))
        fakeNetworkMonitor.statusFlow.value = NetworkStatus(isConnected = false, transportLabel = "Sin conexión")
        val vm = createChatViewModel(provider)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        awaitCondition { vm.uiState.value.activeConversationId != null && vm.uiState.value.isOffline }

        vm.onDraftChanged("Consulta sin internet")
        vm.sendMessage()
        val convId = vm.uiState.value.activeConversationId!!
        awaitCondition { conversationRepo.observeMessages(convId).first().size == 2 }

        assertEquals(0, provider.invocationCount)
        val msgs = conversationRepo.observeMessages(convId).first()
        assertEquals(2, msgs.size)
        assertEquals(MessageSender.SYSTEM_NOTICE, msgs[1].sender)
        assertTrue(msgs[1].content.contains("Sin conexión", ignoreCase = true))

        collector.cancel()
    }

    @Test
    fun `pending provider still reports pending integration honestly`() = runTest(testDispatcher) {
        val pending = GeminiPendingProvider()
        val vm = createChatViewModel(pending)
        val collector = backgroundScope.launch { vm.uiState.collect {} }

        preferencesRepo.setPreferredAiProvider(pending.id)
        awaitCondition { vm.uiState.value.activeConversationId != null && vm.uiState.value.isProviderPending }

        vm.onDraftChanged("Prueba pendiente")
        vm.sendMessage()
        val convId = vm.uiState.value.activeConversationId!!
        awaitCondition { conversationRepo.observeMessages(convId).first().size == 2 }

        val msgs = conversationRepo.observeMessages(convId).first()
        assertEquals(2, msgs.size)
        assertEquals(MessageSender.SYSTEM_NOTICE, msgs[1].sender)

        collector.cancel()
    }

    @Test
    fun `settings viewmodel resolves voice profiles and handles unavailable language gracefully`() = runTest(testDispatcher) {
        fakeTts.supportedLanguages = setOf("es")
        fakeTts.voicesList = listOf(
            DeviceVoiceInfo(
                name = "es-es-local",
                displayName = "Español (ES) · Local",
                localeTag = "es-ES",
                requiresNetwork = false,
                qualityLabel = "Alta"
            )
        )

        val settingsVm = SettingsViewModel(
            preferencesRepository = preferencesRepo,
            aiProviderRegistry = AiProviderRegistry(listOf(GeminiPendingProvider())),
            textToSpeechEngine = fakeTts
        )
        val collector = backgroundScope.launch { settingsVm.voiceProfiles.collect {} }
        val errorCollector = backgroundScope.launch { settingsVm.ttsError.collect {} }
        awaitCondition({ "voiceProfiles not all available: ${settingsVm.voiceProfiles.value}" }) {
            settingsVm.voiceProfiles.value.all { it.isAvailableNow }
        }

        // Cambiar a portugués no instalado -> perfiles marcados como no disponibles con honestidad
        settingsVm.setLanguage(AppLanguage.PORTUGUESE)
        awaitCondition({ "voiceProfiles still available after PT switch: ${settingsVm.voiceProfiles.value}" }) {
            settingsVm.voiceProfiles.value.none { it.isAvailableNow }
        }

        // Prueba de voz con error -> reporta error en ttsError
        fakeTts.failSpeakWith = IllegalStateException("Idioma sin datos TTS")
        settingsVm.testVoicePreview()
        awaitCondition({ "ttsError was ${settingsVm.ttsError.value}" }) {
            settingsVm.ttsError.value == "Idioma sin datos TTS"
        }

        collector.cancel()
        errorCollector.cancel()
    }

    private suspend fun awaitCondition(
        diagnostic: suspend () -> String = { "Condition timed out" },
        timeoutMillis: Long = 4_000L,
        condition: suspend () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            withContext(Dispatchers.Default) {
                delay(15L)
            }
        }
        assertTrue("${diagnostic()} (after ${timeoutMillis}ms)", condition())
    }

    private fun createChatViewModel(provider: AiProvider): ChatViewModel {
        return ChatViewModel(
            conversationRepository = conversationRepo,
            memoryRepository = memoryRepo,
            preferencesRepository = preferencesRepo,
            aiProviderRegistry = AiProviderRegistry(listOf(provider)),
            audioCoordinator = audioCoordinator,
            networkMonitor = fakeNetworkMonitor,
            speechToTextEngine = fakeStt,
            textToSpeechEngine = fakeTts,
            audioPlaybackEngine = fakePlayback
        )
    }

    private class TestNetworkMonitor(context: Context) : NetworkMonitor(context) {
        val statusFlow = MutableStateFlow(NetworkStatus(isConnected = true, transportLabel = "Wi-Fi"))
        override fun observeNetworkStatus(): Flow<NetworkStatus> = statusFlow
    }

    private class TestSpeechToTextEngine : SpeechToTextEngine {
        var availability = SttAvailability(true, true, "Disponible")
        var lastStartedLanguage: String? = null
        private val _isListening = MutableStateFlow(false)
        override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
        override val isIntegrationReady: Boolean get() = availability.isServiceAvailable
        private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 16)

        override fun checkAvailability(): SttAvailability = availability
        override fun observePartialTranscripts(): Flow<String> =
            _events.filterIsInstance<SttEvent.PartialTranscript>().map { it.text }
        override fun observeEvents(): Flow<SttEvent> = _events.asSharedFlow()

        override suspend fun startListening(languageCode: String, preferOffline: Boolean): Result<Unit> {
            if (!availability.isServiceAvailable) {
                return Result.failure(IllegalStateException(availability.note))
            }
            lastStartedLanguage = languageCode
            _isListening.value = true
            _events.tryEmit(SttEvent.ListeningStarted)
            return Result.success(Unit)
        }

        override suspend fun stopListening() {
            _isListening.value = false
            _events.tryEmit(SttEvent.Stopped)
        }

        override fun cancel() {
            _isListening.value = false
        }

        override fun release() {
            _isListening.value = false
        }

        fun emitEvent(event: SttEvent) {
            if (event is SttEvent.FinalTranscript || event is SttEvent.Error || event is SttEvent.Stopped) {
                _isListening.value = false
            }
            _events.tryEmit(event)
        }
    }

    private class TestTextToSpeechEngine : TextToSpeechEngine {
        var supportedLanguages: Set<String> = setOf("es", "en", "pt")
        var voicesList: List<DeviceVoiceInfo> = emptyList()
        var failSpeakWith: Throwable? = null
        var holdSpeakUntilInterrupted: Boolean = false
        var lastSpokenText: String? = null
        var lastVoiceId: String? = null
        var lastRate: Float? = null
        var lastPitch: Float? = null
        var interruptedCount: Int = 0
        private var speakGate: CompletableDeferred<Unit>? = null

        override val isIntegrationReady: Boolean = true
        private val _isSpeaking = MutableStateFlow(false)
        override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
        private val _availableVoices = MutableStateFlow<List<DeviceVoiceInfo>>(emptyList())
        override val availableVoices: StateFlow<List<DeviceVoiceInfo>> = _availableVoices.asStateFlow()
        private val _availableLanguages = MutableStateFlow<List<String>>(emptyList())
        override val availableLanguages: StateFlow<List<String>> = _availableLanguages.asStateFlow()
        private val _lastError = MutableStateFlow<String?>(null)
        override val lastError: StateFlow<String?> = _lastError.asStateFlow()

        override suspend fun initialize(): Boolean {
            _availableVoices.value = voicesList
            _availableLanguages.value = supportedLanguages.toList()
            return true
        }

        override fun resolveVoiceProfiles(languageCode: String): List<VoiceProfile> {
            val available = supportedLanguages.contains(languageCode.lowercase())
            return VoiceProfileCatalog.profiles.map {
                it.copy(
                    isAvailableNow = available,
                    availabilityNote = if (available) "Disponible" else "Sin idioma"
                )
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
            failSpeakWith?.let {
                _lastError.value = it.message
                return Result.failure(it)
            }
            lastSpokenText = text
            lastVoiceId = voiceId
            lastRate = speechRate
            lastPitch = pitch
            _isSpeaking.value = true
            if (holdSpeakUntilInterrupted) {
                val gate = CompletableDeferred<Unit>()
                speakGate = gate
                gate.await()
            } else {
                withContext(Dispatchers.Default) {
                    delay(10)
                }
            }
            _isSpeaking.value = false
            return Result.success(Unit)
        }

        override suspend fun interrupt() {
            interruptedCount += 1
            _isSpeaking.value = false
            speakGate?.complete(Unit)
            speakGate = null
        }

        override fun release() {
            _isSpeaking.value = false
            speakGate?.complete(Unit)
        }
    }

    private class TestAudioPlaybackEngine : AudioPlaybackEngine {
        var stopCount: Int = 0
        private val _isPlaying = MutableStateFlow(false)
        override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
        override suspend fun playPcmChunk(pcmData: ByteArray) = Unit
        override suspend fun stopImmediately() {
            stopCount += 1
            _isPlaying.value = false
        }
    }

    private class TestStreamingAiProvider(
        override val id: String,
        private val chunks: List<String>,
        private val holdAfterFirstChunk: CompletableDeferred<Unit>? = null
    ) : AiProvider {
        override val displayName: String = "Test Streaming Provider"
        override val capabilities = AiProviderCapabilities(
            supportsStreamingText = true,
            supportsBidirectionalAudio = false,
            supportsOfflineExecution = false,
            supportsToolCalling = false
        )
        override val status: AiProviderStatus = AiProviderStatus.Ready
        var invocationCount: Int = 0
        var interrupted: Boolean = false

        override fun streamResponse(request: AiRequest): Flow<AiResponseEvent> = flow {
            invocationCount += 1
            for ((index, chunk) in chunks.withIndex()) {
                emit(AiResponseEvent.TextDelta(chunk))
                if (index == 0 && holdAfterFirstChunk != null) {
                    holdAfterFirstChunk.await()
                }
            }
            emit(AiResponseEvent.Completed)
        }

        override suspend fun interruptActiveResponse() {
            interrupted = true
            holdAfterFirstChunk?.cancel()
        }
    }
}
