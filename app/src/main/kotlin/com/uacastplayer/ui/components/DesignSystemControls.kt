package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.DurPress
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.PillText
import com.uacastplayer.ui.theme.PressScaleIcon
import com.uacastplayer.ui.theme.PressScalePlay
import com.uacastplayer.ui.theme.PressScaleRound
import com.uacastplayer.ui.theme.PlayButtonSize
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RadiusSeg
import com.uacastplayer.ui.theme.RadiusSegInner
import com.uacastplayer.ui.theme.RoundButtonSize
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.TabLabel

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
        targetValue = if (pressed) PressScalePlay else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "playButtonScale",
    )
    Box(
        modifier = modifier
            .size(PlayButtonSize)
            .scale(scale)
            .shadow(10.dp, CircleShape, spotColor = UaTheme.palette.azureGlow)
            .clip(CircleShape)
            .background(UaTheme.palette.accentGradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(26.dp))
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
        targetValue = if (pressed) PressScaleRound else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "roundButtonScale",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(RoundButtonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(if (pressed) UaTheme.palette.surface2 else UaTheme.palette.surface1)
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressScaleIcon else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "smallIconButtonScale",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(IconButtonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

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
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** §5.5 - a status dot with a soft glow matching route health. */
@Composable
fun GlowStatusDot(variant: StatusPillVariant, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = com.uacastplayer.ui.theme.StatusDotSize) {
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

/** §5.4 - equal-width segmented control with an animated sliding highlight. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerSizePx by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    val segmentWidthPx = if (options.isEmpty()) 0 else containerSizePx.width / options.size
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetX by animateDpAsState(
        targetValue = with(density) { (segmentWidthPx * selectedIndex).toDp() },
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "segmentOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusSeg))
            .background(UaTheme.palette.surface1)
            .padding(3.dp)
            .onSizeChanged { containerSizePx = it }
            .selectableGroup(),
    ) {
        if (containerSizePx.width > 0) {
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .width(with(density) { segmentWidthPx.toDp() })
                    .fillMaxHeight()
                    .shadow(8.dp, RoundedCornerShape(RadiusSegInner), ambientColor = Color.Black.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(RadiusSegInner))
                    .background(UaTheme.palette.surface2),
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
                        color = if (selected) UaTheme.palette.labelPrimary else UaTheme.palette.labelSecondary,
                    )
                }
            }
        }
    }
}

/** §5.6 - thin non-interactive progress track used inside list rows, and a bold interactive variant for the player. */
@Composable
fun TrackProgress(progress: Float, modifier: Modifier = Modifier, bold: Boolean = false) {
    val height = if (bold) com.uacastplayer.ui.theme.ProgressHeightBold else com.uacastplayer.ui.theme.ProgressHeightThin
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(UaTheme.palette.surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(UaTheme.palette.accentGradient),
        )
    }
}

/** §5.7 - secondary text-only action button (export/import, "open" links). Use instead of Material's OutlinedButton. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PressScaleRound else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "secondaryButtonScale",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(RadiusItem))
            .background(if (pressed) UaTheme.palette.surface2 else UaTheme.palette.surface1)
            .border(1.dp, UaTheme.palette.hairline, RoundedCornerShape(RadiusItem))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BodyRegular,
            color = if (enabled) UaTheme.palette.labelPrimary else UaTheme.palette.labelTertiary,
        )
    }
}

/** §5.9 tab bar label chrome - shared by GlassTabBar items. */
@Composable
fun TabBarLabel(text: String, selected: Boolean) {
    Text(
        text = text,
        style = TabLabel,
        color = if (selected) UaTheme.palette.azure else UaTheme.palette.labelSecondary,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}
