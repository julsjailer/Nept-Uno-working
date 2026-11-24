package com.example.neptuno

import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.ai.gemini.GeminiFirebaseProvider
import com.example.neptuno.ai.gemini.GeminiErrorCategory
import com.example.neptuno.ai.gemini.GeminiStreamClient
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.PersonalityMode
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: el proveedor usa android.util.Log, que no existe en un test JUnit puro.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiModelFallbackTest {

    private fun request() = AiRequest(1L, "Hola", emptyList(), emptyList(), PersonalityMode.ANALYTICAL, "es")


    @Test
    fun `si Firebase corta el JSON despues de enviar texto conserva el fragmento y no reintenta`() = runBlocking {
        val tried = mutableListOf<String>()
        val client = GeminiStreamClient { model, _, _ ->
            tried += model
            flow {
                emit("Esta es la parte recibida")
                throw IllegalStateException(
                    "Expected end of the object ')', but had 'EOF' instead at path: $ JSON input: {"
                )
            }
        }
        val provider = GeminiFirebaseProvider(streamClient = client, modelChain = listOf("m1", "m2"))

        val events = provider.streamResponse(request()).toList()

        assertEquals(listOf("m1"), tried)
        assertEquals("Esta es la parte recibida", (events.first() as AiResponseEvent.TextDelta).text)
        assertTrue(events.last() is AiResponseEvent.Failure)
        assertEquals(
            GeminiErrorCategory.STREAM_TRUNCATED,
            GeminiFirebaseProvider.classifyProviderException(
                IllegalStateException("Expected end of the object ')', but had 'EOF' instead at path: $ JSON input: {")
            ).category
        )
    }

    @Test
    fun `EOF del JSON se clasifica como flujo incompleto y no como cuota`() {
        val classified = GeminiFirebaseProvider.classifyProviderException(
            IllegalStateException("Expected end of the object ')', but had 'EOF' instead at path: $ JSON input: {")
        )
        assertEquals(GeminiErrorCategory.STREAM_TRUNCATED, classified.category)
        assertTrue(classified.userMessage.contains("conservó el texto"))
    }

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

    @Test
    fun `si un modelo esta saturado se prueba el siguiente pero no se recuerda`() = runBlocking {
        val tried = mutableListOf<String>()
        val client = GeminiStreamClient { model, _, _ ->
            tried += model
            flow {
                if (model == "m1") throw IllegalStateException("503 The model is overloaded. High demand, try later")
                emit("ok desde $model")
            }
        }
        val provider = GeminiFirebaseProvider(streamClient = client, modelChain = listOf("m1", "m2"))

        provider.streamResponse(request()).toList()
        assertEquals(listOf("m1", "m2"), tried)

        // La saturación es pasajera: la siguiente consulta vuelve a empezar por el primer modelo.
        tried.clear()
        provider.streamResponse(request()).toList()
        assertEquals("m1", tried.first())
    }
}
