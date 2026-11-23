package com.example.neptuno.android.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.neptuno.core.PermissionHelper
import com.example.neptuno.core.model.BluetoothAdapterState
import com.example.neptuno.core.model.BluetoothDeviceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Capa real de gestión de estado Bluetooth y dispositivos vinculados del sistema.
 * - Adapta permisos a Android 10–11 vs Android 12+ (API 31+).
 * - Nunca inventa dispositivos ni simula conexiones inexistentes.
 */
class BluetoothDeviceController(private val context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun observeAdapterState(): Flow<BluetoothAdapterState> = callbackFlow {
        fun readCurrentState(): BluetoothAdapterState {
            val adapter = bluetoothAdapter ?: return BluetoothAdapterState.UNAVAILABLE
            return when (adapter.state) {
                BluetoothAdapter.STATE_ON -> BluetoothAdapterState.ON
                BluetoothAdapter.STATE_OFF -> BluetoothAdapterState.OFF
                BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.TURNING_ON
                BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.TURNING_OFF
                else -> BluetoothAdapterState.OFF
            }
        }

        trySend(readCurrentState())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    trySend(readCurrentState())
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
        }

        awaitClose {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }.distinctUntilChanged()

    /**
     * Devuelve los dispositivos vinculados reales del dispositivo Android si existen permisos.
     */
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): Result<List<BluetoothDeviceInfo>> {
        val adapter = bluetoothAdapter
            ?: return Result.success(emptyList())

        if (!PermissionHelper.hasBluetoothConnectPermission(appContext)) {
            return Result.failure(SecurityException("Permiso BLUETOOTH_CONNECT no concedido."))
        }

        return runCatching {
            val bonded = adapter.bondedDevices.orEmpty()
            bonded.map { device ->
                BluetoothDeviceInfo(
                    address = device.address ?: "Sin dirección",
                    name = device.name?.takeIf { it.isNotBlank() } ?: "Dispositivo Bluetooth",
                    deviceTypeLabel = classifyBluetoothDevice(device),
                    isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                )
            }.sortedBy { it.name.lowercase() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun classifyBluetoothDevice(device: BluetoothDevice): String {
        val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        return when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio / Auriculares"
            BluetoothClass.Device.Major.WEARABLE -> "Wearable"
            BluetoothClass.Device.Major.PHONE -> "Teléfono"
            BluetoothClass.Device.Major.COMPUTER -> "Equipo"
            BluetoothClass.Device.Major.PERIPHERAL -> "Periférico"
            else -> "Dispositivo vinculado"
        }
    }
}

/**
 * Contrato reservado para futuros servicios en primer plano de sesión de voz / Bluetooth.
 */
interface AssistantServiceContract {
    fun isForegroundSessionSupported(): Boolean = false
}
