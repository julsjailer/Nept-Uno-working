package com.example.neptuno.feature.cloud

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.neptuno.core.NetworkConnectivityState
import com.example.neptuno.core.NetworkMonitor
import com.example.neptuno.data.local.ConversationRepository
import com.example.neptuno.data.local.MemoryRepository
import com.example.neptuno.data.local.UserPreferencesRepository
import com.example.neptuno.data.remote.CloudMessageWrite
import com.example.neptuno.data.remote.CloudRemoteStore
import com.example.neptuno.data.remote.CloudUiState
import com.example.neptuno.data.remote.FirestoreCloudSyncGateway
import com.example.neptuno.data.remote.FirestoreConversation
import com.example.neptuno.data.remote.FirestoreMemoryEntry
import com.example.neptuno.data.remote.FirestoreUserProfile
import com.example.neptuno.data.remote.NeptunoFirestoreRepository
import com.example.neptuno.data.remote.NoOpCloudSyncGateway
import com.example.neptuno.data.remote.SwitchableCloudSyncGateway
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

enum class CloudSyncStatus(val label: String) {
    NO_FIREBASE_SESSION("Sin sesión Firebase"),
    LOCAL_ONLY("Modo local"),
    CONNECTING("Conectando con Firebase"),
    SYNCING("Sincronizando"),
    SYNCED("Sincronización completada"),
    PARTIAL_SYNC("Sincronización parcial"),
    AUTH_ERROR("Error de autenticación"),
    PERMISSION_ERROR("Error de permisos"),
    OFFLINE("Sin conexión"),
    TIMEOUT("Tiempo de espera agotado")
}

data class CloudAuthUser(
    val userId: String,
    val displayName: String,
    val email: String?
)

data class CloudAccountSummary(
    val userId: String? = null,
    val isAuthenticated: Boolean = false,
    val displayName: String = "Modo local (sin cuenta)",
    val email: String? = null,
    val syncStatus: CloudSyncStatus = CloudSyncStatus.NO_FIREBASE_SESSION,
    val isSyncing: Boolean = false,
    val lastSyncMessage: String? = "Operando localmente con Room y DataStore.",
    val profileError: String? = null,
    val memoriesError: String? = null,
    val conversationsError: String? = null,
    val lastSyncedAtMillis: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSessionViewModel(
    initialUser: CloudAuthUser?,
    private val remoteStoreFactory: () -> CloudRemoteStore,
    private val preferencesRepository: UserPreferencesRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val networkMonitor: NetworkMonitor,
    private val operationTimeoutMs: Long = DEFAULT_OPERATION_TIMEOUT_MS,
    private val globalTimeoutMs: Long = DEFAULT_GLOBAL_TIMEOUT_MS,
    private val autoSyncOnInit: Boolean = true
) : ViewModel() {

    val cloudSyncGateway = SwitchableCloudSyncGateway(NoOpCloudSyncGateway)

    private val syncInFlight = AtomicBoolean(false)
    private var activeSyncJob: Job? = null

    @Volatile
    private var cachedRemoteStore: CloudRemoteStore? = null

    private fun getOrCreateRemoteStore(): CloudRemoteStore {
        return cachedRemoteStore ?: synchronized(this) {
            cachedRemoteStore ?: remoteStoreFactory().also { cachedRemoteStore = it }
        }
    }

    private val _authUser = MutableStateFlow(initialUser)

    private val _accountSummary = MutableStateFlow(buildInitialSummary(initialUser))
    val accountSummary: StateFlow<CloudAccountSummary> = _accountSummary.asStateFlow()

    val profileState: StateFlow<CloudUiState<FirestoreUserProfile?>> =
        _authUser.flatMapLatest { user ->
            if (user == null) {
                flowOf(CloudUiState.Idle)
            } else {
                getOrCreateRemoteStore().observeUserProfile(user.userId)
                    .map<FirestoreUserProfile?, CloudUiState<FirestoreUserProfile?>> { CloudUiState.Success(it) }
                    .catch { error ->
                        val firestoreError = NeptunoFirestoreRepository.unwrapFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing user profile", rootError)
                        emit(CloudUiState.Error(describeRemoteError(rootError, "No se pudo leer el perfil en la nube.")))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = if (initialUser == null) CloudUiState.Idle else CloudUiState.Loading
        )

    val cloudMemoriesState: StateFlow<CloudUiState<List<FirestoreMemoryEntry>>> =
        _authUser.flatMapLatest { user ->
            if (user == null) {
                flowOf(CloudUiState.Idle)
            } else {
                getOrCreateRemoteStore().observeMemories(user.userId)
                    .map<List<FirestoreMemoryEntry>, CloudUiState<List<FirestoreMemoryEntry>>> { CloudUiState.Success(it) }
                    .catch { error ->
                        val firestoreError = NeptunoFirestoreRepository.unwrapFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing cloud memories", rootError)
                        emit(CloudUiState.Error(describeRemoteError(rootError, "No se pudieron leer las memorias en la nube.")))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = if (initialUser == null) CloudUiState.Idle else CloudUiState.Loading
        )

    val cloudConversationsState: StateFlow<CloudUiState<List<FirestoreConversation>>> =
        _authUser.flatMapLatest { user ->
            if (user == null) {
                flowOf(CloudUiState.Idle)
            } else {
                getOrCreateRemoteStore().observeConversations(user.userId)
                    .map<List<FirestoreConversation>, CloudUiState<List<FirestoreConversation>>> { CloudUiState.Success(it) }
                    .catch { error ->
                        val firestoreError = NeptunoFirestoreRepository.unwrapFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing cloud conversations", rootError)
                        emit(CloudUiState.Error(describeRemoteError(rootError, "No se pudieron leer las conversaciones en la nube.")))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = if (initialUser == null) CloudUiState.Idle else CloudUiState.Loading
        )

    init {
        if (initialUser != null) {
            cloudSyncGateway.setDelegate(
                FirestoreCloudSyncGateway(
                    userId = initialUser.userId,
                    repository = getOrCreateRemoteStore(),
                    preferencesRepository = preferencesRepository,
                    networkMonitor = networkMonitor,
                    operationTimeoutMs = operationTimeoutMs
                )
            )
            if (autoSyncOnInit) {
                syncProfileAndLocalState()
            }
        }
    }

    fun updateAuthUser(user: CloudAuthUser?) {
        val current = _authUser.value
        if (current?.userId == user?.userId &&
            current?.displayName == user?.displayName &&
            current?.email == user?.email
        ) {
            return
        }

        if (user == null) {
            activeSyncJob?.cancel()
            activeSyncJob = null
            syncInFlight.set(false)
            cloudSyncGateway.resetToLocal()
            _authUser.value = null
            _accountSummary.value = CloudAccountSummary(
                userId = null,
                isAuthenticated = false,
                displayName = "Modo local (sin cuenta)",
                email = null,
                syncStatus = CloudSyncStatus.NO_FIREBASE_SESSION,
                isSyncing = false,
                lastSyncMessage = "Modo local activo. Tus datos en Room permanecen intactos."
            )
            return
        }

        val uidChanged = current?.userId != user.userId
        if (uidChanged) {
            activeSyncJob?.cancel()
            activeSyncJob = null
            syncInFlight.set(false)
        }

        cloudSyncGateway.setDelegate(
            FirestoreCloudSyncGateway(
                userId = user.userId,
                repository = getOrCreateRemoteStore(),
                preferencesRepository = preferencesRepository,
                networkMonitor = networkMonitor,
                operationTimeoutMs = operationTimeoutMs
            )
        )
        _authUser.value = user
        _accountSummary.update {
            it.copy(
                userId = user.userId,
                isAuthenticated = true,
                displayName = user.displayName.ifBlank { "Usuario Neptuno" },
                email = user.email,
                syncStatus = CloudSyncStatus.CONNECTING,
                isSyncing = false,
                lastSyncMessage = "Sesión Google detectada. Preparando respaldo en la nube…",
                profileError = null,
                memoriesError = null,
                conversationsError = null
            )
        }
        if (uidChanged) {
            syncProfileAndLocalState()
        }
    }

    fun syncProfileAndLocalState() {
        val currentUser = _authUser.value
        if (currentUser == null) {
            _accountSummary.value = _accountSummary.value.copy(
                isAuthenticated = false,
                syncStatus = CloudSyncStatus.NO_FIREBASE_SESSION,
                isSyncing = false,
                lastSyncMessage = "Sin sesión Firebase: operando localmente con Room y DataStore.",
                profileError = null,
                memoriesError = null,
                conversationsError = null
            )
            return
        }

        if (!syncInFlight.compareAndSet(false, true)) {
            // Single-flight: ya hay una sincronización en curso, no duplicar.
            return
        }

        val job = viewModelScope.launch {
            try {
                val user = _authUser.value ?: return@launch

                val prefs = preferencesRepository.userPreferences.first()
                val network = networkMonitor.observeNetworkStatus().first()

                if (prefs.offlineMode) {
                    _accountSummary.update {
                        it.copy(
                            syncStatus = CloudSyncStatus.LOCAL_ONLY,
                            isSyncing = false,
                            lastSyncMessage = "Modo sin conexión activo: usando almacenamiento local Room."
                        )
                    }
                    return@launch
                }

                if (!network.isConnected) {
                    val offlineMsg = if (network.connectivityState == NetworkConnectivityState.CONNECTED_WITHOUT_INTERNET) {
                        "Red conectada sin acceso a Internet: operando localmente en Room."
                    } else {
                        "Sin conexión a Internet: usando almacenamiento local Room."
                    }
                    _accountSummary.update {
                        it.copy(
                            syncStatus = CloudSyncStatus.OFFLINE,
                            isSyncing = false,
                            lastSyncMessage = offlineMsg
                        )
                    }
                    return@launch
                }

                _accountSummary.update {
                    it.copy(
                        syncStatus = CloudSyncStatus.SYNCING,
                        isSyncing = true,
                        lastSyncMessage = "Sincronizando perfil, memoria y conversaciones…",
                        profileError = null,
                        memoriesError = null,
                        conversationsError = null
                    )
                }

                val store = getOrCreateRemoteStore()
                var profileError: String? = null
                var memoriesError: String? = null
                var conversationsError: String? = null
                var dominantStatus: CloudSyncStatus? = null

                fun recordFailure(err: Throwable, fallbackMessage: String): String {
                    val status = classifySyncFailure(err)
                    if (dominantStatus == null || status == CloudSyncStatus.AUTH_ERROR || status == CloudSyncStatus.PERMISSION_ERROR) {
                        dominantStatus = status
                    }
                    return describeRemoteError(err, fallbackMessage)
                }

                withTimeout(globalTimeoutMs) {
                    // 1. Perfil de usuario
                    val profileResult = runTimedStep {
                        store.upsertUserProfile(
                            userId = user.userId,
                            displayName = user.displayName,
                            email = user.email,
                            languageCode = prefs.language.code,
                            personalityId = prefs.personalityMode.id,
                            avatarStyleId = prefs.avatarStyle.id
                        )
                    }
                    if (profileResult.isFailure) {
                        val err = profileResult.exceptionOrNull()!!
                        profileError = recordFailure(err, "No se pudo sincronizar el perfil.")
                    }

                    // 2. Memorias autorizadas en Room -> Firestore (idempotente por local_<id>)
                    if (prefs.memoryAuthorized) {
                        val localMemories = memoryRepository.getUnsyncedMemoriesSnapshot()
                        var memoryFailureCount = 0
                        var lastMemoryErr: String? = null
                        for (memory in localMemories) {
                            if (!memory.authorizedByUser) continue
                            val res = runTimedStep {
                                store.saveMemory(
                                    userId = user.userId,
                                    title = memory.title,
                                    content = memory.content,
                                    category = memory.category,
                                    localId = memory.id.takeIf { it > 0L }
                                ).map { }
                            }
                            if (res.isFailure) {
                                memoryFailureCount += 1
                                lastMemoryErr = recordFailure(
                                    res.exceptionOrNull()!!,
                                    "No se pudieron respaldar todas las memorias."
                                )
                            } else {
                                memoryRepository.markMemorySynced(memory.id, memory.updatedAtMillis)
                            }
                        }
                        if (memoryFailureCount > 0) {
                            memoriesError = "$memoryFailureCount memoria(s) pendientes: ${lastMemoryErr.orEmpty()}"
                        }
                    }

                    // 3. Conversaciones y mensajes de Room -> Firestore (idempotente por conv_<id> / local_msg_<id>)
                    if (prefs.persistConversationHistory) {
                        val localConversations = conversationRepository.getConversationsNeedingCloudSyncSnapshot()
                        var convFailureCount = 0
                        var lastConvErr: String? = null
                        for (session in localConversations) {
                            val ensureRes = runTimedStep {
                                store.ensureCloudConversation(
                                    userId = user.userId,
                                    localConversationId = session.id,
                                    title = session.title
                                ).map { }
                            }
                            if (ensureRes.isFailure) {
                                convFailureCount += 1
                                lastConvErr = recordFailure(
                                    ensureRes.exceptionOrNull()!!,
                                    "No se pudieron respaldar todas las conversaciones."
                                )
                                continue
                            }
                            conversationRepository.markConversationSynced(
                                id = session.id,
                                expectedUpdatedAtMillis = session.updatedAtMillis
                            )

                            val pendingMessages = conversationRepository.getUnsyncedMessagesSnapshot(session.id)
                                .filter { it.content.isNotBlank() }
                            for (messageChunk in pendingMessages.chunked(400)) {
                                val msgRes = runTimedStep {
                                    store.appendCloudMessages(
                                        userId = user.userId,
                                        localConversationId = session.id,
                                        conversationTitle = session.title,
                                        messages = messageChunk.map { msg ->
                                            CloudMessageWrite(
                                                localMessageId = msg.id,
                                                sender = msg.sender,
                                                content = msg.content,
                                                providerId = msg.providerId,
                                                interrupted = msg.isInterrupted
                                            )
                                        }
                                    )
                                }
                                if (msgRes.isFailure) {
                                    convFailureCount += messageChunk.size
                                    lastConvErr = recordFailure(
                                        msgRes.exceptionOrNull()!!,
                                        "No se pudieron respaldar todos los mensajes."
                                    )
                                } else {
                                    conversationRepository.markMessagesSynced(messageChunk.map { it.id })
                                }
                            }
                        }
                        if (convFailureCount > 0) {
                            conversationsError = "$convFailureCount elemento(s) de historial pendientes: ${lastConvErr.orEmpty()}"
                        }
                    }
                }

                val anyError = profileError != null || memoriesError != null || conversationsError != null
                val finalStatus = if (!anyError) {
                    CloudSyncStatus.SYNCED
                } else {
                    when (dominantStatus) {
                        CloudSyncStatus.AUTH_ERROR -> CloudSyncStatus.AUTH_ERROR
                        CloudSyncStatus.PERMISSION_ERROR -> CloudSyncStatus.PERMISSION_ERROR
                        CloudSyncStatus.OFFLINE -> CloudSyncStatus.OFFLINE
                        else -> CloudSyncStatus.PARTIAL_SYNC
                    }
                }

                val finalMessage = when (finalStatus) {
                    CloudSyncStatus.SYNCED -> "Sincronización con Cloud Firestore completada."
                    CloudSyncStatus.AUTH_ERROR -> "Error de autenticación con Firebase. Puedes seguir usando Neptuno localmente."
                    CloudSyncStatus.PERMISSION_ERROR -> "Error de permisos en Firestore. Tus datos locales en Room siguen seguros."
                    CloudSyncStatus.OFFLINE -> "Sin acceso a Firebase por red. Tus datos locales en Room siguen seguros."
                    CloudSyncStatus.PARTIAL_SYNC -> {
                        val failedParts = buildList {
                            if (profileError != null) add("perfil")
                            if (memoriesError != null) add("memoria")
                            if (conversationsError != null) add("historial")
                        }.joinToString(", ")
                        "Sincronización parcial ($failedParts con errores). Puedes reintentar manualmente."
                    }
                    else -> "Sincronización finalizada."
                }

                _accountSummary.update {
                    it.copy(
                        syncStatus = finalStatus,
                        isSyncing = false,
                        lastSyncMessage = finalMessage,
                        profileError = profileError,
                        memoriesError = memoriesError,
                        conversationsError = conversationsError,
                        lastSyncedAtMillis = if (!anyError) System.currentTimeMillis() else it.lastSyncedAtMillis
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Global cloud sync timed out; keeping local Room operational.", e)
                _accountSummary.update {
                    it.copy(
                        syncStatus = CloudSyncStatus.TIMEOUT,
                        isSyncing = false,
                        lastSyncMessage = "Tiempo de espera agotado al sincronizar con Firebase. Neptuno sigue operando localmente."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error during cloud sync", e)
                val status = classifySyncFailure(e)
                _accountSummary.update {
                    it.copy(
                        syncStatus = status,
                        isSyncing = false,
                        lastSyncMessage = describeRemoteError(
                            e,
                            "No se pudo completar la sincronización. Tus datos locales siguen seguros."
                        )
                    )
                }
            } finally {
                withContext(NonCancellable) {
                    _accountSummary.update { it.copy(isSyncing = false) }
                }
                syncInFlight.set(false)
            }
        }
        job.invokeOnCompletion {
            _accountSummary.update { it.copy(isSyncing = false) }
            syncInFlight.set(false)
        }
        activeSyncJob = job
    }

    private suspend fun runTimedStep(block: suspend () -> Result<Unit>): Result<Unit> {
        return try {
            withTimeout(operationTimeoutMs) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteCloudMemory(memoryDocId: String) {
        val user = _authUser.value ?: return
        viewModelScope.launch {
            runTimedStep {
                getOrCreateRemoteStore().deleteMemory(user.userId, memoryDocId)
            }
        }
    }

    fun deleteCloudConversation(conversationDocId: String) {
        val user = _authUser.value ?: return
        viewModelScope.launch {
            runTimedStep {
                getOrCreateRemoteStore().deleteCloudConversation(user.userId, conversationDocId)
            }
        }
    }

    override fun onCleared() {
        activeSyncJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "CloudSessionVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val DEFAULT_OPERATION_TIMEOUT_MS = 8_000L
        private const val DEFAULT_GLOBAL_TIMEOUT_MS = 20_000L

        private fun buildInitialSummary(user: CloudAuthUser?): CloudAccountSummary {
            return if (user == null) {
                CloudAccountSummary(
                    userId = null,
                    isAuthenticated = false,
                    displayName = "Modo local (sin cuenta)",
                    email = null,
                    syncStatus = CloudSyncStatus.NO_FIREBASE_SESSION,
                    isSyncing = false,
                    lastSyncMessage = "Operando localmente con Room y DataStore."
                )
            } else {
                CloudAccountSummary(
                    userId = user.userId,
                    isAuthenticated = true,
                    displayName = user.displayName.ifBlank { "Usuario Neptuno" },
                    email = user.email,
                    syncStatus = CloudSyncStatus.CONNECTING,
                    isSyncing = false,
                    lastSyncMessage = "Conectando con Cloud Firestore…"
                )
            }
        }

        internal fun classifySyncFailure(error: Throwable): CloudSyncStatus {
            val firestoreException = NeptunoFirestoreRepository.unwrapFirestoreException(error)
            if (firestoreException != null) {
                return when (firestoreException.code) {
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> CloudSyncStatus.AUTH_ERROR
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> CloudSyncStatus.PERMISSION_ERROR
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CloudSyncStatus.TIMEOUT
                    FirebaseFirestoreException.Code.UNAVAILABLE -> CloudSyncStatus.OFFLINE
                    else -> CloudSyncStatus.PARTIAL_SYNC
                }
            }
            if (error is TimeoutCancellationException) return CloudSyncStatus.TIMEOUT
            val msg = error.localizedMessage.orEmpty()
            return when {
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                    msg.contains("Missing or insufficient permissions", ignoreCase = true) ->
                    CloudSyncStatus.PERMISSION_ERROR
                msg.contains("UNAUTHENTICATED", ignoreCase = true) ||
                    msg.contains("must be signed in", ignoreCase = true) ->
                    CloudSyncStatus.AUTH_ERROR
                msg.contains("UNAVAILABLE", ignoreCase = true) ||
                    msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("network", ignoreCase = true) ->
                    CloudSyncStatus.OFFLINE
                msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("DEADLINE_EXCEEDED", ignoreCase = true) ->
                    CloudSyncStatus.TIMEOUT
                else -> CloudSyncStatus.PARTIAL_SYNC
            }
        }

        internal fun describeRemoteError(error: Throwable, fallback: String): String {
            return when (classifySyncFailure(error)) {
                CloudSyncStatus.AUTH_ERROR ->
                    "Error de autenticación con Firebase: vuelve a iniciar sesión si deseas sincronizar."
                CloudSyncStatus.PERMISSION_ERROR ->
                    "Error de permisos en Firestore para esta cuenta o documento."
                CloudSyncStatus.TIMEOUT ->
                    "Tiempo de espera agotado al contactar con Cloud Firestore."
                CloudSyncStatus.OFFLINE ->
                    "Cloud Firestore no accesible temporalmente por red."
                else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
            }
        }

        fun factory(
            initialUser: CloudAuthUser?,
            preferencesRepository: UserPreferencesRepository,
            memoryRepository: MemoryRepository,
            conversationRepository: ConversationRepository,
            networkMonitor: NetworkMonitor
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                CloudSessionViewModel(
                    initialUser = initialUser,
                    remoteStoreFactory = {
                        val databaseId = app.getString(R.string.firestore_database_id)
                        NeptunoFirestoreRepository(FirebaseFirestore.getInstance(databaseId))
                    },
                    preferencesRepository = preferencesRepository,
                    memoryRepository = memoryRepository,
                    conversationRepository = conversationRepository,
                    networkMonitor = networkMonitor
                )
            }
        }
    }
}
