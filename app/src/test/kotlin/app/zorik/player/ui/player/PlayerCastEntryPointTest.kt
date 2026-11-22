package app.zorik.player.ui.player

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.R
import app.zorik.player.player.PlayerUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerCastEntryPointTest {
    @get:Rule val rule = createComposeRule()

    @Test fun castAndDlnaAreSeparateTopLevelControlsEvenAtDoubleFontScale() {
        var dlnaOpens = 0
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.fillMaxSize()) {
                        val channel = M3uChannel("Channel", "https://example.test")
                        PlayerOverlayHeader(PlayerUiState(currentChannel = channel),
                            {}, false, { dlnaOpens++ }, {})
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cast = rule.onNodeWithContentDescription(context.getString(R.string.player_chromecast_cast))
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val dlna = rule.onNodeWithContentDescription(context.getString(R.string.dlna_sheet_title))
        val bounds = dlna.assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(cast.right - cast.left >= 48.dp && cast.bottom - cast.top >= 48.dp)
        assertTrue(cast.right <= bounds.left && bounds.right <= viewport.right)
        dlna.performClick()
        assertEquals(1, dlnaOpens)
    }
}
