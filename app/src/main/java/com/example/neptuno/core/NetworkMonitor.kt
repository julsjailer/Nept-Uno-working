package com.example.neptuno.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkConnectivityState {
    VALIDATED_INTERNET,
    CONNECTED_WITHOUT_INTERNET,
    OFFLINE,
    UNAVAILABLE
}

data class NetworkStatus(
    val isConnected: Boolean,
    val transportLabel: String,
    val isValidated: Boolean = isConnected,
    val isConnectedWithoutInternet: Boolean = false,
    val hasActiveNetwork: Boolean = isConnected || isConnectedWithoutInternet,
    val connectivityState: NetworkConnectivityState = when {
        isConnected && isValidated -> NetworkConnectivityState.VALIDATED_INTERNET
        isConnectedWithoutInternet -> NetworkConnectivityState.CONNECTED_WITHOUT_INTERNET
        else -> NetworkConnectivityState.OFFLINE
    }
) {
    fun describeWithOfflineMode(offlineModeForced: Boolean): String = when {
        offlineModeForced -> "Modo offline forzado (local)"
        connectivityState == NetworkConnectivityState.VALIDATED_INTERNET -> "Internet validado · $transportLabel"
        connectivityState == NetworkConnectivityState.CONNECTED_WITHOUT_INTERNET ->
            "Red conectada sin acceso a Internet · $transportLabel"
        connectivityState == NetworkConnectivityState.UNAVAILABLE -> "Servicio de red no disponible"
        else -> "Sin conexión a Internet"
    }
}

/**
 * Monitor no bloqueante de conectividad a Internet basado en ConnectivityManager (Android 10+ / API 29+).
 * Diferencia red disponible con Internet validado, red conectada sin Internet y estado sin conexión.
 */
open class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    open fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(
                NetworkStatus(
                    isConnected = false,
                    transportLabel = "Sin servicio de red",
                    isValidated = false,
                    isConnectedWithoutInternet = false,
                    hasActiveNetwork = false,
                    connectivityState = NetworkConnectivityState.UNAVAILABLE
                )
            )
            awaitClose { }
            return@callbackFlow
        }

        fun currentStatus(): NetworkStatus {
            val activeNetwork = runCatching { manager.activeNetwork }.getOrNull()
                ?: return evaluateCapabilities(
                    hasActiveNetwork = false,
                    hasInternetCapability = false,
                    hasValidatedCapability = false,
                    transportLabel = "Sin conexión"
                )
            val caps = runCatching { manager.getNetworkCapabilities(activeNetwork) }.getOrNull()
                ?: return evaluateCapabilities(
                    hasActiveNetwork = false,
                    hasInternetCapability = false,
                    hasValidatedCapability = false,
                    transportLabel = "Sin conexión"
                )
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Red activa"
            }
            return evaluateCapabilities(
                hasActiveNetwork = true,
                hasInternetCapability = hasInternet,
                hasValidatedCapability = isValidated,
                transportLabel = transport
            )
        }

        trySend(currentStatus())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentStatus())
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(currentStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { manager.registerNetworkCallback(request, callback) }
            .onFailure {
                trySend(
                    NetworkStatus(
                        isConnected = false,
                        transportLabel = "No disponible",
                        isValidated = false,
                        isConnectedWithoutInternet = false,
                        hasActiveNetwork = false,
                        connectivityState = NetworkConnectivityState.UNAVAILABLE
                    )
                )
            }

        awaitClose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    companion object {
        fun evaluateCapabilities(
            hasActiveNetwork: Boolean,
            hasInternetCapability: Boolean,
            hasValidatedCapability: Boolean,
            transportLabel: String
        ): NetworkStatus {
            if (!hasActiveNetwork || !hasInternetCapability) {
                return NetworkStatus(
                    isConnected = false,
                    transportLabel = "Sin conexión",
                    isValidated = false,
                    isConnectedWithoutInternet = false,
                    hasActiveNetwork = hasActiveNetwork,
                    connectivityState = NetworkConnectivityState.OFFLINE
                )
            }
            if (!hasValidatedCapability) {
                return NetworkStatus(
                    isConnected = false,
                    transportLabel = transportLabel,
                    isValidated = false,
                    isConnectedWithoutInternet = true,
                    hasActiveNetwork = true,
                    connectivityState = NetworkConnectivityState.CONNECTED_WITHOUT_INTERNET
                )
            }
            return NetworkStatus(
                isConnected = true,
                transportLabel = transportLabel,
                isValidated = true,
                isConnectedWithoutInternet = false,
                hasActiveNetwork = true,
                connectivityState = NetworkConnectivityState.VALIDATED_INTERNET
            )
        }
    }
}
