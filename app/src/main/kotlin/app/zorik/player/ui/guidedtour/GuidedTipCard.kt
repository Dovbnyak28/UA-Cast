package app.zorik.player.ui.guidedtour

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.guidedtour.TipGlyph
import app.zorik.player.ui.components.PrimaryButton
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.CaptionSemibold
import app.zorik.player.ui.theme.CardTitle
import app.zorik.player.ui.theme.CategoryTones
import app.zorik.player.ui.theme.GapL
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.RadiusCard
import app.zorik.player.ui.theme.UaTheme

private val GlyphBadgeSize = 36.dp
private val GlyphSize = 20.dp

private const val GLYPH_BADGE_ALPHA = 0.16f

/**
 * A tip: a small label, a title, two or three lines each led by a coloured glyph, and "Got it".
 *
 * The lines are short on purpose - a tip is read in the second before the user goes back to what
 * they were doing, and a paragraph would not be.
 */
@Composable
internal fun GuidedTipCard(tip: GuidedTip, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val palette = UaTheme.palette
    val shape = RoundedCornerShape(RadiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface2)
            .border(1.dp, palette.hairline, shape)
            .padding(GapL),
        verticalArrangement = Arrangement.spacedBy(GapM),
    ) {
        // Takes what is left after the button and scrolls past that, so at large font sizes the text
        // gives way and "Got it" stays on screen.
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GapM),
        ) {
            Text(
                text = stringResource(R.string.guided_tip_label).uppercase(),
                style = CaptionSemibold,
                color = palette.accentText,
            )
            Text(
                text = stringResource(tip.titleRes),
                style = CardTitle,
                color = palette.labelPrimary,
                modifier = Modifier.semantics { heading() },
            )
            tip.points.forEach { point ->
                TipLine(glyph = point.glyph, text = stringResource(point.textRes))
            }
        }
        PrimaryButton(
            text = stringResource(R.string.guided_tip_got_it),
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun TipLine(glyph: TipGlyph, text: String) {
    val tint = CategoryTones.color(glyph.hue(), UaTheme.palette.isDark)
    Row(horizontalArrangement = Arrangement.spacedBy(GapM), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(GlyphBadgeSize)
                .clip(CircleShape)
                .background(tint.copy(alpha = GLYPH_BADGE_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(glyph.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(GlyphSize))
        }
        Text(text = text, style = BodyText, color = UaTheme.palette.labelSecondary, modifier = Modifier.weight(1f))
    }
}

/** The Cast line uses the same glyph as the playback notification, so it matches the button. */
@Composable
private fun TipGlyph.icon(): ImageVector = when (this) {
    TipGlyph.SEARCH -> AppIcons.Search
    TipGlyph.FAVORITE -> AppIcons.Favorites
    TipGlyph.TAP -> AppIcons.Fullscreen
    TipGlyph.SWITCH_CHANNEL -> AppIcons.SkipNext
    TipGlyph.LEVELS -> AppIcons.Volume
    TipGlyph.CAST -> ImageVector.vectorResource(R.drawable.ic_notification_cast)
    TipGlyph.OTHER_TV -> AppIcons.Tv
}

private fun TipGlyph.hue(): CategoryTones.Hue = when (this) {
    TipGlyph.SEARCH -> CategoryTones.Hue.Sky
    TipGlyph.FAVORITE -> CategoryTones.Hue.Amber
    TipGlyph.TAP -> CategoryTones.Hue.Violet
    TipGlyph.SWITCH_CHANNEL -> CategoryTones.Hue.Rose
    TipGlyph.LEVELS -> CategoryTones.Hue.Emerald
    TipGlyph.CAST -> CategoryTones.Hue.Teal
    TipGlyph.OTHER_TV -> CategoryTones.Hue.Indigo
}
