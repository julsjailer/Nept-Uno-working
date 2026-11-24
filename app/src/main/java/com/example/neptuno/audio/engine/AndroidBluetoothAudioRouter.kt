package com.example.neptuno.audio.engine

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Selecciona una entrada de micrófono real para la captura local.
 *
 * A2DP y los dispositivos Bluetooth que solo son salida nunca se eligen como micrófono.
 * Android 12+ usa setCommunicationDevice; Android 10/11 activa SCO antes de buscar la entrada.
 * Si no hay una ruta Bluetooth compatible, AudioRecord conserva la entrada predeterminada del teléfono.
 */
class AndroidBluetoothAudioRouter(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var originalAudioMode: Int? = null
    private var legacyScoRequested = false
    private var communicationDeviceSelected = false

    suspend fun configureInput(
        recorder: AudioRecord,
        preferredInputId: Int?,
        preferBluetooth: Boolean
    ): AudioInputRouteSelection {
        release()
        val manager = audioManager ?: return AudioInputRouteSelection("Micrófono predeterminado", false)

        val initialInputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val explicit = preferredInputId?.let { id -> initialInputs.firstOrNull { it.id == id } }

        if (explicit != null && !isBluetoothInput(explicit)) {
            return applyInput(recorder, explicit)
                ?: AudioInputRouteSelection("Micrófono predeterminado", false)
        }

        val shouldTryBluetooth = explicit?.let(::isBluetoothInput) ?: preferBluetooth
        if (shouldTryBluetooth) {
            val bluetoothInput = connectBluetoothInput(manager, explicit?.type)
            if (bluetoothInput != null) {
                applyInput(recorder, bluetoothInput)?.let { return it }
            }
            releaseBluetoothRoute(manager)
        }

        val fallback = explicit ?: preferredInputId?.let { id ->
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }
        }
        if (fallback != null && !isBluetoothInput(fallback)) {
            applyInput(recorder, fallback)?.let { return it }
        }

        runCatching { recorder.setPreferredDevice(null) }
        return AudioInputRouteSelection("Micrófono predeterminado del teléfono", false)
    }

    private suspend fun connectBluetoothInput(
        manager: AudioManager,
        preferredType: Int?
    ): AudioDeviceInfo? {
        val communicationOutput = findBluetoothCommunicationOutput(manager, preferredType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (communicationOutput != null) {
                val accepted = runCatching {
                    manager.setCommunicationDevice(communicationOutput)
                }.getOrDefault(false)
                if (accepted) {
                    communicationDeviceSelected = true
                    delay(BLUETOOTH_ROUTE_SETTLE_MS)
                }
            }
        } else if (communicationOutput?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            originalAudioMode = manager.mode
            runCatching {
                manager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                manager.startBluetoothSco()
                legacyScoRequested = true
            }
            withTimeoutOrNull(LEGACY_SCO_TIMEOUT_MS) {
                while (!runCatching {
                    @Suppress("DEPRECATION")
                    manager.isBluetoothScoOn
                }.getOrDefault(false)) {
                    delay(SCO_POLL_INTERVAL_MS)
                }
            }
        }

        val availableInputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return availableInputs.firstOrNull { device ->
            isBluetoothInput(device) &&
                (preferredType == null || device.type == preferredType)
        } ?: availableInputs.firstOrNull(::isBluetoothInput)
    }

    private fun findBluetoothCommunicationOutput(
        manager: AudioManager,
        preferredType: Int?
    ): AudioDeviceInfo? {
        val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.getAvailableCommunicationDevices()
        } else {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        return outputs.firstOrNull { device ->
            isBluetoothCommunicationOutput(device) &&
                (preferredType == null || device.type == preferredType)
        } ?: outputs.firstOrNull(::isBluetoothCommunicationOutput)
    }

    private fun isBluetoothCommunicationOutput(device: AudioDeviceInfo): Boolean {
        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    private fun isBluetoothInput(device: AudioDeviceInfo): Boolean =
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private fun applyInput(
        recorder: AudioRecord,
        device: AudioDeviceInfo
    ): AudioInputRouteSelection? {
        val accepted = runCatching { recorder.setPreferredDevice(device) }.getOrDefault(false)
        if (!accepted) return null
        val bluetooth = isBluetoothInput(device)
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: if (bluetooth) "Micrófono Bluetooth" else "Micrófono externo"
        return AudioInputRouteSelection(name, bluetooth)
    }

    fun release() {
        val manager = audioManager ?: return
        releaseBluetoothRoute(manager)
    }

    private fun releaseBluetoothRoute(manager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && communicationDeviceSelected) {
            runCatching { manager.clearCommunicationDevice() }
            communicationDeviceSelected = false
        }
        if (legacyScoRequested) {
            runCatching {
                @Suppress("DEPRECATION")
                manager.stopBluetoothSco()
            }
            legacyScoRequested = false
        }
        originalAudioMode?.let { previousMode ->
            runCatching { manager.mode = previousMode }
            originalAudioMode = null
        }
    }

    companion object {
        private const val BLUETOOTH_ROUTE_SETTLE_MS = 350L
        private const val LEGACY_SCO_TIMEOUT_MS = 3_000L
        private const val SCO_POLL_INTERVAL_MS = 100L
    }
}

data class AudioInputRouteSelection(
    val displayName: String,
    val isBluetooth: Boolean
)
