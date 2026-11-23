package com.example.neptuno

import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.ai.gemini.GeminiFirebaseProvider
import com.example.neptuno.ai.gemini.GeminiStreamClient
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.PersonalityMode
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelFallbackTest {

    private fun request() = AiRequest(1L, "Hola", emptyList(), emptyList(), PersonalityMode.ANALYTICAL, "es")

    @Test
    fun `si un modelo agota la cuota se prueba el siguiente y se recuerda`() = runBlocking {
        val tried = mutableListOf<String>()
        val client = GeminiStreamClient { model, _, _ ->
            tried += model
            flow {
                if (model == "m1") throw IllegalStateException("429 RESOURCE_EXHAUSTED quota")
                emit("ok desde $model")
            }
        }
        val provider = GeminiFirebaseProvider(streamClient = client, modelChain = listOf("m1", "m2", "m3"))

        val events = provider.streamResponse(request()).toList()
        assertEquals(listOf("m1", "m2"), tried)
        assertTrue(events.any { it is AiResponseEvent.Completed })
        assertTrue(provider.status is AiProviderStatus.Ready)

        // La siguiente consulta empieza directamente por el modelo que funcionó.
        tried.clear()
        provider.streamResponse(request()).toList()
        assertEquals(listOf("m2"), tried)
    }

    @Test
    fun `si todos los modelos agotan la cuota se informa el fallo`() = runBlocking {
        val tried = mutableListOf<String>()
        val client = GeminiStreamClient { model, _, _ ->
            tried += model
            flow { throw IllegalStateException("429 RESOURCE_EXHAUSTED quota") }
        }
        val provider = GeminiFirebaseProvider(streamClient = client, modelChain = listOf("m1", "m2"))

        val events = provider.streamResponse(request()).toList()
        assertEquals(listOf("m1", "m2"), tried)
        assertTrue(events.last() is AiResponseEvent.Failure)
        assertTrue(provider.status is AiProviderStatus.Unavailable)
    }

    @Test
    fun `un error que no es de cuota no cambia de modelo`() = runBlocking {
        val tried = mutableListOf<String>()
        val client = GeminiStreamClient { model, _, _ ->
            tried += model
            flow { throw IllegalStateException("HTTP 403 Firebase App Check attestation failed") }
        }
        val provider = GeminiFirebaseProvider(streamClient = client, modelChain = listOf("m1", "m2"))

        provider.streamResponse(request()).toList()
        assertEquals(listOf("m1"), tried)
    }
}
