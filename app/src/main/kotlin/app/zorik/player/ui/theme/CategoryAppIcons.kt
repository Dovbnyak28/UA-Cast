package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Channel-group category cues, drawn in the same 24-unit, 2-unit-outline style as the rest of the
 * hand-made set, so a grid of groups is not one repeated TV glyph.
 */
internal object CategoryAppIcons {
    /** Clapperboard: a slanted clapper over an outlined board. */
    val Movie: ImageVector by lazy {
        ImageVector.Builder(
            name = "Movie", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 9f); lineTo(21f, 9f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                moveTo(5f, 11f); lineTo(5f, 18f); lineTo(19f, 18f); lineTo(19f, 11f); close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 4.6f); lineTo(19.6f, 2f); lineTo(20.2f, 5.4f); lineTo(3.6f, 8f); close()
                // Two cut-outs give the clapper its stripes.
                moveTo(8f, 4.3f); lineTo(10.2f, 3.95f); lineTo(9.4f, 6.9f); lineTo(7.2f, 7.25f); close()
                moveTo(13.4f, 3.45f); lineTo(15.6f, 3.1f); lineTo(14.8f, 6.05f); lineTo(12.6f, 6.4f); close()
            }
        }.build()
    }

    /** Folded newspaper: outlined page, headline block and text lines. */
    val News: ImageVector by lazy {
        ImageVector.Builder(
            name = "News", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                moveTo(5f, 6f); lineTo(5f, 18f); lineTo(19f, 18f); lineTo(19f, 6f); close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(7f, 8f); lineTo(11.5f, 8f); lineTo(11.5f, 12.5f); lineTo(7f, 12.5f); close()
                moveTo(13f, 8f); lineTo(17f, 8f); lineTo(17f, 9.5f); lineTo(13f, 9.5f); close()
                moveTo(13f, 11f); lineTo(17f, 11f); lineTo(17f, 12.5f); lineTo(13f, 12.5f); close()
                moveTo(7f, 14.5f); lineTo(17f, 14.5f); lineTo(17f, 16f); lineTo(7f, 16f); close()
            }
        }.build()
    }

    /** Two beamed quavers: reads as music, unlike the volume speaker it replaces for this use. */
    val Music: ImageVector by lazy {
        ImageVector.Builder(
            name = "Music", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                // Beam and the two stems.
                moveTo(8f, 6.5f); lineTo(20f, 3f); lineTo(20f, 16.5f); lineTo(18f, 16.5f)
                lineTo(18f, 7.6f); lineTo(10f, 9.9f); lineTo(10f, 18.5f); lineTo(8f, 18.5f); close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                // Note heads.
                moveTo(6.25f, 15.5f)
                arcTo(3f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.25f, 20.3f)
                arcTo(3f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.25f, 15.5f)
                close()
                moveTo(16.25f, 13.5f)
                arcTo(3f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.25f, 18.3f)
                arcTo(3f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.25f, 13.5f)
                close()
            }
        }.build()
    }

    /** Football: outlined ball with a centre pentagon and seams. */
    val Sports: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sports", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 21f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 3f)
                close()
                moveTo(12f, 5f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 19f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 5f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 8.8f); lineTo(15.04f, 11.01f); lineTo(13.88f, 14.59f)
                lineTo(10.12f, 14.59f); lineTo(8.96f, 11.01f); close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 8.8f); lineTo(12f, 5.5f)
                moveTo(15.04f, 11.01f); lineTo(18.2f, 10f)
                moveTo(13.88f, 14.59f); lineTo(15.8f, 17.3f)
                moveTo(10.12f, 14.59f); lineTo(8.2f, 17.3f)
                moveTo(8.96f, 11.01f); lineTo(5.8f, 10f)
            }
        }.build()
    }
}
