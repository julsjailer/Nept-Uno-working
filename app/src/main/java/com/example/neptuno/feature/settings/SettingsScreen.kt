package com.example.neptuno.feature.settings

import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Button
import com.example.BuildConfig
import com.example.R
import com.example.neptuno.core.model.AiProviderStatus
import com.example.neptuno.core.model.ThemeMode
import com.example.neptuno.feature.cloud.CloudAccountSummary
import com.example.neptuno.feature.cloud.CloudSyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPersonalization: () -> Unit,
    onOpenMemory: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    cloudSummary: CloudAccountSummary? = null,
    cloudMemoriesCount: Int? = null,
    cloudConversationsCount: Int? = null,
    cloudError: String? = null,
    onSyncNow: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (cloudSummary != null) {
                item {
                    SettingsSectionCard(title = "Cuenta Google y Cloud Firestore (Opcional)") {
                        Text(
                            text = "Estado: ${cloudSummary.syncStatus.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = when (cloudSummary.syncStatus) {
                                CloudSyncStatus.SYNCED -> MaterialTheme.colorScheme.secondary
                                CloudSyncStatus.AUTH_ERROR,
                                CloudSyncStatus.PERMISSION_ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (!cloudSummary.isAuthenticated) {
                            Text(
                                text = "Modo local activo con Room y DataStore. Puedes iniciar sesión con Google para respaldar perfil, memoria y conversaciones en Cloud Firestore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!cloudSummary.lastSyncMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cloudSummary.lastSyncMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (onSignIn != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onSignIn,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_sign_in_button")
                                ) {
                                    Text("Iniciar sesión con Google")
                                }
                            }
                        } else {
                            Text(
                                text = cloudSummary.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!cloudSummary.email.isNullOrBlank()) {
                                Text(
                                    text = cloudSummary.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val countsText = when {
                                cloudMemoriesCount != null && cloudConversationsCount != null ->
                                    "En la nube: $cloudMemoriesCount memorias · $cloudConversationsCount sesiones"
                                cloudSummary.syncStatus == CloudSyncStatus.OFFLINE ||
                                    cloudSummary.syncStatus == CloudSyncStatus.LOCAL_ONLY ->
                                    "Usando caché local Room (sin sincronización activa)"
                                cloudSummary.syncStatus == CloudSyncStatus.AUTH_ERROR ||
                                    cloudSummary.syncStatus == CloudSyncStatus.PERMISSION_ERROR ||
                                    cloudSummary.syncStatus == CloudSyncStatus.TIMEOUT ||
                                    cloudSummary.syncStatus == CloudSyncStatus.PARTIAL_SYNC ->
                                    "Respaldo en nube pendiente · Caché local Room disponible"
                                else -> "Consultando Cloud Firestore…"
                            }
                            Text(
                                text = countsText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (!cloudSummary.lastSyncMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cloudSummary.lastSyncMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            listOfNotNull(
                                cloudSummary.profileError?.let { "Perfil: $it" },
                                cloudSummary.memoriesError?.let { "Memoria: $it" },
                                cloudSummary.conversationsError?.let { "Historial: $it" },
                                cloudError
                            ).distinct().forEach { errMsg ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (onSyncNow != null) {
                                    OutlinedButton(
                                        onClick = onSyncNow,
                                        enabled = !cloudSummary.isSyncing,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_cloud_sync_button")
                                    ) {
                                        Text(
                                            when {
                                                cloudSummary.isSyncing -> "Sincronizando…"
                                                cloudSummary.syncStatus == CloudSyncStatus.PARTIAL_SYNC ||
                                                    cloudSummary.syncStatus == CloudSyncStatus.TIMEOUT ||
                                                    cloudSummary.syncStatus == CloudSyncStatus.OFFLINE -> "Reintentar"
                                                else -> "Sincronizar"
                                            }
                                        )
                                    }
                                }
                                if (onSignOut != null) {
                                    Button(
                                        onClick = onSignOut,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_sign_out_button")
                                    ) {
                                        Text("Cerrar sesión")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Conectividad y proveedor IA previsto
            item {
                SettingsSectionCard(title = stringResource(R.string.settings_connectivity)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_offline_mode),
                        description = stringResource(R.string.settings_offline_mode_desc),
                        checked = prefs.offlineMode,
                        onCheckedChange = viewModel::setOfflineMode,
                        testTag = "settings_offline_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Proveedor de IA preferido (arquitectura desacoplada)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    viewModel.aiProviders.forEach { provider ->
                        val selected = prefs.preferredAiProviderId == provider.id
                        Card(
                            onClick = { viewModel.setPreferredAiProvider(provider.id) },
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("settings_provider_${provider.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    val statusText = when (val st = provider.status) {
                                        is AiProviderStatus.Ready ->
                                            if (provider.capabilities.supportsBidirectionalAudio) {
                                                "Estado: Operativo · Audio bidireccional"
                                            } else {
                                                "Estado: Operativo · Streaming de texto"
                                            }
                                        is AiProviderStatus.Configured ->
                                            "Estado: ${st.statusNote}"
                                        is AiProviderStatus.PendingIntegration ->
                                            "Estado: Integración pendiente"
                                        is AiProviderStatus.Unavailable ->
                                            "No disponible: ${st.reason}"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (provider.status) {
                                            is AiProviderStatus.Ready -> MaterialTheme.colorScheme.secondary
                                            is AiProviderStatus.Unavailable -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Seleccionado",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Preferencias de audio
            item {
                SettingsSectionCard(title = stringResource(R.string.settings_audio)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_auto_speak),
                        description = stringResource(R.string.settings_auto_speak_desc),
                        checked = prefs.autoSpeakResponses,
                        onCheckedChange = viewModel::setAutoSpeakResponses,
                        testTag = "settings_auto_speak_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_auto_send_voice),
                        description = stringResource(R.string.settings_auto_send_voice_desc),
                        checked = prefs.autoSendVoice,
                        onCheckedChange = viewModel::setAutoSendVoice,
                        testTag = "settings_auto_send_voice_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_barge_in),
                        description = stringResource(R.string.settings_barge_in_desc),
                        checked = prefs.bargeInEnabled,
                        onCheckedChange = viewModel::setBargeInEnabled,
                        testTag = "settings_barge_in_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_auto_route_bt),
                        description = stringResource(R.string.settings_auto_route_bt_desc),
                        checked = prefs.preferBluetoothAudio,
                        onCheckedChange = viewModel::setPreferBluetoothAudio,
                        testTag = "settings_bt_audio_switch"
                    )
                }
            }

            // Privacidad y memoria
            item {
                SettingsSectionCard(title = stringResource(R.string.settings_privacy)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_memory_consent),
                        description = stringResource(R.string.settings_memory_consent_desc),
                        checked = prefs.memoryAuthorized,
                        onCheckedChange = viewModel::setMemoryAuthorized,
                        testTag = "settings_memory_consent_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_save_history),
                        description = stringResource(R.string.settings_save_history_desc),
                        checked = prefs.persistConversationHistory,
                        onCheckedChange = viewModel::setPersistConversationHistory,
                        testTag = "settings_persist_history_switch"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenMemory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_open_memory_button")
                        ) {
                            Text("Gestionar memoria")
                        }
                        OutlinedButton(
                            onClick = onOpenPersonalization,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_open_personalization_button")
                        ) {
                            Text("Personalización")
                        }
                    }
                }
            }

            // Apariencia
            item {
                SettingsSectionCard(title = stringResource(R.string.settings_appearance)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ThemeMode.entries) { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("settings_theme_${mode.id}")
                            )
                        }
                    }
                }
            }

            // Información de la aplicación
            item {
                SettingsSectionCard(title = stringResource(R.string.settings_about)) {
                    AboutInfoRow("Aplicación", "NEPTUNO · Fase 2 (Voz STT/TTS y Gemini)")
                    AboutInfoRow("Versión", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    AboutInfoRow("Compatibilidad", "Android 10+ (API 29) · Actual API ${Build.VERSION.SDK_INT}")
                    AboutInfoRow("Almacenamiento", "Room SQLite + DataStore + Cloud Firestore")
                    AboutInfoRow("Autenticación", "Google Sign-In (Credential Manager)")
                    AboutInfoRow("Voz", "Android SpeechRecognizer + TextToSpeech")
                    AboutInfoRow("Motor de IA", "Firebase AI Logic + contratos extensibles")
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
