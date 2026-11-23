package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-authored path vectors for the app's navigation and chrome icons - the app has no dependency
 * on any bundled Material icon set at all (see E4 in the optimization plan).
 */
object AppIcons {
    val Remote: ImageVector by lazy {
        ImageVector.Builder("Remote", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(8f, 1f); lineTo(16f, 1f); lineTo(18f, 3f); lineTo(18f, 21f)
                lineTo(16f, 23f); lineTo(8f, 23f); lineTo(6f, 21f); lineTo(6f, 3f); close()
                moveTo(8f, 3f); lineTo(16f, 3f); lineTo(16f, 21f); lineTo(8f, 21f); close()
                moveTo(11f, 6f); lineTo(13f, 6f); lineTo(13f, 9f); lineTo(16f, 9f)
                lineTo(16f, 11f); lineTo(13f, 11f); lineTo(13f, 14f); lineTo(11f, 14f)
                lineTo(11f, 11f); lineTo(8f, 11f); lineTo(8f, 9f); lineTo(11f, 9f); close()
                moveTo(10f, 17f); lineTo(14f, 17f); lineTo(14f, 19f); lineTo(10f, 19f); close()
            }
        }.build()
    }
    val Home: ImageVector get() = PrimaryAppIcons.Home
    val Channels: ImageVector get() = PrimaryAppIcons.Channels
    val Favorites: ImageVector get() = PrimaryAppIcons.Favorites
    val Settings: ImageVector get() = PrimaryAppIcons.Settings
    val ArrowBack: ImageVector get() = PrimaryAppIcons.ArrowBack
    val Play: ImageVector get() = PrimaryAppIcons.Play
    val Pause: ImageVector get() = PrimaryAppIcons.Pause
    val SkipNext: ImageVector get() = PrimaryAppIcons.SkipNext
    val SkipPrevious: ImageVector get() = PrimaryAppIcons.SkipPrevious
    val Fullscreen: ImageVector get() = PrimaryAppIcons.Fullscreen
    val FullscreenExit: ImageVector get() = PrimaryAppIcons.FullscreenExit

    val Globe: ImageVector get() = MediaAppIcons.Globe
    val Tv: ImageVector get() = MediaAppIcons.Tv
    val Image: ImageVector get() = MediaAppIcons.Image
    val ViewList: ImageVector get() = MediaAppIcons.ViewList
    val GridView: ImageVector get() = MediaAppIcons.GridView
    val LargeIcons: ImageVector get() = MediaAppIcons.LargeIcons
    val Storage: ImageVector get() = MediaAppIcons.Storage
    val HelpCircle: ImageVector get() = MediaAppIcons.HelpCircle

    val Upload: ImageVector get() = ActionAppIcons.Upload
    val Search: ImageVector get() = ActionAppIcons.Search
    val Delete: ImageVector get() = ActionAppIcons.Delete
    val Check: ImageVector get() = ActionAppIcons.Check
    val Close: ImageVector get() = ActionAppIcons.Close
    val ChevronDown: ImageVector get() = ActionAppIcons.ChevronDown
    val Plus: ImageVector get() = ActionAppIcons.Plus
    val Minus: ImageVector get() = ActionAppIcons.Minus
    val Sort: ImageVector get() = ActionAppIcons.Sort
    val More: ImageVector get() = ActionAppIcons.More

    val Timer: ImageVector get() = UtilityAppIcons.Timer
    val Kids: ImageVector get() = UtilityAppIcons.Kids
    val Refresh: ImageVector get() = UtilityAppIcons.Refresh
    val PictureInPicture: ImageVector get() = UtilityAppIcons.PictureInPicture
    val Brightness: ImageVector get() = UtilityAppIcons.Brightness
    val Lock: ImageVector get() = UtilityAppIcons.Lock
    val Volume: ImageVector get() = UtilityAppIcons.Volume
    val AudioTrack: ImageVector get() = UtilityAppIcons.Volume
    val Subtitles: ImageVector get() = PlayerAppIcons.Subtitles
    val Quality: ImageVector get() = PlayerAppIcons.Quality
    val AspectRatio: ImageVector get() = PlayerAppIcons.AspectRatio
    val Guide: ImageVector get() = PlayerAppIcons.Guide

    val Movie: ImageVector get() = CategoryAppIcons.Movie
    val News: ImageVector get() = CategoryAppIcons.News
    val Sports: ImageVector get() = CategoryAppIcons.Sports
    val Music: ImageVector get() = CategoryAppIcons.Music
}
