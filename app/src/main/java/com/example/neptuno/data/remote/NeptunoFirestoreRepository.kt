package com.example.neptuno.data.remote

import android.content.Context
import com.example.R
import com.example.neptuno.core.model.MemoryCategory
import com.example.neptuno.core.model.MessageSender
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface CloudRemoteStore {
    fun observeUserProfile(userId: String): Flow<FirestoreUserProfile?>
    fun observeMemories(userId: String): Flow<List<FirestoreMemoryEntry>>
    fun observeConversations(userId: String): Flow<List<FirestoreConversation>>
    fun observeMessages(userId: String, conversationDocId: String): Flow<List<FirestoreChatMessage>>

    suspend fun upsertUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        languageCode: String,
        personalityId: String,
        avatarStyleId: String
    ): Result<Unit>

    suspend fun saveMemory(
        userId: String,
        title: String,
        content: String,
        category: MemoryCategory,
        localId: Long? = null
    ): Result<String>

    suspend fun deleteMemory(userId: String, memoryDocId: String): Result<Unit>
    suspend fun clearAllMemories(userId: String): Result<Unit>

    suspend fun ensureCloudConversation(
        userId: String,
        localConversationId: Long,
        title: String
    ): Result<String>

    suspend fun appendCloudMessage(
        userId: String,
        localConversationId: Long,
        conversationTitle: String,
        sender: MessageSender,
        content: String,
        providerId: String?,
        interrupted: Boolean,
        localMessageId: Long? = null
    ): Result<Unit>

    suspend fun deleteCloudConversation(userId: String, conversationDocId: String): Result<Unit>
    suspend fun clearAllCloudConversations(userId: String): Result<Unit>
}

class NeptunoFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : CloudRemoteStore {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) {
            "User must be signed in with Google before accessing Firestore."
        }
        return uid
    }

    override fun observeUserProfile(userId: String): Flow<FirestoreUserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch; refusing cross-user profile read." }
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        FirestoreUserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    override fun observeMemories(userId: String): Flow<List<FirestoreMemoryEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch; refusing cross-user memories read." }
        val colRef = db.collection("users").document(uid).collection("memories")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreMemoryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    override fun observeConversations(userId: String): Flow<List<FirestoreConversation>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch; refusing cross-user conversations read." }
        val colRef = db.collection("users").document(uid).collection("conversations")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreConversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    override fun observeMessages(userId: String, conversationDocId: String): Flow<List<FirestoreChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch; refusing cross-user messages read." }
        require(isValidFirestoreId(conversationDocId)) { "Invalid conversation document ID." }
        val colRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationDocId)
            .collection("messages")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    override suspend fun upsertUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        languageCode: String,
        personalityId: String,
        avatarStyleId: String
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user profile write." }
            val safeName = displayName.trim().ifBlank { "Usuario Neptuno" }.take(80)
            val safeEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
            val safeLang = languageCode.takeIf { it in ALLOWED_LANGUAGES } ?: "es"
            val safePersonality = personalityId.takeIf { it in ALLOWED_PERSONALITIES } ?: "analytical"
            val safeAvatar = avatarStyleId.takeIf { it in ALLOWED_AVATARS } ?: "abyssal_core"

            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    put("languageCode", safeLang)
                    put("personalityId", safePersonality)
                    put("avatarStyleId", safeAvatar)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    put("languageCode", safeLang)
                    put("personalityId", safePersonality)
                    put("avatarStyleId", safeAvatar)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath, auth)
            Result.failure(e)
        }
    }

    override suspend fun saveMemory(
        userId: String,
        title: String,
        content: String,
        category: MemoryCategory,
        localId: Long?
    ): Result<String> {
        val cleanTitle = title.trim().take(120)
        val cleanContent = content.trim().take(2000)
        val docId = localId?.takeIf { it > 0L }?.let { "local_$it" }
        var docPath = "users/$userId/memories/${docId ?: "new"}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user memory write." }
            require(cleanTitle.isNotEmpty()) { "El título de memoria no puede estar vacío." }
            require(cleanContent.isNotEmpty()) { "El contenido de memoria no puede estar vacío." }

            val colRef = db.collection("users").document(uid).collection("memories")
            val docRef = if (docId != null) colRef.document(docId) else colRef.document()
            docPath = docRef.path

            val existing = if (docId != null) docRef.get().await() else null
            if (existing != null && existing.exists()) {
                val updates = buildMap<String, Any> {
                    put("title", cleanTitle)
                    put("content", cleanContent)
                    put("category", category.name)
                    if (localId != null && localId > 0L) put("localId", localId)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("title", cleanTitle)
                    put("content", cleanContent)
                    put("category", category.name)
                    put("authorizedByUser", true)
                    if (localId != null && localId > 0L) put("localId", localId)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath, auth)
            Result.failure(e)
        }
    }

    override suspend fun deleteMemory(userId: String, memoryDocId: String): Result<Unit> {
        val docPath = "users/$userId/memories/$memoryDocId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user memory delete." }
            require(isValidFirestoreId(memoryDocId)) { "ID de memoria inválido." }
            db.collection("users").document(uid)
                .collection("memories").document(memoryDocId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath, auth)
            Result.failure(e)
        }
    }

    override suspend fun clearAllMemories(userId: String): Result<Unit> {
        val colPath = "users/$userId/memories"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user memories clear." }
            val snapshot = db.collection("users").document(uid)
                .collection("memories").get().await()
            snapshot.documents.chunked(400).forEach { chunk ->
                db.runBatch { batch ->
                    chunk.forEach { batch.delete(it.reference) }
                }.await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath, auth)
            Result.failure(e)
        }
    }

    override suspend fun ensureCloudConversation(
        userId: String,
        localConversationId: Long,
        title: String
    ): Result<String> {
        val convDocId = "conv_$localConversationId"
        val docPath = "users/$userId/conversations/$convDocId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user conversation write." }
            val safeTitle = title.trim().ifBlank { "Sesión principal" }.take(160)
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document(convDocId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to safeTitle,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("title", safeTitle)
                    put("messageCount", 0L)
                    if (localConversationId >= 0L) put("localId", localConversationId)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(convDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath, auth)
            Result.failure(e)
        }
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
        val convDocId = "conv_$localConversationId"
        val docPath = "users/$userId/conversations/$convDocId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user message write." }
            val cleanContent = content.trim().take(16000)
            require(cleanContent.isNotEmpty()) { "El mensaje no puede estar vacío." }
            val safeTitle = conversationTitle.trim().ifBlank { "Sesión principal" }.take(160)
            val cleanProvider = providerId?.trim()?.takeIf { it.isNotEmpty() }?.take(80)

            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(convDocId)
            val deterministicMsgId = localMessageId?.takeIf { it > 0L }?.let { "local_msg_$it" }
            val msgRef = if (deterministicMsgId != null) {
                convRef.collection("messages").document(deterministicMsgId)
            } else {
                convRef.collection("messages").document()
            }

            // Idempotencia: las reglas bloquean update en mensajes; si ya existe, no duplicar ni incrementar.
            if (deterministicMsgId != null && msgRef.get().await().exists()) {
                return Result.success(Unit)
            }

            val convSnap = convRef.get().await()
            val msgPayload = buildMap<String, Any> {
                put("userId", uid)
                put("conversationId", convDocId)
                put("sender", sender.name)
                put("content", cleanContent)
                put("interrupted", interrupted)
                if (cleanProvider != null) put("providerId", cleanProvider)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            db.runBatch { batch ->
                if (!convSnap.exists()) {
                    val convPayload = buildMap<String, Any> {
                        put("userId", uid)
                        put("title", safeTitle)
                        put("messageCount", 1L)
                        if (localConversationId >= 0L) put("localId", localConversationId)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.set(convRef, convPayload)
                } else {
                    batch.update(
                        convRef,
                        mapOf(
                            "messageCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.set(msgRef, msgPayload)
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath, auth)
            Result.failure(e)
        }
    }

    override suspend fun deleteCloudConversation(userId: String, conversationDocId: String): Result<Unit> {
        val docPath = "users/$userId/conversations/$conversationDocId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user conversation delete." }
            require(isValidFirestoreId(conversationDocId)) { "ID de conversación inválido." }

            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationDocId)
            val messages = convRef.collection("messages").get().await()
            messages.documents.chunked(400).forEach { chunk ->
                db.runBatch { batch ->
                    chunk.forEach { batch.delete(it.reference) }
                }.await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath, auth)
            Result.failure(e)
        }
    }

    override suspend fun clearAllCloudConversations(userId: String): Result<Unit> {
        val colPath = "users/$userId/conversations"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch; refusing cross-user conversations clear." }
            val convs = db.collection("users").document(uid)
                .collection("conversations").get().await()
            for (conv in convs.documents) {
                deleteCloudConversation(uid, conv.id).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath, auth)
            Result.failure(e)
        }
    }

    companion object {
        private val ALLOWED_LANGUAGES = setOf("es", "en", "pt")
        private val ALLOWED_PERSONALITIES = setOf("analytical", "calm_guide", "concise", "explorer")
        private val ALLOWED_AVATARS = setOf("abyssal_core", "tidal_pulse", "trident_matrix")
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

        fun isValidFirestoreId(id: String): Boolean =
            id.length in 1..128 && VALID_ID_REGEX.matches(id)

        fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
            generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
    }
}
