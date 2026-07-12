package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-authored path vectors for the app's navigation and chrome icons, kept independent of the
 * material-icons-extended artifact.
 */
object AppIcons {

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Home", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(pathFillType = PathFillType.NonZero) {
                moveTo(12f, 3f)
                lineTo(21f, 10.5f)
                lineTo(19.5f, 12.3f)
                lineTo(18f, 11.1f)
                lineTo(18f, 20f)
                lineTo(13.5f, 20f)
                lineTo(13.5f, 14f)
                lineTo(10.5f, 14f)
                lineTo(10.5f, 20f)
                lineTo(6f, 20f)
                lineTo(6f, 11.1f)
                lineTo(4.5f, 12.3f)
                lineTo(3f, 10.5f)
                close()
            }
        }.build()
    }

    val Channels: ImageVector by lazy {
        ImageVector.Builder(
            name = "Channels", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                lineTo(21f, 17f)
                lineTo(3f, 17f)
                close()
                moveTo(5f, 8f)
                lineTo(5f, 15f)
                lineTo(19f, 15f)
                lineTo(19f, 8f)
                close()
            }
            path(pathFillType = PathFillType.NonZero) {
                moveTo(8f, 19f)
                lineTo(16f, 19f)
                lineTo(16f, 21f)
                lineTo(8f, 21f)
                close()
            }
        }.build()
    }

    val Favorites: ImageVector by lazy {
        ImageVector.Builder(
            name = "Favorites", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(pathFillType = PathFillType.NonZero) {
                moveTo(12f, 2.5f)
                lineTo(14.7f, 8.6f)
                lineTo(21.3f, 9.3f)
                lineTo(16.4f, 13.7f)
                lineTo(17.8f, 20.2f)
                lineTo(12f, 16.8f)
                lineTo(6.2f, 20.2f)
                lineTo(7.6f, 13.7f)
                lineTo(2.7f, 9.3f)
                lineTo(9.3f, 8.6f)
                close()
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(pathFillType = PathFillType.EvenOdd) {
                moveTo(10.8f, 2f)
                lineTo(13.2f, 2f)
                lineTo(13.6f, 4.4f)
                lineTo(15.8f, 5.3f)
                lineTo(17.9f, 3.9f)
                lineTo(19.6f, 5.6f)
                lineTo(18.2f, 7.7f)
                lineTo(19.1f, 9.9f)
                lineTo(21.5f, 10.3f)
                lineTo(21.5f, 12.7f)
                lineTo(19.1f, 13.1f)
                lineTo(18.2f, 15.3f)
                lineTo(19.6f, 17.4f)
                lineTo(17.9f, 19.1f)
                lineTo(15.8f, 17.7f)
                lineTo(13.6f, 18.6f)
                lineTo(13.2f, 21f)
                lineTo(10.8f, 21f)
                lineTo(10.4f, 18.6f)
                lineTo(8.2f, 17.7f)
                lineTo(6.1f, 19.1f)
                lineTo(4.4f, 17.4f)
                lineTo(5.8f, 15.3f)
                lineTo(4.9f, 13.1f)
                lineTo(2.5f, 12.7f)
                lineTo(2.5f, 10.3f)
                lineTo(4.9f, 9.9f)
                lineTo(5.8f, 7.7f)
                lineTo(4.4f, 5.6f)
                lineTo(6.1f, 3.9f)
                lineTo(8.2f, 5.3f)
                lineTo(10.4f, 4.4f)
                close()
                moveTo(12f, 10.3f)
                arcTo(1.7f, 1.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 13.7f)
                arcTo(1.7f, 1.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 10.3f)
                close()
            }
        }.build()
    }
}
