package com.example.neptuno.voice.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoskPhraseMatcherTest {

    @Test
    fun `la palabra principal es la ultima de la frase`() {
        assertEquals("neptuno", VoskPhraseMatcher.coreWord("Hey Neptuno"))
        assertEquals("neptuno", VoskPhraseMatcher.coreWord("  Néptuno! "))
    }

    @Test
    fun `la gramatica incluye las variantes y unk como lista json`() {
        val grammar = VoskPhraseMatcher.grammarJson("Hey Neptuno")
        assertTrue(grammar.startsWith("[") && grammar.endsWith("]"))
        assertTrue(grammar.contains("\"hey neptuno\""))
        assertTrue(grammar.contains("\"oye neptuno\""))
        assertTrue(grammar.contains("\"neptuno\""))
        assertTrue(grammar.contains("\"[unk]\""))
        // Sin duplicados: "hey neptuno" aparece una sola vez.
        assertEquals(1, Regex("\"hey neptuno\"").findAll(grammar).count())
    }

    @Test
    fun `detecta la frase con acentos mayusculas y signos`() {
        assertTrue(VoskPhraseMatcher.matches("Hey, Néptuno!", "Hey Neptuno", 0.6f))
        assertTrue(VoskPhraseMatcher.matches("oye neptuno", "Hey Neptuno", 0.6f))
    }

    @Test
    fun `ignora otras frases y el marcador desconocido`() {
        assertFalse(VoskPhraseMatcher.matches("hola mundo", "Hey Neptuno", 0.6f))
        assertFalse(VoskPhraseMatcher.matches("[unk]", "Hey Neptuno", 0.6f))
        assertFalse(VoskPhraseMatcher.matches("", "Hey Neptuno", 0.6f))
    }

    @Test
    fun `con sensibilidad baja exige un saludo antes de la palabra principal`() {
        assertTrue(VoskPhraseMatcher.matches("neptuno", "Hey Neptuno", 0.6f))
        assertFalse(VoskPhraseMatcher.matches("neptuno", "Hey Neptuno", 0.3f))
        assertTrue(VoskPhraseMatcher.matches("hey neptuno", "Hey Neptuno", 0.3f))
    }
}
