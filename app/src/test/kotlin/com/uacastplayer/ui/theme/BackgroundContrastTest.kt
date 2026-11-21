package com.uacastplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.uacastplayer.testing.RequiresComposeTestManifest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Decorative wallpaper must never tint the actual text/control/video layers above it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
@OptIn(ExperimentalRoborazziApi::class)
class BackgroundContrastTest {
    @get:Rule val rule = createComposeRule()

    @Test fun azureDecorationsStayBehindOpaqueContent() = assertUntinted(AppTheme.AZURE)
    @Test fun cinemaDecorationsStayBehindOpaqueContent() = assertUntinted(AppTheme.CINEMA)
    @Test fun midnightStillUsesTheFlatPath() = assertUntinted(AppTheme.MIDNIGHT)

    private fun assertUntinted(theme: AppTheme) {
        rule.setContent {
            UaCastTheme(theme) {
                Box(Modifier.size(200.dp).appBackground().testTag("background")) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                }
            }
        }
        val file = File("build/ui-polish/background-${theme.name}.png")
        file.parentFile?.mkdirs()
        // This is a pixel assertion, not a golden update: only a disposable build/ file is recorded.
        rule.onNodeWithTag("background").captureRoboImage(file.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val bitmap = checkNotNull(android.graphics.BitmapFactory.decodeFile(file.path))
        try {
            assertEquals("Ambient glow must not reduce foreground contrast", android.graphics.Color.WHITE,
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        } finally {
            bitmap.recycle()
        }
    }
}
