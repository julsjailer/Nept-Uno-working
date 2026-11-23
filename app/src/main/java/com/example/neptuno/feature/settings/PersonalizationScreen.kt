package com.example.neptuno.feature.settings

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.neptuno.core.model.AppLanguage
import com.example.neptuno.core.model.AssistantState
import com.example.neptuno.core.model.AvatarStyle
import com.example.neptuno.core.model.PersonalityMode
import com.example.neptuno.feature.avatar.NeptunoAvatar
import com.example.neptuno.feature.home.assistantStateLabel
import com.example.ui.theme.AmberPending
import com.example.ui.theme.BioluminescentTurquoise
import com.example.ui.theme.CoralAlert
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val voiceProfiles by viewModel.voiceProfiles.collectAsStateWithLifecycle()
    val deviceVoices by viewModel.deviceVoices.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val ttsError by viewModel.ttsError.collectAsStateWithLifecycle()

    var previewState by remember { mutableStateOf(AssistantState.IDLE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.personalization_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("personalization_back_button")
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
            // Vista previa viva del personaje 2D y sus 6 estados visuales
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NeptunoAvatar(
                            state = previewState,
                            style = prefs.avatarStyle,
                            size = 156.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.personalization_state_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AssistantState.entries) { stateItem ->
                                FilterChip(
                                    selected = previewState == stateItem,
                                    onClick = { previewState = stateItem },
                                    label = { Text(assistantStateLabel(stateItem)) },
                                    modifier = Modifier.testTag("avatar_preview_${stateItem.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Estilo del personaje
            item {
                Text(
                    text = stringResource(R.string.personalization_avatar_section),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(AvatarStyle.entries) { style ->
                SelectableOptionCard(
                    title = style.label,
                    subtitle = style.description,
                    selected = prefs.avatarStyle == style,
                    onClick = { viewModel.setAvatarStyle(style) },
                    testTag = "avatar_style_${style.id}"
                )
            }

            // Personalidad
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.personalization_personality_section),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(PersonalityMode.entries) { mode ->
                SelectableOptionCard(
                    title = mode.label,
                    subtitle = mode.description,
                    selected = prefs.personalityMode == mode,
                    onClick = { viewModel.setPersonality(mode) },
                    testTag = "personality_${mode.id}"
                )
            }

            // Perfiles de voz (vinculados a Android TextToSpeech real)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.personalization_voice_section),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::refreshVoiceCatalog,
                        modifier = Modifier.testTag("voice_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Actualizar voces TTS del sistema"
                        )
                    }
                }
            }

            items(voiceProfiles, key = { it.id }) { voice ->
                Card(
                    onClick = { viewModel.setVoice(voice.id) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (prefs.selectedVoiceId == voice.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (prefs.selectedVoiceId == voice.id) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_profile_${voice.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (voice.isAvailableNow) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                AmberPending
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (voice.isAvailableNow) {
                                        BioluminescentTurquoise.copy(alpha = 0.16f)
                                    } else {
                                        AmberPending.copy(alpha = 0.16f)
                                    }
                                ) {
                                    Text(
                                        text = if (voice.isAvailableNow) "Disponible" else "No disponible",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (voice.isAvailableNow) BioluminescentTurquoise else AmberPending,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = voice.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = voice.availabilityNote.ifBlank { voice.engineType },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (voice.isAvailableNow) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    AmberPending
                                }
                            )
                        }
                        if (prefs.selectedVoiceId == voice.id) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Voces instaladas en el dispositivo y ajuste acústico TTS
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Motor de voz Android TTS",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Voz instalada del dispositivo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (deviceVoices.isEmpty()) {
                            Text(
                                text = "El sistema no expone voces adicionales descargadas o TTS aún está inicializándose.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = prefs.selectedDeviceVoiceName == null,
                                        onClick = { viewModel.setDeviceVoiceName(null) },
                                        label = { Text("Automática") },
                                        modifier = Modifier.testTag("device_voice_auto")
                                    )
                                }
                                items(deviceVoices.take(15), key = { it.name }) { deviceVoice ->
                                    FilterChip(
                                        selected = prefs.selectedDeviceVoiceName == deviceVoice.name,
                                        onClick = { viewModel.setDeviceVoiceName(deviceVoice.name) },
                                        label = { Text("${deviceVoice.displayName} · ${deviceVoice.qualityLabel}") },
                                        modifier = Modifier.testTag("device_voice_${deviceVoice.name}")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Velocidad de lectura",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${((prefs.ttsSpeechRate * 100f).roundToInt() / 100f)}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = prefs.ttsSpeechRate,
                            onValueChange = viewModel::setTtsSpeechRate,
                            valueRange = 0.6f..1.6f,
                            modifier = Modifier.testTag("tts_rate_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tono acústico",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${((prefs.ttsPitch * 100f).roundToInt() / 100f)}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = prefs.ttsPitch,
                            onValueChange = viewModel::setTtsPitch,
                            valueRange = 0.7f..1.4f,
                            modifier = Modifier.testTag("tts_pitch_slider")
                        )

                        ttsError?.let { errorMsg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralAlert
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTtsSpeaking) {
                                FilledTonalButton(
                                    onClick = viewModel::stopVoicePreview,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = CoralAlert.copy(alpha = 0.2f),
                                        contentColor = CoralAlert
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("voice_preview_stop_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Detener")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = viewModel::testVoicePreview,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("voice_preview_play_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Probar voz")
                                }
                            }
                            TextButton(
                                onClick = viewModel::resetVoiceTuning,
                                modifier = Modifier.testTag("voice_reset_tuning_button")
                            ) {
                                Text("Restablecer")
                            }
                        }
                    }
                }
            }

            // Idioma
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.personalization_language_section),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = prefs.language == lang,
                            onClick = { viewModel.setLanguage(lang) },
                            label = { Text(lang.label) },
                            modifier = Modifier.testTag("language_${lang.code}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        onClick = onClick,
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
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
