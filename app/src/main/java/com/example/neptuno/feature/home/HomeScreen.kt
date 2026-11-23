package com.example.neptuno.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.AssistantState
import com.example.neptuno.core.model.BluetoothAdapterState
import com.example.neptuno.feature.avatar.NeptunoAvatar
import com.example.neptuno.feature.cloud.CloudAccountSummary
import com.example.neptuno.feature.cloud.CloudSyncStatus
import com.example.ui.theme.BioluminescentTurquoise
import com.example.ui.theme.ElectricBlue

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    cloudSummary: CloudAccountSummary = CloudAccountSummary(),
    onStartConversation: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSignIn: () -> Unit = onOpenSettings
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val primaryGlow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.78f),
                        radius = size.maxDimension * 0.42f
                    )
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Barra superior: Identidad + acceso directo a Configuración
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.brand_title),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.brand_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onOpenBluetooth,
                            modifier = Modifier.testTag("home_top_bluetooth_button")
                        ) {
                            Icon(
                                imageVector = if (state.bluetoothState == BluetoothAdapterState.ON) {
                                    Icons.Outlined.Bluetooth
                                } else {
                                    Icons.Outlined.BluetoothDisabled
                                },
                                contentDescription = stringResource(R.string.home_quick_bluetooth),
                                tint = if (state.bluetoothState == BluetoothAdapterState.ON) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("home_top_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.home_quick_settings),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Píldoras de estado de conexión a Internet y nube opcional
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isOnline = state.networkStatus.isConnected && !state.offlineModeForced
                    StatusPill(
                        icon = if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.CloudOff,
                        label = state.networkStatus.describeWithOfflineMode(state.offlineModeForced),
                        highlighted = isOnline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_network_status_pill")
                    )

                    val cloudHighlighted = cloudSummary.isAuthenticated &&
                        cloudSummary.syncStatus == CloudSyncStatus.SYNCED
                    val cloudIcon = when {
                        !cloudSummary.isAuthenticated -> Icons.Outlined.CloudOff
                        cloudSummary.isSyncing -> Icons.Outlined.CloudSync
                        cloudSummary.syncStatus == CloudSyncStatus.SYNCED -> Icons.Outlined.CloudDone
                        else -> Icons.Outlined.CloudSync
                    }
                    Surface(
                        onClick = if (cloudSummary.isAuthenticated) onOpenSettings else onOpenSignIn,
                        shape = MaterialTheme.shapes.medium,
                        color = if (cloudHighlighted) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_cloud_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = cloudIcon,
                                contentDescription = null,
                                tint = if (cloudHighlighted) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (cloudSummary.isAuthenticated) {
                                    "Cloud: ${cloudSummary.syncStatus.label}"
                                } else {
                                    "${cloudSummary.syncStatus.label} · Modo local (pulsa para cuenta opcional)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Escenario central del Personaje 2D de Neptuno
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NeptunoAvatar(
                            state = state.assistantState,
                            style = state.avatarStyle,
                            size = 196.dp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (state.assistantState == AssistantState.OFFLINE) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    BioluminescentTurquoise.copy(alpha = 0.55f)
                                }
                            )
                        ) {
                            Text(
                                text = "ESTADO: ${assistantStateLabel(state.assistantState).uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.assistantState == AssistantState.OFFLINE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = when (val st = state.providerStatus) {
                                is AiProviderStatus.Ready ->
                                    "${stringResource(R.string.home_provider_ready)}: ${state.activeProviderName}"
                                is AiProviderStatus.Configured ->
                                    "${state.activeProviderName} · Sin verificación en esta sesión"
                                is AiProviderStatus.Unavailable ->
                                    "${state.activeProviderName}: ${st.reason} · Modo local activo"
                                is AiProviderStatus.PendingIntegration ->
                                    "${stringResource(R.string.home_provider_pending)} (${state.activeProviderName})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state.providerStatus) {
                                is AiProviderStatus.Ready -> MaterialTheme.colorScheme.secondary
                                is AiProviderStatus.Unavailable -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onStartConversation,
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = Color(0xFF040D1A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("home_start_conversation_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.home_start_conversation),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Accesos rápidos reales a módulos del sistema
            item {
                Text(
                    text = "Módulos del sistema",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickModuleCard(
                            icon = Icons.Outlined.Bluetooth,
                            title = stringResource(R.string.home_quick_bluetooth),
                            subtitle = when (state.bluetoothState) {
                                BluetoothAdapterState.ON -> "Activado"
                                BluetoothAdapterState.OFF -> "Desactivado"
                                BluetoothAdapterState.UNAVAILABLE -> "No disponible"
                                else -> "Cambiando…"
                            },
                            onClick = onOpenBluetooth,
                            testTag = "home_quick_bluetooth_card",
                            modifier = Modifier.weight(1f)
                        )
                        QuickModuleCard(
                            icon = Icons.Outlined.Face,
                            title = stringResource(R.string.home_quick_personalization),
                            subtitle = state.avatarStyle.label,
                            onClick = onOpenPersonalization,
                            testTag = "home_quick_personalization_card",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickModuleCard(
                            icon = Icons.Outlined.Memory,
                            title = stringResource(R.string.nav_memory),
                            subtitle = "${state.memoryCount} recuerdos · ${state.conversationCount} chats",
                            onClick = onOpenMemory,
                            testTag = "home_quick_memory_card",
                            modifier = Modifier.weight(1f)
                        )
                        QuickModuleCard(
                            icon = Icons.Outlined.Build,
                            title = stringResource(R.string.home_quick_tools),
                            subtitle = "Catálogo extensible",
                            onClick = onOpenTools,
                            testTag = "home_quick_tools_card",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (highlighted) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun QuickModuleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun assistantStateLabel(state: AssistantState): String = when (state) {
    AssistantState.IDLE -> "Inactivo"
    AssistantState.LISTENING -> "Escuchando"
    AssistantState.PROCESSING -> "Procesando"
    AssistantState.SPEAKING -> "Hablando"
    AssistantState.ERROR -> "Error"
    AssistantState.OFFLINE -> "Sin conexión"
}
