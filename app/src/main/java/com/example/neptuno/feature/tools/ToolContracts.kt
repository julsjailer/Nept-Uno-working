package com.example.neptuno.feature.tools

import com.example.neptuno.core.model.ToolCategory
import com.example.neptuno.core.model.ToolDescriptor
import com.example.neptuno.core.model.ToolStatus

/**
 * Contrato extensible para herramientas invocables por Neptuno en futuras fases.
 */
interface NeptunoTool {
    val descriptor: ToolDescriptor
    val isOperational: Boolean get() = false

    suspend fun execute(parameters: Map<String, String>): Result<String> {
        return Result.failure(
            UnsupportedOperationException("La herramienta '${descriptor.name}' todavía no está implementada.")
        )
    }
}

class ToolRegistry {
    val registeredDescriptors: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            id = "tool_voice_notes",
            name = "Notas rápidas de voz",
            description = "Captura dictados y los indexa en la memoria local autorizada.",
            category = ToolCategory.PRODUCTIVITY,
            status = ToolStatus.INTERFACE_READY,
            requiredCapability = "STT + Room"
        ),
        ToolDescriptor(
            id = "tool_reminders",
            name = "Recordatorios y alarmas",
            description = "Programación de avisos del sistema con confirmación del usuario.",
            category = ToolCategory.PRODUCTIVITY,
            status = ToolStatus.PLANNED,
            requiredCapability = "AlarmManager / Notificaciones"
        ),
        ToolDescriptor(
            id = "tool_bluetooth_routing",
            name = "Control de rutas Bluetooth",
            description = "Conmutación directa de entrada y salida de audio hacia auriculares vinculados.",
            category = ToolCategory.SYSTEM,
            status = ToolStatus.INTERFACE_READY,
            requiredCapability = "AudioManager + Bluetooth"
        ),
        ToolDescriptor(
            id = "tool_device_diagnostics",
            name = "Telemetría del dispositivo",
            description = "Consulta de batería, conectividad y estado de sensores autorizados.",
            category = ToolCategory.SYSTEM,
            status = ToolStatus.PLANNED,
            requiredCapability = "Android System Services"
        ),
        ToolDescriptor(
            id = "tool_contextual_routines",
            name = "Rutinas contextuales",
            description = "Secuencias automáticas locales activadas por voz o conexión de dispositivo.",
            category = ToolCategory.AUTOMATION,
            status = ToolStatus.PLANNED,
            requiredCapability = "WorkManager + WakeWord"
        ),
        ToolDescriptor(
            id = "tool_quick_messages",
            name = "Redacción asistida",
            description = "Preparación de borradores con confirmación explícita antes de enviar.",
            category = ToolCategory.COMMUNICATION,
            status = ToolStatus.PLANNED,
            requiredCapability = "Proveedor IA conectado"
        )
    )
}
