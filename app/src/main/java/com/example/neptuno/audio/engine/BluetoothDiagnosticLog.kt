package com.example.neptuno.audio.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro corto (en memoria) de lo que pasa con el micrófono Bluetooth, para copiarlo desde la pantalla Bluetooth. */
object BluetoothDiagnosticLog {
    private const val MAX = 80
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lines.addLast("$time $message")
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized
    fun report(): String = if (lines.isEmpty()) "Sin eventos todavía. Pulsa «Probar micrófono» y vuelve a copiar." else lines.joinToString("\n")
}
