package app.zorik.player.ui.components
import app.zorik.player.ui.tv.tvFocus
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.raisedSurface
import app.zorik.player.ui.theme.sunkenSurface
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import app.zorik.player.ui.theme.motionDuration
import app.zorik.player.ui.theme.DUR_PRESS
import app.zorik.player.ui.theme.EaseSpring
import app.zorik.player.ui.theme.RadiusSeg
import app.zorik.player.ui.theme.RadiusSegInner
import app.zorik.player.ui.theme.TouchTargetMin


/**
 * §5.4 - equal-width segmented control with an animated sliding highlight. The container is a
 * [sunkenSurface] (it's a recessed track the highlight slides in); the selected segment is a
 * [raisedSurface] filled with [UaPalette.accentGradient] - see docs/DESIGN_SYSTEM.md "§D Depth".
 * Shape follows [UaPalette.pillButtons]: Cinema is a fully-rounded pill, Azure a rounded rect -
 * see docs/DESIGN_SYSTEM.md "Themes". Mechanics/behavior are otherwise unchanged.
 */
@Composable
internal fun HorizontalSegments(
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
        animationSpec = tween(motionDuration(DUR_PRESS), easing = EaseSpring),
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
            .padding(SegmentTrackPadding)
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
                        .fillMaxHeight()
                        .heightIn(min = TouchTargetMin)
                        .tvFocus()
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelected(index) },
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = app.zorik.player.ui.theme.CaptionSemibold,
                        color = if (selected) palette.accentOnFill else palette.labelSecondary,
                    )
                }
            }
        }
    }
}
