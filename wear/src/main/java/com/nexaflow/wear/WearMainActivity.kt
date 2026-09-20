package com.nexaflow.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.nexaflow.wear.presentation.WearApp
import com.nexaflow.wear.presentation.WearViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single entry-point activity for the Wear OS companion app.
 *
 * Hosts the Compose UI tree rooted at [WearApp] with the Hilt-injected
 * [WearViewModel] provided via [viewModels].
 */
@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(viewModel = viewModel)
        }
    }
}
