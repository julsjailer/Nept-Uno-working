package com.example.neptuno.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neptuno.ai.api.AiProviderRegistry
import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.audio.engine.AudioPlaybackEngine
import com.example.neptuno.audio.engine.BidirectionalAudioCoordinator
import com.example.neptuno.audio.engine.SpeechToTextEngine
import com.example.neptuno.audio.engine.SttAvailability
import com.example.neptuno.audio.engine.SttEvent
import com.example.neptuno.audio.engine.TextToSpeechEngine
import com.example.neptuno.core.NetworkMonitor
import com.example.neptuno.core.NetworkStatus
import com.example.neptuno.core.model.AppLanguage
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.AssistantState
import com.example.neptuno.core.model.AvatarStyle
import com.example.neptuno.core.model.ChatMessage
import com.example.neptuno.core.model.MessageSender
import com.example.neptuno.core.model.UserPreferences
import com.example.neptuno.data.local.ConversationRepository
import com.example.neptuno.data.local.MemoryRepository
import com.example.neptuno.data.local.UserPreferencesRepository
import com.example.neptuno.data.remote.CloudSyncGateway
import com.example.neptuno.data.remote.NoOpCloudSyncGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val activeConversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingResponseText: String? = null,
    val assistantState: AssistantState = AssistantState.IDLE,
    val statusDetail: String = "Inactivo",
    val avatarStyle: AvatarStyle = AvatarStyle.ABYSSAL_CORE,
    val activeProviderName: String = "Gemini (Texto y Streaming)",
    val isProviderPending: Boolean = false,
    val isOffline: Boolean = false,
    val isListening: Boolean = false,
    val isGeneratingText: Boolean = false,
    val isSpeaking: Boolean = false,
    val bargeInEnabled: Boolean = true,
    val autoSpeakResponses: Boolean = true,
    val persistHistoryEnabled: Boolean = true,
    val microphoneStatusBanner: String? = null,
    val audioRouteNotice: String? = null,
    val sttAvailability: SttAvailability = SttAvailability(
        isServiceAvailable = true,
        isOnDeviceSupported = false,
        note = ""
    )
)

private data class ChatDataSnapshot(
    val conversationId: Long?,
    val messages: List<ChatMessage>,
    val preferences: UserPreferences,
    val networkStatus: NetworkStatus
)

private data class ChatTurnState(
    val isGeneratingText: Boolean = false,
    val streamingResponseText: String? = null,
    val statusOverride: Pair<AssistantState, String>? = null
)

private data class VoiceUiSnapshot(
    val turnState: ChatTurnState,
    val micBanner: String?,
    val sttListening: Boolean,
    val ttsSpeaking: Boolean,
    val sttAvailability: SttAvailability
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val aiProviderRegistry: AiProviderRegistry,
    private val audioCoordinator: BidirectionalAudioCoordinator,
    private val networkMonitor: NetworkMonitor,
    private val speechToTextEngine: SpeechToTextEngine,
    private val textToSpeechEngine: TextToSpeechEngine,
    private val audioPlaybackEngine: AudioPlaybackEngine,
    private val cloudSyncGateway: CloudSyncGateway = NoOpCloudSyncGateway
) : ViewModel() {

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    private val _turnState = MutableStateFlow(ChatTurnState())
    private val _micStatusBanner = MutableStateFlow<String?>(null)
    private val _inMemoryMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _sttAvailability = MutableStateFlow(speechToTextEngine.checkAvailability())

    private var activeTurnJob: Job? = null
    private var activeTtsJob: Job? = null
    private var voiceBaseDraftPrefix: String = ""
    private var currentStreamBuffer = StringBuilder()
    private var currentTurnConversationId: Long? = null
    private var currentTurnProviderId: String? = null
    private var currentTurnPersist: Boolean = true
    private var partialSavedForCurrentTurn: Boolean = false

    private val persistedMessagesFlow = _activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else conversationRepository.observeMessages(id)
    }

    private val dataSnapshotFlow = combine(
        _activeConversationId,
        persistedMessagesFlow,
        _inMemoryMessages,
        preferencesRepository.userPreferences,
        networkMonitor.observeNetworkStatus()
    ) { convId, persisted, ephemeral, prefs, network ->
        ChatDataSnapshot(
            conversationId = convId,
            messages = if (prefs.persistConversationHistory) persisted else ephemeral,
            preferences = prefs,
            networkStatus = network
        )
    }

    private val voiceSnapshotFlow = combine(
        _turnState,
        _micStatusBanner,
        speechToTextEngine.isListening,
        textToSpeechEngine.isSpeaking,
        _sttAvailability
    ) { turnState, micBanner, sttListening, ttsSpeaking, sttAvail ->
        VoiceUiSnapshot(
            turnState = turnState,
            micBanner = micBanner,
            sttListening = sttListening,
            ttsSpeaking = ttsSpeaking,
            sttAvailability = sttAvail
        )
    }

    val uiState: StateFlow<ChatUiState> = combine(
        dataSnapshotFlow,
        voiceSnapshotFlow,
        com.example.neptuno.audio.engine.AudioRouteNotice.state
    ) { data, voice, routeNotice ->
        val prefs = data.preferences
        val provider = aiProviderRegistry.getActiveProvider(prefs.preferredAiProviderId)
        val offline = prefs.offlineMode || !data.networkStatus.isConnected
        val statusOverride = voice.turnState.statusOverride

        val isListening = voice.sttListening ||
            statusOverride?.first == AssistantState.LISTENING
        val isSpeaking = voice.ttsSpeaking ||
            statusOverride?.first == AssistantState.SPEAKING

        val (computedState, computedDetail) = when {
            voice.sttListening -> AssistantState.LISTENING to "Escuchando"
            voice.ttsSpeaking -> AssistantState.SPEAKING to "Reproduciendo voz"
            statusOverride != null -> statusOverride
            offline -> AssistantState.OFFLINE to "Sin conexión"
            else -> AssistantState.IDLE to "Inactivo"
        }

        ChatUiState(
            activeConversationId = data.conversationId,
            messages = data.messages,
            streamingResponseText = voice.turnState.streamingResponseText,
            assistantState = computedState,
            statusDetail = computedDetail,
            avatarStyle = prefs.avatarStyle,
            activeProviderName = provider.displayName,
            isProviderPending = provider.status is AiProviderStatus.PendingIntegration,
            isOffline = offline,
            isListening = isListening,
            isGeneratingText = voice.turnState.isGeneratingText,
            isSpeaking = isSpeaking,
            bargeInEnabled = prefs.bargeInEnabled,
            autoSpeakResponses = prefs.autoSpeakResponses,
            persistHistoryEnabled = prefs.persistConversationHistory,
            microphoneStatusBanner = voice.micBanner,
            audioRouteNotice = routeNotice,
            sttAvailability = voice.sttAvailability
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    private val _draftInput = MutableStateFlow("")
    val draftInput: StateFlow<String> = _draftInput.asStateFlow()

    init {
        viewModelScope.launch {
            val id = conversationRepository.getOrCreateActiveConversation("Sesión principal")
            _activeConversationId.value = id
        }
        viewModelScope.launch {
            textToSpeechEngine.initialize()
        }
        viewModelScope.launch {
            speechToTextEngine.observeEvents().collect { event ->
                handleSttEvent(event)
            }
        }
    }

    fun onDraftChanged(value: String) {
        _draftInput.value = value
    }

    fun selectConversation(conversationId: Long) {
        if (_activeConversationId.value != conversationId) {
            viewModelScope.launch {
                interruptActiveResponseInternal(markAsInterrupted = false)
                _activeConversationId.value = conversationId
                _micStatusBanner.value = null
            }
        }
    }

    fun startNewSession() {
        viewModelScope.launch {
            interruptActiveResponseInternal(markAsInterrupted = false)
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.persistConversationHistory) {
                val id = conversationRepository.createConversation("Nueva conversación")
                _activeConversationId.value = id
            } else {
                _inMemoryMessages.value = emptyList()
            }
            _micStatusBanner.value = null
            _turnState.update { it.copy(statusOverride = null) }
        }
    }

    fun sendMessage() {
        val trimmed = _draftInput.value.trim()
        if (trimmed.isEmpty()) return

        _draftInput.value = ""
        voiceBaseDraftPrefix = ""

        viewModelScope.launch {
            if (speechToTextEngine.isListening.value) {
                speechToTextEngine.stopListening()
            }
            interruptActiveResponseInternal(markAsInterrupted = false)

            activeTurnJob = launch {
                val prefs = preferencesRepository.userPreferences.first()
                val network = networkMonitor.observeNetworkStatus().first()
                val effectiveOffline = prefs.offlineMode || !network.isConnected

                val conversationId = if (prefs.persistConversationHistory) {
                    conversationRepository.ensureConversationExists(
                        conversationId = _activeConversationId.value,
                        defaultTitle = "Sesión principal"
                    ).also { _activeConversationId.value = it }
                } else {
                    _activeConversationId.value ?: 0L
                }

                val existingCount = if (prefs.persistConversationHistory) {
                    conversationRepository.observeMessages(conversationId).first().size
                } else {
                    _inMemoryMessages.value.size
                }

                val userMsg = ChatMessage(
                    conversationId = conversationId,
                    sender = MessageSender.USER,
                    content = trimmed,
                    timestampMillis = System.currentTimeMillis()
                )

                if (prefs.persistConversationHistory) {
                    val savedId = conversationRepository.addMessage(userMsg)
                    var convTitle = "Sesión principal"
                    if (existingCount == 0) {
                        val autoTitle = trimmed.take(42).let { if (trimmed.length > 42) "$it…" else it }
                        conversationRepository.updateConversationTitle(conversationId, autoTitle)
                        convTitle = autoTitle
                        cloudSyncGateway.syncConversationTitle(conversationId, autoTitle)
                    }
                    cloudSyncGateway.syncMessageAdded(
                        conversationId = conversationId,
                        conversationTitle = convTitle,
                        message = userMsg.copy(id = savedId)
                    )
                } else {
                    _inMemoryMessages.value = _inMemoryMessages.value + userMsg.copy(id = System.nanoTime())
                }

                val provider = aiProviderRegistry.getActiveProvider(prefs.preferredAiProviderId)

                // Frases triviales (saludo, gracias, hora, fecha) se responden en el teléfono: 0 solicitudes a Gemini.
                val quickReply = if (prefs.language == AppLanguage.SPANISH) LocalQuickReply.match(trimmed) else null
                if (quickReply != null) {
                    appendMessage(
                        persist = prefs.persistConversationHistory,
                        message = ChatMessage(
                            conversationId = conversationId,
                            sender = MessageSender.ASSISTANT,
                            content = quickReply,
                            providerId = "local_quick_reply"
                        )
                    )
                    _turnState.update {
                        it.copy(isGeneratingText = false, streamingResponseText = null, statusOverride = null)
                    }
                    if (prefs.autoSpeakResponses) {
                        speakAssistantResponse(quickReply, prefs)
                    } else {
                        scheduleContinuousListening()
                    }
                    return@launch
                }

                if (effectiveOffline &&
                    !provider.capabilities.supportsOfflineExecution &&
                    provider.status !is AiProviderStatus.PendingIntegration
                ) {
                    val offlineReason = when {
                        prefs.offlineMode -> "Modo sin conexión activo."
                        network.isConnectedWithoutInternet ->
                            "La red (${network.transportLabel}) está conectada sin acceso validado a Internet."
                        else -> "Sin conexión a Internet."
                    }
                    appendMessage(
                        persist = prefs.persistConversationHistory,
                        message = ChatMessage(
                            conversationId = conversationId,
                            sender = MessageSender.SYSTEM_NOTICE,
                            content = "$offlineReason El mensaje se guardó localmente en Room, pero ${provider.displayName} requiere conexión.",
                            providerId = provider.id
                        )
                    )
                    _turnState.update {
                        it.copy(
                            isGeneratingText = false,
                            streamingResponseText = null,
                            statusOverride = AssistantState.OFFLINE to "Sin conexión"
                        )
                    }
                    scheduleContinuousListening()
                    return@launch
                }

                val recentHistory = if (prefs.persistConversationHistory) {
                    conversationRepository.observeMessages(conversationId).first().takeLast(10)
                } else {
                    _inMemoryMessages.value.takeLast(10)
                }

                val memories = if (prefs.memoryAuthorized) {
                    memoryRepository.observeMemories().first()
                } else {
                    emptyList()
                }

                val request = AiRequest(
                    conversationId = conversationId,
                    userInput = trimmed,
                    recentHistory = recentHistory,
                    authorizedMemories = memories,
                    personality = prefs.personalityMode,
                    languageCode = prefs.language.code
                )

                currentStreamBuffer = StringBuilder()
                currentTurnConversationId = conversationId
                currentTurnProviderId = provider.id
                currentTurnPersist = prefs.persistConversationHistory
                partialSavedForCurrentTurn = false
                _turnState.value = ChatTurnState(
                    isGeneratingText = true,
                    streamingResponseText = null,
                    statusOverride = AssistantState.PROCESSING to "Procesando"
                )
                _micStatusBanner.value = null
                audioCoordinator.markSessionActive(true)

                try {
                    provider.streamResponse(request).collect { event ->
                        when (event) {
                            is AiResponseEvent.IntegrationPending -> {
                                _turnState.value = ChatTurnState()
                                audioCoordinator.markSessionActive(false)
                                appendMessage(
                                    persist = prefs.persistConversationHistory,
                                    message = ChatMessage(
                                        conversationId = conversationId,
                                        sender = MessageSender.SYSTEM_NOTICE,
                                        content = event.message,
                                        providerId = provider.id
                                    )
                                )
                                scheduleContinuousListening()
                            }

                            is AiResponseEvent.TextDelta -> {
                                currentStreamBuffer.append(event.text)
                                _turnState.value = ChatTurnState(
                                    isGeneratingText = true,
                                    streamingResponseText = currentStreamBuffer.toString(),
                                    statusOverride = AssistantState.PROCESSING to "Respondiendo"
                                )
                            }

                            is AiResponseEvent.Completed -> {
                                val finalResponse = currentStreamBuffer.toString().trim()
                                _turnState.update {
                                    it.copy(
                                        isGeneratingText = false,
                                        streamingResponseText = null,
                                        statusOverride = null
                                    )
                                }

                                if (finalResponse.isNotEmpty() && !partialSavedForCurrentTurn) {
                                    partialSavedForCurrentTurn = true
                                    appendMessage(
                                        persist = prefs.persistConversationHistory,
                                        message = ChatMessage(
                                            conversationId = conversationId,
                                            sender = MessageSender.ASSISTANT,
                                            content = finalResponse,
                                            providerId = provider.id
                                        )
                                    )
                                }

                                audioCoordinator.markSessionActive(false)

                                if (prefs.autoSpeakResponses && finalResponse.isNotEmpty()) {
                                    speakAssistantResponse(finalResponse, prefs)
                                } else {
                                    scheduleContinuousListening()
                                }
                            }

                            is AiResponseEvent.Interrupted -> {
                                finalizeInterruptedPartialMessage()
                                audioCoordinator.interruptCurrentOutput()
                                appendMessage(
                                    persist = prefs.persistConversationHistory,
                                    message = ChatMessage(
                                        conversationId = conversationId,
                                        sender = MessageSender.SYSTEM_NOTICE,
                                        content = "Respuesta interrumpida: ${event.reason}",
                                        providerId = provider.id,
                                        isInterrupted = true
                                    )
                                )
                                _turnState.value = ChatTurnState(
                                    isGeneratingText = false,
                                    streamingResponseText = null,
                                    statusOverride = AssistantState.IDLE to "Interrumpido"
                                )
                                finishWakeTurn()
                            }

                            is AiResponseEvent.Failure -> {
                                finalizeInterruptedPartialMessage()
                                audioCoordinator.markSessionActive(false)
                                _turnState.value = ChatTurnState(
                                    isGeneratingText = false,
                                    streamingResponseText = null,
                                    statusOverride = AssistantState.ERROR to "Error"
                                )
                                appendMessage(
                                    persist = prefs.persistConversationHistory,
                                    message = ChatMessage(
                                        conversationId = conversationId,
                                        sender = MessageSender.SYSTEM_NOTICE,
                                        content = "Error del proveedor: ${event.errorMessage}",
                                        providerId = provider.id
                                    )
                                )
                                scheduleContinuousListening()
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    _turnState.update { it.copy(isGeneratingText = false) }
                    audioCoordinator.markSessionActive(false)
                    throw e
                } catch (e: Exception) {
                    finalizeInterruptedPartialMessage()
                    audioCoordinator.markSessionActive(false)
                    _turnState.value = ChatTurnState(
                        isGeneratingText = false,
                        streamingResponseText = null,
                        statusOverride = AssistantState.ERROR to "Error"
                    )
                    appendMessage(
                        persist = prefs.persistConversationHistory,
                        message = ChatMessage(
                            conversationId = conversationId,
                            sender = MessageSender.SYSTEM_NOTICE,
                            content = "Error inesperado: ${e.localizedMessage ?: "No se pudo completar la respuesta."}",
                            providerId = provider.id
                        )
                    )
                    scheduleContinuousListening()
                }
            }
        }
    }

    fun speakMessage(message: ChatMessage) {
        if (message.content.isBlank()) return
        viewModelScope.launch {
            interruptActiveResponseInternal(markAsInterrupted = false)
            val prefs = preferencesRepository.userPreferences.first()
            speakAssistantResponse(message.content, prefs)
        }
    }

    /** Sesión iniciada por la frase de activación; mantiene Vosk pausado durante dictado, Gemini y TTS. */
    private var wakeInitiated = false
    private var continuousWakeSession = false
    private var noSpeechAttempts = 0
    private var suppressNextStoppedEvent = false
    private val _voiceTurnEnded = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emite cuando termina un dictado iniciado por voz de activación (para reanudar la escucha de fondo). */
    val voiceTurnEnded: kotlinx.coroutines.flow.SharedFlow<Unit> = _voiceTurnEnded

    fun startVoiceFromWake() {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            wakeInitiated = true
            continuousWakeSession = prefs.continuousConversation
            noSpeechAttempts = 0
            val alreadyListening = speechToTextEngine.isListening.value
            if (!alreadyListening) toggleListeningSession()
            if (!speechToTextEngine.isListening.value &&
                _turnState.value.statusOverride?.first != AssistantState.LISTENING
            ) {
                // No se pudo empezar a escuchar: se libera la escucha de fondo.
                finishWakeTurn()
            }
        }
    }

    private fun finishWakeTurn() {
        if (wakeInitiated) {
            wakeInitiated = false
            continuousWakeSession = false
            noSpeechAttempts = 0
            _voiceTurnEnded.tryEmit(Unit)
        }
    }

    /** Inicia el siguiente turno solo después de terminar TTS; nunca comparte el micrófono con Vosk. */
    private fun scheduleContinuousListening() {
        if (!wakeInitiated) return
        if (!continuousWakeSession) {
            finishWakeTurn()
            return
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(450)
            if (!wakeInitiated || !continuousWakeSession) return@launch
            val prefs = preferencesRepository.userPreferences.first()
            val availability = speechToTextEngine.checkAvailability()
            _sttAvailability.value = availability
            if (!availability.isServiceAvailable) {
                _micStatusBanner.value = availability.note
                finishWakeTurn()
                return@launch
            }
            val network = networkMonitor.observeNetworkStatus().first()
            val offlineActive = prefs.offlineMode || !network.isConnected
            voiceBaseDraftPrefix = ""
            val result = speechToTextEngine.startListening(
                languageCode = prefs.language.code,
                preferOffline = offlineActive
            )
            if (result.isSuccess) {
                _micStatusBanner.value = null
                _turnState.update { it.copy(statusOverride = AssistantState.LISTENING to "Escuchando") }
            } else {
                _micStatusBanner.value = result.exceptionOrNull()?.localizedMessage
                    ?: "No se pudo reanudar la escucha."
                finishWakeTurn()
            }
        }
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        if (!granted) {
            _turnState.update {
                it.copy(statusOverride = AssistantState.ERROR to "Error de micrófono")
            }
            _micStatusBanner.value =
                "Permiso de micrófono denegado. Concédelo para habilitar el dictado y la conversación por voz."
            return
        }

        viewModelScope.launch {
            toggleListeningSession()
        }
    }

    fun interruptActiveResponse() {
        viewModelScope.launch {
            interruptActiveResponseInternal(markAsInterrupted = true)
        }
    }

    fun dismissMicBanner() {
        _micStatusBanner.value = null
        _turnState.update { current ->
            if (current.statusOverride?.first == AssistantState.ERROR) {
                current.copy(statusOverride = null)
            } else {
                current
            }
        }
    }

    private suspend fun toggleListeningSession() {
        val currentlyListening = speechToTextEngine.isListening.value ||
            _turnState.value.statusOverride?.first == AssistantState.LISTENING
        if (currentlyListening) {
            speechToTextEngine.stopListening()
            _turnState.update { current ->
                if (current.statusOverride?.first == AssistantState.LISTENING) {
                    current.copy(statusOverride = null)
                } else {
                    current
                }
            }
            return
        }

        val prefs = preferencesRepository.userPreferences.first()
        val hasOngoingOutput = _turnState.value.isGeneratingText ||
            activeTurnJob?.isActive == true ||
            textToSpeechEngine.isSpeaking.value ||
            activeTtsJob?.isActive == true

        if (hasOngoingOutput) {
            if (prefs.bargeInEnabled) {
                interruptActiveResponseInternal(markAsInterrupted = true)
            } else {
                _micStatusBanner.value =
                    "Neptuno está respondiendo. Espera a que termine o activa la interrupción (barge-in) en Ajustes."
                return
            }
        }

        val availability = speechToTextEngine.checkAvailability()
        _sttAvailability.value = availability
        if (!availability.isServiceAvailable) {
            _turnState.update {
                it.copy(statusOverride = AssistantState.ERROR to "Servicio de voz no disponible")
            }
            _micStatusBanner.value = availability.note
            return
        }

        val network = networkMonitor.observeNetworkStatus().first()
        val offlineActive = prefs.offlineMode || !network.isConnected
        if (offlineActive && !availability.isOnDeviceSupported) {
            _micStatusBanner.value =
                "Sin conexión: el dictado dependerá de paquetes de voz offline instalados en el dispositivo."
        } else {
            _micStatusBanner.value = null
        }

        voiceBaseDraftPrefix = _draftInput.value.trim()
        val startResult = speechToTextEngine.startListening(
            languageCode = prefs.language.code,
            preferOffline = offlineActive
        )

        if (startResult.isSuccess) {
            _turnState.update {
                it.copy(statusOverride = AssistantState.LISTENING to "Escuchando")
            }
        } else {
            voiceBaseDraftPrefix = ""
            val errorMsg = startResult.exceptionOrNull()?.localizedMessage
                ?: "No se pudo iniciar el reconocimiento de voz."
            val detail = if (availability.isServiceAvailable) {
                "Error de micrófono"
            } else {
                "Servicio de voz no disponible"
            }
            _turnState.update {
                it.copy(statusOverride = AssistantState.ERROR to detail)
            }
            _micStatusBanner.value = errorMsg
        }
    }

    private fun handleSttEvent(event: SttEvent) {
        when (event) {
            is SttEvent.ListeningStarted -> {
                _turnState.update {
                    it.copy(statusOverride = AssistantState.LISTENING to "Escuchando")
                }
            }

            is SttEvent.PartialTranscript -> {
                _draftInput.value = mergeVoiceTranscript(voiceBaseDraftPrefix, event.text)
                _turnState.update {
                    it.copy(statusOverride = AssistantState.LISTENING to "Escuchando")
                }
            }

            is SttEvent.FinalTranscript -> {
                _draftInput.value = mergeVoiceTranscript(voiceBaseDraftPrefix, event.text)
                voiceBaseDraftPrefix = ""
                // Envío automático por voz: si está activado, el mensaje dictado se envía sin pulsar el botón.
                viewModelScope.launch {
                    val autoSend = preferencesRepository.userPreferences.first().autoSendVoice || wakeInitiated
                    if (autoSend && _draftInput.value.isNotBlank()) {
                        noSpeechAttempts = 0
                        suppressNextStoppedEvent = true
                        sendMessage()
                    } else {
                        finishWakeTurn()
                    }
                }
                _turnState.update { current ->
                    if (current.statusOverride?.first == AssistantState.LISTENING) {
                        current.copy(statusOverride = null)
                    } else {
                        current
                    }
                }
            }

            is SttEvent.Stopped -> {
                voiceBaseDraftPrefix = ""
                if (suppressNextStoppedEvent) {
                    suppressNextStoppedEvent = false
                } else {
                    finishWakeTurn()
                }
                _turnState.update { current ->
                    if (current.statusOverride?.first == AssistantState.LISTENING) {
                        current.copy(statusOverride = null)
                    } else {
                        current
                    }
                }
            }

            is SttEvent.Error -> {
                voiceBaseDraftPrefix = ""
                val noSpeech = event.message.contains("no se entendió", ignoreCase = true) ||
                    event.message.contains("tiempo de espera agotado", ignoreCase = true) ||
                    event.message.contains("no se detectó texto", ignoreCase = true)
                if (wakeInitiated && continuousWakeSession && noSpeech) {
                    noSpeechAttempts += 1
                    if (noSpeechAttempts < MAX_CONTINUOUS_NO_SPEECH_ATTEMPTS) {
                        scheduleContinuousListening()
                    } else {
                        finishWakeTurn()
                    }
                } else {
                    finishWakeTurn()
                }
                val detail = when {
                    event.isServiceUnavailable -> "Servicio de voz no disponible"
                    event.isNetworkError -> "Sin conexión"
                    else -> "Error de micrófono"
                }
                _turnState.update {
                    it.copy(statusOverride = AssistantState.ERROR to detail)
                }
                _micStatusBanner.value = event.message
            }
        }
    }

    private fun speakAssistantResponse(text: String, prefs: UserPreferences) {
        activeTtsJob?.cancel()
        activeTtsJob = viewModelScope.launch {
            _turnState.update {
                it.copy(statusOverride = AssistantState.SPEAKING to "Reproduciendo voz")
            }
            audioCoordinator.markSessionActive(true)
            val result = textToSpeechEngine.speak(
                text = text,
                voiceId = prefs.selectedVoiceId,
                languageCode = prefs.language.code,
                speechRate = prefs.ttsSpeechRate,
                pitch = prefs.ttsPitch,
                preferredDeviceVoiceName = prefs.selectedDeviceVoiceName
            )
            audioCoordinator.markSessionActive(false)
            if (result.isFailure) {
                _micStatusBanner.value =
                    "Respuesta guardada en texto, pero la síntesis de voz falló: ${
                        result.exceptionOrNull()?.localizedMessage ?: "motor TTS no disponible"
                    }"
            }
            _turnState.update { current ->
                if (current.statusOverride?.first == AssistantState.SPEAKING) {
                    current.copy(statusOverride = null)
                } else {
                    current
                }
            }
            scheduleContinuousListening()
        }
    }

    private suspend fun interruptActiveResponseInternal(markAsInterrupted: Boolean) {
        val wasActive = _turnState.value.isGeneratingText ||
            activeTurnJob?.isActive == true ||
            textToSpeechEngine.isSpeaking.value ||
            activeTtsJob?.isActive == true ||
            speechToTextEngine.isListening.value

        activeTurnJob?.cancel()
        activeTurnJob = null
        activeTtsJob?.cancel()
        activeTtsJob = null

        speechToTextEngine.cancel()
        audioCoordinator.interruptCurrentOutput()
        textToSpeechEngine.interrupt()
        audioPlaybackEngine.stopImmediately()

        if (wasActive) {
            val prefs = preferencesRepository.userPreferences.first()
            aiProviderRegistry.getActiveProvider(prefs.preferredAiProviderId).interruptActiveResponse()
        }

        finalizeInterruptedPartialMessage()

        _turnState.value = ChatTurnState(
            isGeneratingText = false,
            streamingResponseText = null,
            statusOverride = if (markAsInterrupted && wasActive) {
                AssistantState.IDLE to "Interrumpido"
            } else {
                null
            }
        )
    }

    private suspend fun finalizeInterruptedPartialMessage() {
        val partial = currentStreamBuffer.toString().trim()
        currentStreamBuffer.clear()
        _turnState.update {
            it.copy(isGeneratingText = false, streamingResponseText = null)
        }

        val convId = currentTurnConversationId
        if (partial.isNotEmpty() && !partialSavedForCurrentTurn && convId != null) {
            partialSavedForCurrentTurn = true
            appendMessage(
                persist = currentTurnPersist,
                message = ChatMessage(
                    conversationId = convId,
                    sender = MessageSender.ASSISTANT,
                    content = partial,
                    providerId = currentTurnProviderId,
                    isInterrupted = true
                )
            )
        }
    }

    private suspend fun appendMessage(persist: Boolean, message: ChatMessage) {
        if (persist) {
            val savedId = conversationRepository.addMessage(message)
            cloudSyncGateway.syncMessageAdded(
                conversationId = message.conversationId,
                conversationTitle = "Sesión principal",
                message = message.copy(id = savedId)
            )
        } else {
            _inMemoryMessages.value = _inMemoryMessages.value + message.copy(id = System.nanoTime())
        }
    }

    private fun mergeVoiceTranscript(prefix: String, spokenText: String): String {
        val cleanSpoken = spokenText.trim()
        if (prefix.isBlank()) return cleanSpoken
        if (cleanSpoken.isBlank()) return prefix
        return "$prefix $cleanSpoken"
    }

    override fun onCleared() {
        activeTurnJob?.cancel()
        activeTtsJob?.cancel()
        speechToTextEngine.cancel()
        audioCoordinator.interruptCurrentOutput()
        super.onCleared()
    }

    companion object {
        private const val MAX_CONTINUOUS_NO_SPEECH_ATTEMPTS = 2

        fun factory(
            conversationRepository: ConversationRepository,
            memoryRepository: MemoryRepository,
            preferencesRepository: UserPreferencesRepository,
            aiProviderRegistry: AiProviderRegistry,
            audioCoordinator: BidirectionalAudioCoordinator,
            networkMonitor: NetworkMonitor,
            speechToTextEngine: SpeechToTextEngine,
            textToSpeechEngine: TextToSpeechEngine,
            audioPlaybackEngine: AudioPlaybackEngine,
            cloudSyncGateway: CloudSyncGateway = NoOpCloudSyncGateway
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    conversationRepository = conversationRepository,
                    memoryRepository = memoryRepository,
                    preferencesRepository = preferencesRepository,
                    aiProviderRegistry = aiProviderRegistry,
                    audioCoordinator = audioCoordinator,
                    networkMonitor = networkMonitor,
                    speechToTextEngine = speechToTextEngine,
                    textToSpeechEngine = textToSpeechEngine,
                    audioPlaybackEngine = audioPlaybackEngine,
                    cloudSyncGateway = cloudSyncGateway
                ) as T
            }
        }
    }
}
