package app.zorik.player.ui.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.appBackground
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class RemoteSurfacesScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Test fun phoneDpad() {
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                Column(Modifier.fillMaxSize().appBackground().padding(24.dp)) {
                    RemoteNavigationControls(PhoneRemoteMode.REMOTE) { true }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/phone_remote_dpad.png")
    }
    @Test fun phoneTouchpad() {
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                Column(Modifier.fillMaxSize().appBackground().padding(24.dp)) {
                    RemoteNavigationControls(PhoneRemoteMode.TOUCHPAD) { true }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/phone_remote_touchpad.png")
    }
}
