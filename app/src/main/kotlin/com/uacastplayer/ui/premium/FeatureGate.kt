package com.uacastplayer.ui.premium

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.FeatureManager
import com.uacastplayer.premium.PremiumSectionState

/**
 * How a screen asks whether something is paid for, and what to do when it is not.
 *
 * A [CompositionLocal] rather than a parameter, for the reason this codebase already learned the
 * hard way: gating touches Settings, the playlist screens and the cast sheet, and threading a
 * manager plus a callback through each of them would add two more parameters to signatures that
 * already run to twenty-six (see `ScaffoldZone`, whose size is measurable in JIT compilation time
 * on a real device). The guided tour's target registry is provided the same way for the same
 * reason.
 *
 * **The default is wide open.** A composable rendered outside a provider - a preview, a screenshot
 * test, a unit test - gets a gate that unlocks everything and paywalls nothing, so forgetting to
 * provide one can only ever fail towards the user rather than against them.
 */
@Stable
class FeatureGate(
    private val isUnlocked: (Feature) -> Boolean,
    private val onPaywall: (Feature) -> Unit,
) {

    fun isLocked(feature: Feature): Boolean = !isUnlocked(feature)

    /**
     * Wraps an action in its own entitlement.
     *
     * The call site reads as what it is - "export a backup, if backups are paid for" - and the
     * decision stays here rather than becoming an `if (premium)` at every offer point, which is the
     * sprawl the premium layer exists to prevent.
     */
    fun guard(feature: Feature, action: () -> Unit): () -> Unit = {
        if (isUnlocked(feature)) action() else onPaywall(feature)
    }

    companion object {
        /** Everything unlocked, nothing to sell - see the class doc on why this is the default. */
        val Open = FeatureGate(isUnlocked = { true }, onPaywall = {})
    }
}

val LocalFeatureGate = staticCompositionLocalOf { FeatureGate.Open }

/**
 * The upgrade banner, already bound to the current entitlement and to the sheet it opens - see
 * [UpgradeBanner] for the two situations in which it draws anything at all.
 *
 * Provided rather than passed for the same reason as [LocalFeatureGate], and separate from it
 * because it is a different kind of thing: the gate answers a question a screen asks, this is a
 * surface a screen makes room for. Home is the only caller today, and it should stay somewhere a
 * user is already looking rather than being repeated onto every tab.
 *
 * The default draws nothing, so a preview or a screenshot test never has to know it exists.
 */
val LocalPremiumNotice = staticCompositionLocalOf<@Composable (Modifier) -> Unit> { {} }

/**
 * Everything [rememberFeatureGate] hands back: the gate itself, the banner that can appear without
 * being asked for, and the dialog/sheet pair that only ever answer a tap.
 *
 * A named type rather than a `Triple` because the three are placed in three different parts of the
 * tree - provided, drawn inline, drawn last - and `third` would say none of that.
 */
@Stable
class PremiumSurfaces(
    val gate: FeatureGate,
    val notice: @Composable (Modifier) -> Unit,
    val overlays: @Composable () -> Unit,
)

/**
 * Builds the gate and owns the two surfaces it opens.
 *
 * The paywall is state, not navigation - which feature was refused decides what the dialog says
 * (see [UnlockDialog]), and it has to outlive the tap that caused it. Keeping that here means no
 * screen holds a "which feature did they just tap" field of its own, and "see what premium is"
 * opens [PremiumBottomSheet] in place rather than sending the user to a different tab and leaving
 * them to walk back to whatever they were doing.
 *
 * Returns the gate and the composables that draw its surfaces; the caller places each where it
 * belongs - see [PremiumSurfaces].
 */
@Composable
fun rememberFeatureGate(
    featureManager: FeatureManager,
    section: PremiumSectionState,
): PremiumSurfaces {
    var refused by remember { mutableStateOf<Feature?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    // Keyed on the manager alone: it reads the entitlement StateFlow on each call, so a purchase
    // that lands while a screen is open changes the answer without rebuilding the gate.
    val gate = remember(featureManager) {
        FeatureGate(
            isUnlocked = featureManager::isUnlocked,
            onPaywall = { feature -> refused = feature },
        )
    }
    // Outside the `remember` above deliberately: a composable lambda is memoized by the compiler and
    // still reads the latest `section`, whereas capturing it in the gate would mean keying the
    // remember on a value that is rebuilt whenever any of the flows behind it emits.
    val notice: @Composable (Modifier) -> Unit = { modifier ->
        UpgradeBanner(
            section = section,
            nowMillis = System.currentTimeMillis(),
            onSeePremium = { showSheet = true },
            modifier = modifier,
        )
    }
    val overlays: @Composable () -> Unit = {
        refused?.let { feature ->
            UnlockDialog(
                feature = feature,
                onSeePremium = {
                    refused = null
                    showSheet = true
                },
                onDismiss = { refused = null },
            )
        }
        if (showSheet) {
            PremiumBottomSheet(
                section = section,
                nowMillis = System.currentTimeMillis(),
                onDismiss = { showSheet = false },
            )
        }
    }
    return PremiumSurfaces(gate = gate, notice = notice, overlays = overlays)
}
