package com.example.neptuno

import android.speech.tts.Voice
import com.example.neptuno.audio.engine.VoiceRanking
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceRankingTest {
    private fun voice(name: String, country: String, quality: Int, network: Boolean) =
        Voice(name, Locale("es", country), quality, Voice.LATENCY_NORMAL, network, emptySet())

    @Test
    fun `prefiere la voz de mayor calidad`() {
        val low = voice("es-es-low", "ES", Voice.QUALITY_NORMAL, false)
        val high = voice("es-es-high", "ES", Voice.QUALITY_VERY_HIGH, false)
        assertEquals("es-es-high", VoiceRanking.best(listOf(low, high))?.name)
    }

    @Test
    fun `a igual calidad prefiere español latino y luego voz de red`() {
        val spain = voice("es-es", "ES", Voice.QUALITY_HIGH, false)
        val us = voice("es-us", "US", Voice.QUALITY_HIGH, false)
        val usNet = voice("es-us-net", "US", Voice.QUALITY_HIGH, true)
        assertEquals("es-us-net", VoiceRanking.best(listOf(spain, us, usNet))?.name)
    }

    @Test
    fun `sin voces devuelve nulo`() {
        assertNull(VoiceRanking.best(emptyList()))
    }
}
