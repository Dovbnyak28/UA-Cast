package app.zorik.player.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val GATE_TIMEOUT_MILLIS = 5_000L

/**
 * How far a fresh install is driven decides what ends up AOT-compiled, so this walks the whole
 * first-launch path - language picker, Terms, then every page of the intro - and records what the
 * compiler saw into app/src/main/baseline-prof.txt (see docs/RELEASING.md for how to regenerate).
 *
 * **The intro is walked rather than skipped**, so its pager and drawn pictures are in the profile -
 * they are the first thing every new user sees, on exactly the cold start the profile is for.
 *
 * After the first-run journey, a variant-only fixture installs a credential-free playlist/EPG
 * snapshot through production codecs (with every tip already marked seen, so none covers a screen
 * mid-journey). The generator then restarts the app and reaches Channels, first player launch,
 * fullscreen, the guide, Favorites and Settings. The fixture activities exist only in the throwaway
 * benchmark/non-minified variants; release and debug APKs do not contain a backdoor.
 *
 * None of these screens have testTags, and this module drives the app as a black box via UiAutomator
 * (no Compose semantics across the process/APK boundary), so selection goes by accessibility
 * role/class and tree order rather than by text - labels render in whatever language the device's
 * system locale resolves to (see core.i18n.LanguageResolver), which would make text matching
 * device-dependent. Tree order disambiguates every screen here, but **not in one direction**: the
 * gates put their forward action first, while the intro puts the one that moves on last (`Skip …
 * Next` on the first pages, `Add playlist, Later` on the last - see IntroScreen). Both are read off
 * the source layout, which is why each click below says which end it takes and why.
 */
class BaselineProfileGenerator {

    private companion object {
        /**
         * [app.zorik.player.guidedtour.IntroPage]'s three pages. Deliberately a fixed count rather
         * than "click until the intro disappears": a loop with no bound would keep pressing whatever
         * button Home happens to expose once the intro is gone, and pull whatever that opens into
         * the profile.
         */
        const val INTRO_PAGES = 3
        const val FAVORITES_LABEL = "Favorites"
        const val SETTINGS_LABEL = "Settings"
    }

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "app.zorik.player",
    ) {
        pressHome()
        startActivityAndWait()

        // Only the first iteration starts from a fresh install. The fixture below writes a chosen
        // language and accepted terms, so every later iteration opens straight on Home - and
        // waiting for a language picker there used to fail the whole run on iteration 2. The first
        // iteration's first-run code stays in the collected profile, which accumulates.
        if (device.wait(Until.hasObject(By.clazz("android.widget.RadioButton")), GATE_TIMEOUT_MILLIS) == true) {
            walkFirstRun()
        }

        // Home, past every gate and with the intro closed.
        check(
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), GATE_TIMEOUT_MILLIS),
        ) { "Timed out waiting for Home after completing the intro" }
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
        driver.waitForGuideProgramme()
        device.waitForIdle()
        // The two tabs the old tour used to walk through; nothing else in this journey reaches them.
        device.pressBack()
        driver.clickDescription(FAVORITES_LABEL)
        device.waitForIdle()
        driver.clickDescription(SETTINGS_LABEL)
        device.waitForIdle()
    }

    /** Language picker, Terms and the three intro pages - what a fresh install walks through. */
    private fun MacrobenchmarkScope.walkFirstRun() {
        // Language picker: pick whichever language sorts first (AppLanguage.entries order) - which
        // one doesn't matter for what gets profiled, every language renders through the same code.
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

        // The intro, one page at a time. The last button is Next on the first pages - the leading one
        // is Skip, which would end the intro on its first page - and Later on the last, which closes
        // it without opening the add-playlist screen.
        repeat(INTRO_PAGES) { pageIndex ->
            check(
                device.wait(Until.hasObject(By.clazz("android.widget.Button")), GATE_TIMEOUT_MILLIS),
            ) { "Timed out waiting for intro page ${pageIndex + 1}/$INTRO_PAGES" }
            val actions = device.findObjects(By.clazz("android.widget.Button"))
            check(actions.isNotEmpty()) {
                "Intro page ${pageIndex + 1}/$INTRO_PAGES reported ready but exposed no action button"
            }
            actions.last().click()
            // The pager animates to the next page, so its buttons are not settled until this does.
            device.waitForIdle()
        }
    }
}
