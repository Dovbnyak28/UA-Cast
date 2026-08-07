package com.uacastplayer.ui.onboarding
import com.uacastplayer.ui.theme.UaTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.LargeTitle
import com.uacastplayer.ui.theme.ScreenHPadding

private const val STEP_COUNT = 3

/**
 * Three-step, first-launch-only explainer shown once between [com.uacastplayer.ui.legal.TermsScreen]
 * and the main app (see [com.uacastplayer.data.prefs.AppPreferences.hasSeenOnboarding] /
 * [com.uacastplayer.MainActivity]): what the app is, what a playlist is and where it comes from, and
 * how to cast. "Skip" on any step and the final step's primary action both end onboarding - they only
 * differ in [onFinished]'s `openAddPlaylist` argument, so the caller can route the final step straight
 * into adding a playlist without this screen needing to know anything about that flow itself.
 */
@Composable
fun OnboardingScreen(onFinished: (openAddPlaylist: Boolean) -> Unit, modifier: Modifier = Modifier) {
    // Saveable: rotation is handled by MainActivity's `configChanges`, but a process death - or a
    // font-size change, which is *not* in that list and so does recreate the Activity - would
    // otherwise drop the user back onto step 1 of a walkthrough they had almost finished.
    var step by rememberSaveable { mutableIntStateOf(0) }
    val isLastStep = step == STEP_COUNT - 1

    // System back behaves like "Skip" - there is nothing behind this screen to reveal (it sits
    // between the mandatory Terms gate and the main app in MainActivity's routing `when`), and
    // treating it as "leave onboarding" rather than a no-op avoids a dead-end back button.
    BackHandler { onFinished(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UaTheme.palette.void)
            // safeDrawing, not statusBars alone - one of the full-bleed gate screens with no
            // scaffold above it to pad the bottom, so under edge-to-edge the Next/Start button ran
            // under the navigation bar.
            .safeDrawingPadding()
            .padding(horizontal = ScreenHPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onFinished(false) }) {
                Text(stringResource(R.string.onboarding_skip_button))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(step.titleRes()),
                style = LargeTitle,
                color = UaTheme.palette.labelPrimary,
            )
            Text(
                text = stringResource(step.bodyRes()),
                style = BodyText,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = GapM),
            )
        }

        StepDots(stepCount = STEP_COUNT, currentStep = step, modifier = Modifier.padding(bottom = GapM))

        val primaryLabelRes = if (isLastStep) {
            R.string.onboarding_get_started_button
        } else {
            R.string.onboarding_next_button
        }
        Button(
            onClick = { if (isLastStep) onFinished(true) else step += 1 },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        ) {
            Text(stringResource(primaryLabelRes))
        }
    }
}

@Composable
private fun StepDots(stepCount: Int, currentStep: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step_indicator, currentStep + 1, stepCount)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(stepCount) { index ->
            val color = if (index == currentStep) UaTheme.palette.labelPrimary else UaTheme.palette.hairline
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private fun Int.titleRes(): Int = when (this) {
    0 -> R.string.onboarding_step1_title
    1 -> R.string.onboarding_step2_title
    else -> R.string.onboarding_step3_title
}

private fun Int.bodyRes(): Int = when (this) {
    0 -> R.string.onboarding_step1_body
    1 -> R.string.onboarding_step2_body
    else -> R.string.onboarding_step3_body
}
