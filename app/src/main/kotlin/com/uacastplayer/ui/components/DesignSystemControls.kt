package com.uacastplayer.ui.components
import com.uacastplayer.ui.tv.tvFocus
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.darken
import com.uacastplayer.ui.theme.pressedSurface
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.sunkenSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.uacastplayer.ui.theme.ButtonLabel
import androidx.compose.material3.MaterialTheme
import com.uacastplayer.ui.theme.PillText
import com.uacastplayer.ui.theme.PRESS_SCALE_ICON
import com.uacastplayer.ui.theme.PRESS_SCALE_PLAY
import com.uacastplayer.ui.theme.PRESS_SCALE_ROUND
import com.uacastplayer.ui.theme.PlayButtonSize
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RoundButtonSize
import androidx.compose.ui.unit.Dp
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.SecondaryButtonStyle
import com.uacastplayer.ui.theme.TabLabel
import com.uacastplayer.ui.theme.TouchTargetMin

private const val GHOST_BUTTON_PRESSED_ALPHA = 0.12f
private const val DISABLED_CONTROL_ALPHA = 0.38f
internal const val PILL_SHAPE_PERCENT = 50

/** Full-strength app action, replacing theme-dependent Material Button chrome. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = if (UaTheme.palette.pillButtons) {
        RoundedCornerShape(PILL_SHAPE_PERCENT)
    } else {
        RoundedCornerShape(RadiusItem)
    }
    Row(
        modifier = modifier
            .heightIn(min = TouchTargetMin)
            .controlFeedback(pressed, PRESS_SCALE_ROUND, enabled)
            .let { base ->
                if (enabled) {
                    base.raisedSurface(shape, UaTheme.palette.accentGradient, shadow = false)
                } else {
                    base.raisedSurface(shape, UaTheme.palette.surface2, shadow = false)
                }
            }
            .tvFocus(shape, enabled).clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (enabled) UaTheme.palette.accentOnFill else UaTheme.palette.labelTertiary
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
        Text(
            text = text,
            style = ButtonLabel,
            color = contentColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = if (leadingIcon != null) Modifier.padding(start = 8.dp) else Modifier,
        )
    }
}

/**
 * Colors for `OutlinedTextField` that give it the app's "sunken"/recessed input look and an
 * accent-colored focus indicator, instead of Material3's defaults - see docs/DESIGN_SYSTEM.md
 * "§D Depth". `TextFieldColors`' container is a flat `Color`, not a `Brush`, so this can't reuse
 * [sunkenSurface]'s actual gradient directly - [darken] alone (the same fraction sunkenSurface
 * uses) gives the equivalent flat "darker than the surrounding surface" tone instead.
 */
@Composable
fun uaTextFieldColors(): TextFieldColors {
    val palette = UaTheme.palette
    val container = darken(palette.surface1, palette.surfaceLiftAmount)
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        focusedBorderColor = palette.azure,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = palette.azure,
        focusedTextColor = palette.labelPrimary,
        unfocusedTextColor = palette.labelPrimary,
        focusedLabelColor = palette.azure,
        unfocusedLabelColor = palette.labelSecondary,
    )
}

/** §5.1 - the main circular play/pause control. */
@Composable
fun GradientPlayButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(PlayButtonSize)
            .alpha(if (enabled) 1f else DISABLED_CONTROL_ALPHA)
            .controlFeedback(pressed, PRESS_SCALE_PLAY, enabled)
            // One of the three places in the app allowed to glow - see docs/DESIGN_SYSTEM.md "§D
            // Depth". The edge-highlight border below is the same raisedSurface(fill = Brush)
            // treatment used elsewhere, kept manual here since the glow shadow needs its own
            // spotColor that raisedSurface's shared shadowSoft tone doesn't provide.
            .then(if (com.uacastplayer.ui.theme.LocalReducedEffects.current) Modifier else
                Modifier.shadow(10.dp, CircleShape, spotColor = UaTheme.palette.azureGlow))
            .clip(CircleShape)
            .background(UaTheme.palette.accentGradient)
            .border(1.dp, UaTheme.palette.edgeHighlightAccent, CircleShape)
            .tvFocus(CircleShape, enabled)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = UaTheme.palette.accentOnFill,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** §5.2 - secondary round icon button (prev/next). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClickLabel: String? = contentDescription,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(RoundButtonSize)
            .alpha(if (enabled) 1f else DISABLED_CONTROL_ALPHA)
            .controlFeedback(pressed, PRESS_SCALE_ROUND, enabled)
            .raisedSurface(CircleShape, pressedSurface(UaTheme.palette.surface1, pressed), shadow = false)
            .tvFocus(CircleShape, enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = UaTheme.palette.labelPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** App-bar-scale round icon button, same press behaviour as [RoundIconButton] at [IconButtonSize]. */
@Composable
fun SmallRoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClickLabel: String? = contentDescription,
    tint: Color = UaTheme.palette.labelPrimary,
    background: Color = UaTheme.palette.surface1,
    /** The glyph inside the circle. Raise it for an icon that has to hold its own beside the
     * platform Cast button, whose `MediaRouteButton` draws at its own scale and is noticeably
     * heavier than [SmallIconGlyphSize] - see `PlayerCastButton`. */
    iconSize: Dp = SmallIconGlyphSize,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(IconButtonSize)
            .controlFeedback(pressed, PRESS_SCALE_ICON)
            .raisedSurface(CircleShape, pressedSurface(background, pressed), shadow = false)
            .tvFocus(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Default glyph size inside [SmallRoundIconButton]'s [IconButtonSize] circle. */
val SmallIconGlyphSize = 20.dp

/** For a glyph sitting next to the platform Cast button - see [SmallRoundIconButton]'s iconSize. */
val CastPeerIconGlyphSize = 24.dp

enum class StatusPillVariant { Good, Proxy, Bad }

/** §5.3 - small rounded status label. */
@Composable
fun StatusPill(text: String, variant: StatusPillVariant, modifier: Modifier = Modifier) {
    val colors = when (variant) {
        StatusPillVariant.Good -> UaTheme.colors.success
        StatusPillVariant.Proxy -> UaTheme.colors.warning
        StatusPillVariant.Bad -> UaTheme.colors.danger
    }
    Text(
        text = text,
        style = PillText,
        color = colors.onContainer,
        modifier = modifier
            .clip(RoundedCornerShape(PILL_SHAPE_PERCENT))
            .background(colors.container)
            .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(PILL_SHAPE_PERCENT))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** §5.5 - a status dot with a soft glow matching route health. */
@Composable
fun GlowStatusDot(
    variant: StatusPillVariant,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = com.uacastplayer.ui.theme.StatusDotSize,
) {
    val color = when (variant) {
        StatusPillVariant.Good -> UaTheme.palette.routeGreen
        StatusPillVariant.Proxy -> UaTheme.palette.routeAmber
        StatusPillVariant.Bad -> UaTheme.palette.routeRed
    }
    val glow = when (variant) {
        StatusPillVariant.Good -> UaTheme.palette.greenGlow
        StatusPillVariant.Proxy -> UaTheme.palette.amberGlow
        StatusPillVariant.Bad -> UaTheme.palette.redGlow
    }
    Box(
        modifier = modifier
            .size(size + 8.dp)
            .background(glow.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(size).background(color, CircleShape))
    }
}

/** §5.6 - thin non-interactive progress track used inside list rows, and a bold interactive variant for the player. */
@Composable
fun TrackProgress(progress: Float, modifier: Modifier = Modifier, bold: Boolean = false) {
    val height = if (bold) {
        com.uacastplayer.ui.theme.ProgressHeightBold
    } else {
        com.uacastplayer.ui.theme.ProgressHeightThin
    }
    Box(
        // flat by design: a progress track reads as a groove the fill moves along, not raised
        // chrome - and it's used inside list rows, where shadows are forbidden regardless.
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(PILL_SHAPE_PERCENT))
            .background(UaTheme.palette.surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(PILL_SHAPE_PERCENT))
                .background(UaTheme.palette.accentGradient),
        )
    }
}

/**
 * §5.7 - secondary text-only action button (export/import, "open" links). Use instead of
 * Material's OutlinedButton. Shape and fill follow the active palette: `pillButtons` swaps the
 * rounded-rect corner for a full pill, and `secondaryButtonStyle` picks RAISED (Azure - solid
 * surface fill, hairline border) or GHOST (Cinema - transparent fill, gold border) - see
 * docs/DESIGN_SYSTEM.md "Themes".
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val palette = UaTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = if (palette.pillButtons) {
        RoundedCornerShape(PILL_SHAPE_PERCENT)
    } else {
        RoundedCornerShape(RadiusItem)
    }
    val isGhost = palette.secondaryButtonStyle == SecondaryButtonStyle.GHOST
    Box(
        modifier = modifier
            .heightIn(min = TouchTargetMin)
            .controlFeedback(pressed, PRESS_SCALE_ROUND, enabled)
            .let { m ->
                if (isGhost) {
                    val ghostBackground = if (pressed) {
                        palette.azure.copy(alpha = GHOST_BUTTON_PRESSED_ALPHA)
                    } else {
                        Color.Transparent
                    }
                    m.clip(shape).background(ghostBackground).border(1.dp, palette.azure, shape)
                } else {
                    m.raisedSurface(shape, pressedSurface(palette.surface1, pressed), shadow = false)
                }
            }
            .tvFocus(shape, enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) UaTheme.palette.labelPrimary else UaTheme.palette.labelTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                style = ButtonLabel,
                color = if (enabled) UaTheme.palette.labelPrimary else UaTheme.palette.labelTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = if (leadingIcon != null) Modifier.padding(start = 8.dp) else Modifier,
            )
        }
    }
}

/** Labels sit on the dock; only the selected icon has a tonal indicator. */
@Composable
fun TabBarLabel(text: String, selected: Boolean) {
    Text(
        text = text,
        style = TabLabel,
        color = if (selected) UaTheme.palette.labelPrimary else UaTheme.palette.labelSecondary,
        maxLines = 2,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}
