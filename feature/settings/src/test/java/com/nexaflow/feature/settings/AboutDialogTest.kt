package com.nexaflow.feature.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render test for the About dialog: every project surface a user can reach —
 * source repository, announcement channel and community group — must actually
 * be on screen, tagged with the developer's own rows so the two are not
 * confused.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class AboutDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int): String = context.getString(resId)

    private fun render(visible: Boolean = true) {
        composeRule.setContent {
            AboutDialog(visible = visible, appVersion = "3.91.15", onDismiss = {})
        }
    }

    /** Records the target each row launches instead of opening a real app. */
    private class RecordingContext(context: Context) : ContextWrapper(context) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
        }
    }

    private fun renderWithIntentRecorder(recorder: RecordingContext) {
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides recorder) {
                AboutDialog(visible = true, appVersion = "3.91.15", onDismiss = {})
            }
        }
    }

    @Test
    fun theRepositoryRowOpensTheRepositoryUrl() {
        val recorder = RecordingContext(context)
        renderWithIntentRecorder(recorder)

        composeRule.onNodeWithText("Alaa91H/NexaFlow").performClick()

        val started = recorder.started.single()
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://github.com/Alaa91H/NexaFlow", started.data.toString())
    }

    @Test
    fun theEmailRowAddressesTheDeveloper() {
        val recorder = RecordingContext(context)
        renderWithIntentRecorder(recorder)

        composeRule.onNodeWithText(string(R.string.about_email)).performClick()

        val started = recorder.started.single()
        assertEquals(Intent.ACTION_SENDTO, started.action)
        assertEquals("mailto:alahus2591@gmail.com", started.data.toString())
    }

    @Test
    fun projectSurfacesAndDeveloperContactsAreListed() {
        render()

        listOf(
            R.string.about_developer,
            R.string.about_github,
            R.string.about_email,
            R.string.about_telegram,
            R.string.about_repository,
            R.string.about_channel,
            R.string.about_community,
            R.string.about_support,
        ).forEach { key ->
            composeRule.onNodeWithText(string(key)).assertExists()
        }
    }

    @Test
    fun everyTargetIsNamedWithOrWithoutADialog() {
        // Handles and repository path are literal, never translated, so the
        // rows state exactly where they lead.
        render()
        composeRule.onNodeWithText("@NexaFlowApp").assertExists()
        composeRule.onNodeWithText("@NexaFlowCommunity").assertExists()
        composeRule.onNodeWithText("Alaa91H/NexaFlow").assertExists()
    }

    @Test
    fun hiddenDialogRendersNothing() {
        render(visible = false)

        val assertion = runCatching {
            composeRule.onNodeWithText("NexaFlow").assertExists()
        }
        assertTrue(assertion.isFailure)
    }
}
