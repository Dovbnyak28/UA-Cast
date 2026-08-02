package com.uacastplayer.ui.home

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import com.uacastplayer.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home screen renders a count and its label as two separate pieces of text, which is exactly
 * how the label came to be a fixed string: nothing about the layout forces the two to agree. In
 * Ukrainian that showed as "1 Улюблених" and "2863 Каналів", both ungrammatical, on the app's main
 * screen in its primary language - and the screenshot goldens never caught it because the only ones
 * that exist cover empty states.
 *
 * So this asserts the grammar directly rather than the pixels. The interesting counts are not 1 and
 * 5: they are 21 and 22, where Ukrainian and Russian stop agreeing with the English intuition that
 * "one" means literally one. 21 takes the singular, 22 the "few" form, 25 the "many" form - and a
 * fixed label gets all three wrong.
 */
@RunWith(RobolectricTestRunner::class)
class HomeStatPluralsTest {

    private fun label(quantity: Int, plural: Int): String =
        ApplicationProvider.getApplicationContext<Application>()
            .resources.getQuantityString(plural, quantity)

    @Test
    @Config(qualifiers = "uk")
    fun `ukrainian channels decline across all four quantity classes`() {
        assertEquals("Канал", label(1, R.plurals.home_stat_channels))
        assertEquals("Канали", label(2, R.plurals.home_stat_channels))
        assertEquals("Каналів", label(5, R.plurals.home_stat_channels))
        assertEquals("Каналів", label(11, R.plurals.home_stat_channels))
        // The two that a fixed label, and an English-shaped one/other pair, both get wrong.
        assertEquals("Канал", label(21, R.plurals.home_stat_channels))
        assertEquals("Канали", label(22, R.plurals.home_stat_channels))
    }

    @Test
    @Config(qualifiers = "uk")
    fun `ukrainian groups and favorites decline too`() {
        assertEquals("Група", label(1, R.plurals.home_stat_groups))
        assertEquals("Групи", label(3, R.plurals.home_stat_groups))
        assertEquals("Груп", label(11, R.plurals.home_stat_groups))

        assertEquals("Улюблений", label(1, R.plurals.home_stat_favorites))
        assertEquals("Улюблені", label(2, R.plurals.home_stat_favorites))
        assertEquals("Улюблених", label(9, R.plurals.home_stat_favorites))
    }

    @Test
    @Config(qualifiers = "ru")
    fun `russian channels decline across all four quantity classes`() {
        assertEquals("Канал", label(1, R.plurals.home_stat_channels))
        assertEquals("Канала", label(2, R.plurals.home_stat_channels))
        assertEquals("Каналов", label(5, R.plurals.home_stat_channels))
        assertEquals("Канал", label(21, R.plurals.home_stat_channels))
    }

    /** English has no "few"/"many", so the only thing to get wrong is the singular - which the old
     * fixed label did, reading "1 Favorites". */
    @Test
    @Config(qualifiers = "en")
    fun `english still distinguishes the singular`() {
        assertEquals("Favorite", label(1, R.plurals.home_stat_favorites))
        assertEquals("Favorites", label(2, R.plurals.home_stat_favorites))
        assertEquals("Channel", label(1, R.plurals.home_stat_channels))
        assertEquals("Channels", label(2863, R.plurals.home_stat_channels))
    }
}
