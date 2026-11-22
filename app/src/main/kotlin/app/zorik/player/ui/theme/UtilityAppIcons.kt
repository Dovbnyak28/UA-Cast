package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object UtilityAppIcons {
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

    val Kids: ImageVector by lazy {
        ImageVector.Builder(
            name = "Kids", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 20f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 2f)
                close()
                moveTo(12f, 4f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 18f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 4f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(8.5f, 8f)
                arcTo(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.5f, 10.5f)
                arcTo(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.5f, 8f)
                close()
                moveTo(15.5f, 8f)
                arcTo(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.5f, 10.5f)
                arcTo(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.5f, 8f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(7.3f, 13.2f)
                curveTo(8.6f, 14.7f, 10.1f, 15.4f, 12f, 15.4f)
                curveTo(13.9f, 15.4f, 15.4f, 14.7f, 16.7f, 13.2f)
                lineTo(18.2f, 14.5f)
                curveTo(16.6f, 16.4f, 14.5f, 17.4f, 12f, 17.4f)
                curveTo(9.5f, 17.4f, 7.4f, 16.4f, 5.8f, 14.5f)
                close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
                curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
                curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
                lineTo(17.65f, 14f)
                curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
                curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
                curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
                curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
                lineTo(13f, 11f)
                lineTo(20f, 11f)
                lineTo(20f, 4f)
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

    val Brightness: ImageVector by lazy {
        ImageVector.Builder(
            name = "Brightness", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 8f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 16f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 8f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(11.2f, 3f); lineTo(12.8f, 3f); lineTo(12.8f, 6f); lineTo(11.2f, 6f); close()
                moveTo(11.2f, 18f); lineTo(12.8f, 18f); lineTo(12.8f, 21f); lineTo(11.2f, 21f); close()
                moveTo(18f, 11.2f); lineTo(21f, 11.2f); lineTo(21f, 12.8f); lineTo(18f, 12.8f); close()
                moveTo(3f, 11.2f); lineTo(6f, 11.2f); lineTo(6f, 12.8f); lineTo(3f, 12.8f); close()
                moveTo(18.4f, 6.7f); lineTo(17.3f, 7.8f); lineTo(16.2f, 6.7f); lineTo(17.3f, 5.6f); close()
                moveTo(7.8f, 6.7f); lineTo(6.7f, 7.8f); lineTo(5.6f, 6.7f); lineTo(6.7f, 5.6f); close()
                moveTo(18.4f, 17.3f); lineTo(17.3f, 18.4f); lineTo(16.2f, 17.3f); lineTo(17.3f, 16.2f); close()
                moveTo(7.8f, 17.3f); lineTo(6.7f, 18.4f); lineTo(5.6f, 17.3f); lineTo(6.7f, 16.2f); close()
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Shackle - top half of a ring (same outer/inner-arc EvenOdd technique as Globe/HelpCircle
            // above), its open ends running straight down to meet the body below.
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(8f, 10f)
                lineTo(8f, 9f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 9f)
                lineTo(16f, 10f)
                lineTo(14.2f, 10f)
                lineTo(14.2f, 9f)
                arcTo(2.2f, 2.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.8f, 9f)
                lineTo(9.8f, 10f)
                close()
            }
            // Body with a keyhole cut out via EvenOdd.
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(5f, 10f)
                lineTo(19f, 10f)
                lineTo(19f, 21f)
                lineTo(5f, 21f)
                close()
                moveTo(11.2f, 13.5f)
                arcTo(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 11.2f, 15.1f)
                lineTo(10.6f, 18f)
                lineTo(13.4f, 18f)
                lineTo(12.8f, 15.1f)
                arcTo(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12.8f, 13.5f)
                close()
            }
        }.build()
    }

    val Volume: ImageVector by lazy {
        ImageVector.Builder(
            name = "Volume", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(3f, 9f)
                lineTo(3f, 15f)
                lineTo(7f, 15f)
                lineTo(12f, 20f)
                lineTo(12f, 4f)
                lineTo(7f, 9f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                lineTo(14f, 16.02f)
                curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(14f, 3.23f)
                lineTo(14f, 5.29f)
                curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
                curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
                lineTo(14f, 20.77f)
                curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
                curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
                close()
            }
        }.build()
    }
}
