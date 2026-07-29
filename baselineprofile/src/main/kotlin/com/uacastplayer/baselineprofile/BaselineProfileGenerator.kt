package com.uacastplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val GATE_TIMEOUT_MILLIS = 5_000L

/**
 * Drives a fresh install through the mandatory first-launch gate (language picker -> Terms ->
 * onboarding, see MainActivity's routing `when`) to Home, and records everything the JIT/AOT
 * compiler saw along the way into app/src/main/baseline-prof.txt (replacing the hand-authored
 * stopgap - see README's "Stack" section and docs/RELEASING.md for how to regenerate).
 *
 * None of the three gate screens have testTags, and this module drives the app as a black box via
 * UiAutomator (no Compose semantics access across process/APK boundaries), so selection here goes
 * by accessibility role/class and tree order rather than text - the button labels are rendered in
 * whatever language the connected device's system locale resolves to (see
 * core.i18n.LanguageResolver), which would make text-based matching device-dependent. Tree order
 * happens to disambiguate every gate: on each screen, the first Role.Button-classed node in
 * traversal order is always the "move forward" action (Continue / Accept / Skip), because that's
 * also its position in the source (see each screen's own layout).
 */
class BaselineProfileGenerator {

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
        device.wait(Until.hasObject(By.clazz("android.widget.RadioButton")), GATE_TIMEOUT_MILLIS)
        device.findObject(By.clazz("android.widget.RadioButton"))?.click()
        device.waitForIdle()
        device.findObject(By.clazz("android.widget.Button"))?.click()

        // Terms gate: Button(accept) is composed before OutlinedButton(decline), so it's first in
        // tree order - see TermsScreen's mandatory-gate branch.
        device.wait(Until.hasObject(By.clazz("android.widget.Button")), GATE_TIMEOUT_MILLIS)
        device.findObjects(By.clazz("android.widget.Button")).firstOrNull()?.click()

        // Onboarding: the top-right "Skip" TextButton ends onboarding immediately regardless of
        // step (see OnboardingScreen's onFinished(false) doc) and is composed before the bottom
        // Next/Get-started Button, so it's first in tree order too.
        device.wait(Until.hasObject(By.clazz("android.widget.Button")), GATE_TIMEOUT_MILLIS)
        device.findObjects(By.clazz("android.widget.Button")).firstOrNull()?.click()

        // Home, first real frame past every gate.
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), GATE_TIMEOUT_MILLIS)
        device.waitForIdle()
    }
}
