package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Player-specific glyphs whose meaning should be recognizable without reading the label. */
internal object PlayerAppIcons {

    val Subtitles: ImageVector by lazy {
        ImageVector.Builder(
            name = "Subtitles",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
                moveTo(5f, 7f); lineTo(5f, 17f); lineTo(19f, 17f); lineTo(19f, 7f); close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(6.5f, 11f); lineTo(11.5f, 11f); lineTo(11.5f, 13f); lineTo(6.5f, 13f); close()
                moveTo(13f, 11f); lineTo(17.5f, 11f); lineTo(17.5f, 13f); lineTo(13f, 13f); close()
                moveTo(6.5f, 14f); lineTo(10f, 14f); lineTo(10f, 16f); lineTo(6.5f, 16f); close()
                moveTo(11.5f, 14f); lineTo(17.5f, 14f); lineTo(17.5f, 16f); lineTo(11.5f, 16f); close()
            }
        }.build()
    }

    val Quality: ImageVector by lazy {
        ImageVector.Builder(
            name = "Quality",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(3f, 18f); close()
                moveTo(5f, 8f); lineTo(5f, 16f); lineTo(19f, 16f); lineTo(19f, 8f); close()
            }
            // Compact "HD" mark.
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(7f, 9.5f); lineTo(8.5f, 9.5f); lineTo(8.5f, 11.3f); lineTo(10.5f, 11.3f)
                lineTo(10.5f, 9.5f); lineTo(12f, 9.5f); lineTo(12f, 14.5f); lineTo(10.5f, 14.5f)
                lineTo(10.5f, 12.7f); lineTo(8.5f, 12.7f); lineTo(8.5f, 14.5f); lineTo(7f, 14.5f); close()
                moveTo(13f, 9.5f); lineTo(15.5f, 9.5f); curveTo(17.2f, 9.5f, 18f, 10.4f, 18f, 12f)
                curveTo(18f, 13.6f, 17.2f, 14.5f, 15.5f, 14.5f); lineTo(13f, 14.5f); close()
                moveTo(14.5f, 10.9f); lineTo(14.5f, 13.1f); lineTo(15.5f, 13.1f)
                curveTo(16.2f, 13.1f, 16.5f, 12.8f, 16.5f, 12f)
                curveTo(16.5f, 11.2f, 16.2f, 10.9f, 15.5f, 10.9f); close()
            }
        }.build()
    }

    val AspectRatio: ImageVector by lazy {
        ImageVector.Builder(
            name = "AspectRatio",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(3f, 6f); lineTo(9f, 6f); lineTo(9f, 8f); lineTo(5f, 8f)
                lineTo(5f, 11f); lineTo(3f, 11f); close()
                moveTo(15f, 6f); lineTo(21f, 6f); lineTo(21f, 11f); lineTo(19f, 11f); lineTo(19f, 8f)
                lineTo(15f, 8f); close()
                moveTo(3f, 13f); lineTo(5f, 13f); lineTo(5f, 16f); lineTo(9f, 16f); lineTo(9f, 18f)
                lineTo(3f, 18f); close()
                moveTo(19f, 13f); lineTo(21f, 13f); lineTo(21f, 18f); lineTo(15f, 18f); lineTo(15f, 16f)
                lineTo(19f, 16f); close()
            }
        }.build()
    }

    val Guide: ImageVector by lazy {
        ImageVector.Builder(
            name = "Guide",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 21f); lineTo(4f, 21f); close()
                moveTo(6f, 9f); lineTo(6f, 19f); lineTo(18f, 19f); lineTo(18f, 9f); close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(7f, 2f); lineTo(9f, 2f); lineTo(9f, 6f); lineTo(7f, 6f); close()
                moveTo(15f, 2f); lineTo(17f, 2f); lineTo(17f, 6f); lineTo(15f, 6f); close()
                moveTo(8f, 11f); lineTo(11f, 11f); lineTo(11f, 14f); lineTo(8f, 14f); close()
                moveTo(13f, 11f); lineTo(16f, 11f); lineTo(16f, 14f); lineTo(13f, 14f); close()
                moveTo(8f, 15f); lineTo(11f, 15f); lineTo(11f, 18f); lineTo(8f, 18f); close()
                moveTo(13f, 15f); lineTo(16f, 15f); lineTo(16f, 18f); lineTo(13f, 18f); close()
            }
        }.build()
    }
}
