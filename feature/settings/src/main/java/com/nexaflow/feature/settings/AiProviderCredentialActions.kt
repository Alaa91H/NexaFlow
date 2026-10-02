package com.nexaflow.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nexaflow.core.airuntime.AiProviderProtocol

@Composable
internal fun AiProviderCredentialActions(
    state: AgentSettingsUiState,
    viewModel: AgentSettingsViewModel,
    editingProfileId: String?,
    profilePresetId: String?,
    profileProtocol: AiProviderProtocol,
    providerName: String,
    providerUrl: String,
    providerModel: String,
    providerLocal: Boolean,
    providerApiKey: String,
    reasoningEffort: String,
    onApiKeyChange: (String) -> Unit,
    onSaved: () -> Unit
) {
    val hasStoredCredential = editingProfileId != null &&
        state.providerProfiles.any {
            it.id == editingProfileId && it.credentialRef != null
        }
    val credentiallessLocal = profilePresetId == null &&
        providerLocal &&
        profileProtocol == AiProviderProtocol.OPENAI_CHAT_COMPLETIONS
    val hasUsableCredential = providerApiKey.isNotBlank() ||
        hasStoredCredential ||
        credentiallessLocal
    val validDraft = providerName.isNotBlank() &&
        providerUrl.isNotBlank() &&
        providerModel.isNotBlank() &&
        hasUsableCredential

    OutlinedTextField(
        value = providerApiKey,
        onValueChange = { if (it.length <= MAX_API_KEY_LENGTH) onApiKeyChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.ai_provider_api_key)) },
        placeholder = { Text(stringResource(R.string.ai_provider_api_key_hint)) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true
    )

    if (hasStoredCredential) {
        Text(
            text = stringResource(R.string.ai_provider_api_key_saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        TextButton(
            onClick = {
                editingProfileId?.let(viewModel::clearProviderProfileApiKey)
                onApiKeyChange("")
            }
        ) {
            Text(
                stringResource(R.string.ai_provider_remove) + " " +
                    stringResource(R.string.ai_provider_api_key)
            )
        }
    }

    TextButton(
        onClick = {
            viewModel.verifyProviderDraft(
                profileId = editingProfileId,
                presetId = profilePresetId,
                protocol = profileProtocol,
                displayName = providerName,
                baseUrl = providerUrl,
                modelId = providerModel,
                local = providerLocal,
                apiKey = providerApiKey
            )
        },
        enabled = validDraft
    ) {
        Text(stringResource(R.string.ai_provider_verify))
    }

    Button(
        onClick = {
            viewModel.saveProviderProfile(
                profileId = editingProfileId,
                presetId = profilePresetId,
                protocol = profileProtocol,
                displayName = providerName,
                baseUrl = providerUrl,
                modelId = providerModel,
                local = providerLocal,
                apiKey = providerApiKey,
                reasoningEffort = reasoningEffort,
                enableRequested = AiProviderSetupPolicy.enableOnSave(
                    state.providerProbeState
                ),
                onComplete = { saved ->
                    if (saved) {
                        onApiKeyChange("")
                        onSaved()
                    }
                }
            )
        },
        enabled = validDraft
    ) {
        Text(
            stringResource(
                if (
                    state.providerProbeState == AiProviderProbeState.SUCCESS &&
                    editingProfileId == null
                ) {
                    R.string.ai_provider_add
                } else {
                    R.string.ai_provider_save
                }
            )
        )
    }
}

private const val MAX_API_KEY_LENGTH = 16_384
