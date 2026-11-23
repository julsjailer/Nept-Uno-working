package com.example.neptuno.data.remote

import androidx.annotation.Keep
import com.example.neptuno.core.model.MemoryCategory
import com.example.neptuno.core.model.MessageSender
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface CloudUiState<out T> {
    data object Idle : CloudUiState<Nothing>
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val languageCode: String = "es",
    val personalityId: String = "analytical",
    val avatarStyleId: String = "abyssal_core",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreMemoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = MemoryCategory.GENERAL.name,
    val authorizedByUser: Boolean = true,
    val localId: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): MemoryCategory =
        enumValues<MemoryCategory>().firstOrNull { it.name == category } ?: MemoryCategory.GENERAL
}

@Keep
@IgnoreExtraProperties
data class FirestoreConversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val messageCount: Long = 0L,
    val localId: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val sender: String = MessageSender.SYSTEM_NOTICE.name,
    val content: String = "",
    val interrupted: Boolean = false,
    val providerId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSender(): MessageSender =
        enumValues<MessageSender>().firstOrNull { it.name == sender } ?: MessageSender.SYSTEM_NOTICE
}
