package com.nexaflow.feature.ai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexaflow.core.airuntime.AiConversationMessage
import com.nexaflow.core.airuntime.AiRole
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar

@Composable
fun AiChatScreen(
    navController: NavController,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.ai_chat_title),
                subtitle = state.providerName
                    ?: stringResource(R.string.ai_chat_no_provider),
                onBack = { navController.popBackStack() },
                actions = {
                    TextButton(onClick = viewModel::clearConversation) {
                        Text(stringResource(R.string.ai_chat_clear))
                    }
                }
            )
        },
        bottomBar = {
            ChatComposer(
                value = input,
                running = state.running,
                onValueChange = { input = it },
                onSend = {
                    val submitted = input
                    input = ""
                    viewModel.send(submitted)
                },
                onCancel = viewModel::cancel
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.providerName == null) {
                item(key = "provider_warning") {
                    NexaFlowCard(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.ai_chat_provider_required),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.ai_chat_provider_required_detail),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (state.messages.isEmpty() && state.assistantDraft.isBlank()) {
                item(key = "empty") {
                    NexaFlowCard {
                        Text(
                            text = stringResource(R.string.ai_chat_welcome),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.ai_chat_welcome_detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(
                items = state.messages,
                key = { index, message ->
                    message.role.name + ":" + index + ":" + message.text.hashCode()
                }
            ) { _, message ->
                ChatBubble(message)
            }

            if (state.assistantDraft.isNotBlank()) {
                item(key = "assistant_stream") {
                    ChatBubble(
                        AiConversationMessage(
                            role = AiRole.ASSISTANT,
                            text = state.assistantDraft
                        )
                    )
                }
            }

            state.activeToolName?.let { toolName ->
                item(key = "active_tool") {
                    NexaFlowCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.widthIn(max = 22.dp),
                                strokeWidth = 2.dp
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.ai_chat_tool_running),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = toolName,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            state.errorCode?.let { code ->
                item(key = "error") {
                    NexaFlowCard(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = errorMessage(code),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = viewModel::clearError) {
                            Text(stringResource(R.string.ai_chat_dismiss))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AiConversationMessage) {
    val user = message.role == AiRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = if (user) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    running: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    // System speech recognizer: transcription runs in the system UI, so no
    // RECORD_AUDIO permission is needed here; the transcript only fills the
    // composer and still goes through the normal send path.
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                val prefix = if (value.isBlank()) "" else value.trimEnd() + " "
                onValueChange((prefix + spoken).take(MAX_COMPOSER_CHARS))
            }
        }
    }
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (it.length <= MAX_COMPOSER_CHARS) onValueChange(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(stringResource(R.string.ai_chat_input_hint))
                },
                minLines = 1,
                maxLines = 5,
                enabled = !running
            )
            if (running) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.ai_chat_cancel))
                }
            } else {
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                        }
                        try {
                            voiceLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            // No system recognizer installed; the typed path remains.
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.ai_chat_voice_input)
                    )
                }
                Button(
                    onClick = onSend,
                    enabled = value.isNotBlank()
                ) {
                    Text(stringResource(R.string.ai_chat_send))
                }
            }
        }
    }
}

private const val MAX_COMPOSER_CHARS = 32_768

@Composable
private fun errorMessage(code: String): String = when (code) {
    "no_provider" -> stringResource(R.string.ai_chat_no_provider_error)
    "invalid_conversation" -> stringResource(R.string.ai_chat_invalid_request)
    "output_limit" -> stringResource(R.string.ai_chat_output_limit)
    "tool_limit", "tool_iteration_limit" ->
        stringResource(R.string.ai_chat_tool_limit)
    else -> stringResource(R.string.ai_chat_provider_failure)
}
