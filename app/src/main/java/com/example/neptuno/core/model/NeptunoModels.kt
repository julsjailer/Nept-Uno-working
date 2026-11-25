package com.example.neptuno.core.model

/**
 * Estados visuales y operativos del asistente Neptuno.
 * Consumidos por el personaje 2D (feature-avatar) y el flujo de conversación (feature-chat).
 */
enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
    OFFLINE
}

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM_NOTICE
}

data class ChatMessage(
    val id: Long = 0L,
    val conversationId: Long,
    val sender: MessageSender,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val providerId: String? = null,
    val isInterrupted: Boolean = false
)

data class ConversationSession(
    val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)

enum class MemoryCategory(val displayName: String) {
    PREFERENCE("Preferencia"),
    CONTEXT("Contexto"),
    WORKFLOW("Flujo de trabajo"),
    GENERAL("General")
}

data class MemoryEntry(
    val id: Long = 0L,
    val title: String,
    val content: String,
    val category: MemoryCategory = MemoryCategory.PREFERENCE,
    val authorizedByUser: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

enum class BluetoothAdapterState {
    ON,
    OFF,
    TURNING_ON,
    TURNING_OFF,
    UNAVAILABLE
}

data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val deviceTypeLabel: String,
    val isBonded: Boolean
)

enum class AudioRouteDirection {
    INPUT,
    OUTPUT
}

data class AudioRouteInfo(
    val id: Int,
    val productName: String,
    val typeLabel: String,
    val direction: AudioRouteDirection,
    val isBluetooth: Boolean
)

/**
 * Perfil de voz extensible: conectado con Android TextToSpeech y preparado para Gemini Live.
 */
data class VoiceProfile(
    val id: String,
    val displayName: String,
    val description: String,
    val engineType: String,
    val isAvailableNow: Boolean = false,
    val availabilityNote: String = "",
    val basePitch: Float = 1.0f,
    val baseRate: Float = 1.0f
)

/**
 * Voz real instalada en el dispositivo reportada por Android TextToSpeech.
 */
data class DeviceVoiceInfo(
    val name: String,
    val displayName: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
    val qualityLabel: String
)

enum class PersonalityMode(val id: String, val label: String, val description: String) {
    ANALYTICAL("analytical", "Analítico", "Preciso, estructurado y orientado a soluciones técnicas."),
    CALM_GUIDE("calm_guide", "Sereno", "Cercano, claro y pausado para el trabajo diario."),
    CONCISE("concise", "Directo", "Respuestas breves y accionables sin rodeos."),
    EXPLORER("explorer", "Creativo", "Curioso y propositivo para lluvia de ideas.")
}

enum class AvatarStyle(val id: String, val label: String, val description: String) {
    ABYSSAL_CORE("abyssal_core", "Núcleo Abisal", "Núcleo energético con anillos sonar y visor oceánico."),
    TIDAL_PULSE("tidal_pulse", "Pulso Mareal", "Ondas hidrodinámicas concéntricas en turquesa."),
    TRIDENT_MATRIX("trident_matrix", "Matriz Tridente", "Geometría orbital azul eléctrico de alta precisión.")
}

enum class AppLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    PORTUGUESE("pt", "Português")
}

enum class ThemeMode(val id: String, val label: String) {
    DARK("dark", "Oscuro oceánico"),
    LIGHT("light", "Claro oceánico"),
    SYSTEM("system", "Seguir al sistema")
}

data class UserPreferences(
    val selectedVoiceId: String = "neptuno_ocean_neutral",
    val selectedDeviceVoiceName: String? = null,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val autoSpeakResponses: Boolean = true,
    val personalityMode: PersonalityMode = PersonalityMode.ANALYTICAL,
    val avatarStyle: AvatarStyle = AvatarStyle.ABYSSAL_CORE,
    val language: AppLanguage = AppLanguage.SPANISH,
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Color de acento elegido (ARGB). 0 = paleta original de NEPTUNO. */
    val themeAccentArgb: Int = 0,
    val offlineMode: Boolean = false,
    val bargeInEnabled: Boolean = true,
    val autoSendVoice: Boolean = true,
    val continuousConversation: Boolean = false,
    val preferBluetoothAudio: Boolean = true,
    val memoryAuthorized: Boolean = true,
    val persistConversationHistory: Boolean = true,
    val preferredAudioInputId: Int? = null,
    val preferredAudioOutputId: Int? = null,
    val preferredAiProviderId: String = "gemini_firebase"
)

enum class ToolCategory(val label: String) {
    SYSTEM("Sistema"),
    PRODUCTIVITY("Productividad"),
    COMMUNICATION("Comunicación"),
    AUTOMATION("Automatización")
}

enum class ToolStatus(val label: String) {
    INTERFACE_READY("Contrato listo · Pendiente"),
    PLANNED("Planificada")
}

data class ToolDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val status: ToolStatus,
    val requiredCapability: String
)

sealed interface AiProviderStatus {
    data object PendingIntegration : AiProviderStatus
    data class Configured(val statusNote: String) : AiProviderStatus
    data object Ready : AiProviderStatus
    data class Unavailable(val reason: String) : AiProviderStatus
}
