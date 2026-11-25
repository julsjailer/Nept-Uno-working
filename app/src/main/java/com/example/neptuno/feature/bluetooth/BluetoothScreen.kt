package com.example.neptuno.feature.bluetooth

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.neptuno.core.PermissionHelper
import com.example.neptuno.core.model.AudioRouteDirection
import com.example.neptuno.core.model.AudioRouteInfo
import com.example.neptuno.core.model.BluetoothAdapterState
import com.example.neptuno.core.model.BluetoothDeviceInfo
import com.example.ui.theme.AmberPending
import com.example.ui.theme.AmberPendingContainer
import com.example.ui.theme.BioluminescentTurquoise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    viewModel: BluetoothViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val btPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onPermissionsResult()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bluetooth_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("bluetooth_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refreshHardwareState,
                        modifier = Modifier.testTag("bluetooth_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.bluetooth_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Modo automático/híbrido: auricular detectado, prueba de micrófono y vuelta a automático.
            item {
                val headsetStatus by com.example.neptuno.audio.engine.BluetoothHeadsetStatus.state
                    .collectAsStateWithLifecycle()
                val routeNotice by com.example.neptuno.audio.engine.AudioRouteNotice.state
                    .collectAsStateWithLifecycle()
                val testResult by viewModel.micTestResult.collectAsStateWithLifecycle()
                val testRunning by viewModel.micTestRunning.collectAsStateWithLifecycle()
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("bluetooth_auto_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Modo automático + manual",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = headsetStatus ?: "No hay auriculares Bluetooth conectados. NEPTUNO usa el teléfono.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        routeNotice?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "NEPTUNO detecta los auriculares al conectarlos, los usa solo y vuelve al teléfono si no captan tu voz. " +
                                "Abajo puedes elegir a mano una entrada o salida concreta; con «Volver a automático» se quita esa elección.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        testResult?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::testMicrophone,
                                enabled = !testRunning,
                                modifier = Modifier.weight(1f).testTag("bluetooth_test_mic")
                            ) { Text("Probar micrófono") }
                            OutlinedButton(
                                onClick = viewModel::useAutomaticMode,
                                modifier = Modifier.weight(1f).testTag("bluetooth_use_auto")
                            ) { Text("Volver a automático") }
                        }
                        val diagContext = LocalContext.current
                        var diagCopied by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = {
                                val cm = diagContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "Informe Bluetooth",
                                        com.example.neptuno.audio.engine.BluetoothDiagnosticLog.report()
                                    )
                                )
                                diagCopied = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("bluetooth_copy_report")
                        ) { Text(if (diagCopied) "Informe copiado (pégalo en el chat)" else "Copiar informe técnico") }
                    }
                }
            }

            // Tarjeta de estado real del adaptador Bluetooth
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.adapterState == BluetoothAdapterState.ON) {
                                    Icons.Filled.Bluetooth
                                } else {
                                    Icons.Outlined.BluetoothDisabled
                                },
                                contentDescription = null,
                                tint = if (state.adapterState == BluetoothAdapterState.ON) {
                                    BioluminescentTurquoise
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Adaptador Bluetooth",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = when (state.adapterState) {
                                        BluetoothAdapterState.ON -> stringResource(R.string.bluetooth_status_on)
                                        BluetoothAdapterState.OFF -> stringResource(R.string.bluetooth_status_off)
                                        BluetoothAdapterState.TURNING_ON -> "Activando…"
                                        BluetoothAdapterState.TURNING_OFF -> "Desactivando…"
                                        BluetoothAdapterState.UNAVAILABLE -> stringResource(R.string.bluetooth_status_unavailable)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.adapterState == BluetoothAdapterState.ON) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bluetooth_open_system_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.bluetooth_open_system_settings))
                        }
                    }
                }
            }

            // Solicitud de permisos en Android 12+ (API 31+)
            if (!state.hasBluetoothPermission) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.bluetooth_permission_required),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val perms = PermissionHelper.requiredBluetoothPermissions()
                                    if (perms.isNotEmpty()) {
                                        btPermissionsLauncher.launch(perms)
                                    } else {
                                        viewModel.refreshHardwareState()
                                    }
                                },
                                modifier = Modifier.testTag("bluetooth_request_permission_button")
                            ) {
                                Text(stringResource(R.string.bluetooth_grant_permission))
                            }
                        }
                    }
                }
            }

            // Dispositivos vinculados reales del sistema
            item {
                Text(
                    text = stringResource(R.string.bluetooth_paired_devices),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (state.bondedDevices.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.bluetooth_no_paired_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.bondedDevices, key = { it.address }) { device ->
                    BondedDeviceCard(device = device)
                }
            }

            // Rutas reales de entrada y salida de audio (AudioManager)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bluetooth_audio_routes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = AmberPendingContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, AmberPending.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bluetooth_audio_routing_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = AmberPending,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.bluetooth_audio_routes_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.bluetooth_audio_input),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (state.inputRoutes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.bluetooth_no_audio_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.inputRoutes, key = { "in_${it.id}" }) { route ->
                    AudioRouteRow(
                        route = route,
                        selected = state.selectedInputRouteId == route.id,
                        onSelect = { viewModel.selectInputRoute(route.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bluetooth_audio_output),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (state.outputRoutes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.bluetooth_no_audio_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.outputRoutes, key = { "out_${it.id}" }) { route ->
                    AudioRouteRow(
                        route = route,
                        selected = state.selectedOutputRouteId == route.id,
                        onSelect = { viewModel.selectOutputRoute(route.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BondedDeviceCard(device: BluetoothDeviceInfo) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${device.deviceTypeLabel} · ${device.address}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AudioRouteRow(
    route: AudioRouteInfo,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_route_${route.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (route.direction == AudioRouteDirection.INPUT) Icons.Outlined.Mic else Icons.Outlined.Speaker,
                contentDescription = null,
                tint = if (route.isBluetooth) BioluminescentTurquoise else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.productName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = route.typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.bluetooth_preference_saved),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.bluetooth_preference_saved),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
