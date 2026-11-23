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
    fun `la gramatica solo tiene saludo mas palabra principal y unk`() {
        val grammar = VoskPhraseMatcher.grammarJson("Hey Neptuno")
        assertTrue(grammar.startsWith("[") && grammar.endsWith("]"))
        assertTrue(grammar.contains("\"hey neptuno\""))
        assertTrue(grammar.contains("\"oye neptuno\""))
        assertTrue(grammar.contains("\"[unk]\""))
        assertFalse(grammar.contains("\"neptuno\""))
        assertEquals(1, Regex("\"hey neptuno\"").findAll(grammar).count())
    }

    @Test
    fun `detecta la frase con acentos mayusculas y signos`() {
        assertTrue(VoskPhraseMatcher.matches("Hey, Néptuno!", "Hey Neptuno"))
        assertTrue(VoskPhraseMatcher.matches("oye neptuno", "Hey Neptuno"))
    }

    @Test
    fun `ignora palabra suelta otras frases y el marcador desconocido`() {
        assertFalse(VoskPhraseMatcher.matches("neptuno", "Hey Neptuno"))
        assertFalse(VoskPhraseMatcher.matches("hola mundo", "Hey Neptuno"))
        assertFalse(VoskPhraseMatcher.matches("[unk] neptuno", "Hey Neptuno"))
        assertFalse(VoskPhraseMatcher.matches("[unk]", "Hey Neptuno"))
        assertFalse(VoskPhraseMatcher.matches("", "Hey Neptuno"))
    }

    @Test
    fun `la confianza minima baja al subir la sensibilidad`() {
        assertTrue(VoskPhraseMatcher.minConfidence(0.9f) < VoskPhraseMatcher.minConfidence(0.3f))
    }
}
