package com.example.neptuno.feature.chat

import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Respuestas locales para frases triviales (saludo, gracias, hora, fecha): no gastan cuota de Gemini
 * (el plan gratuito permite ~20 solicitudes por modelo y día). Solo coincide con frases cortas y exactas.
 */
object LocalQuickReply {
    private val GREETINGS = setOf(
        "hola", "buenas", "buenos dias", "buenas tardes", "buenas noches", "hey", "ey", "oye",
        "hola neptuno", "hey neptuno", "oye neptuno", "buenas neptuno"
    )
    private val THANKS = setOf("gracias", "muchas gracias", "gracias neptuno", "muchas gracias neptuno", "mil gracias")
    private val TIME = setOf("que hora es", "dime la hora", "la hora", "hora", "que hora es neptuno")
    private val DATE = setOf(
        "que dia es hoy", "que dia es", "que fecha es hoy", "que fecha es", "dime la fecha", "la fecha", "fecha"
    )

    fun match(input: String, now: LocalDateTime = LocalDateTime.now()): String? {
        val text = normalize(input)
        if (text.isEmpty() || text.split(' ').size > 5) return null
        return when (text) {
            in GREETINGS -> "¡Hola! Aquí estoy. ¿En qué te ayudo?"
            in THANKS -> "¡De nada! Si necesitas algo más, solo dímelo."
            in TIME -> "Son las ${now.format(DateTimeFormatter.ofPattern("h:mm a", Locale("es")))}."
            in DATE -> "Hoy es ${now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale("es")))}."
            else -> null
        }
    }

    internal fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
}
