package com.example.neptuno

import com.example.neptuno.feature.chat.LocalQuickReply
import java.time.LocalDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQuickReplyTest {
    private val now = LocalDateTime.of(2026, 11, 24, 15, 30)

    @Test
    fun `responde saludos y agradecimientos sin Gemini`() {
        assertNotNull(LocalQuickReply.match("¡Hola!", now))
        assertNotNull(LocalQuickReply.match("Hey, Neptuno", now))
        assertNotNull(LocalQuickReply.match("Muchas gracias", now))
    }

    @Test
    fun `responde hora y fecha`() {
        assertTrue(LocalQuickReply.match("¿Qué hora es?", now)!!.contains("3:30"))
        assertTrue(LocalQuickReply.match("qué día es hoy", now)!!.contains("2026"))
    }

    @Test
    fun `no intercepta preguntas reales ni frases largas`() {
        assertNull(LocalQuickReply.match("hola, explícame cómo funciona la fotosíntesis", now))
        assertNull(LocalQuickReply.match("Saluda", now))
        assertNull(LocalQuickReply.match("", now))
    }
}
