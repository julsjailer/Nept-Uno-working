package com.example.neptuno

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.neptuno.core.model.AppLanguage
import com.example.neptuno.core.model.AvatarStyle
import com.example.neptuno.core.model.ChatMessage
import com.example.neptuno.core.model.MemoryCategory
import com.example.neptuno.core.model.MemoryEntry
import com.example.neptuno.core.model.MessageSender
import com.example.neptuno.core.model.PersonalityMode
import com.example.neptuno.core.model.ThemeMode
import com.example.neptuno.data.local.ConversationEntity
import com.example.neptuno.data.local.LocalConversationRepository
import com.example.neptuno.data.local.LocalMemoryRepository
import com.example.neptuno.data.local.MemoryEntity
import com.example.neptuno.data.local.MessageEntity
import com.example.neptuno.data.local.NeptunoDatabase
import com.example.neptuno.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NeptunoPersistenceTest {

    private lateinit var context: Context
    private lateinit var database: NeptunoDatabase
    private lateinit var conversationRepository: LocalConversationRepository
    private lateinit var memoryRepository: LocalMemoryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NeptunoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationRepository = LocalConversationRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao()
        )
        memoryRepository = LocalMemoryRepository(
            memoryDao = database.memoryDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `repositories start with empty states`() = runTest {
        assertTrue(conversationRepository.observeConversations().first().isEmpty())
        assertTrue(memoryRepository.observeMemories().first().isEmpty())
    }

    @Test
    fun `room conversations and messages persist and cascade delete`() = runTest {
        val convId = conversationRepository.createConversation("Sesión de prueba")
        conversationRepository.addMessage(
            ChatMessage(
                conversationId = convId,
                sender = MessageSender.USER,
                content = "Hola Neptuno"
            )
        )
        conversationRepository.addMessage(
            ChatMessage(
                conversationId = convId,
                sender = MessageSender.SYSTEM_NOTICE,
                content = "Proveedor pendiente"
            )
        )

        val conversations = conversationRepository.observeConversations().first()
        assertEquals(1, conversations.size)
        assertEquals("Sesión de prueba", conversations.first().title)
        assertEquals(2, conversations.first().messageCount)

        val messages = conversationRepository.observeMessages(convId).first()
        assertEquals(2, messages.size)
        assertEquals(MessageSender.USER, messages[0].sender)
        assertEquals(MessageSender.SYSTEM_NOTICE, messages[1].sender)

        conversationRepository.deleteConversation(convId)
        assertTrue(conversationRepository.observeConversations().first().isEmpty())
        assertTrue(conversationRepository.observeMessages(convId).first().isEmpty())
    }

    @Test
    fun `ensureConversationExists recreates session when previous id was deleted`() = runTest {
        val deletedId = conversationRepository.createConversation("Temporal")
        conversationRepository.deleteConversation(deletedId)

        val recoveredId = conversationRepository.ensureConversationExists(
            conversationId = deletedId,
            defaultTitle = "Sesión recuperada"
        )

        assertNotEquals(deletedId, recoveredId)
        conversationRepository.addMessage(
            ChatMessage(
                conversationId = recoveredId,
                sender = MessageSender.USER,
                content = "Mensaje tras borrado"
            )
        )
        assertEquals(1, conversationRepository.observeMessages(recoveredId).first().size)
    }

    @Test
    fun `room repositories handle unknown enum values gracefully`() = runTest {
        val now = System.currentTimeMillis()
        val convId = database.conversationDao().insertConversation(
            ConversationEntity(title = "Fallback", createdAtMillis = now, updatedAtMillis = now)
        )
        database.messageDao().insertMessage(
            MessageEntity(
                conversationId = convId,
                sender = "UNKNOWN_SENDER_VALUE",
                content = "Dato heredado",
                timestampMillis = now
            )
        )
        database.memoryDao().insertMemory(
            MemoryEntity(
                title = "Preferencia",
                content = "Valor",
                category = "UNKNOWN_CATEGORY_VALUE",
                authorizedByUser = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )

        val messages = conversationRepository.observeMessages(convId).first()
        assertEquals(MessageSender.SYSTEM_NOTICE, messages.first().sender)

        val memories = memoryRepository.observeMemories().first()
        assertEquals(MemoryCategory.GENERAL, memories.first().category)
    }

    @Test
    fun `room memory repository saves trims deletes and clears entries`() = runTest {
        val id1 = memoryRepository.saveMemory(
            MemoryEntry(
                title = "  Idioma preferido  ",
                content = "  Responder en español técnico  ",
                category = MemoryCategory.PREFERENCE,
                authorizedByUser = true
            )
        )
        memoryRepository.saveMemory(
            MemoryEntry(
                title = "Proyecto activo",
                content = "Neptuno Android",
                category = MemoryCategory.CONTEXT,
                authorizedByUser = true
            )
        )

        val stored = memoryRepository.observeMemories().first()
        assertEquals(2, stored.size)
        val trimmed = stored.first { it.id == id1 }
        assertEquals("Idioma preferido", trimmed.title)
        assertEquals("Responder en español técnico", trimmed.content)

        memoryRepository.deleteMemory(id1)
        assertEquals(1, memoryRepository.observeMemories().first().size)

        memoryRepository.clearAllMemories()
        assertTrue(memoryRepository.observeMemories().first().isEmpty())
    }


    @Test
    fun `cloud sync markers make synchronization incremental and are invalidated by local changes`() = runTest {
        val conversationId = conversationRepository.createConversation("Pendiente")
        val firstMessageId = conversationRepository.addMessage(
            ChatMessage(
                conversationId = conversationId,
                sender = MessageSender.USER,
                content = "Primer mensaje"
            )
        )

        assertEquals(1, conversationRepository.getConversationsNeedingCloudSyncSnapshot().size)
        assertEquals(1, conversationRepository.getUnsyncedMessagesSnapshot(conversationId).size)

        conversationRepository.markConversationSynced(
            conversationId,
            conversationRepository.getConversationsSnapshot().single().updatedAtMillis
        )
        conversationRepository.markMessagesSynced(listOf(firstMessageId))
        assertTrue(conversationRepository.getUnsyncedMessagesSnapshot(conversationId).isEmpty())
        assertTrue(conversationRepository.getConversationsNeedingCloudSyncSnapshot().isEmpty())

        val secondMessageId = conversationRepository.addMessage(
            ChatMessage(
                conversationId = conversationId,
                sender = MessageSender.ASSISTANT,
                content = "Respuesta nueva"
            )
        )
        assertEquals(listOf(secondMessageId), conversationRepository.getUnsyncedMessagesSnapshot(conversationId).map { it.id })
        assertEquals(1, conversationRepository.getConversationsNeedingCloudSyncSnapshot().size)

        conversationRepository.updateConversationTitle(conversationId, "Título actualizado")
        assertEquals("Título actualizado", conversationRepository.getConversationsNeedingCloudSyncSnapshot().single().title)
    }

    @Test
    fun `authorized memory sync marker resets after a local edit`() = runTest {
        val memoryId = memoryRepository.saveMemory(
            MemoryEntry(
                title = "Preferencia",
                content = "Valor inicial",
                category = MemoryCategory.PREFERENCE,
                authorizedByUser = true
            )
        )
        val first = memoryRepository.getUnsyncedMemoriesSnapshot().single()
        assertEquals(memoryId, first.id)
        memoryRepository.markMemorySynced(memoryId, first.updatedAtMillis)
        assertTrue(memoryRepository.getUnsyncedMemoriesSnapshot().isEmpty())

        memoryRepository.saveMemory(first.copy(content = "Valor actualizado"))
        assertEquals("Valor actualizado", memoryRepository.getUnsyncedMemoriesSnapshot().single().content)
    }

    @Test
    fun `datastore persists and restores main user preferences`() = runTest {
        val prefsRepo = UserPreferencesRepository(context)

        val modelDefaults = com.example.neptuno.core.model.UserPreferences()
        assertEquals("gemini_firebase", modelDefaults.preferredAiProviderId)
        assertTrue(modelDefaults.autoSpeakResponses)

        prefsRepo.setSelectedVoice("neptuno_abyssal_deep")
        prefsRepo.setSelectedDeviceVoiceName("es-es-x-eed-local")
        prefsRepo.setTtsSpeechRate(1.25f)
        prefsRepo.setTtsPitch(0.85f)
        prefsRepo.setAutoSpeakResponses(false)
        prefsRepo.setPersonalityMode(PersonalityMode.CONCISE)
        prefsRepo.setAvatarStyle(AvatarStyle.TRIDENT_MATRIX)
        prefsRepo.setLanguage(AppLanguage.ENGLISH)
        prefsRepo.setThemeMode(ThemeMode.LIGHT)
        prefsRepo.setOfflineMode(true)
        prefsRepo.setBargeInEnabled(false)
        prefsRepo.setPreferBluetoothAudio(false)
        prefsRepo.setMemoryAuthorized(false)
        prefsRepo.setPersistConversationHistory(false)
        prefsRepo.setPreferredAudioInput(101)
        prefsRepo.setPreferredAudioOutput(202)
        prefsRepo.setPreferredAiProvider("local_engine_pending")

        val loaded = prefsRepo.userPreferences.first()
        assertEquals("neptuno_abyssal_deep", loaded.selectedVoiceId)
        assertEquals("es-es-x-eed-local", loaded.selectedDeviceVoiceName)
        assertEquals(1.25f, loaded.ttsSpeechRate, 0.001f)
        assertEquals(0.85f, loaded.ttsPitch, 0.001f)
        assertFalse(loaded.autoSpeakResponses)
        assertEquals(PersonalityMode.CONCISE, loaded.personalityMode)
        assertEquals(AvatarStyle.TRIDENT_MATRIX, loaded.avatarStyle)
        assertEquals(AppLanguage.ENGLISH, loaded.language)
        assertEquals(ThemeMode.LIGHT, loaded.themeMode)
        assertTrue(loaded.offlineMode)
        assertFalse(loaded.bargeInEnabled)
        assertFalse(loaded.preferBluetoothAudio)
        assertFalse(loaded.memoryAuthorized)
        assertFalse(loaded.persistConversationHistory)
        assertEquals(101, loaded.preferredAudioInputId)
        assertEquals(202, loaded.preferredAudioOutputId)
        assertEquals("local_engine_pending", loaded.preferredAiProviderId)

        prefsRepo.setPreferredAudioInput(null)
        prefsRepo.setPreferredAudioOutput(null)
        prefsRepo.setSelectedDeviceVoiceName(null)
        val cleared = prefsRepo.userPreferences.first()
        assertNull(cleared.preferredAudioInputId)
        assertNull(cleared.preferredAudioOutputId)
        assertNull(cleared.selectedDeviceVoiceName)
    }
}
