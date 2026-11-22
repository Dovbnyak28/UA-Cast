package app.zorik.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.zorik.player.ui.theme.AppSpacing
import app.zorik.player.ui.theme.CaptionSemibold
import app.zorik.player.ui.theme.RadiusSeg
import app.zorik.player.ui.theme.RadiusSegInner
import app.zorik.player.ui.theme.TouchTargetMin
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.raisedSurface
import app.zorik.player.ui.theme.sunkenSurface
import app.zorik.player.ui.tv.tvFocus

internal val SegmentTrackPadding = 3.dp

/** Keep complete labels: narrow/large-text layouts use a full-width single-choice stack. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelWidth = remember(options, measurer) {
        options.maxOfOrNull { measurer.measure(it, CaptionSemibold, softWrap = false).size.width } ?: 0
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val innerWidth = constraints.maxWidth - with(density) { (SegmentTrackPadding * 2).toPx() }
        val requiredWidth = labelWidth + with(density) { (AppSpacing.xs * 2).toPx() }
        val stacked = options.isNotEmpty() && requiredWidth * options.size > innerWidth
        if (stacked) VerticalSegments(options, selectedIndex, onSelected)
        else HorizontalSegments(options, selectedIndex, onSelected)
    }
}

@Composable
private fun VerticalSegments(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    val palette = UaTheme.palette
    val container = RoundedCornerShape(RadiusSeg)
    val shape = RoundedCornerShape(RadiusSegInner)
    Column(Modifier.fillMaxWidth().sunkenSurface(container, palette.surface1)
        .padding(SegmentTrackPadding).selectableGroup()) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(Modifier.fillMaxWidth().heightIn(min = TouchTargetMin)
                .then(if (selected) Modifier.raisedSurface(shape, palette.accentGradient) else Modifier)
                .tvFocus(shape).selectable(selected, role = Role.RadioButton, onClick = { onSelected(index) })
                .padding(AppSpacing.xs), contentAlignment = Alignment.Center) {
                Text(label, style = CaptionSemibold,
                    color = if (selected) palette.accentOnFill else palette.labelSecondary)
            }
        }
    }
}
