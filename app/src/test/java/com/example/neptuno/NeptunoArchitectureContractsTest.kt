package com.example.neptuno

import com.example.neptuno.ai.api.AiProviderRegistry
import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.ai.gemini.GeminiPendingProvider
import com.example.neptuno.ai.local.LocalEnginePendingProvider
import com.example.neptuno.audio.engine.BidirectionalAudioCoordinator
import com.example.neptuno.audio.engine.PendingAudioRoutingController
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.PersonalityMode
import com.example.neptuno.core.model.ToolStatus
import com.example.neptuno.feature.tools.ToolRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeptunoArchitectureContractsTest {

    @Test
    fun `ai provider registry falls back cleanly on unknown provider id`() {
        val gemini = GeminiPendingProvider()
        val local = LocalEnginePendingProvider()
        val registry = AiProviderRegistry(listOf(gemini, local))

        assertEquals(gemini.id, registry.getActiveProvider("non_existent_id").id)
        assertEquals(local.id, registry.getActiveProvider(local.id).id)
    }

    @Test
    fun `pending ai providers honestly report pending integration without simulating replies`() = runTest {
        val providers = listOf(GeminiPendingProvider(), LocalEnginePendingProvider())
        val request = AiRequest(
            conversationId = 1L,
            userInput = "Prueba",
            recentHistory = emptyList(),
            authorizedMemories = emptyList(),
            personality = PersonalityMode.ANALYTICAL,
            languageCode = "es"
        )

        providers.forEach { provider ->
            assertTrue(provider.status is AiProviderStatus.PendingIntegration)
            val event = provider.streamResponse(request).first()
            assertTrue(event is AiResponseEvent.IntegrationPending)
        }
    }

    @Test
    fun `tool registry does not mark unimplemented tools as operational`() {
        val registry = ToolRegistry()
        assertTrue(registry.registeredDescriptors.isNotEmpty())
        assertTrue(
            registry.registeredDescriptors.all {
                it.status == ToolStatus.INTERFACE_READY || it.status == ToolStatus.PLANNED
            }
        )
    }

    @Test
    fun `pending audio routing controller reports inactive routing honestly`() = runTest {
        val controller = PendingAudioRoutingController()
        assertFalse(controller.isActiveRoutingSupported)
        assertTrue(controller.applyPreferredInputRoute(1).isFailure)
        assertTrue(controller.applyPreferredOutputRoute(2).isFailure)
    }

    @Test
    fun `bidirectional audio coordinator records interruption signal`() {
        val coordinator = BidirectionalAudioCoordinator()
        coordinator.interruptCurrentOutput()
        assertFalse(coordinator.isSessionActive.value)
        assertNotNull(coordinator.lastInterruptionMillis.value)
    }
}
