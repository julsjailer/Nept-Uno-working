package com.example.neptuno

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.ai.gemini.GeminiErrorCategory
import com.example.neptuno.ai.gemini.GeminiFirebaseProvider
import com.example.neptuno.ai.gemini.GeminiStreamClient
import com.example.neptuno.core.NetworkConnectivityState
import com.example.neptuno.core.NetworkMonitor
import com.example.neptuno.core.NetworkStatus
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.ChatMessage
import com.example.neptuno.core.model.MemoryCategory
import com.example.neptuno.core.model.MemoryEntry
import com.example.neptuno.core.model.MessageSender
import com.example.neptuno.core.model.PersonalityMode
import com.example.neptuno.data.local.LocalConversationRepository
import com.example.neptuno.data.local.LocalMemoryRepository
import com.example.neptuno.data.local.NeptunoDatabase
import com.example.neptuno.data.local.UserPreferencesRepository
import com.example.neptuno.data.remote.CloudRemoteStore
import com.example.neptuno.data.remote.CloudUiState
import com.example.neptuno.data.remote.FirestoreChatMessage
import com.example.neptuno.data.remote.FirestoreConversation
import com.example.neptuno.data.remote.FirestoreMemoryEntry
import com.example.neptuno.data.remote.FirestoreUserProfile
import com.example.neptuno.feature.cloud.CloudAuthUser
import com.example.neptuno.feature.cloud.CloudSessionViewModel
import com.example.neptuno.feature.cloud.CloudSyncStatus
import com.example.neptuno.feature.memory.MemoryViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * Validación Robolectric/Unit de arquitectura offline-first, autenticación opcional,
 * red validada vs sin Internet, App Check / Gemini honesto y escenarios A–J de sincronización.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NeptunoOfflineFirstSyncTest {

    private lateinit var mainDispatcher: ExecutorCoroutineDispatcher
    private lateinit var context: Context
    private lateinit var database: NeptunoDatabase
    private lateinit var conversationRepo: LocalConversationRepository
    private lateinit var memoryRepo: LocalMemoryRepository
    private lateinit var preferencesRepo: UserPreferencesRepository
    private lateinit var fakeNetworkMonitor: TestNetworkMonitor
    private lateinit var fakeRemoteStore: FakeCloudRemoteStore

    @Before
    fun setUp() {
        mainDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "NeptunoTestMain")
        }.asCoroutineDispatcher()
        Dispatchers.setMain(mainDispatcher)
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
            preferencesRepo.setOfflineMode(false)
            preferencesRepo.setMemoryAuthorized(true)
            preferencesRepo.setPersistConversationHistory(true)
        }
        fakeNetworkMonitor = TestNetworkMonitor(context)
        fakeRemoteStore = FakeCloudRemoteStore()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    @Test
    fun `escenario A primer inicio sin cuenta Google abre en modo local sin bloquearse`() = runBlocking {
        val vm = createCloudSessionViewModel(initialUser = null)

        val summary = vm.accountSummary.value
        assertFalse(summary.isAuthenticated)
        assertFalse(summary.isSyncing)
        assertEquals(CloudSyncStatus.NO_FIREBASE_SESSION, summary.syncStatus)
        assertFalse(vm.cloudSyncGateway.isCloudEnabled())
        assertTrue(vm.profileState.value is CloudUiState.Idle)
        assertTrue(vm.cloudMemoriesState.value is CloudUiState.Idle)
        assertTrue(vm.cloudConversationsState.value is CloudUiState.Idle)

        // Usuario crea memoria y conversación localmente sin sesión Google ni llamadas remotas
        val memoryVm = MemoryViewModel(
            memoryRepository = memoryRepo,
            conversationRepository = conversationRepo,
            preferencesRepository = preferencesRepo,
            cloudSyncGateway = vm.cloudSyncGateway
        )
        memoryVm.saveAuthorizedMemory("Preferencia local", "Usar modo oceánico", MemoryCategory.PREFERENCE)
        val convId = conversationRepo.createConversation("Chat local inicial")
        conversationRepo.addMessage(
            ChatMessage(
                conversationId = convId,
                sender = MessageSender.USER,
                content = "Hola sin cuenta"
            )
        )

        awaitCondition { memoryRepo.observeMemories().first().size == 1 }
        assertEquals(0, fakeRemoteStore.upsertProfileCalls)
        assertEquals(0, fakeRemoteStore.savedMemories.size)
        assertEquals(0, fakeRemoteStore.savedConversations.size)
    }

    @Test
    fun `escenario B inicio con sesion Google valida existente sincroniza automaticamente sin bloquear`() = runBlocking {
        val user = CloudAuthUser("uid_valid_1", "Ana Oceánica", "ana@example.com")
        val vm = createCloudSessionViewModel(initialUser = user, autoSyncOnInit = true)

        awaitCondition({ "summary=${vm.accountSummary.value}, upsert=${fakeRemoteStore.upsertProfileCalls}" }) {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.SYNCED
        }

        val summary = vm.accountSummary.value
        assertTrue(summary.isAuthenticated)
        assertEquals("uid_valid_1", summary.userId)
        assertTrue(vm.cloudSyncGateway.isCloudEnabled())
        assertEquals(1, fakeRemoteStore.upsertProfileCalls)
        assertNotNull(summary.lastSyncedAtMillis)
    }

    @Test
    fun `escenario C cancelacion de inicio de sesion mantiene modo local y datos intactos`() = runBlocking {
        val localMemId = memoryRepo.saveMemory(
            MemoryEntry(title = "Dato local", content = "No perder al cancelar login")
        )
        val vm = createCloudSessionViewModel(initialUser = null)

        // Simula retorno sin autenticación tras cancelar Google Sign-In
        vm.updateAuthUser(null)
        vm.syncProfileAndLocalState()

        val summary = vm.accountSummary.value
        assertFalse(summary.isAuthenticated)
        assertFalse(summary.isSyncing)
        assertEquals(CloudSyncStatus.NO_FIREBASE_SESSION, summary.syncStatus)
        val persistedMemories = memoryRepo.observeMemories().first()
        assertEquals(1, persistedMemories.size)
        assertEquals(localMemId, persistedMemories.first().id)
        assertEquals(0, fakeRemoteStore.upsertProfileCalls)
    }

    @Test
    fun `escenario D Firebase inaccesible o con error de permisos no bloquea ni deja isSyncing activo`() = runBlocking {
        memoryRepo.saveMemory(MemoryEntry(title = "Nota crítica", content = "Debe seguir en Room"))
        fakeRemoteStore.failMemoryWith = FirebaseFirestoreException(
            "Missing or insufficient permissions",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )

        val user = CloudAuthUser("uid_perm_err", "Carlos", "carlos@example.com")
        val vm = createCloudSessionViewModel(initialUser = user, autoSyncOnInit = true)

        awaitCondition({ "summary=${vm.accountSummary.value}" }) {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.PERMISSION_ERROR
        }

        val summary = vm.accountSummary.value
        assertFalse(summary.isSyncing)
        assertNotNull(summary.memoriesError)
        assertEquals(1, memoryRepo.observeMemories().first().size)
    }

    @Test
    fun `escenario E dispositivo offline evita llamadas remotas y mantiene Room operativo`() = runBlocking {
        fakeNetworkMonitor.statusFlow.value = NetworkMonitor.evaluateCapabilities(
            hasActiveNetwork = false,
            hasInternetCapability = false,
            hasValidatedCapability = false,
            transportLabel = "Sin conexión"
        )
        val user = CloudAuthUser("uid_offline", "Lucía", "lucia@example.com")
        val vm = createCloudSessionViewModel(initialUser = user, autoSyncOnInit = true)

        awaitCondition {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.OFFLINE
        }

        assertEquals(0, fakeRemoteStore.upsertProfileCalls)
        // Escritura en Room sigue funcionando con gateway habilitado sin colgar ni fallar
        val memId = memoryRepo.saveMemory(MemoryEntry(title = "Offline", content = "Guardado local"))
        vm.cloudSyncGateway.syncMemorySaved(MemoryEntry(id = memId, title = "Offline", content = "Guardado local"))
        assertEquals(1, memoryRepo.observeMemories().first().size)
        assertEquals(0, fakeRemoteStore.savedMemories.size)
    }

    @Test
    fun `escenario F red sin Internet validado vs Firebase que no responde distingue estados correctamente`() = runBlocking {
        // 1. Red conectada sin Internet validado
        val wifiNoInternet = NetworkMonitor.evaluateCapabilities(
            hasActiveNetwork = true,
            hasInternetCapability = true,
            hasValidatedCapability = false,
            transportLabel = "Wi-Fi"
        )
        assertEquals(NetworkConnectivityState.CONNECTED_WITHOUT_INTERNET, wifiNoInternet.connectivityState)
        assertFalse(wifiNoInternet.isConnected)
        assertTrue(wifiNoInternet.isConnectedWithoutInternet)
        assertTrue(wifiNoInternet.describeWithOfflineMode(false).contains("sin acceso a Internet", ignoreCase = true))

        // 2. Internet validado pero Firebase colgado (sin respuesta ni error inmediato)
        fakeNetworkMonitor.statusFlow.value = NetworkMonitor.evaluateCapabilities(
            hasActiveNetwork = true,
            hasInternetCapability = true,
            hasValidatedCapability = true,
            transportLabel = "Wi-Fi"
        )
        fakeRemoteStore.hangProfileGate = CompletableDeferred()
        val user = CloudAuthUser("uid_hang", "Marco", "marco@example.com")
        val vm = createCloudSessionViewModel(
            initialUser = user,
            operationTimeoutMs = 150L,
            globalTimeoutMs = 2_000L,
            autoSyncOnInit = true
        )

        awaitCondition({ "summary=${vm.accountSummary.value}" }) {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.profileError != null
        }
        assertFalse(vm.accountSummary.value.isSyncing)
        assertTrue(
            vm.accountSummary.value.syncStatus == CloudSyncStatus.PARTIAL_SYNC ||
                vm.accountSummary.value.syncStatus == CloudSyncStatus.TIMEOUT
        )
    }

    @Test
    fun `escenario G iniciar sesion tras uso local en Room sube todo sin borrar ni duplicar datos`() = runBlocking {
        // Uso previo completamente local (sin sesión)
        val vm = createCloudSessionViewModel(initialUser = null)
        val memId = memoryRepo.saveMemory(MemoryEntry(title = "Idioma", content = "Español técnico"))
        val convId = conversationRepo.createConversation("Proyecto Neptuno")
        val msg1Id = conversationRepo.addMessage(
            ChatMessage(conversationId = convId, sender = MessageSender.USER, content = "Hola Neptuno")
        )
        val msg2Id = conversationRepo.addMessage(
            ChatMessage(conversationId = convId, sender = MessageSender.ASSISTANT, content = "Listo para ayudar", providerId = "gemini_firebase")
        )

        // Inicia sesión más tarde
        vm.updateAuthUser(CloudAuthUser("uid_late_login", "Elena", "elena@example.com"))

        awaitCondition({ "summary=${vm.accountSummary.value}" }) {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.SYNCED
        }

        // Verifica preservación local
        assertEquals(1, memoryRepo.observeMemories().first().size)
        assertEquals(2, conversationRepo.observeMessages(convId).first().size)

        // Verifica subida idempotente con IDs deterministas
        assertTrue(fakeRemoteStore.savedMemories.containsKey("uid_late_login:local_$memId"))
        assertTrue(fakeRemoteStore.savedConversations.containsKey("uid_late_login:conv_$convId"))
        assertTrue(fakeRemoteStore.savedMessages.containsKey("uid_late_login:conv_$convId:local_msg_$msg1Id"))
        assertTrue(fakeRemoteStore.savedMessages.containsKey("uid_late_login:conv_$convId:local_msg_$msg2Id"))
        val initialMessageCount = fakeRemoteStore.savedMessages.size

        // Segunda sincronización manual NO debe duplicar memorias ni mensajes
        vm.syncProfileAndLocalState()
        awaitCondition {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.SYNCED
        }
        assertEquals(1, fakeRemoteStore.savedMemories.size)
        assertEquals(initialMessageCount, fakeRemoteStore.savedMessages.size)
    }

    @Test
    fun `escenario H cerrar sesion conserva datos en Room y permite seguir operando localmente`() = runBlocking {
        val user = CloudAuthUser("uid_signout", "Sofía", "sofia@example.com")
        val vm = createCloudSessionViewModel(initialUser = user, autoSyncOnInit = true)
        awaitCondition({ "summary=${vm.accountSummary.value}" }) { vm.accountSummary.value.syncStatus == CloudSyncStatus.SYNCED }

        val memId = memoryRepo.saveMemory(MemoryEntry(title = "Recuerdo previo", content = "Persistente"))
        val convId = conversationRepo.createConversation("Sesión autenticada")

        // Cierre de sesión
        vm.updateAuthUser(null)

        val summary = vm.accountSummary.value
        assertFalse(summary.isAuthenticated)
        assertFalse(summary.isSyncing)
        assertEquals(CloudSyncStatus.NO_FIREBASE_SESSION, summary.syncStatus)
        assertFalse(vm.cloudSyncGateway.isCloudEnabled())

        // Room NO se borra y admite nuevas escrituras locales sin tocar Firestore
        assertEquals(1, memoryRepo.observeMemories().first().size)
        assertEquals(1, conversationRepo.observeConversations().first().size)
        val afterSignOutMemId = memoryRepo.saveMemory(MemoryEntry(title = "Post cierre", content = "Solo local"))
        vm.cloudSyncGateway.syncMemorySaved(MemoryEntry(id = afterSignOutMemId, title = "Post cierre", content = "Solo local"))
        assertEquals(2, memoryRepo.observeMemories().first().size)
        assertFalse(fakeRemoteStore.savedMemories.containsKey("uid_signout:local_$afterSignOutMemId"))
        assertTrue(memId > 0L && convId > 0L)
    }

    @Test
    fun `escenario I recuperar conexion y sincronizar manualmente sube cambios locales pendientes`() = runBlocking {
        fakeNetworkMonitor.statusFlow.value = NetworkMonitor.evaluateCapabilities(
            hasActiveNetwork = false,
            hasInternetCapability = false,
            hasValidatedCapability = false,
            transportLabel = "Sin conexión"
        )
        val user = CloudAuthUser("uid_recover", "Diego", "diego@example.com")
        val vm = createCloudSessionViewModel(initialUser = user, autoSyncOnInit = true)

        awaitCondition {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.OFFLINE
        }

        // Usuario crea memoria mientras estaba offline
        val offlineMemId = memoryRepo.saveMemory(MemoryEntry(title = "Offline primero", content = "Subir al reconectar"))
        assertEquals(0, fakeRemoteStore.savedMemories.size)

        // Recupera Internet validado y sincroniza manualmente
        fakeNetworkMonitor.statusFlow.value = NetworkMonitor.evaluateCapabilities(
            hasActiveNetwork = true,
            hasInternetCapability = true,
            hasValidatedCapability = true,
            transportLabel = "Wi-Fi"
        )
        vm.syncProfileAndLocalState()

        awaitCondition {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.SYNCED
        }
        assertTrue(fakeRemoteStore.savedMemories.containsKey("uid_recover:local_$offlineMemId"))
    }

    @Test
    fun `escenario J recuperacion despues de timeout no queda bloqueado en Sincronizando y permite reintento exitoso`() = runBlocking {
        fakeRemoteStore.hangGlobalGate = CompletableDeferred()
        val user = CloudAuthUser("uid_timeout_recovery", "Valeria", "valeria@example.com")
        val vm = createCloudSessionViewModel(
            initialUser = user,
            operationTimeoutMs = 400L,
            globalTimeoutMs = 150L,
            autoSyncOnInit = true
        )

        awaitCondition({ "summary=${vm.accountSummary.value}" }) {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.TIMEOUT
        }
        assertFalse(vm.accountSummary.value.isSyncing)

        // Backend vuelve a responder: el reintento manual NO debe estar bloqueado por mutex/flag
        fakeRemoteStore.hangGlobalGate = null
        vm.syncProfileAndLocalState()

        awaitCondition {
            !vm.accountSummary.value.isSyncing &&
                vm.accountSummary.value.syncStatus == CloudSyncStatus.SYNCED
        }
        assertFalse(vm.accountSummary.value.isSyncing)
        assertNotNull(vm.accountSummary.value.lastSyncedAtMillis)
    }

    @Test
    fun `gemini provider no declara operativo antes de respuesta real y clasifica errores AppCheck Auth Config Red`() = runBlocking {
        // Inicialmente Configured, NO Ready
        val failingAppCheckClient = GeminiStreamClient { _, _, _ ->
            flow { throw IllegalStateException("HTTP 403 Firebase App Check attestation failed") }
        }
        val appCheckProvider = GeminiFirebaseProvider(streamClient = failingAppCheckClient)
        assertTrue(appCheckProvider.status is AiProviderStatus.Configured)

        val errEvents = appCheckProvider.streamResponse(
            AiRequest(1L, "Hola", emptyList(), emptyList(), PersonalityMode.ANALYTICAL, "es")
        ).toList()
        assertTrue(errEvents.single() is AiResponseEvent.Failure)
        assertTrue(appCheckProvider.status is AiProviderStatus.Unavailable)
        assertTrue((appCheckProvider.status as AiProviderStatus.Unavailable).reason.contains("App Check", ignoreCase = true))

        // Clasificación diferenciada
        assertEquals(
            GeminiErrorCategory.AUTH,
            GeminiFirebaseProvider.classifyProviderException(IllegalStateException("401 UNAUTHENTICATED token")).category
        )
        assertEquals(
            GeminiErrorCategory.QUOTA,
            GeminiFirebaseProvider.classifyProviderException(IllegalStateException("429 RESOURCE_EXHAUSTED quota")).category
        )
        assertEquals(
            GeminiErrorCategory.NETWORK,
            GeminiFirebaseProvider.classifyProviderException(IllegalStateException("Unable to resolve host")).category
        )

        // Pasa a Ready solo tras respuesta real exitosa
        val workingProvider = GeminiFirebaseProvider(
            streamClient = GeminiStreamClient { _, _, _ -> flow { emit("Hola desde Gemini") } }
        )
        assertTrue(workingProvider.status is AiProviderStatus.Configured)
        val okEvents = workingProvider.streamResponse(
            AiRequest(1L, "Hola", emptyList(), emptyList(), PersonalityMode.ANALYTICAL, "es")
        ).toList()
        assertTrue(okEvents.any { it is AiResponseEvent.Completed })
        assertTrue(workingProvider.status is AiProviderStatus.Ready)
    }

    private fun createCloudSessionViewModel(
        initialUser: CloudAuthUser?,
        operationTimeoutMs: Long = 2_000L,
        globalTimeoutMs: Long = 4_000L,
        autoSyncOnInit: Boolean = false
    ): CloudSessionViewModel {
        return CloudSessionViewModel(
            initialUser = initialUser,
            remoteStoreFactory = { fakeRemoteStore },
            preferencesRepository = preferencesRepo,
            memoryRepository = memoryRepo,
            conversationRepository = conversationRepo,
            networkMonitor = fakeNetworkMonitor,
            operationTimeoutMs = operationTimeoutMs,
            globalTimeoutMs = globalTimeoutMs,
            autoSyncOnInit = autoSyncOnInit
        )
    }

    private suspend fun awaitCondition(
        diagnostic: () -> String = { "Condition timed out" },
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

    private class TestNetworkMonitor(context: Context) : NetworkMonitor(context) {
        val statusFlow = MutableStateFlow(
            NetworkMonitor.evaluateCapabilities(
                hasActiveNetwork = true,
                hasInternetCapability = true,
                hasValidatedCapability = true,
                transportLabel = "Wi-Fi"
            )
        )
        override fun observeNetworkStatus(): Flow<NetworkStatus> = statusFlow
    }

    private class FakeCloudRemoteStore : CloudRemoteStore {
        var upsertProfileCalls = 0
        var failMemoryWith: Throwable? = null
        var hangProfileGate: CompletableDeferred<Unit>? = null
        var hangGlobalGate: CompletableDeferred<Unit>? = null

        val savedMemories = linkedMapOf<String, FirestoreMemoryEntry>()
        val savedConversations = linkedMapOf<String, FirestoreConversation>()
        val savedMessages = linkedMapOf<String, FirestoreChatMessage>()

        override fun observeUserProfile(userId: String): Flow<FirestoreUserProfile?> =
            flow { emit(null) }

        override fun observeMemories(userId: String): Flow<List<FirestoreMemoryEntry>> =
            flow { emit(savedMemories.values.filter { it.userId == userId }) }

        override fun observeConversations(userId: String): Flow<List<FirestoreConversation>> =
            flow { emit(savedConversations.values.filter { it.userId == userId }) }

        override fun observeMessages(userId: String, conversationDocId: String): Flow<List<FirestoreChatMessage>> =
            flow { emit(savedMessages.values.filter { it.userId == userId && it.conversationId == conversationDocId }) }

        override suspend fun upsertUserProfile(
            userId: String,
            displayName: String,
            email: String?,
            languageCode: String,
            personalityId: String,
            avatarStyleId: String
        ): Result<Unit> {
            hangGlobalGate?.await()
            hangProfileGate?.await()
            upsertProfileCalls += 1
            return Result.success(Unit)
        }

        override suspend fun saveMemory(
            userId: String,
            title: String,
            content: String,
            category: MemoryCategory,
            localId: Long?
        ): Result<String> {
            hangGlobalGate?.await()
            failMemoryWith?.let { return Result.failure(it) }
            val docId = localId?.let { "local_$it" } ?: "mem_${savedMemories.size + 1}"
            savedMemories["$userId:$docId"] = FirestoreMemoryEntry(
                id = docId,
                userId = userId,
                title = title,
                content = content,
                category = category.name,
                authorizedByUser = true,
                localId = localId
            )
            return Result.success(docId)
        }

        override suspend fun deleteMemory(userId: String, memoryDocId: String): Result<Unit> {
            savedMemories.remove("$userId:$memoryDocId")
            return Result.success(Unit)
        }

        override suspend fun clearAllMemories(userId: String): Result<Unit> {
            savedMemories.keys.removeAll { it.startsWith("$userId:") }
            return Result.success(Unit)
        }

        override suspend fun ensureCloudConversation(
            userId: String,
            localConversationId: Long,
            title: String
        ): Result<String> {
            hangGlobalGate?.await()
            val convDocId = "conv_$localConversationId"
            savedConversations["$userId:$convDocId"] = FirestoreConversation(
                id = convDocId,
                userId = userId,
                title = title,
                localId = localConversationId
            )
            return Result.success(convDocId)
        }

        override suspend fun appendCloudMessage(
            userId: String,
            localConversationId: Long,
            conversationTitle: String,
            sender: MessageSender,
            content: String,
            providerId: String?,
            interrupted: Boolean,
            localMessageId: Long?
        ): Result<Unit> {
            hangGlobalGate?.await()
            val convDocId = "conv_$localConversationId"
            val msgDocId = localMessageId?.let { "local_msg_$it" } ?: "msg_${savedMessages.size + 1}"
            val key = "$userId:$convDocId:$msgDocId"
            if (!savedMessages.containsKey(key)) {
                savedMessages[key] = FirestoreChatMessage(
                    id = msgDocId,
                    userId = userId,
                    conversationId = convDocId,
                    sender = sender.name,
                    content = content,
                    interrupted = interrupted,
                    providerId = providerId
                )
            }
            return Result.success(Unit)
        }

        override suspend fun deleteCloudConversation(userId: String, conversationDocId: String): Result<Unit> {
            savedConversations.remove("$userId:$conversationDocId")
            return Result.success(Unit)
        }

        override suspend fun clearAllCloudConversations(userId: String): Result<Unit> {
            savedConversations.keys.removeAll { it.startsWith("$userId:") }
            return Result.success(Unit)
        }
    }
}
