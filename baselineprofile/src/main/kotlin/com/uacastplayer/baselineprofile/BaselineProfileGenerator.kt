package com.uacastplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val GATE_TIMEOUT_MILLIS = 5_000L

/**
 * How far a fresh install is driven decides what ends up AOT-compiled, so this walks the whole
 * first-launch path - language picker, Terms, then every step of the guided tour - and records what
 * the compiler saw into app/src/main/baseline-prof.txt (see docs/RELEASING.md for how to
 * regenerate).
 *
 * **The tour is walked rather than skipped, and that is the point.** The previous version dismissed
 * the first-launch gate and stopped at Home, so the profile it produced covered `ui/theme`,
 * `ui/nav` and `MainActivity` and nothing else: `ui/channels`, `ui/settings` and the tour itself had
 * zero rules in it. The tour drives the app's own navigation - each step moves the bottom
 * destination to wherever its target lives (see RootScaffold's `guidedTourDestination`) - so
 * walking it composes Home, Channels, Favorites and Settings in one pass, which is exactly the set
 * a first-time user reaches and the set the old profile missed.
 *
 * After the first-run journey, a variant-only fixture installs a credential-free playlist/EPG
 * snapshot through production codecs. The generator then restarts the app and reaches Channels,
 * first player launch, fullscreen and the guide. The fixture activities exist only in the
 * throwaway benchmark/non-minified variants; release and debug APKs do not contain a backdoor.
 *
 * None of these screens have testTags, and this module drives the app as a black box via UiAutomator
 * (no Compose semantics across the process/APK boundary), so selection goes by accessibility
 * role/class and tree order rather than by text - labels render in whatever language the device's
 * system locale resolves to (see core.i18n.LanguageResolver), which would make text matching
 * device-dependent. Tree order disambiguates every screen here, but **not in one direction**: the
 * gates put their forward action first, while every tour card puts it last (`Skip … Back, Next`, and
 * `Skip, Start` on the welcome card - see GuidedTourOverlay's StepActions/WelcomeContent). Both are
 * read off the source layout, which is why each click below says which end it takes and why.
 */
class BaselineProfileGenerator {

    private companion object {
        /**
         * Welcome + [com.uacastplayer.guidedtour.GuidedTourSteps.DEFAULT]'s seven steps + the done
         * card. Deliberately a fixed count rather than "click until the tour disappears": a loop
         * with no bound would keep pressing whatever button Home happens to expose once the tour is
         * gone, and pull whatever that opens into the profile.
         */
        const val TOUR_CARDS = 9
    }

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.uacastplayer",
    ) {
        pressHome()
        startActivityAndWait()

        // Language picker: pick whichever language sorts first (AppLanguage.entries order) - which
        // one doesn't matter for what gets profiled, every language renders through the same code.
        check(
            device.wait(Until.hasObject(By.clazz("android.widget.RadioButton")), GATE_TIMEOUT_MILLIS),
        ) { "Timed out waiting for the language picker" }
        requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.RadioButton")), GATE_TIMEOUT_MILLIS)) {
            "Language picker reported ready but exposed no RadioButton"
        }.click()
        device.waitForIdle()
        requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.Button")), GATE_TIMEOUT_MILLIS)) {
            "Language picker exposed no Continue button"
        }.click()

        // Terms gate: Button(accept) is composed before OutlinedButton(decline), so it's first in
        // tree order - see TermsScreen's mandatory-gate branch. Taking the last one here would
        // decline and call finish(), ending the run instead of the gate.
        check(
            device.wait(Until.hasObject(By.clazz("android.widget.Button")), GATE_TIMEOUT_MILLIS),
        ) { "Timed out waiting for the Terms gate" }
        requireNotNull(
            device.wait(Until.findObject(By.clazz("android.widget.Button")), GATE_TIMEOUT_MILLIS),
        ) {
            "Terms gate reported ready but exposed no action button"
        }.click()

        // The tour, one card at a time. The forward action is the trailing filled Button on every
        // card, so this takes the last node rather than the first - the leading one is Skip, which
        // would end the tour on the welcome card and profile none of what follows.
        repeat(TOUR_CARDS) { cardIndex ->
            check(
                device.wait(Until.hasObject(By.clazz("android.widget.Button")), GATE_TIMEOUT_MILLIS),
            ) { "Timed out waiting for tour card ${cardIndex + 1}/$TOUR_CARDS" }
            val actions = device.findObjects(By.clazz("android.widget.Button"))
            check(actions.isNotEmpty()) {
                "Tour card ${cardIndex + 1}/$TOUR_CARDS reported ready but exposed no action button"
            }
            actions.last().click()
            // Each step animates the spotlight to its target and may switch the bottom destination
            // on the way, so the next card is not composed until this settles.
            device.waitForIdle()
        }

        // Home, past every gate and with the tour dismissed.
        check(
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), GATE_TIMEOUT_MILLIS),
        ) { "Timed out waiting for Home after completing the tour" }
        device.waitForIdle()

        val driver = BenchmarkAppDriver(device)
        driver.prepareFixture(BenchmarkAppDriver.MODE_PROFILE, clearPackage = false)
        startActivityAndWait()
        driver.openChannels()
        driver.openFirstGroup()
        driver.openFirstPlayer()
        driver.clickDescription(BenchmarkAppDriver.FULLSCREEN_DESCRIPTION)
        driver.waitForDescription(BenchmarkAppDriver.EXIT_FULLSCREEN_DESCRIPTION)
        driver.clickDescription(BenchmarkAppDriver.EXIT_FULLSCREEN_DESCRIPTION)
        driver.waitForDescription(BenchmarkAppDriver.FULLSCREEN_DESCRIPTION)
        driver.exitPlayerToChannelList()
        driver.openFirstChannelActions()
        driver.clickText(BenchmarkAppDriver.GUIDE_LABEL)
        driver.waitForText(BenchmarkAppDriver.FIRST_PROGRAMME)
        device.waitForIdle()
    }
}
