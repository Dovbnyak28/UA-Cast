package com.uacastplayer.data.prefs

import com.uacastplayer.core.settings.BufferSize
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.performance.HeapBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which buffer size is the one that gets used.
 *
 * There are two answers to "what is the buffer size" and they are not the same question. The stored
 * value is what the backup exports and what "has the user chosen one" is about; the *effective* one
 * is what ExoPlayer should allocate.
 *
 * Keeping them apart matters because conflating them was a live defect. The heap-derived default
 * arrived resolved inside `SettingsController`, so the Settings screen showed the smaller buffer
 * while `PlayerViewModel` - which reads preferences itself - carried on allocating the larger one.
 * The 128MB device the default was written for kept the 16MB that helped run it out of memory, and
 * the screen told its owner otherwise.
 *
 * The heap is injected because this harness runs with 512MB, where the tight-heap branch is
 * unreachable and every assertion below would pass by accident.
 */
@RunWith(RobolectricTestRunner::class)
class EffectiveBufferSizeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun mb(count: Long) = count * 1024 * 1024

    private fun preferences(heapMb: Long) = AppPreferences(context, maxHeapBytes = { mb(heapMb) })

    /** The device the budget was written from. */
    @Test
    fun `a tight heap gets the smaller buffer even though nothing is stored`() {
        val prefs = preferences(TIGHT_HEAP_MB)

        assertEquals(BufferSize.SMALL, prefs.effectiveBufferSize)
    }

    /**
     * And the stored value is untouched by that, which is the whole point of them being separate:
     * `hasChosenBufferSize` must stay false so a later device with room falls back to its own
     * default, and the backup must not carry this device's answer anywhere.
     */
    @Test
    fun `the heap default is not a stored choice`() {
        val prefs = preferences(TIGHT_HEAP_MB)

        assertEquals("nothing has been chosen", false, prefs.hasChosenBufferSize)
        assertNotEquals(
            "the effective value must differ from the raw one here, or this test proves nothing",
            prefs.bufferSize,
            prefs.effectiveBufferSize,
        )
    }

    /** A heap with room keeps exactly the default every device had before. */
    @Test
    fun `a roomy heap gets the buffer it always had`() {
        assertEquals(BufferSize.MEDIUM, preferences(ROOMY_HEAP_MB).effectiveBufferSize)
    }

    /** The user's own choice wins forever, on any heap - the same contract the icon and density
     * tier defaults have. */
    @Test
    fun `a chosen buffer size wins over the heap on both sides`() {
        val tight = preferences(TIGHT_HEAP_MB)
        tight.bufferSize = BufferSize.LARGE
        assertEquals(BufferSize.LARGE, tight.effectiveBufferSize)

        val roomy = preferences(ROOMY_HEAP_MB)
        assertEquals(
            "and a deliberate SMALL is not quietly promoted either",
            BufferSize.SMALL,
            roomy.also { it.bufferSize = BufferSize.SMALL }.effectiveBufferSize,
        )
    }

    /** It is the same rule the budget states, not a second copy of it that could drift. */
    @Test
    fun `the unchosen answer is exactly what the heap budget says`() {
        for (heapMb in listOf(96L, 128L, 256L, 320L, 512L)) {
            assertEquals(
                "disagreement at ${heapMb}MB",
                HeapBudget.defaultBufferSize(mb(heapMb)),
                preferences(heapMb).effectiveBufferSize,
            )
        }
    }

    private companion object {
        const val TIGHT_HEAP_MB = 128L
        const val ROOMY_HEAP_MB = 512L
    }
}
