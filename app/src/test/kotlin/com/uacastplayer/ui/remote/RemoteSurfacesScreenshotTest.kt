package com.uacastplayer.ui.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.appBackground
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
            UaCastTheme(AppTheme.CINEMA) {
                Column(Modifier.fillMaxSize().appBackground().padding(24.dp)) {
                    RemoteNavigationControls(PhoneRemoteMode.REMOTE) { true }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/phone_remote_dpad.png")
    }
    @Test fun phoneTouchpad() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column(Modifier.fillMaxSize().appBackground().padding(24.dp)) {
                    RemoteNavigationControls(PhoneRemoteMode.TOUCHPAD) { true }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/phone_remote_touchpad.png")
    }
}
