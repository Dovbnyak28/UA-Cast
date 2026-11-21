package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object MediaAppIcons {
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
                // (12, 4.2) and (12, 19.8) sit 7.8 from the center - the arc radius must match,
                // or the "inner circle" degenerates into a narrow lens and EvenOdd fills the ring
                // into a nearly solid disc (illegible at banner/tab sizes).
                arcTo(7.8f, 7.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 19.8f)
                arcTo(7.8f, 7.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 4.2f)
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
                // Same ring geometry note as Globe above: endpoint radius 7.8, so arc radius 7.8.
                arcTo(7.8f, 7.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 19.8f)
                arcTo(7.8f, 7.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 4.2f)
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

}
