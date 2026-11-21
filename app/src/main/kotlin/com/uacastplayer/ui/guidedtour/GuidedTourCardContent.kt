package com.uacastplayer.ui.guidedtour

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.guidedtour.GuidedTourPhase
import com.uacastplayer.guidedtour.GuidedTourState
import com.uacastplayer.guidedtour.GuidedTourStep
import com.uacastplayer.guidedtour.GuidedTourTarget
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.LargeTitle
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.UaTheme

/** Material's default button height is under the 48dp minimum touch target, and these are the only
 * controls reachable while the tour is up. */
private val ActionMinHeight = 48.dp

private val DotSize = 8.dp
private val ScreenshotMaxHeight = 220.dp

/**
 * Above this font scale the three step actions stack instead of sharing a row.
 *
 * 1.5 and 2.0 are where `FontScaleLayoutTest` measured the row overflowing a 4.5" viewport; 1.3
 * still fits. The threshold sits between them rather than at 1.5 so a device whose Display-size
 * setting narrows the screen as well has a little room before it breaks.
 */
private const val STACKED_ACTIONS_FONT_SCALE = 1.4f

@Composable
internal fun WelcomeContent(onSkip: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.guided_tour_welcome_title),
            style = LargeTitle,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.IconButton(onClick = onSkip) {
            androidx.compose.material3.Icon(
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.guided_tour_not_now),
                tint = UaTheme.palette.labelSecondary,
            )
        }
    }
    Text(
        text = stringResource(R.string.guided_tour_welcome_body),
        style = BodyText,
        color = UaTheme.palette.labelSecondary,
    )

}

@Composable
internal fun StepContent(
    state: GuidedTourState,
    step: GuidedTourStep,
) {
    StepProgress(current = state.stepNumber, total = state.stepCount)
    StepIllustration(step = step)
    Text(
        text = stringResource(step.titleRes),
        style = CardTitle,
        color = UaTheme.palette.labelPrimary,
    )
    Text(
        text = stringResource(step.descriptionRes),
        style = BodyText,
        color = UaTheme.palette.labelSecondary,
    )
}

/** Actions stay visible while long descriptions and illustrations scroll at accessibility sizes. */
@Composable
internal fun GuidedTourActions(
    phase: GuidedTourPhase,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onAddPlaylist: () -> Unit,
    onComplete: () -> Unit,
) {
    when (phase) {
        GuidedTourPhase.WELCOME -> Column(verticalArrangement = Arrangement.spacedBy(GapM)) {
            Button(
                onClick = onAddPlaylist,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) {
                Text(stringResource(R.string.guided_tour_add_playlist))
            }
            TextButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) {
                Text(stringResource(R.string.guided_tour_start), color = UaTheme.palette.accentText)
            }
        }
        GuidedTourPhase.STEPS -> StepActions(onNext, onBack, onSkip)
        GuidedTourPhase.DONE -> {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) {
                Text(stringResource(R.string.guided_tour_finish))
            }
        }
        GuidedTourPhase.IDLE -> Unit
    }
}

/**
 * Skip, Back and Next - side by side normally, stacked when the text is too big for one line.
 *
 * The stacked branch is not defensive programming; `FontScaleLayoutTest` caught the row running off
 * a 4.5" screen at 1.5x and 2.0x, which is a real Display-size setting and not an exotic one. Three
 * labels plus their padding simply do not fit in the card's inner width there, and the item that
 * left the screen was Next - the one control the step exists to offer.
 *
 * Stacked, the order flips to importance-first (Next, Back, Skip) rather than keeping the
 * horizontal layout's left-to-right reading, because vertically the top item is the primary one.
 */
@Composable
private fun StepActions(onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    val skipLabel = stringResource(R.string.guided_tour_skip)
    val backLabel = stringResource(R.string.guided_tour_back)
    val nextLabel = stringResource(R.string.guided_tour_next)

    if (LocalDensity.current.fontScale >= STACKED_ACTIONS_FONT_SCALE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GapS),
        ) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) { Text(nextLabel) }
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) { Text(backLabel, color = UaTheme.palette.azure) }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) { Text(skipLabel, color = UaTheme.palette.labelSecondary) }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(skipLabel, color = UaTheme.palette.labelSecondary)
        }
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(backLabel, color = UaTheme.palette.azure)
        }
        Button(onClick = onNext, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(nextLabel)
        }
    }
}

/**
 * The step's picture, when it has one.
 *
 * Composed only for the step being shown, so exactly one drawable is ever decoded - which is the
 * reason a screenshot is a resource id on the step rather than an entry in a preloaded list. A step
 * whose element *is* on screen shows nothing here: the live highlight already is the illustration.
 */
@Composable
private fun StepIllustration(step: GuidedTourStep) {
    val registry = LocalGuidedTourTargets.current
    val resource = when (val target = step.target) {
        is GuidedTourTarget.Screenshot -> target.resource
        is GuidedTourTarget.Element ->
            if (registry.boundsOf(target.key) == null) target.fallbackScreenshot else null
        GuidedTourTarget.None -> null
    } ?: return

    Image(
        painter = painterResource(resource),
        contentDescription = stringResource(step.titleRes),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ScreenshotMaxHeight)
            .clip(RoundedCornerShape(RadiusItem)),
    )
}

@Composable
internal fun DoneContent() {
    Text(
        text = stringResource(R.string.guided_tour_done_title),
        style = LargeTitle,
        color = UaTheme.palette.labelPrimary,
    )
    Text(
        text = stringResource(R.string.guided_tour_done_body),
        style = BodyText,
        color = UaTheme.palette.labelSecondary,
    )

}

/**
 * Dots plus "3 / 7".
 *
 * Both, not one: the dots give the shape of the thing at a glance, the numbers answer "how much is
 * left" without counting them. The whole row is a single accessibility node reading the numbers -
 * seven unlabelled dots announced one at a time would be noise.
 */
@Composable
private fun StepProgress(current: Int, total: Int) {
    val label = stringResource(R.string.guided_tour_progress, current, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(if (index < current) UaTheme.palette.azure else UaTheme.palette.hairline),
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        Text(text = label, style = Caption, color = UaTheme.palette.labelTertiary)
    }
}
