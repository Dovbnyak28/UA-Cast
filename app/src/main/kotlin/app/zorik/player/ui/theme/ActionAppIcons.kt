package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ActionAppIcons {
    val More: ImageVector by lazy {
        ImageVector.Builder(
            name = "More", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                listOf(5f, 12f, 19f).forEach { x ->
                    moveTo(x + 2f, 12f)
                    arcTo(2f, 2f, 0f, false, true, x - 2f, 12f)
                    arcTo(2f, 2f, 0f, false, true, x + 2f, 12f)
                    close()
                }
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

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
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

    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "Plus", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(11f, 4f)
                lineTo(13f, 4f)
                lineTo(13f, 11f)
                lineTo(20f, 11f)
                lineTo(20f, 13f)
                lineTo(13f, 13f)
                lineTo(13f, 20f)
                lineTo(11f, 20f)
                lineTo(11f, 13f)
                lineTo(4f, 13f)
                lineTo(4f, 11f)
                lineTo(11f, 11f)
                close()
            }
        }.build()
    }

    val Minus: ImageVector by lazy {
        ImageVector.Builder(
            name = "Minus", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(4f, 11f)
                lineTo(20f, 11f)
                lineTo(20f, 13f)
                lineTo(4f, 13f)
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

}
