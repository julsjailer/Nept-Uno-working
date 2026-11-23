package com.example.neptuno.ai.gemini

import android.util.Log
import com.example.neptuno.ai.api.AiProvider
import com.example.neptuno.ai.api.AiProviderCapabilities
import com.example.neptuno.ai.api.AiRequest
import com.example.neptuno.ai.api.AiResponseEvent
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.MessageSender
import com.example.neptuno.core.model.PersonalityMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiFirebaseProvider"
private const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
private const val STREAM_TIMEOUT_MS = 60_000L

enum class GeminiErrorCategory {
    APP_CHECK,
    AUTH,
    CONFIG,
    QUOTA,
    NETWORK,
    UNKNOWN
}

data class ClassifiedGeminiError(
    val category: GeminiErrorCategory,
    val shortStatus: String,
    val userMessage: String
)

fun interface GeminiStreamClient {
    fun generateTextStream(
        modelName: String,
        systemInstruction: String,
        contents: List<Content>
    ): Flow<String>
}

private object FirebaseAiGeminiStreamClient : GeminiStreamClient {
    override fun generateTextStream(
        modelName: String,
        systemInstruction: String,
        contents: List<Content>
    ): Flow<String> {
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = content { text(systemInstruction) }
        )
        return model.generateContentStream(contents).mapNotNull { chunk -> chunk.text }
    }
}

/**
 * Proveedor online oficial de Gemini mediante Firebase AI Logic (`Firebase.ai`).
 * - No declara estado Ready ("Operativo") hasta comprobar al menos una respuesta real de Gemini.
 * - Distingue errores de App Check, autenticación, configuración, cuota y red sin bloquear funciones locales.
 * - Mantiene cancelación cooperativa para barge-in y timeout de 60 s.
 */
class GeminiFirebaseProvider(
    private val streamClient: GeminiStreamClient = FirebaseAiGeminiStreamClient,
    private val streamTimeoutMs: Long = STREAM_TIMEOUT_MS
) : AiProvider {

    override val id: String = "gemini_firebase"
    override val displayName: String = "Gemini (Texto y Streaming)"
    override val capabilities: AiProviderCapabilities = AiProviderCapabilities(
        supportsStreamingText = true,
        supportsBidirectionalAudio = false,
        supportsOfflineExecution = false,
        supportsToolCalling = false
    )

    @Volatile
    private var currentStatus: AiProviderStatus = AiProviderStatus.Configured(
        statusNote = "Configurado vía Firebase AI Logic · Sin respuesta verificada en esta sesión"
    )

    override val status: AiProviderStatus
        get() = currentStatus

    @Volatile
    private var interruptedByUser: Boolean = false

    override fun streamResponse(request: AiRequest): Flow<AiResponseEvent> = flow {
        interruptedByUser = false
        val systemText = buildSystemInstruction(request)
        val promptContents = buildConversationContents(request)
        var receivedAnyChunk = false

        try {
            withTimeout(streamTimeoutMs) {
                streamClient.generateTextStream(
                    modelName = DEFAULT_GEMINI_MODEL,
                    systemInstruction = systemText,
                    contents = promptContents
                ).collect { delta ->
                    if (interruptedByUser) {
                        throw CancellationException("Interrupción solicitada por el usuario.")
                    }
                    if (delta.isNotEmpty()) {
                        receivedAnyChunk = true
                        emit(AiResponseEvent.TextDelta(delta))
                    }
                }
            }

            if (interruptedByUser) {
                emit(AiResponseEvent.Interrupted("Respuesta detenida por el usuario."))
            } else if (!receivedAnyChunk) {
                currentStatus = AiProviderStatus.Unavailable("Respuesta vacía de Gemini")
                emit(
                    AiResponseEvent.Failure(
                        "Gemini devolvió una respuesta vacía o incompleta. Tus datos locales siguen disponibles; intenta reformular tu mensaje."
                    )
                )
            } else {
                currentStatus = AiProviderStatus.Ready
                emit(AiResponseEvent.Completed)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            currentStatus = AiProviderStatus.Unavailable("Tiempo de espera agotado")
            emit(
                AiResponseEvent.Failure(
                    "La conexión con Gemini agotó el tiempo de espera. Neptuno sigue operando localmente con Room, voz offline y memoria."
                )
            )
        } catch (e: CancellationException) {
            if (interruptedByUser) {
                emit(AiResponseEvent.Interrupted("Respuesta interrumpida por el usuario."))
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini streaming failed", e)
            val classified = classifyProviderException(e)
            currentStatus = AiProviderStatus.Unavailable(classified.shortStatus)
            emit(AiResponseEvent.Failure(classified.userMessage))
        }
    }

    override suspend fun interruptActiveResponse() {
        interruptedByUser = true
    }

    private fun buildSystemInstruction(request: AiRequest): String {
        val languageName = when (request.languageCode.lowercase()) {
            "es" -> "español"
            "en" -> "inglés"
            "pt" -> "portugués"
            else -> request.languageCode
        }

        val personaRule = when (request.personality) {
            PersonalityMode.ANALYTICAL ->
                "Mantén un tono analítico, preciso y estructurado, ideal para conversación hablada clara."
            PersonalityMode.CALM_GUIDE ->
                "Mantén un tono sereno, cercano y pausado para el acompañamiento diario."
            PersonalityMode.CONCISE ->
                "Responde de forma breve, directa y accionable, optimizada para lectura en voz alta."
            PersonalityMode.EXPLORER ->
                "Mantén un tono curioso y creativo que proponga ideas útiles sin ser verboso."
        }

        val memoriesSection = if (request.authorizedMemories.isNotEmpty()) {
            val formatted = request.authorizedMemories.take(10).joinToString("\n") {
                "- [${it.category.displayName}] ${it.title}: ${it.content}"
            }
            "\n\nMemorias autorizadas por el usuario (úsalas solo si son relevantes):\n$formatted"
        } else {
            ""
        }

        return """
            Eres NEPTUNO, un asistente personal de inteligencia artificial con identidad tecnológica oceánica.
            Responde siempre en $languageName salvo que el usuario pida otro idioma.
            $personaRule
            Tu respuesta puede reproducirse mediante síntesis de voz (TTS), así que evita listas excesivamente largas o formato Markdown innecesario cuando una explicación natural sea más clara.
            No afirmes tener acceso a herramientas del dispositivo ni a audio bidireccional nativo si no han sido ejecutados.$memoriesSection
        """.trimIndent()
    }

    private fun buildConversationContents(request: AiRequest): List<Content> {
        val contents = mutableListOf<Content>()
        request.recentHistory
            .filter { it.sender == MessageSender.USER || it.sender == MessageSender.ASSISTANT }
            .takeLast(12)
            .forEach { msg ->
                val role = if (msg.sender == MessageSender.USER) "user" else "model"
                if (msg.content.isNotBlank()) {
                    contents += content(role) { text(msg.content) }
                }
            }

        val lastIsSameUserInput = request.recentHistory.lastOrNull()?.let {
            it.sender == MessageSender.USER && it.content.trim() == request.userInput.trim()
        } ?: false

        if (!lastIsSameUserInput && request.userInput.isNotBlank()) {
            contents += content("user") { text(request.userInput.trim()) }
        }
        return contents
    }

    companion object {
        // "rate" suelto coincidía con "gene-rate" (p. ej. streamGenerateContent); exigir "rate limit"/"rate-limit"/"rate limited".
        private val RATE_LIMIT_REGEX = Regex("\\brate[\\s_-]?limit", RegexOption.IGNORE_CASE)

        // Código HTTP como número aislado: no debe coincidir dentro de otros números (p. ej. 14030).
        private fun String.hasHttpCode(code: String): Boolean =
            Regex("(?<!\\d)$code(?!\\d)").containsMatchIn(this)

        fun classifyProviderException(e: Throwable): ClassifiedGeminiError {
            val raw = generateSequence(e) { it.cause }
                .mapNotNull { it.localizedMessage ?: it.message }
                .joinToString(" | ")

            return when {
                raw.contains("App Check", ignoreCase = true) ||
                    raw.contains("appcheck", ignoreCase = true) ||
                    raw.hasHttpCode("403") -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.APP_CHECK,
                    shortStatus = "Error de App Check (HTTP 403)",
                    userMessage = "Error de Firebase App Check (HTTP 403): la solicitud fue rechazada por seguridad de la app. Puedes seguir usando Neptuno en modo local."
                )

                raw.hasHttpCode("401") ||
                    raw.contains("UNAUTHENTICATED", ignoreCase = true) ||
                    raw.contains("auth token", ignoreCase = true) ||
                    raw.contains("credential", ignoreCase = true) -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.AUTH,
                    shortStatus = "Error de autenticación",
                    userMessage = "Error de autenticación al invocar Gemini. Revisa tu sesión o configuración en Firebase sin perder tus datos locales."
                )

                raw.contains("API key", ignoreCase = true) ||
                    raw.contains("not enabled", ignoreCase = true) ||
                    raw.contains("FirebaseApp", ignoreCase = true) ||
                    raw.contains("model", ignoreCase = true) && raw.contains("not found", ignoreCase = true) ||
                    raw.hasHttpCode("400") ||
                    raw.hasHttpCode("404") -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.CONFIG,
                    shortStatus = "Error de configuración",
                    userMessage = "Error de configuración de Gemini / Firebase AI Logic ($raw). Neptuno continúa operativo localmente."
                )

                raw.hasHttpCode("429") ||
                    raw.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    raw.contains("quota", ignoreCase = true) ||
                    RATE_LIMIT_REGEX.containsMatchIn(raw) -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.QUOTA,
                    shortStatus = "Cuota de Gemini agotada",
                    userMessage = "Se alcanzó temporalmente el límite de cuota de Gemini (HTTP 429). Espera unos momentos antes de reintentar."
                )

                raw.contains("Unable to resolve host", ignoreCase = true) ||
                    raw.contains("UnknownHost", ignoreCase = true) ||
                    raw.contains("ConnectException", ignoreCase = true) ||
                    raw.contains("network", ignoreCase = true) ||
                    raw.contains("timeout", ignoreCase = true) ||
                    raw.contains("timed out", ignoreCase = true) ||
                    raw.contains("UNAVAILABLE", ignoreCase = true) -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.NETWORK,
                    shortStatus = "Error de red con Gemini",
                    userMessage = "Error de red o tiempo de espera al conectar con Gemini. Tus mensajes y memoria local Room permanecen intactos."
                )

                raw.isNotBlank() -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.UNKNOWN,
                    shortStatus = "Error de Gemini",
                    userMessage = "Error de Gemini (Firebase AI): $raw"
                )

                else -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.UNKNOWN,
                    shortStatus = "Error desconocido de Gemini",
                    userMessage = "No se pudo completar la solicitud con Gemini. Neptuno sigue disponible localmente."
                )
            }
        }
    }
}
