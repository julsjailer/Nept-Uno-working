package com.example.neptuno.audio.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Textos que NEPTUNO dice cuando detecta o pierde unos auriculares Bluetooth. */
object HeadsetMessages {
    fun connected(name: String, hasMic: Boolean): String =
        if (hasMic) {
            "🎧 Detecté tus auriculares «$name» con micrófono. Los uso automáticamente para escucharte y hablarte; " +
                "si no captan tu voz, vuelvo solo al micrófono del teléfono. Puedes cambiarlo en Bluetooth."
        } else {
            "🎧 Detecté «$name». Android los expone solo para audio (música), sin micrófono: te escucho con el " +
                "micrófono del teléfono y te hablo por los auriculares."
        }

    fun disconnected(name: String): String =
        "🎧 Se desconectaron «$name». Vuelvo al micrófono y al altavoz del teléfono."

    fun status(name: String, hasMic: Boolean): String =
        if (hasMic) "Conectado: $name · con micrófono (modo automático)"
        else "Conectado: $name · solo audio, sin micrófono"
}

/** Última descripción del auricular conectado, para mostrarla en la pantalla Bluetooth. */
object BluetoothHeadsetStatus {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val state: kotlinx.coroutines.flow.StateFlow<String?> = _state
    fun set(value: String?) { _state.value = value }
}

/**
 * Vigila auriculares Bluetooth: al conectarlos o retirarlos avisa (mensaje del asistente en el chat), actualiza el
 * estado y da una oportunidad nueva al micrófono Bluetooth (borra el bloqueo por fallos anteriores).
 */
class BluetoothHeadsetWatcher(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _announcements = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val announcements: SharedFlow<String> = _announcements.asSharedFlow()

    private data class Headset(val name: String, val hasMic: Boolean)

    private var started = false
    private var current: Headset? = null
    private var debounce: Job? = null

    fun start() {
        val manager = audioManager ?: return
        if (started) return
        started = true
        current = detect(manager)
        BluetoothHeadsetStatus.set(current?.let { HeadsetMessages.status(it.name, it.hasMic) })
        manager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = schedule()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = schedule()
        }, null)
    }

    private fun schedule() {
        debounce?.cancel()
        debounce = scope.launch {
            delay(1_500)
            val manager = audioManager ?: return@launch
            val now = detect(manager)
            val before = current
            if (now == before) return@launch
            current = now
            BluetoothHeadsetStatus.set(now?.let { HeadsetMessages.status(it.name, it.hasMic) })
            when {
                now != null -> {
                    BluetoothMicHealth.reset()
                    _announcements.tryEmit(HeadsetMessages.connected(now.name, now.hasMic))
                }
                before != null -> {
                    _announcements.tryEmit(HeadsetMessages.disconnected(before.name))
                    AudioRouteNotice.set(null)
                }
            }
        }
    }

    private fun detect(manager: AudioManager): Headset? {
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { isBluetooth(it.type) }
        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { isBluetooth(it.type) }
        val device = inputs.firstOrNull() ?: outputs.firstOrNull() ?: return null
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "auriculares Bluetooth"
        return Headset(name = name, hasMic = inputs.isNotEmpty())
    }

    private fun isBluetooth(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
}
