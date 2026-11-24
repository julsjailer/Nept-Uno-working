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

/**
 * Modelos a probar en orden. La cuota gratuita de Gemini se cuenta por modelo y por día, así que
 * al agotarse uno (HTTP 429) se pasa al siguiente. Los tres figuran en la documentación de
 * Firebase AI Logic como estables y sin necesidad de facturación.
 */
private val DEFAULT_MODEL_CHAIN = listOf("gemini-3.8-flash", "gemini-4-hydrogen", "gemini-3.5-flash-lite")
private const val STREAM_TIMEOUT_MS = 60_000L

enum class GeminiErrorCategory {
    APP_CHECK,
    AUTH,
    CONFIG,
    QUOTA,
    NETWORK,
    STREAM_TRUNCATED,
    UNKNOWN
}

/** Cuenta solicitudes exitosas por modelo y día (la cuota gratuita de Google se reinicia a medianoche, hora del Pacífico). */
interface GeminiUsageStore {
    fun recordSuccess(model: String)
    fun usedToday(model: String): Int
}

class SharedPrefsGeminiUsageStore(context: android.content.Context) : GeminiUsageStore {
    private val prefs = context.applicationContext.getSharedPreferences("gemini_usage", android.content.Context.MODE_PRIVATE)

    private fun today(): String =
        java.time.LocalDate.now(java.time.ZoneId.of("America/Los_Angeles")).toString()

    @Synchronized
    override fun recordSuccess(model: String) {
        val day = today()
        if (prefs.getString("day", null) != day) prefs.edit().clear().putString("day", day).apply()
        prefs.edit().putInt("count_$model", prefs.getInt("count_$model", 0) + 1).apply()
    }

    @Synchronized
    override fun usedToday(model: String): Int =
        if (prefs.getString("day", null) == today()) prefs.getInt("count_$model", 0) else 0
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
    private val streamTimeoutMs: Long = STREAM_TIMEOUT_MS,
    private val modelChain: List<String> = DEFAULT_MODEL_CHAIN,
    private val usageStore: GeminiUsageStore? = null
) : AiProvider {

    override val id: String = "gemini_firebase"
    @Volatile
    private var lastSuccessfulModel: String? = null

    override val displayName: String
        get() = lastSuccessfulModel?.let { model ->
            val usage = usageStore?.let { " · ${it.usedToday(model)}/$FREE_TIER_DAILY_LIMIT hoy" }.orEmpty()
            "Gemini (Texto y Streaming) · $model$usage"
        } ?: "Gemini (Texto y Streaming)"
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

    // Primer modelo a intentar en la siguiente consulta: evita gastar una solicitud en un modelo ya agotado hoy.
    @Volatile
    private var startModelIndex: Int = 0

    override fun streamResponse(request: AiRequest): Flow<AiResponseEvent> = flow {
        interruptedByUser = false
        val systemText = buildSystemInstruction(request)
        val promptContents = buildConversationContents(request)
        var modelIndex = startModelIndex.coerceIn(0, modelChain.lastIndex)

        while (true) {
            var receivedAnyChunk = false
            val modelName = modelChain[modelIndex]

            try {
                withTimeout(streamTimeoutMs) {
                    streamClient.generateTextStream(
                        modelName = modelName,
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
                    lastSuccessfulModel = modelName
                    runCatching { usageStore?.recordSuccess(modelName) }
                    emit(AiResponseEvent.Completed)
                }
                break
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini streaming timed out", e)
                currentStatus = AiProviderStatus.Unavailable("Tiempo de espera agotado")
                emit(
                    AiResponseEvent.Failure(
                        "La conexión con Gemini agotó el tiempo de espera. Neptuno sigue operando localmente con Room, voz offline y memoria."
                    )
                )
                break
            } catch (e: CancellationException) {
                if (interruptedByUser) {
                    emit(AiResponseEvent.Interrupted("Respuesta interrumpida por el usuario."))
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini streaming failed con $modelName", e)
                val classified = classifyProviderException(e)
                if (classified.category == GeminiErrorCategory.STREAM_TRUNCATED) {
                    // No reintentar: el SDK ya pudo entregar texto y una segunda solicitud consumiría cuota.
                    Log.w(TAG, "Gemini stream incompleto en $modelName; chunks recibidos: $receivedAnyChunk")
                }
                val quotaExhausted = classified.category == GeminiErrorCategory.QUOTA
                if ((quotaExhausted || isModelOverloaded(e)) &&
                    !receivedAnyChunk &&
                    modelIndex < modelChain.lastIndex
                ) {
                    // Cuota agotada o modelo saturado: probar el siguiente. Solo la cuota (que dura todo el día)
                    // se recuerda para las próximas consultas; la saturación es pasajera.
                    modelIndex++
                    if (quotaExhausted) startModelIndex = modelIndex
                    Log.w(TAG, "Modelo no disponible; probando ${modelChain[modelIndex]}")
                    continue
                }
                if (classified.category == GeminiErrorCategory.QUOTA) {
                    // Todos agotados: la próxima consulta vuelve a empezar por el primero (la cuota pudo reiniciarse).
                    startModelIndex = 0
                }
                currentStatus = AiProviderStatus.Unavailable(classified.shortStatus)
                emit(AiResponseEvent.Failure(classified.userMessage))
                break
            }
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
            .takeLast(MAX_HISTORY_MESSAGES)
            .forEach { msg ->
                val role = if (msg.sender == MessageSender.USER) "user" else "model"
                if (msg.content.isNotBlank()) {
                    contents += content(role) { text(msg.content.take(MAX_HISTORY_CHARS_PER_MESSAGE)) }
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
        /** Límite diario aproximado del plan gratuito por modelo (el usuario lo ve en Google AI Studio). */
        const val FREE_TIER_DAILY_LIMIT = 20

        // Menos historial = menos tokens por solicitud (la cuota gratuita también limita tokens por minuto).
        private const val MAX_HISTORY_MESSAGES = 8
        private const val MAX_HISTORY_CHARS_PER_MESSAGE = 1_500

        // "rate" suelto coincidía con "gene-rate" (p. ej. streamGenerateContent); exigir "rate limit"/"rate-limit"/"rate limited".
        private val RATE_LIMIT_REGEX = Regex("\\brate[\\s_-]?limit", RegexOption.IGNORE_CASE)

        // Código HTTP como número aislado: no debe coincidir dentro de otros números (p. ej. 14030).
        private fun String.hasHttpCode(code: String): Boolean =
            Regex("(?<!\\d)$code(?!\\d)").containsMatchIn(this)

        /** Detecta el EOF del parser de Firebase AI Logic sin confundirlo con cuota o red. */
        fun isTruncatedStream(raw: String): Boolean {
            val normalized = raw.lowercase()
            return normalized.contains("expected end of the object") ||
                normalized.contains("had 'eof' instead") ||
                (normalized.contains("json input") && normalized.contains("eof"))
        }

        /** Modelo saturado temporalmente (HTTP 503 / "high demand" / "overloaded"). */
        fun isModelOverloaded(e: Throwable): Boolean {
            val raw = generateSequence(e) { it.cause }
                .mapNotNull { it.localizedMessage ?: it.message }
                .joinToString(" | ")
            return raw.hasHttpCode("503") ||
                raw.contains("high demand", ignoreCase = true) ||
                raw.contains("overloaded", ignoreCase = true)
        }

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
                    userMessage = "Se alcanzó el límite de uso de Gemini (HTTP 429) en todos los modelos disponibles. En el nivel gratuito el límite es por día: espera unos minutos o vuelve mañana. Neptuno sigue operando localmente."
                )

                isTruncatedStream(raw) -> ClassifiedGeminiError(
                    category = GeminiErrorCategory.STREAM_TRUNCATED,
                    shortStatus = "Respuesta incompleta de Gemini",
                    userMessage = "Gemini interrumpió el flujo de respuesta antes de completarlo. Neptuno conservó el texto que ya había recibido; puedes pedir que continúe desde el último punto."
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
