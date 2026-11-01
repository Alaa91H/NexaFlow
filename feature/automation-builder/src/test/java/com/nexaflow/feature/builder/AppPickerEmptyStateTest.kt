package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalValueKind
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeSchemaField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app picker is a bottom sheet, not a dialog, and it is the only way a
 * package field can be filled. Rendering it with an empty launcher list is the
 * state a fresh install or a filtered device runs into, so the sheet must say
 * why it is empty instead of opening a blank panel.
 *
 * Robolectric installs no launcher activities, which is exactly the empty case
 * these tests need.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class AppPickerEmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int) = context.getString(resId)

    private fun host(content: @Composable () -> Unit) {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }

    private fun packageField(collection: Boolean): NodeSchemaField = NodeSchemaField(
        id = CanonicalFieldId(if (collection) "packages" else "package"),
        type = if (collection) NodeFieldType.COLLECTION else NodeFieldType.PACKAGE_ID,
        collectionElementKind = if (collection) CanonicalValueKind.PACKAGE_ID else null,
    )

    /**
     * Registers one launcher activity so the sheet has exactly one app to offer.
     *
     * `addResolveInfoForIntent` is deprecated in Robolectric 4.17 in favour of
     * installing a whole [android.content.pm.PackageInfo]; registering a single
     * resolve target is all this test needs, so the deprecated overload stays.
     */
    @Suppress("DEPRECATION")
    private fun installFakeLauncherApp(
        packageName: String = "com.example.fake",
        label: String = "Fake App",
    ) {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
            flags = 0
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = "$packageName.MainActivity"
            this.applicationInfo = appInfo
        }
        val resolveInfo = ResolveInfo().apply { this.activityInfo = activityInfo }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            resolveInfo,
        )
    }

    @Test
    fun pickingOneAppWritesItsPackage() {
        installFakeLauncherApp()
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = packageField(collection = false),
                schemaTarget = null,
                rawValue = "",
                showLabel = true,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onAllNodesWithText(string(R.string.canonical_choose_app)).onFirst().performClick()
        composeRule.onNodeWithText("Fake App").performClick()
        composeRule.onNodeWithText(string(R.string.ok)).performClick()

        assertEquals("com.example.fake", latest)
    }

    @Test
    fun confirmingAMultiSelectionWritesTheJoinedPackages() {
        installFakeLauncherApp()
        var latest: String? = null
        host {
            CanonicalFieldControl(
                field = packageField(collection = true),
                schemaTarget = null,
                rawValue = "",
                showLabel = false,
                hasParseError = false,
                onValueChange = { latest = it },
            )
        }

        composeRule.onAllNodesWithText(string(R.string.choose_apps)).onFirst().performClick()
        composeRule.onNodeWithText("Fake App").performClick()
        composeRule.onNodeWithText("${string(R.string.ok)} (1)").performClick()

        assertEquals("com.example.fake", latest)
    }

    @Test
    fun theSheetExplainsAnEmptyLauncherList() {
        composeRule.setContent {
            AppPickerDialog(onPickSingle = {}, onDismiss = {})
        }

        composeRule.onNodeWithText(string(R.string.canonical_no_apps_found)).assertExists()
    }

    @Test
    fun packageFieldOpensTheSheetAndShowsTheEmptyState() {
        host {
            CanonicalFieldControl(
                field = packageField(collection = false),
                schemaTarget = null,
                rawValue = "",
                showLabel = true,
                hasParseError = false,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.canonical_choose_app)).performClick()

        composeRule.onAllNodesWithText(string(R.string.canonical_no_apps_found)).onFirst().assertExists()
    }

    @Test
    fun packageCollectionOpensTheSheetInMultiSelectMode() {
        host {
            CanonicalFieldControl(
                field = packageField(collection = true),
                schemaTarget = null,
                rawValue = "",
                showLabel = false,
                hasParseError = false,
                onValueChange = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.choose_apps)).performClick()

        // The multi-select sheet titles itself with the same string as the
        // button that opened it, so both nodes are present.
        composeRule.onAllNodesWithText(string(R.string.choose_apps)).onFirst().assertExists()
        composeRule.onAllNodesWithText(string(R.string.canonical_no_apps_found)).onFirst().assertExists()
    }
}
