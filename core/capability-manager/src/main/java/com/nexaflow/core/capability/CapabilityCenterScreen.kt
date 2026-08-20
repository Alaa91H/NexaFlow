package com.nexaflow.core.capability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityEnvironmentId
import com.nexaflow.domain.capability.CapabilityEnvironmentReport
import com.nexaflow.domain.capability.CapabilityEnvironmentState

/**
 * Settings-only capability diagnostics. It observes the shared state store and
 * never grants permissions, starts external apps, invokes shell commands, or
 * changes device state. Actions and triggers are hidden in their own pickers;
 * this screen is the single place that explains an unavailable requirement.
 */
@Composable
fun CapabilityCenterScreen(viewModel: CapabilityCenterViewModel = hiltViewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val environments by viewModel.environmentReports.collectAsStateWithLifecycle()
    val unavailable = viewModel.unavailable(snapshot)
    val availableCount = snapshot.reports.values.count { it.availability == CapabilityAvailability.AVAILABLE }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.capability_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.capability_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        item { SectionHeader(text = stringResource(R.string.capability_runtime_title)) }
        items(environments, key = { it.environment.name }) { report ->
            EnvironmentDiagnosticCard(report)
        }

        item {
            SectionHeader(
                text = stringResource(R.string.capability_unavailable_title),
                trailing = {
                    StatusPill(
                        text = stringResource(R.string.capability_unavailable_count, unavailable.size),
                        background = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
        if (unavailable.isEmpty()) {
            item {
                NexaFlowCard {
                    Text(
                        text = stringResource(R.string.capabilities_count, availableCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            items(unavailable, key = { it.capability.name }) { report ->
                UnavailableCapabilityCard(
                    label = viewModel.capabilityLabels[report.capability] ?: report.capability.name,
                    report = report
                )
            }
        }

        item { SectionHeader(text = stringResource(R.string.section_about)) }
        item {
            NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = Icons.Filled.Info,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.about_privileged_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvironmentDiagnosticCard(report: CapabilityEnvironmentReport) {
    val available = report.state == CapabilityEnvironmentState.AVAILABLE
    NexaFlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.Security,
                containerColor = if (available) NexaFlowTheme.colors.success else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (available) NexaFlowTheme.colors.onSuccess else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(environmentLabel(report.environment)), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.capability_reason, stringResource(environmentStateLabel(report.state))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            StatusPill(
                text = stringResource(environmentStateLabel(report.state)),
                background = if (available) NexaFlowTheme.colors.success else MaterialTheme.colorScheme.error,
                contentColor = if (available) NexaFlowTheme.colors.onSuccess else MaterialTheme.colorScheme.onError
            )
        }
    }
}

@Composable
private fun UnavailableCapabilityCard(label: String, report: CapabilityAvailabilityReport) {
    NexaFlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.Security,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.capability_reason, stringResource(availabilityLabel(report.availability))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            StatusPill(
                text = stringResource(availabilityLabel(report.availability)),
                background = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        }
    }
}

private fun environmentLabel(environment: CapabilityEnvironmentId): Int = when (environment) {
    CapabilityEnvironmentId.STANDARD -> R.string.environment_standard
    CapabilityEnvironmentId.SHIZUKU -> R.string.environment_shizuku
    CapabilityEnvironmentId.ROOT -> R.string.environment_root
    CapabilityEnvironmentId.MANAGED_DEVICE -> R.string.environment_managed_device
    CapabilityEnvironmentId.ADB -> R.string.environment_adb
}

private fun environmentStateLabel(state: CapabilityEnvironmentState): Int = when (state) {
    CapabilityEnvironmentState.AVAILABLE -> R.string.capability_state_available
    CapabilityEnvironmentState.NOT_INSTALLED -> R.string.capability_state_not_installed
    CapabilityEnvironmentState.NOT_RUNNING -> R.string.capability_state_not_running
    CapabilityEnvironmentState.PERMISSION_REQUIRED -> R.string.capability_state_permission_required
    CapabilityEnvironmentState.SERVICE_UNAVAILABLE -> R.string.capability_state_service_unavailable
    CapabilityEnvironmentState.UNAVAILABLE -> R.string.capability_state_unavailable
    CapabilityEnvironmentState.UNSUPPORTED -> R.string.capability_state_unsupported
}

private fun availabilityLabel(availability: CapabilityAvailability): Int = when (availability) {
    CapabilityAvailability.AVAILABLE -> R.string.capability_state_available
    CapabilityAvailability.PARTIAL -> R.string.capability_availability_partial
    CapabilityAvailability.PERMISSION_REQUIRED -> R.string.capability_availability_permission_required
    CapabilityAvailability.UNAVAILABLE -> R.string.capability_availability_unavailable
    CapabilityAvailability.UNSUPPORTED -> R.string.capability_availability_unsupported
}
