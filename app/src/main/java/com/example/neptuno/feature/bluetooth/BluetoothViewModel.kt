package com.example.neptuno.feature.bluetooth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neptuno.android.services.BluetoothDeviceController
import com.example.neptuno.audio.engine.AndroidAudioRouteManager
import com.example.neptuno.core.PermissionHelper
import com.example.neptuno.core.model.AudioRouteInfo
import com.example.neptuno.core.model.BluetoothAdapterState
import com.example.neptuno.core.model.BluetoothDeviceInfo
import com.example.neptuno.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BluetoothUiState(
    val adapterState: BluetoothAdapterState = BluetoothAdapterState.OFF,
    val hasBluetoothPermission: Boolean = false,
    val bondedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val inputRoutes: List<AudioRouteInfo> = emptyList(),
    val outputRoutes: List<AudioRouteInfo> = emptyList(),
    val selectedInputRouteId: Int? = null,
    val selectedOutputRouteId: Int? = null,
    val errorMessage: String? = null
)

class BluetoothViewModel(
    private val appContext: Context,
    private val bluetoothController: BluetoothDeviceController,
    private val audioRouteManager: AndroidAudioRouteManager,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _hasPermission = MutableStateFlow(PermissionHelper.hasBluetoothConnectPermission(appContext))
    private val _bondedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    private val _inputRoutes = MutableStateFlow<List<AudioRouteInfo>>(emptyList())
    private val _outputRoutes = MutableStateFlow<List<AudioRouteInfo>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BluetoothUiState> = combine(
        bluetoothController.observeAdapterState(),
        _hasPermission,
        _bondedDevices,
        _inputRoutes,
        _outputRoutes,
        preferencesRepository.userPreferences,
        _errorMessage
    ) { values ->
        val adapterState = values[0] as BluetoothAdapterState
        val hasPerm = values[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val bonded = values[2] as List<BluetoothDeviceInfo>
        @Suppress("UNCHECKED_CAST")
        val inputs = values[3] as List<AudioRouteInfo>
        @Suppress("UNCHECKED_CAST")
        val outputs = values[4] as List<AudioRouteInfo>
        val prefs = values[5] as com.example.neptuno.core.model.UserPreferences
        val err = values[6] as String?

        BluetoothUiState(
            adapterState = adapterState,
            hasBluetoothPermission = hasPerm,
            bondedDevices = bonded,
            inputRoutes = inputs,
            outputRoutes = outputs,
            selectedInputRouteId = prefs.preferredAudioInputId,
            selectedOutputRouteId = prefs.preferredAudioOutputId,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BluetoothUiState()
    )

    init {
        refreshHardwareState()
    }

    fun onPermissionsResult() {
        refreshHardwareState()
    }

    fun refreshHardwareState() {
        val granted = PermissionHelper.hasBluetoothConnectPermission(appContext)
        _hasPermission.value = granted

        if (granted) {
            bluetoothController.getBondedDevices()
                .onSuccess { devices ->
                    _bondedDevices.value = devices
                    _errorMessage.value = null
                }
                .onFailure { error ->
                    _bondedDevices.value = emptyList()
                    _errorMessage.value = error.localizedMessage
                }
        } else {
            _bondedDevices.value = emptyList()
        }

        _inputRoutes.value = audioRouteManager.getAvailableInputRoutes()
        _outputRoutes.value = audioRouteManager.getAvailableOutputRoutes()
    }

    private val _micTestResult = MutableStateFlow<String?>(null)
    val micTestResult: StateFlow<String?> = _micTestResult
    private val _micTestRunning = MutableStateFlow(false)
    val micTestRunning: StateFlow<Boolean> = _micTestRunning

    /** Vuelve al modo automático: NEPTUNO elige solo entrada y salida (Bluetooth si funciona, si no el teléfono). */
    fun useAutomaticMode() {
        viewModelScope.launch {
            preferencesRepository.setPreferredAudioInput(null)
            preferencesRepository.setPreferredAudioOutput(null)
            preferencesRepository.setPreferBluetoothAudio(true)
            com.example.neptuno.audio.engine.BluetoothMicHealth.reset()
            _micTestResult.value = "Modo automático activado: NEPTUNO elige solo el mejor micrófono y altavoz."
        }
    }

    /** Graba ~3 s con la ruta que usaría NEPTUNO y dice si realmente llega audio. Habla cuando empiece. */
    fun testMicrophone() {
        if (_micTestRunning.value) return
        if (!PermissionHelper.hasRecordAudioPermission(appContext)) {
            _micTestResult.value = "Falta el permiso de micrófono."
            return
        }
        _micTestRunning.value = true
        _micTestResult.value = "Escuchando 3 segundos… habla ahora."
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { runMicrophoneTest() }.getOrElse { "No se pudo probar el micrófono: ${it.localizedMessage}" }
            }
            _micTestResult.value = result
            _micTestRunning.value = false
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun runMicrophoneTest(): String {
        val sampleRate = 16_000
        val minBuffer = android.media.AudioRecord.getMinBufferSize(
            sampleRate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return "Android no pudo preparar el micrófono."
        val router = com.example.neptuno.audio.engine.AndroidBluetoothAudioRouter(appContext)
        val prefs = preferencesRepository.userPreferences.first()
        val (recorder, selection) = router.openRecorder(
            sampleRate = sampleRate,
            bufferBytes = maxOf(minBuffer * 2, sampleRate),
            preferredInputId = prefs.preferredAudioInputId,
            preferBluetooth = prefs.preferBluetoothAudio
        )
        try {
            if (recorder.state != android.media.AudioRecord.STATE_INITIALIZED) return "No se pudo abrir el micrófono."
            recorder.startRecording()
            val buffer = ShortArray(1_600)
            var peak = 0
            val end = System.currentTimeMillis() + 3_000
            while (System.currentTimeMillis() < end) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n <= 0) break
                for (i in 0 until n) peak = maxOf(peak, kotlin.math.abs(buffer[i].toInt()))
            }
            recorder.stop()
            val percent = peak * 100 / 32_767
            return when {
                peak >= 600 -> "✅ Se te oye bien con: ${selection.displayName} (nivel $percent %)."
                peak >= 4 && selection.isBluetooth ->
                    "⚠️ Llega muy poco audio por ${selection.displayName} (nivel $percent %). Habla más cerca o prueba el modo automático."
                peak >= 4 -> "⚠️ Se oye muy bajo con: ${selection.displayName} (nivel $percent %). Habla más cerca."
                selection.isBluetooth -> {
                    com.example.neptuno.audio.engine.BluetoothMicHealth.markFailed()
                    "❌ Los auriculares no envían audio (silencio total). NEPTUNO usará el micrófono del teléfono unos minutos. " +
                        "Prueba apagar y encender los auriculares, o que Android los use como \"llamadas\" y no solo \"multimedia\"."
                }
                else -> "❌ No llega audio con: ${selection.displayName}. Revisa el permiso de micrófono y que ninguna otra app lo use."
            }
        } finally {
            runCatching { recorder.release() }
            router.release()
        }
    }

    fun selectInputRoute(deviceId: Int) {
        viewModelScope.launch {
            preferencesRepository.setPreferredAudioInput(deviceId)
        }
    }

    fun selectOutputRoute(deviceId: Int) {
        viewModelScope.launch {
            preferencesRepository.setPreferredAudioOutput(deviceId)
        }
    }

    companion object {
        fun factory(
            context: Context,
            bluetoothController: BluetoothDeviceController,
            audioRouteManager: AndroidAudioRouteManager,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BluetoothViewModel(
                    appContext = context.applicationContext,
                    bluetoothController = bluetoothController,
                    audioRouteManager = audioRouteManager,
                    preferencesRepository = preferencesRepository
                ) as T
            }
        }
    }
}
