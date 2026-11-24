package com.example.neptuno.feature.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.neptuno.core.PermissionHelper
import com.example.neptuno.core.model.AssistantState
import com.example.neptuno.core.model.ChatMessage
import com.example.neptuno.core.model.MessageSender
import com.example.neptuno.feature.avatar.NeptunoAvatar
import com.example.ui.theme.AmberPending
import com.example.ui.theme.AmberPendingContainer
import com.example.ui.theme.BioluminescentTurquoise
import com.example.ui.theme.CoralAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draftInput.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
    }

    LaunchedEffect(state.messages.size, state.streamingResponseText) {
        val totalItems = state.messages.size + if (state.streamingResponseText != null) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val isInterruptible = state.isListening ||
        state.isGeneratingText ||
        state.isSpeaking ||
        state.assistantState == AssistantState.LISTENING ||
        state.assistantState == AssistantState.PROCESSING ||
        state.assistantState == AssistantState.SPEAKING

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeptunoAvatar(
                            state = state.assistantState,
                            style = state.avatarStyle,
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.brand_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${state.statusDetail} · ${state.activeProviderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.assistantState == AssistantState.ERROR) {
                                    CoralAlert
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        }
                    }
                },
                actions = {
                    if (isInterruptible) {
                        FilledTonalButton(
                            onClick = viewModel::interruptActiveResponse,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CoralAlert.copy(alpha = 0.2f),
                                contentColor = CoralAlert
                            ),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .testTag("chat_interrupt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.chat_interrupt_cd),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.chat_interrupt_label),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    IconButton(
                        onClick = viewModel::startNewSession,
                        modifier = Modifier.testTag("chat_new_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddComment,
                            contentDescription = stringResource(R.string.chat_new_session)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                // Banner transparente cuando el proveedor seleccionado sigue pendiente
                if (state.isProviderPending) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = AmberPendingContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, AmberPending.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
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
                            Column {
                                Text(
                                    text = stringResource(R.string.chat_provider_banner_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.chat_provider_banner_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Aviso de qué micrófono se usa (Bluetooth o teléfono) y por qué.
                state.audioRouteNotice?.let { routeText ->
                    Text(
                        text = routeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp)
                    )
                }

                // Banner de estado del micrófono / STT / TTS
                AnimatedVisibility(visible = state.microphoneStatusBanner != null) {
                    state.microphoneStatusBanner?.let { bannerText ->
                        val isErrorBanner = state.assistantState == AssistantState.ERROR
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isErrorBanner) {
                                CoralAlert.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isErrorBanner) {
                                    CoralAlert.copy(alpha = 0.55f)
                                } else {
                                    BioluminescentTurquoise.copy(alpha = 0.45f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bannerText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::dismissMicBanner,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Cerrar aviso",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Lista de mensajes o estado vacío
                if (state.messages.isEmpty() && state.streamingResponseText == null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NeptunoAvatar(
                                state = state.assistantState,
                                style = state.avatarStyle,
                                size = 120.dp
                            )
                            Text(
                                text = stringResource(R.string.chat_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.chat_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (!state.sttAvailability.isServiceAvailable) {
                                Text(
                                    text = state.sttAvailability.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberPending,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                onSpeakMessage = { viewModel.speakMessage(msg) }
                            )
                        }
                        state.streamingResponseText?.let { partialText ->
                            item(key = "streaming_assistant_bubble") {
                                StreamingAssistantBubble(text = partialText)
                            }
                        }
                    }
                }

                // Barra de entrada de texto + micrófono STT + envío
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isListening = state.isListening
                        FilledIconButton(
                            onClick = {
                                if (PermissionHelper.hasRecordAudioPermission(context)) {
                                    viewModel.onMicrophonePermissionResult(true)
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isListening) {
                                    BioluminescentTurquoise
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (isListening) {
                                    MaterialTheme.colorScheme.onSecondary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_mic_button")
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Outlined.MicOff else Icons.Filled.Mic,
                                contentDescription = if (isListening) {
                                    "Detener micrófono"
                                } else {
                                    stringResource(R.string.chat_mic_cd)
                                }
                            )
                        }

                        OutlinedTextField(
                            value = draft,
                            onValueChange = viewModel::onDraftChanged,
                            placeholder = {
                                Text(
                                    text = if (isListening) {
                                        "Escuchando voz…"
                                    } else {
                                        stringResource(R.string.chat_input_placeholder)
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            maxLines = 4,
                            shape = MaterialTheme.shapes.large,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        FilledIconButton(
                            onClick = viewModel::sendMessage,
                            enabled = draft.isNotBlank(),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send_cd)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, BioluminescentTurquoise.copy(alpha = 0.5f)),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .testTag("chat_streaming_bubble")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = stringResource(R.string.chat_streaming_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = BioluminescentTurquoise
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onSpeakMessage: () -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val isAssistant = message.sender == MessageSender.ASSISTANT
    val isSystemNotice = message.sender == MessageSender.SYSTEM_NOTICE
    val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMillis))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isSystemNotice -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            border = BorderStroke(
                1.dp,
                when {
                    isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    isSystemNotice -> AmberPending.copy(alpha = 0.4f)
                    message.isInterrupted -> CoralAlert.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (isSystemNotice) {
                    Text(
                        text = if (message.isInterrupted) {
                            "AVISO DEL SISTEMA · INTERRUPCIÓN"
                        } else {
                            "AVISO DEL SISTEMA"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberPending
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (isAssistant && message.isInterrupted) {
                    Text(
                        text = stringResource(R.string.chat_interrupted_badge).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralAlert
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAssistant) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAssistant) {
                        IconButton(
                            onClick = onSpeakMessage,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("chat_speak_message_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Reproducir respuesta en voz alta",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
