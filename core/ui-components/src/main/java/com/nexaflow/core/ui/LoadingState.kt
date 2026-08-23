package com.nexaflow.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.nexaflow.core.ui.Dimens.Space4
import com.nexaflow.core.ui.Dimens.Space6
import com.nexaflow.core.ui.Dimens.Space8

/**
 * Shared full-width loading state. A compact progress indicator centered in
 * the available space — used for list/screen loads where the task is quick
 * (spinner over skeleton, per M3 guidance). Declared as a polite live region
 * so screen readers announce the load without stealing focus.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space8)
            .semantics {
                if (contentDescription != null) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(Dimens.Space6))
    }
}

/**
 * Shared error state with an optional retry action. Announces itself to
 * screen readers via an assertive live region, so a failed load is reported
 * immediately (as opposed to the polite loading region above).
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space6, vertical = Space8)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space4)
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(Space8),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text(text = retryLabel)
            }
        }
    }
}

@Preview(name = "LoadingState", showBackground = true)
@Composable
private fun LoadingStatePreview() {
    MaterialTheme { LoadingState() }
}

@Preview(name = "LoadingState – Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoadingStateDarkPreview() {
    MaterialTheme { LoadingState() }
}

@Preview(name = "ErrorState", showBackground = true)
@Composable
private fun ErrorStatePreview() {
    MaterialTheme {
        ErrorState(
            message = "Something went wrong",
            retryLabel = "Retry",
            onRetry = {}
        )
    }
}

@Preview(name = "ErrorState – Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ErrorStateDarkPreview() {
    MaterialTheme {
        ErrorState(
            message = "Something went wrong",
            retryLabel = "Retry",
            onRetry = {}
        )
    }
}
