package com.example.neptuno

import com.example.neptuno.audio.engine.HeadsetMessages
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetMessagesTest {
    @Test
    fun `con micrófono explica el uso automático y el regreso al teléfono`() {
        val text = HeadsetMessages.connected("Redmi Buds", hasMic = true)
        assertTrue(text.contains("Redmi Buds"))
        assertTrue(text.contains("automáticamente"))
        assertTrue(text.contains("teléfono"))
    }

    @Test
    fun `sin micrófono avisa que solo hay audio`() {
        val text = HeadsetMessages.connected("Parlante", hasMic = false)
        assertTrue(text.contains("sin micrófono"))
        assertTrue(HeadsetMessages.status("Parlante", false).contains("solo audio"))
    }

    @Test
    fun `desconexion vuelve al teléfono`() {
        assertTrue(HeadsetMessages.disconnected("X").contains("teléfono"))
    }
}
