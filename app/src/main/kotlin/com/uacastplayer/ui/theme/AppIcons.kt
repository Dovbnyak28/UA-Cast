package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
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
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
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
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
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
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
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
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
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
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
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

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(11f, 4f)
                lineTo(13.1f, 6.1f)
                lineTo(7.7f, 11f)
                lineTo(21f, 11f)
                lineTo(21f, 13f)
                lineTo(7.7f, 13f)
                lineTo(13.1f, 17.9f)
                lineTo(11f, 20f)
                lineTo(3f, 12f)
                close()
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(7f, 4f)
                lineTo(20f, 12f)
                lineTo(7f, 20f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(6f, 4f)
                lineTo(10f, 4f)
                lineTo(10f, 20f)
                lineTo(6f, 20f)
                close()
                moveTo(14f, 4f)
                lineTo(18f, 4f)
                lineTo(18f, 20f)
                lineTo(14f, 20f)
                close()
            }
        }.build()
    }

    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipNext", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(6f, 5f)
                lineTo(15f, 12f)
                lineTo(6f, 19f)
                close()
                moveTo(16f, 5f)
                lineTo(18.5f, 5f)
                lineTo(18.5f, 19f)
                lineTo(16f, 19f)
                close()
            }
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipPrevious", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(18f, 5f)
                lineTo(18f, 19f)
                lineTo(9f, 12f)
                close()
                moveTo(8f, 5f)
                lineTo(5.5f, 5f)
                lineTo(5.5f, 19f)
                lineTo(8f, 19f)
                close()
            }
        }.build()
    }

    val Fullscreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "Fullscreen", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(4f, 4f)
                lineTo(10f, 4f)
                lineTo(10f, 6.5f)
                lineTo(6.5f, 6.5f)
                lineTo(6.5f, 10f)
                lineTo(4f, 10f)
                close()
                moveTo(20f, 4f)
                lineTo(20f, 10f)
                lineTo(17.5f, 10f)
                lineTo(17.5f, 6.5f)
                lineTo(14f, 6.5f)
                lineTo(14f, 4f)
                close()
                moveTo(4f, 14f)
                lineTo(6.5f, 14f)
                lineTo(6.5f, 17.5f)
                lineTo(10f, 17.5f)
                lineTo(10f, 20f)
                lineTo(4f, 20f)
                close()
                moveTo(17.5f, 14f)
                lineTo(20f, 14f)
                lineTo(20f, 20f)
                lineTo(14f, 20f)
                lineTo(14f, 17.5f)
                lineTo(17.5f, 17.5f)
                close()
            }
        }.build()
    }

    val FullscreenExit: ImageVector by lazy {
        ImageVector.Builder(
            name = "FullscreenExit", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(6.5f, 4f)
                lineTo(9f, 4f)
                lineTo(9f, 9f)
                lineTo(4f, 9f)
                lineTo(4f, 6.5f)
                lineTo(6.5f, 6.5f)
                close()
                moveTo(15f, 4f)
                lineTo(17.5f, 4f)
                lineTo(17.5f, 6.5f)
                lineTo(20f, 6.5f)
                lineTo(20f, 9f)
                lineTo(15f, 9f)
                close()
                moveTo(4f, 15f)
                lineTo(9f, 15f)
                lineTo(9f, 20f)
                lineTo(6.5f, 20f)
                lineTo(6.5f, 17.5f)
                lineTo(4f, 17.5f)
                close()
                moveTo(17.5f, 17.5f)
                lineTo(17.5f, 20f)
                lineTo(15f, 20f)
                lineTo(15f, 15f)
                lineTo(20f, 15f)
                lineTo(20f, 17.5f)
                close()
            }
        }.build()
    }

    val Globe: ImageVector by lazy {
        ImageVector.Builder(
            name = "Globe", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 22f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 2f)
                close()
                moveTo(12f, 4.2f)
                arcTo(9.8f, 9.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 19.8f)
                arcTo(9.8f, 9.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 4.2f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(3f, 11f)
                lineTo(21f, 11f)
                lineTo(21f, 13f)
                lineTo(3f, 13f)
                close()
                moveTo(11f, 3f)
                lineTo(13f, 3f)
                lineTo(13f, 21f)
                lineTo(11f, 21f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 4f)
                curveTo(9.8f, 6.8f, 8.6f, 9.3f, 8.6f, 12f)
                curveTo(8.6f, 14.7f, 9.8f, 17.2f, 12f, 20f)
                curveTo(14.2f, 17.2f, 15.4f, 14.7f, 15.4f, 12f)
                curveTo(15.4f, 9.3f, 14.2f, 6.8f, 12f, 4f)
                close()
                moveTo(12f, 6.3f)
                curveTo(13.1f, 8.1f, 13.6f, 10f, 13.6f, 12f)
                curveTo(13.6f, 14f, 13.1f, 15.9f, 12f, 17.7f)
                curveTo(10.9f, 15.9f, 10.4f, 14f, 10.4f, 12f)
                curveTo(10.4f, 10f, 10.9f, 8.1f, 12f, 6.3f)
                close()
            }
        }.build()
    }

    val Tv: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tv", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 16f)
                lineTo(3f, 16f)
                close()
                moveTo(5f, 7f)
                lineTo(5f, 14f)
                lineTo(19f, 14f)
                lineTo(19f, 7f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(9f, 18f)
                lineTo(15f, 18f)
                lineTo(15f, 20f)
                lineTo(9f, 20f)
                close()
                moveTo(7f, 9f)
                lineTo(12.5f, 9f)
                lineTo(12.5f, 10.6f)
                lineTo(7f, 10.6f)
                close()
                moveTo(7f, 11.4f)
                lineTo(11f, 11.4f)
                lineTo(11f, 13f)
                lineTo(7f, 13f)
                close()
            }
        }.build()
    }

    val Image: ImageVector by lazy {
        ImageVector.Builder(
            name = "Image", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 4f)
                lineTo(21f, 4f)
                lineTo(21f, 20f)
                lineTo(3f, 20f)
                close()
                moveTo(5f, 6f)
                lineTo(5f, 18f)
                lineTo(19f, 18f)
                lineTo(19f, 6f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(8.2f, 8.4f)
                arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.2f, 12f)
                arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.2f, 8.4f)
                close()
                moveTo(6.5f, 16.5f)
                lineTo(10.3f, 11.8f)
                lineTo(13f, 15f)
                lineTo(14.7f, 12.8f)
                lineTo(17.7f, 16.5f)
                close()
            }
        }.build()
    }

    val ViewList: ImageVector by lazy {
        ImageVector.Builder(
            name = "ViewList", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(4f, 5.2f)
                lineTo(20f, 5.2f)
                lineTo(20f, 7.2f)
                lineTo(4f, 7.2f)
                close()
                moveTo(4f, 11f)
                lineTo(20f, 11f)
                lineTo(20f, 13f)
                lineTo(4f, 13f)
                close()
                moveTo(4f, 16.8f)
                lineTo(20f, 16.8f)
                lineTo(20f, 18.8f)
                lineTo(4f, 18.8f)
                close()
            }
        }.build()
    }

    val GridView: ImageVector by lazy {
        ImageVector.Builder(
            name = "GridView", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(4f, 4f)
                lineTo(10.5f, 4f)
                lineTo(10.5f, 10.5f)
                lineTo(4f, 10.5f)
                close()
                moveTo(13.5f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 10.5f)
                lineTo(13.5f, 10.5f)
                close()
                moveTo(4f, 13.5f)
                lineTo(10.5f, 13.5f)
                lineTo(10.5f, 20f)
                lineTo(4f, 20f)
                close()
                moveTo(13.5f, 13.5f)
                lineTo(20f, 13.5f)
                lineTo(20f, 20f)
                lineTo(13.5f, 20f)
                close()
            }
        }.build()
    }

    val LargeIcons: ImageVector by lazy {
        ImageVector.Builder(
            name = "LargeIcons", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 3.5f)
                lineTo(20.5f, 20.5f)
                lineTo(3.5f, 20.5f)
                close()
                moveTo(6f, 6f)
                lineTo(6f, 18f)
                lineTo(18f, 18f)
                lineTo(18f, 6f)
                close()
            }
        }.build()
    }

    val Storage: ImageVector by lazy {
        ImageVector.Builder(
            name = "Storage", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 4f)
                lineTo(21f, 4f)
                lineTo(21f, 9f)
                lineTo(3f, 9f)
                close()
                moveTo(3f, 11f)
                lineTo(21f, 11f)
                lineTo(21f, 16f)
                lineTo(3f, 16f)
                close()
                moveTo(3f, 18f)
                lineTo(21f, 18f)
                lineTo(21f, 20f)
                lineTo(3f, 20f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(6f, 6f)
                lineTo(8.4f, 6f)
                lineTo(8.4f, 7f)
                lineTo(6f, 7f)
                close()
                moveTo(6f, 13f)
                lineTo(8.4f, 13f)
                lineTo(8.4f, 14f)
                lineTo(6f, 14f)
                close()
            }
        }.build()
    }

    val HelpCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "HelpCircle", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 22f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 2f)
                close()
                moveTo(12f, 4.2f)
                arcTo(9.8f, 9.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 19.8f)
                arcTo(9.8f, 9.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 4.2f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(10.9f, 16.4f)
                lineTo(13.1f, 16.4f)
                lineTo(13.1f, 18.4f)
                lineTo(10.9f, 18.4f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12.05f, 6.2f)
                curveTo(10.15f, 6.2f, 8.8f, 7.35f, 8.55f, 9.1f)
                lineTo(10.5f, 9.35f)
                curveTo(10.65f, 8.5f, 11.15f, 8f, 12.05f, 8f)
                curveTo(12.85f, 8f, 13.4f, 8.45f, 13.4f, 9.1f)
                curveTo(13.4f, 9.65f, 13.1f, 10f, 12.5f, 10.45f)
                curveTo(11.55f, 11.15f, 11f, 11.75f, 11f, 13f)
                lineTo(11f, 13.4f)
                lineTo(12.9f, 13.4f)
                lineTo(12.9f, 13.1f)
                curveTo(12.9f, 12.25f, 13.2f, 11.9f, 14f, 11.3f)
                curveTo(14.75f, 10.75f, 15.35f, 10.1f, 15.35f, 9f)
                curveTo(15.35f, 7.35f, 13.95f, 6.2f, 12.05f, 6.2f)
                close()
            }
        }.build()
    }

    val Upload: ImageVector by lazy {
        ImageVector.Builder(
            name = "Upload", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(11f, 3.6f)
                lineTo(16f, 8.6f)
                lineTo(14.6f, 10f)
                lineTo(13f, 8.4f)
                lineTo(13f, 15f)
                lineTo(11f, 15f)
                lineTo(11f, 8.4f)
                lineTo(9.4f, 10f)
                lineTo(8f, 8.6f)
                close()
                moveTo(5f, 17f)
                lineTo(19f, 17f)
                lineTo(19f, 19f)
                lineTo(5f, 19f)
                close()
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(10.5f, 3f)
                arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.5f, 18f)
                arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.5f, 3f)
                close()
                moveTo(10.5f, 5.2f)
                arcTo(5.3f, 5.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.5f, 15.8f)
                arcTo(5.3f, 5.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.5f, 5.2f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(15.6f, 14.2f)
                lineTo(21f, 19.6f)
                lineTo(19.6f, 21f)
                lineTo(14.2f, 15.6f)
                close()
            }
        }.build()
    }

    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(7f, 6f)
                lineTo(17f, 6f)
                lineTo(16.2f, 21f)
                lineTo(7.8f, 21f)
                close()
                moveTo(9f, 8.5f)
                lineTo(9.4f, 18.5f)
                lineTo(10.4f, 18.5f)
                lineTo(10f, 8.5f)
                close()
                moveTo(13.6f, 8.5f)
                lineTo(13.2f, 18.5f)
                lineTo(14.2f, 18.5f)
                lineTo(14.6f, 8.5f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(9.5f, 3f)
                lineTo(14.5f, 3f)
                lineTo(15.3f, 5f)
                lineTo(8.7f, 5f)
                close()
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 7f)
                lineTo(4f, 7f)
                close()
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(9.5f, 16.2f)
                lineTo(4.8f, 11.5f)
                lineTo(6.2f, 10.1f)
                lineTo(9.5f, 13.4f)
                lineTo(17.8f, 5.1f)
                lineTo(19.2f, 6.5f)
                close()
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronDown", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 15.5f)
                lineTo(4.5f, 8f)
                lineTo(6.4f, 6.1f)
                lineTo(12f, 11.7f)
                lineTo(17.6f, 6.1f)
                lineTo(19.5f, 8f)
                close()
            }
        }.build()
    }

    val Sort: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sort", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(6f, 4f)
                lineTo(8f, 4f)
                lineTo(8f, 16.2f)
                lineTo(10.6f, 13.6f)
                lineTo(12f, 15f)
                lineTo(7f, 20f)
                lineTo(2f, 15f)
                lineTo(3.4f, 13.6f)
                lineTo(6f, 16.2f)
                close()
                moveTo(14f, 4f)
                lineTo(22f, 4f)
                lineTo(22f, 6f)
                lineTo(14f, 6f)
                close()
                moveTo(14f, 9f)
                lineTo(20f, 9f)
                lineTo(20f, 11f)
                lineTo(14f, 11f)
                close()
                moveTo(14f, 14f)
                lineTo(18f, 14f)
                lineTo(18f, 16f)
                lineTo(14f, 16f)
                close()
            }
        }.build()
    }

    val Timer: ImageVector by lazy {
        ImageVector.Builder(
            name = "Timer", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 4f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 20f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 4f)
                close()
                moveTo(12f, 6f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 18f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 6f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(11.25f, 8f)
                lineTo(12.75f, 8f)
                lineTo(12.75f, 12.4f)
                lineTo(15.3f, 13.9f)
                lineTo(14.55f, 15.2f)
                lineTo(11.25f, 13.2f)
                close()
            }
        }.build()
    }

    val PictureInPicture: ImageVector by lazy {
        ImageVector.Builder(
            name = "PictureInPicture", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(2f, 5f)
                lineTo(22f, 5f)
                lineTo(22f, 19f)
                lineTo(2f, 19f)
                close()
                moveTo(4f, 7f)
                lineTo(4f, 17f)
                lineTo(20f, 17f)
                lineTo(20f, 7f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 11f)
                lineTo(19f, 11f)
                lineTo(19f, 16f)
                lineTo(12f, 16f)
                close()
            }
        }.build()
    }
}
