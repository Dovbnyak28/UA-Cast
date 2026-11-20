package com.uacastplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.performance.DeviceTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReducedEffectsSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = AppPreferences(context)

    @Before fun reset() { preferences.reducedVisualEffects = null }

    @Test fun automaticModeFollowsTheTierWithoutWritingAnExplicitChoice() {
        val controller = controller(DeviceTier.HIGH_END)
        assertFalse(controller.settingsState.value.reducedVisualEffects)
        controller.recomputeDeviceTierDefaults(DeviceTier.LOW_END)
        assertTrue(controller.settingsState.value.reducedVisualEffects)
        assertNull(preferences.reducedVisualEffects)
    }

    @Test fun manualFullEffectsSurviveReclassificationAndOwnerRecreation() {
        val controller = controller(DeviceTier.LOW_END)
        assertTrue(controller.settingsState.value.reducedVisualEffects)
        controller.setReducedVisualEffects(false)
        controller.recomputeDeviceTierDefaults(DeviceTier.LOW_END)
        assertFalse(controller.settingsState.value.reducedVisualEffects)
        assertFalse(controller(DeviceTier.LOW_END).settingsState.value.reducedVisualEffects)
    }

    @Test fun manualReducedEffectsAlsoApplyOnFastDevicesAfterRecreation() {
        controller(DeviceTier.HIGH_END).setReducedVisualEffects(true)
        assertTrue(controller(DeviceTier.HIGH_END).settingsState.value.reducedVisualEffects)
    }

    private fun controller(tier: DeviceTier) =
        SettingsController(preferences, IconSourceController(IconRepository(context)), tier)
}
