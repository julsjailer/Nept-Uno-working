package com.example.neptuno

import com.example.neptuno.audio.engine.BluetoothMicHealth
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothMicHealthTest {
    @After
    fun cleanup() = BluetoothMicHealth.reset()

    @Test
    fun `tras un fallo el bluetooth queda bloqueado y se libera pasado el tiempo`() {
        BluetoothMicHealth.reset()
        assertFalse(BluetoothMicHealth.isBlocked(nowMillis = 1_000L))
        BluetoothMicHealth.markFailed(nowMillis = 1_000L)
        assertTrue(BluetoothMicHealth.isBlocked(nowMillis = 1_000L + 60_000L))
        assertFalse(BluetoothMicHealth.isBlocked(nowMillis = 1_000L + BluetoothMicHealth.COOLDOWN_MS + 1))
    }

    @Test
    fun `reset desbloquea`() {
        BluetoothMicHealth.markFailed()
        BluetoothMicHealth.reset()
        assertFalse(BluetoothMicHealth.isBlocked())
    }
}
