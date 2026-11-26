package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Fallback "logo" tones for a channel with no resolved icon: a stable gradient picked from the
 * channel name, with white initials on top. Mid-dark, saturated pairs so the initials keep contrast
 * on every theme (dark Cinema/Midnight and light Daylight alike) and a grid of logo-less channels
 * reads as distinct tiles rather than a wall of identical grey boxes.
 */
internal object ChannelAvatarColors {
    private val pairs = listOf(
        Color(0xFF4F46E5) to Color(0xFF312E81), // indigo
        Color(0xFF0D9488) to Color(0xFF134E4A), // teal
        Color(0xFFE11D48) to Color(0xFF881337), // rose
        Color(0xFFB45309) to Color(0xFF78350F), // amber
        Color(0xFF7C3AED) to Color(0xFF4C1D95), // violet
        Color(0xFF0284C7) to Color(0xFF0C4A6E), // sky
        Color(0xFF059669) to Color(0xFF064E3B), // emerald
        Color(0xFFC026D3) to Color(0xFF701A75), // fuchsia
    )

    val initials: Color = Color.White

    /** Same name, same colour - on every screen and across launches (String.hashCode is specified). */
    fun brushFor(name: String): Brush {
        val (top, bottom) = pairs[Math.floorMod(name.hashCode(), pairs.size)]
        return Brush.linearGradient(listOf(top, bottom))
    }

    /** The [index]th pair, wrapping - for drawings that want several distinct tiles in a row. */
    fun brushAt(index: Int): Brush {
        val (top, bottom) = pairs[Math.floorMod(index, pairs.size)]
        return Brush.linearGradient(listOf(top, bottom))
    }
}

private val RoseOnDark = Color(0xFFFB7185)
private val RoseOnLight = Color(0xFFE11D48)
private val SkyOnDark = Color(0xFF38BDF8)
private val SkyOnLight = Color(0xFF0284C7)
private val EmeraldOnDark = Color(0xFF34D399)
private val EmeraldOnLight = Color(0xFF059669)
private val AmberOnDark = Color(0xFFFBBF24)
private val AmberOnLight = Color(0xFFB45309)
private val VioletOnDark = Color(0xFFA78BFA)
private val VioletOnLight = Color(0xFF7C3AED)
private val IndigoOnDark = Color(0xFF818CF8)
private val IndigoOnLight = Color(0xFF4F46E5)
private val TealOnDark = Color(0xFF2DD4BF)
private val TealOnLight = Color(0xFF0F766E)
private val FuchsiaOnDark = Color(0xFFE879F9)
private val FuchsiaOnLight = Color(0xFFA21CAF)

/**
 * One hue per channel-group category, so the groups grid scans by colour as well as by glyph.
 * Lighter tones on the dark themes and deeper ones on Daylight keep the glyph at a similar contrast
 * against the card either way.
 */
internal object CategoryTones {
    enum class Hue(val onDark: Color, val onLight: Color) {
        Rose(RoseOnDark, RoseOnLight),
        Sky(SkyOnDark, SkyOnLight),
        Emerald(EmeraldOnDark, EmeraldOnLight),
        Amber(AmberOnDark, AmberOnLight),
        Violet(VioletOnDark, VioletOnLight),
        Indigo(IndigoOnDark, IndigoOnLight),
        Teal(TealOnDark, TealOnLight),
        Fuchsia(FuchsiaOnDark, FuchsiaOnLight),
    }

    fun color(hue: Hue, isDark: Boolean): Color = if (isDark) hue.onDark else hue.onLight
}
