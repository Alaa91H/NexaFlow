package com.nexaflow.feature.themes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.GlassEffectsMode
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.datastore.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Themes screen writes through [ThemeViewModel]; every control there has to
 * reach the store, including the new Glass effects selector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeViewModelTest {

    private lateinit var preferences: ThemePreferences
    private lateinit var viewModel: ThemeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = ThemePreferences(context)
        viewModel = ThemeViewModel(preferences)
        runBlocking {
            preferences.setThemeMode(ThemeMode.SYSTEM)
            preferences.setAccent("ink")
            preferences.setDynamicColor(false)
            preferences.setGlassEffects(GlassEffectsMode.AUTO)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun glassEffectsSelectionReachesTheStore() = runBlocking {
        viewModel.setGlassEffects(GlassEffectsMode.OFF)

        val settings = withTimeout(5_000) {
            preferences.theme.first { it.glassEffects == GlassEffectsMode.OFF }
        }
        assertEquals(GlassEffectsMode.OFF, settings.glassEffects)
    }

    @Test
    fun everyAppearanceControlWritesThrough() = runBlocking {
        viewModel.setThemeMode(ThemeMode.LIGHT)
        viewModel.setDynamicColor(true)
        viewModel.setGlassEffects(GlassEffectsMode.ON)

        val settings = withTimeout(5_000) {
            preferences.theme.first { it.mode == ThemeMode.LIGHT && it.dynamicColor }
        }
        assertEquals(ThemeMode.LIGHT, settings.mode)
        assertEquals(true, settings.dynamicColor)
        assertEquals(ThemeMode.LIGHT, viewModel.theme.first().mode)
    }
}
