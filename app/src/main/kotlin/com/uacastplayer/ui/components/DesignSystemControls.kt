package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.darken
import com.uacastplayer.ui.theme.pressedSurface
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.sunkenSurface

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.DUR_PRESS
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.PillText
import com.uacastplayer.ui.theme.PRESS_SCALE_ICON
import com.uacastplayer.ui.theme.PRESS_SCALE_PLAY
import com.uacastplayer.ui.theme.PRESS_SCALE_ROUND
import com.uacastplayer.ui.theme.PlayButtonSize
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RadiusSeg
import com.uacastplayer.ui.theme.RadiusSegInner
import com.uacastplayer.ui.theme.RoundButtonSize
import androidx.compose.ui.unit.Dp
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.SecondaryButtonStyle
import com.uacastplayer.ui.theme.TabLabel
import com.uacastplayer.ui.theme.TouchTargetMin
import com.uacastplayer.ui.theme.UaCastTheme
private const val GHOST_BUTTON_PRESSED_ALPHA = 0.12f
private const val PILL_SHAPE_PERCENT = 50
// The app is dark-only (see UaCastTheme's KDoc) - previews below use this instead of Studio's
// default white canvas so raisedSurface/sunkenSurface depth cues are visible at a glance.
private const val PREVIEW_BACKGROUND = 0xFF0B0B12L

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
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE_ROUND else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "primaryButtonScale",
    )
    val shape = if (UaTheme.palette.pillButtons) {
        RoundedCornerShape(PILL_SHAPE_PERCENT)
    } else {
        RoundedCornerShape(RadiusItem)
    }
    Row(
        modifier = modifier
            .heightIn(min = TouchTargetMin)
            .scale(scale)
            .let { base ->
                if (enabled) {
                    base.raisedSurface(shape, UaTheme.palette.accentGradient, shadow = false)
                } else {
                    base.raisedSurface(shape, UaTheme.palette.surface2, shadow = false)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
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
            style = BodyRegular,
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
        unfocusedBorderColor = palette.hairline,
        cursorColor = palette.azure,
    )
}

/** §5.1 - the main circular play/pause control. */
@Composable
fun GradientPlayButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE_PLAY else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "playButtonScale",
    )
    Box(
        modifier = modifier
            .size(PlayButtonSize)
            .scale(scale)
            // One of the three places in the app allowed to glow - see docs/DESIGN_SYSTEM.md "§D
            // Depth". The edge-highlight border below is the same raisedSurface(fill = Brush)
            // treatment used elsewhere, kept manual here since the glow shadow needs its own
            // spotColor that raisedSurface's shared shadowSoft tone doesn't provide.
            .shadow(10.dp, CircleShape, spotColor = UaTheme.palette.azureGlow)
            .clip(CircleShape)
            .background(UaTheme.palette.accentGradient)
            .border(1.dp, UaTheme.palette.edgeHighlightAccent, CircleShape)
            .clickable(
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE_ROUND else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "roundButtonScale",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(RoundButtonSize)
            .scale(scale)
            .raisedSurface(CircleShape, pressedSurface(UaTheme.palette.surface1, pressed), shadow = false)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
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
    tint: Color = UaTheme.palette.labelPrimary,
    background: Color = UaTheme.palette.surface1,
    /** The glyph inside the circle. Raise it for an icon that has to hold its own beside the
     * platform Cast button, whose `MediaRouteButton` draws at its own scale and is noticeably
     * heavier than [SmallIconGlyphSize] - see `PlayerCastButton`. */
    iconSize: Dp = SmallIconGlyphSize,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE_ICON else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "smallIconButtonScale",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(IconButtonSize)
            .scale(scale)
            .raisedSurface(CircleShape, pressedSurface(background, pressed), shadow = false)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
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
    val color = when (variant) {
        StatusPillVariant.Good -> UaTheme.palette.routeGreen
        StatusPillVariant.Proxy -> UaTheme.palette.routeAmber
        StatusPillVariant.Bad -> UaTheme.palette.routeRed
    }
    Text(
        text = text,
        style = PillText,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(PILL_SHAPE_PERCENT))
            .background(color.copy(alpha = 0.14f))
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

/**
 * §5.4 - equal-width segmented control with an animated sliding highlight. The container is a
 * [sunkenSurface] (it's a recessed track the highlight slides in); the selected segment is a
 * [raisedSurface] filled with [UaPalette.accentGradient] - see docs/DESIGN_SYSTEM.md "§D Depth".
 * Shape follows [UaPalette.pillButtons]: Cinema is a fully-rounded pill, Azure a rounded rect -
 * see docs/DESIGN_SYSTEM.md "Themes". Mechanics/behavior are otherwise unchanged.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = UaTheme.palette
    val containerShape = if (palette.pillButtons) {
        RoundedCornerShape(PILL_SHAPE_PERCENT)
    } else {
        RoundedCornerShape(RadiusSeg)
    }
    val segmentShape = if (palette.pillButtons) {
        RoundedCornerShape(PILL_SHAPE_PERCENT)
    } else {
        RoundedCornerShape(RadiusSegInner)
    }

    var containerSizePx by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    val segmentWidthPx = if (options.isEmpty()) 0 else containerSizePx.width / options.size
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetX by animateDpAsState(
        targetValue = with(density) { (segmentWidthPx * selectedIndex).toDp() },
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "segmentOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Without this, the highlight box below (fillMaxHeight) gets unbounded height
            // constraints from this wrap-content Box inside a Column and collapses to zero height
            // - a classic Compose gotcha. IntrinsicSize.Min forces this Box to first resolve its
            // height from its non-fillMaxHeight children (the Row of labels), then hand that
            // bounded height down, so fillMaxHeight actually has something to fill.
            .height(IntrinsicSize.Min)
            .sunkenSurface(containerShape, palette.surface1)
            .padding(3.dp)
            .onSizeChanged { containerSizePx = it }
            .selectableGroup(),
    ) {
        if (containerSizePx.width > 0) {
            Box(
                modifier = Modifier
                    // Lambda overload on purpose: offsetX is animated, and the value-taking
                    // overload reads it during composition, so the whole segmented control
                    // recomposed on every frame of the slide. Read inside the lambda it is a
                    // layout-phase read - the animation moves the highlight without recomposing
                    // anything.
                    .offset { IntOffset(x = offsetX.roundToPx(), y = 0) }
                    .width(with(density) { segmentWidthPx.toDp() })
                    .fillMaxHeight()
                    .raisedSurface(segmentShape, palette.accentGradient, shadow = true),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            onClick = { onSelected(index) },
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = com.uacastplayer.ui.theme.CaptionSemibold,
                        color = if (selected) palette.accentOnFill else palette.labelSecondary,
                    )
                }
            }
        }
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
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE_ROUND else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "secondaryButtonScale",
    )
    val shape = if (palette.pillButtons) {
        RoundedCornerShape(PILL_SHAPE_PERCENT)
    } else {
        RoundedCornerShape(RadiusItem)
    }
    val isGhost = palette.secondaryButtonStyle == SecondaryButtonStyle.GHOST
    Box(
        modifier = modifier
            .heightIn(min = TouchTargetMin)
            .scale(scale)
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
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
                style = BodyRegular,
                color = if (enabled) UaTheme.palette.labelPrimary else UaTheme.palette.labelTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = if (leadingIcon != null) Modifier.padding(start = 8.dp) else Modifier,
            )
        }
    }
}

/** §5.9 tab bar label chrome - shared by GlassTabBar items. [selected]'s pill background is now a
 * full-strength [UaPalette.accentGradient] (see GlassTabBar's TabBarButton) rather than a
 * translucent azure tint, so the label needs [UaPalette.accentOnFill]'s contrast rather than
 * [UaPalette.azure] on selected - the same content-on-filled-accent-surface rule the icon follows. */
@Composable
fun TabBarLabel(text: String, selected: Boolean) {
    Text(
        text = text,
        style = TabLabel,
        color = if (selected) UaTheme.palette.accentOnFill else UaTheme.palette.labelSecondary,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun GradientPlayButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        GradientPlayButton(icon = AppIcons.Play, onClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun RoundIconButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(icon = AppIcons.SkipPrevious, onClick = {})
            RoundIconButton(icon = AppIcons.SkipNext, onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SmallRoundIconButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        SmallRoundIconButton(icon = AppIcons.Settings, onClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun StatusPillPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(text = "Direct", variant = StatusPillVariant.Good)
            StatusPill(text = "Proxy", variant = StatusPillVariant.Proxy)
            StatusPill(text = "Failed", variant = StatusPillVariant.Bad)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun GlowStatusDotPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlowStatusDot(variant = StatusPillVariant.Good)
            GlowStatusDot(variant = StatusPillVariant.Proxy)
            GlowStatusDot(variant = StatusPillVariant.Bad)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 300)
@Composable
private fun SegmentedControlPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        SegmentedControl(options = listOf("Small", "Medium", "Large"), selectedIndex = 1, onSelected = {})
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 300)
@Composable
private fun TrackProgressPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackProgress(progress = 0.35f)
            TrackProgress(progress = 0.7f, bold = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SecondaryButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Export", onClick = {})
            SecondaryButton(text = "Disabled", onClick = {}, enabled = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun TabBarLabelPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TabBarLabel(text = "Channels", selected = true)
            TabBarLabel(text = "Favorites", selected = false)
        }
    }
}
