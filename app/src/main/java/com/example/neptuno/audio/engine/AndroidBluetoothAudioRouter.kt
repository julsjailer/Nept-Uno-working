package com.example.neptuno.audio.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.neptuno.core.PermissionHelper
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

    private val appContext = context.applicationContext

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var legacyScoRequested = false
    private var communicationDeviceSelected = false
    private var boundBluetoothInput: AudioDeviceInfo? = null

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

        val shouldTryBluetooth = explicit?.let(::isBluetoothInput)
            ?: (preferBluetooth && !BluetoothMicHealth.isBlocked())
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
                // Sin MODE_IN_COMMUNICATION muchos auriculares nunca abren el canal de micrófono (SCO/HFP):
                // la captura llegaba en silencio (0 %).
                rememberOriginalMode(manager)
                runCatching { manager.mode = AudioManager.MODE_IN_COMMUNICATION }
                val accepted = runCatching {
                    manager.setCommunicationDevice(communicationOutput)
                }.getOrDefault(false)
                if (accepted) {
                    communicationDeviceSelected = true
                    awaitCommunicationDevice(manager, communicationOutput)
                    delay(BLUETOOTH_ROUTE_SETTLE_MS)
                }
            }
        } else if (communicationOutput?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            rememberOriginalMode(manager)
            runCatching {
                manager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                manager.startBluetoothSco()
                legacyScoRequested = true
            }
            awaitLegacySco(manager)
            delay(BLUETOOTH_ROUTE_SETTLE_MS)
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
        boundBluetoothInput = if (bluetooth) device else null
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: if (bluetooth) "Micrófono Bluetooth" else "Micrófono externo"
        return AudioInputRouteSelection(name, bluetooth)
    }

    /**
     * Prepara la ruta de comunicación Bluetooth para SpeechRecognizer.
     * SpeechRecognizer no expone AudioRecord.setPreferredDevice; Android enruta la entrada
     * asociada al dispositivo de comunicación seleccionado. Si no existe micrófono BT,
     * deja la ruta del teléfono intacta y permite continuar con el reconocedor del sistema.
     */
    suspend fun configureCommunicationInput(
        preferredInputId: Int?,
        preferBluetooth: Boolean
    ): AudioInputRouteSelection {
        release()
        val manager = audioManager
            ?: return phoneFallback("no se pudo acceder al audio del sistema")

        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val preferredInput = preferredInputId?.let { id -> inputs.firstOrNull { it.id == id } }
        val shouldTryBluetooth = when {
            preferredInput != null -> isBluetoothInput(preferredInput)
            preferredInputId != null -> false
            else -> preferBluetooth && !BluetoothMicHealth.isBlocked()
        }
        if (!shouldTryBluetooth) {
            return phoneFallback(
                if (preferBluetooth && preferredInputId == null && BluetoothMicHealth.isBlocked()) {
                    "el micrófono Bluetooth no envió audio hace poco; se volverá a probar en unos minutos"
                } else {
                    "Bluetooth no está priorizado en Ajustes o elegiste otro micrófono"
                }
            )
        }

        val communicationOutput = findBluetoothCommunicationOutput(
            manager,
            preferredInput?.type
        ) ?: return phoneFallback(noBluetoothMicReason())

        rememberOriginalMode(manager)
        val modeAccepted = runCatching {
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            true
        }.getOrDefault(false)
        if (!modeAccepted) {
            releaseBluetoothRoute(manager)
            return phoneFallback("Android no permitió el modo de comunicación")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val accepted = runCatching {
                manager.setCommunicationDevice(communicationOutput)
            }.getOrDefault(false)
            if (!accepted) {
                releaseBluetoothRoute(manager)
                return phoneFallback("Android rechazó los auriculares como micrófono")
            }
            communicationDeviceSelected = true
            awaitCommunicationDevice(manager, communicationOutput)
            delay(BLUETOOTH_ROUTE_SETTLE_MS)
        } else if (communicationOutput.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            runCatching {
                @Suppress("DEPRECATION")
                manager.startBluetoothSco()
                legacyScoRequested = true
            }.onFailure {
                releaseBluetoothRoute(manager)
                return phoneFallback("no se pudo iniciar la conexión del micrófono Bluetooth (SCO)")
            }
            val connected = awaitLegacySco(manager)
            if (connected) delay(BLUETOOTH_ROUTE_SETTLE_MS)
            if (!connected) {
                releaseBluetoothRoute(manager)
                return phoneFallback("los auriculares no activaron su canal de micrófono (SCO)")
            }
        } else {
            releaseBluetoothRoute(manager)
            return phoneFallback("esta versión de Android no permite usar el micrófono Bluetooth")
        }

        val inputName = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull(::isBluetoothInput)
            ?.productName
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: communicationOutput.productName.toString().takeIf { it.isNotBlank() }
            ?: "Bluetooth"
        AudioRouteNotice.set("🎧 Usando el micrófono Bluetooth: $inputName")
        return AudioInputRouteSelection("Micrófono Bluetooth · $inputName", true)
    }

    private fun phoneFallback(reason: String): AudioInputRouteSelection {
        AudioRouteNotice.set("🎙️ Usando el micrófono del teléfono: $reason.")
        return AudioInputRouteSelection("Micrófono predeterminado del teléfono", false)
    }

    private fun noBluetoothMicReason(): String =
        if (!PermissionHelper.hasBluetoothConnectPermission(appContext)) {
            "falta el permiso de Bluetooth (concédelo en Ajustes del teléfono > Apps > NEPTUNO > Permisos)"
        } else {
            "no hay unos auriculares Bluetooth con micrófono conectados, o solo están conectados para música"
        }

    /** Firma de las entradas visibles para detectar conexiones/desconexiones reales. */
    fun currentInputSignature(): String =
        audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)
            ?.sortedBy { it.id }
            ?.joinToString("|") { "${it.id}:${it.type}:${it.productName}" }
            .orEmpty()

    /** Observa cambios de dispositivos de audio; el propietario debe desregistrar el callback. */
    fun registerInputDeviceCallback(onDevicesChanged: () -> Unit): AudioDeviceCallback? {
        val manager = audioManager ?: return null
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                onDevicesChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                onDevicesChanged()
            }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        return callback
    }

    fun unregisterInputDeviceCallback(callback: AudioDeviceCallback?) {
        if (callback == null) return
        runCatching { audioManager?.unregisterAudioDeviceCallback(callback) }
    }

    /** Espera (máx. 3 s) a que Android confirme el dispositivo de comunicación Bluetooth (Android 12+). */
    private suspend fun awaitCommunicationDevice(manager: AudioManager, target: AudioDeviceInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        withTimeoutOrNull(LEGACY_SCO_TIMEOUT_MS) {
            while (runCatching { manager.communicationDevice?.id }.getOrNull() != target.id) {
                delay(SCO_POLL_INTERVAL_MS)
            }
        }
    }

    /** Espera a que el canal SCO esté realmente CONECTADO (broadcast de Android), no solo "encendido". */
    private suspend fun awaitLegacySco(manager: AudioManager): Boolean {
        val filter = android.content.IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        fun connectedIn(intent: android.content.Intent?): Boolean =
            intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED
        val sticky = runCatching { appContext.registerReceiver(null, filter) }.getOrNull()
        if (connectedIn(sticky)) return true
        val result = withTimeoutOrNull(LEGACY_SCO_TIMEOUT_MS + 1_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: android.content.Intent?) {
                        if (connectedIn(intent) && continuation.isActive) continuation.resumeWith(Result.success(true))
                    }
                }
                runCatching { appContext.registerReceiver(receiver, filter) }
                continuation.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }
                // Por si el estado ya cambió entre el sticky y el registro.
                if (runCatching { @Suppress("DEPRECATION") manager.isBluetoothScoOn }.getOrDefault(false) &&
                    connectedIn(runCatching { appContext.registerReceiver(null, filter) }.getOrNull())
                ) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(true))
                }
            }
        }
        return result == true ||
            runCatching { @Suppress("DEPRECATION") manager.isBluetoothScoOn }.getOrDefault(false)
    }

    /**
     * Crea el AudioRecord y lo enruta. Si hay que usar un micrófono Bluetooth (HFP/SCO), prueba varias formas de abrir
     * el canal —cada auricular/ROM se comporta distinto— y se queda con la primera por la que REALMENTE llega audio
     * (mide ~1 s). La que funciona se recuerda por dispositivo. Si ninguna entrega audio, vuelve al micrófono del teléfono.
     */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun openRecorder(
        sampleRate: Int,
        bufferBytes: Int,
        preferredInputId: Int?,
        preferBluetooth: Boolean
    ): Pair<AudioRecord, AudioInputRouteSelection> {
        fun newRecorder(source: Int) = AudioRecord(
            source, sampleRate, android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT, bufferBytes
        )
        val manager = audioManager
        if (manager != null) {
            release()
            val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
            val explicit = preferredInputId?.let { id -> inputs.firstOrNull { it.id == id } }
            val wantsBluetooth = when {
                explicit != null -> isBluetoothInput(explicit)
                else -> preferBluetooth && !BluetoothMicHealth.isBlocked()
            }
            val communicationOutput = findBluetoothCommunicationOutput(manager, explicit?.type)
            BluetoothDiagnosticLog.add(
                "Abrir micrófono: BT=$wantsBluetooth, salida comunicación=${communicationOutput?.productName}, " +
                    "entradas=" + inputs.joinToString { "${it.productName}/${it.type}" } +
                    ", Android ${Build.VERSION.SDK_INT}"
            )
            if (wantsBluetooth && communicationOutput != null) {
                val name = communicationOutput.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                val order = orderedStrategies(name)
                val tried = mutableListOf<String>()
                val remembered = rememberedStrategy(name)
                for (strategy in order) {
                    val recorder = runCatching {
                        attemptBluetooth(manager, strategy, communicationOutput, ::newRecorder, trustSilence = strategy == remembered)
                    }.getOrNull()
                    if (recorder != null) {
                        rememberStrategy(name, strategy)
                        AudioRouteNotice.set("🎧 Usando el micrófono Bluetooth: $name (ruta ${strategy.label})")
                        return recorder to AudioInputRouteSelection("Micrófono Bluetooth · $name", true)
                    }
                    BluetoothDiagnosticLog.add("Ruta ${strategy.label} descartada")
                    tried += strategy.label
                    releaseBluetoothRoute(manager)
                }
                BluetoothMicHealth.markFailed()
                AudioRouteNotice.set(
                    "🎙️ Probé ${tried.size} formas de abrir el micrófono de «$name» (${tried.joinToString()}) y ninguna " +
                        "entrega audio; uso el micrófono del teléfono y respondo por los auriculares."
                )
                release()
                val phone = newRecorder(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
                return phone to AudioInputRouteSelection("Micrófono predeterminado del teléfono", false)
            }
        }
        val recorder = newRecorder(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
        val selection = configureInput(recorder, preferredInputId, preferBluetooth)
        return recorder to selection
    }

    /** Formas de abrir el micrófono Bluetooth, de la más estándar a la más permisiva. */
    enum class ScoStrategy(val label: String) {
        COMMUNICATION_DEVICE_AND_SCO("A"),
        COMMUNICATION_DEVICE_ONLY("B"),
        LEGACY_SCO_ONLY("C"),
        PLAIN_MIC_PREFERRED_DEVICE("D")
    }

    private fun orderedStrategies(deviceName: String): List<ScoStrategy> {
        val all = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ScoStrategy.values().toList()
        } else {
            listOf(ScoStrategy.LEGACY_SCO_ONLY, ScoStrategy.PLAIN_MIC_PREFERRED_DEVICE)
        }
        val remembered = appContext.getSharedPreferences("bt_route", Context.MODE_PRIVATE)
            .getString("strategy_$deviceName", null)
        val first = all.firstOrNull { it.name == remembered }
        return if (first != null) listOf(first) + all.filter { it != first } else all
    }

    private fun rememberedStrategy(deviceName: String): ScoStrategy? {
        val saved = appContext.getSharedPreferences("bt_route", Context.MODE_PRIVATE)
            .getString("strategy_$deviceName", null)
        return ScoStrategy.values().firstOrNull { it.name == saved }
    }

    private fun rememberStrategy(deviceName: String, strategy: ScoStrategy) {
        appContext.getSharedPreferences("bt_route", Context.MODE_PRIVATE)
            .edit().putString("strategy_$deviceName", strategy.name).apply()
    }

    /** Abre el canal con una estrategia y devuelve el AudioRecord solo si en ~1 s llegó audio real (no silencio). */
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun attemptBluetooth(
        manager: AudioManager,
        strategy: ScoStrategy,
        communicationOutput: AudioDeviceInfo,
        create: (Int) -> AudioRecord,
        trustSilence: Boolean = false
    ): AudioRecord? {
        releaseBluetoothRoute(manager)
        val modeNeeded = strategy != ScoStrategy.PLAIN_MIC_PREFERRED_DEVICE
        if (modeNeeded) {
            rememberOriginalMode(manager)
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        val useCommunicationDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (strategy == ScoStrategy.COMMUNICATION_DEVICE_AND_SCO || strategy == ScoStrategy.COMMUNICATION_DEVICE_ONLY)
        val useLegacySco = strategy == ScoStrategy.COMMUNICATION_DEVICE_AND_SCO ||
            strategy == ScoStrategy.LEGACY_SCO_ONLY
        if (useCommunicationDevice && manager.setCommunicationDevice(communicationOutput)) {
            communicationDeviceSelected = true
            awaitCommunicationDevice(manager, communicationOutput)
        }
        if (useLegacySco) {
            @Suppress("DEPRECATION")
            manager.startBluetoothSco()
            legacyScoRequested = true
            awaitLegacySco(manager)
        }
        delay(BLUETOOTH_ROUTE_SETTLE_MS)

        val bluetoothInput = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull(::isBluetoothInput)
        val source = if (strategy == ScoStrategy.PLAIN_MIC_PREFERRED_DEVICE) {
            android.media.MediaRecorder.AudioSource.MIC
        } else {
            android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
        val recorder = create(source)
        val bindDevice = strategy != ScoStrategy.COMMUNICATION_DEVICE_ONLY && strategy != ScoStrategy.LEGACY_SCO_ONLY
        if (bindDevice && bluetoothInput != null) runCatching { recorder.setPreferredDevice(bluetoothInput) }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        boundBluetoothInput = if (bindDevice) bluetoothInput else null

        recorder.startRecording()
        val probe = ShortArray(1_600)
        var peak = 0
        val end = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < end) {
            val n = recorder.read(probe, 0, probe.size)
            if (n <= 0) break
            for (i in 0 until n) peak = maxOf(peak, kotlin.math.abs(probe[i].toInt()))
        }
        val routed = runCatching { recorder.routedDevice }.getOrNull()
        val routedBluetooth = routed != null && (routed.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            routed.type == 26 || routed.type == 23)
        BluetoothDiagnosticLog.add(
            "Ruta ${strategy.label}: pico=$peak, dispositivo real=${routed?.productName}/tipo ${routed?.type}, " +
                "BT-entrada=${bluetoothInput?.productName}, SCO=${manager.isBluetoothScoOn}, modo=${manager.mode}"
        )
        recorder.stop()
        // Un micrófono Bluetooth que está bien enrutado pero en silencio (nadie habla) NO es un fallo: se acepta.
        if (routedBluetooth) return recorder
        // Muchos micrófonos Bluetooth cortan el sonido (silencio digital) cuando nadie habla; una ruta que ya funcionó
        // antes con este auricular se acepta aunque el segundo de prueba sea silencio.
        if (peak < AUDIO_PRESENT_PEAK && !trustSilence) {
            recorder.release()
            return null
        }
        return recorder
    }

    fun release() {
        boundBluetoothInput = null
        val manager = audioManager ?: return
        releaseBluetoothRoute(manager)
    }

    /** Guarda el modo de audio original (solo el primero que abre la ruta) y marca a este router como dueño de la ruta. */
    private fun rememberOriginalMode(manager: AudioManager) {
        synchronized(SharedRoute) {
            if (SharedRoute.originalMode == null) SharedRoute.originalMode = manager.mode
            SharedRoute.owners.add(this)
        }
    }

    /**
     * Cierra la ruta Bluetooth SOLO si ningún otro componente la sigue usando. Antes, al terminar el dictado (STT) se
     * apagaba el SCO que "Hey Neptuno" (Vosk) seguía usando y su micrófono quedaba mudo sin avisar.
     */
    private fun releaseBluetoothRoute(manager: AudioManager) {
        val othersStillUse = synchronized(SharedRoute) {
            SharedRoute.owners.remove(this)
            SharedRoute.owners.isNotEmpty()
        }
        if (othersStillUse) {
            communicationDeviceSelected = false
            legacyScoRequested = false
            return
        }
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
        synchronized(SharedRoute) {
            SharedRoute.originalMode?.let { previousMode ->
                runCatching { manager.mode = previousMode }
                SharedRoute.originalMode = null
            }
        }
    }

    /** Estado compartido entre todos los routers del proceso (el audio del sistema es único). */
    private object SharedRoute {
        val owners = mutableSetOf<AndroidBluetoothAudioRouter>()
        var originalMode: Int? = null
    }

    companion object {
        private const val BLUETOOTH_ROUTE_SETTLE_MS = 350L
        private const val LEGACY_SCO_TIMEOUT_MS = 3_000L
        private const val SCO_POLL_INTERVAL_MS = 100L
        private const val AUDIO_PRESENT_PEAK = 3
    }
}

data class AudioInputRouteSelection(
    val displayName: String,
    val isBluetooth: Boolean
)
