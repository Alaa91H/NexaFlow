package com.nexaflow.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
        // The watch pulls the list on every resume: connectivity may have
        // returned while the UI sat on the Connecting spinner.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                viewModel.refreshFromPhone()
            }
        })
        setContent {
            WearApp(viewModel = viewModel)
        }
    }
}
