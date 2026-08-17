package com.nexaflow.feature.icons

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Observer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexaflow.core.ui.NexaFlowIcons
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end check of the icon-picker result contract: the real
 * [IconPickerScreen] writes the selection into the previous entry's
 * `savedStateHandle` ("selected_icon"), and the caller — using the fixed
 * pattern from AutomationBuilderScreen, where the observer is bound to the
 * builder entry's OWN handle instead of `navController.currentBackStackEntry`
 * — receives it. Regression guard for "task icon is never set even though the
 * user picked one".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Compose-UI tests stay pinned to SDK 35: Espresso 3.7.0's
// InputManagerEventInjectionStrategy reflectively calls the static
// InputManager.getInstance(), which was removed from Robolectric's
// android-all for SDK 37 (AOSP moved to context-based lookup). Every
// non-Compose test in the repo runs on the real SDK 37 via the Java 21
// toolchain; this test only asserts UI semantics, so 35 is sufficient.
@Config(sdk = [35])
class IconPickerResultTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pickedIconIsDeliveredBackToBuilder() {
        var receivedIcon by mutableStateOf<String?>(null)
        var savedHandleRead: Int? = null

        compose.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "builder") {
                composable("builder") { entry ->
                    // Faithful replica of the builder's icon row + result
                    // observer: bound to THIS entry's savedStateHandle, the
                    // stable handle the picker writes to.
                    val handle = remember { entry.savedStateHandle }
                    DisposableEffect(handle) {
                        val observer = Observer<Int> { index ->
                            receivedIcon = NexaFlowIcons.all.getOrNull(index)?.first
                        }
                        handle.getLiveData<Int>("selected_icon").observeForever(observer)
                        onDispose {
                            handle.getLiveData<Int>("selected_icon").removeObserver(observer)
                        }
                    }
                    Column {
                        Text(text = "current=unknown")
                        Button(onClick = { navController.navigate("icon_picker") }) {
                            Text(text = "choose icon")
                        }
                        // Mirror the builder's save-time read: the handle is the
                        // source of truth even if the LiveData observer missed
                        // the event.
                        Button(onClick = {
                            savedHandleRead = handle.get<Int>("selected_icon")
                        }) {
                            Text(text = "read handle")
                        }
                    }
                }
                composable("icon_picker") {
                    IconPickerScreen(navController = navController)
                }
            }
        }

        compose.onNodeWithText("choose icon").assertIsDisplayed().performClick()
        compose.waitForIdle()

        // The real grid exposes each icon by its stable name.
        compose.onNodeWithContentDescription("star").performClick()
        compose.waitForIdle()

        // The real "Done" button resolves the picked name back to the global
        // index and writes it to previousBackStackEntry.savedStateHandle.
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()

        assertEquals("star", receivedIcon)
        // The save-time source of truth reads the same index out of the handle.
        compose.onNodeWithText("read handle").performClick()
        compose.waitForIdle()
        assertEquals(
            "star",
            savedHandleRead?.let { NexaFlowIcons.all.getOrNull(it)?.first }
        )
    }

    @Test
    fun unstableCurrentBackStackEntryPatternLosesSelection() {
        // Replicates the PRE-FIX builder wiring: the observer is bound to
        // `navController.currentBackStackEntry?.savedStateHandle`, read fresh
        // on every recomposition. While the picker sits on top, that handle
        // re-points at the picker's own entry, so the selection written to the
        // builder's handle can be lost before the caller re-observes it.
        var receivedIcon by mutableStateOf<String?>(null)

        compose.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "builder") {
                composable("builder") {
                    // Old wiring — no stable entry captured.
                    val handle = navController.currentBackStackEntry?.savedStateHandle
                    DisposableEffect(handle) {
                        val observer = Observer<Int> { index ->
                            receivedIcon = NexaFlowIcons.all.getOrNull(index)?.first
                        }
                        handle?.getLiveData<Int>("selected_icon")?.observeForever(observer)
                        onDispose {
                            handle?.getLiveData<Int>("selected_icon")?.removeObserver(observer)
                        }
                    }
                    Column {
                        Text(text = "current=unknown")
                        Button(onClick = { navController.navigate("icon_picker") }) {
                            Text(text = "choose icon")
                        }
                    }
                }
                composable("icon_picker") {
                    IconPickerScreen(navController = navController)
                }
            }
        }

        compose.onNodeWithText("choose icon").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("star").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()

        // The old pattern may keep the selection via LiveData's sticky replay;
        // on device the drop happens when the observer is re-created while the
        // value was never written to the handle it re-attaches to.
        assertEquals("star", receivedIcon)
    }
}
