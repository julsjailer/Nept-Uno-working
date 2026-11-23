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
