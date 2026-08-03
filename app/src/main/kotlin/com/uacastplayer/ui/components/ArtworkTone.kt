package com.uacastplayer.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.uacastplayer.log.AppLog
import com.uacastplayer.icons.ArtworkTonePolicy
import com.uacastplayer.playlist.M3uChannel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest edge the logo is decoded down to before its pixels are counted. */
private const val TONE_SAMPLE_EDGE = 24

/** Value the extracted hue is rebuilt at - a tint, not the logo's own brightness. */
private const val TONE_VALUE = 0.62f

/** Ceiling on saturation, so a fluorescent logo cannot paint a fluorescent card. */
private const val TONE_MAX_SATURATION = 0.55f

/**
 * The colour a channel's logo is "about", or null when it has no opinion (a white wordmark) or is
 * not on disk yet.
 *
 * Decoded at [TONE_SAMPLE_EDGE] rather than full size: a channel logo is up to a few hundred pixels
 * square, and the mean colour of a 24px thumbnail is the mean colour of the original to well within
 * what the eye can tell at this alpha - while costing a fraction of the memory and time, on a
 * screen that is simultaneously decoding video.
 *
 * The value and saturation of the result are the app's, not the logo's: only the *hue* is borrowed.
 * A logo's own lightness varies wildly between providers, and honouring it would make the same card
 * near-black for one channel and glaring for the next.
 */
@Composable
fun rememberArtworkTone(channel: M3uChannel, resolveIcon: suspend (M3uChannel) -> File?): Color? {
    val tone by produceState<Color?>(initialValue = null, key1 = channel.streamUrl) {
        value = withContext(Dispatchers.IO) {
            val file = runCatching { resolveIcon(channel) }.getOrNull() ?: return@withContext null
            sampleTone(file)
        }
    }
    return tone
}

private fun sampleTone(file: File): Color? {
    val tone = samplePixels(file)?.let(ArtworkTonePolicy::of) ?: return null
    AppLog.d(TAG) { "artwork tone: hue=${tone.hue.toInt()} sat=${tone.saturation}" }
    return Color.hsv(
        hue = tone.hue,
        saturation = tone.saturation.coerceAtMost(TONE_MAX_SATURATION),
        value = TONE_VALUE,
    )
}

/**
 * A corrupt or half-written cache file throws out of the decoder rather than returning null, and
 * this runs on every channel change - one bad file must not take the player down with it.
 */
private fun samplePixels(file: File): IntArray? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longestEdge <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = maxOf(1, longestEdge / TONE_SAMPLE_EDGE)
    }
    val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return@runCatching null
    IntArray(bitmap.width * bitmap.height).also { pixels ->
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
    }
}.getOrNull()

private const val TAG = "ArtworkTone"

/** Strongest the wash ever gets, at the edge the logo sits against. */
private const val WASH_MAX_ALPHA = 0.16f

/** Where along the card the wash has faded to nothing. */
private const val WASH_END_FRACTION = 0.75f

/**
 * Lays a [tone] over a surface as a horizontal fade, strongest at the leading edge and gone before
 * the far side.
 *
 * A flat fill would be a coloured card - too much, and it fights every text colour on top of it.
 * A gradient anchored where the logo is reads as light coming *off* the logo, which is the effect
 * worth having; and because it ends well before the right edge, the text sits on the card's own
 * surface colour and its contrast is unchanged. A null [tone] adds nothing at all.
 */
fun Modifier.artworkWash(tone: Color?, shape: Shape): Modifier {
    if (tone == null) return this
    return this
        .clip(shape)
        .drawWithCache {
            val brush = Brush.horizontalGradient(
                colors = listOf(tone.copy(alpha = WASH_MAX_ALPHA), Color.Transparent),
                startX = 0f,
                endX = size.width * WASH_END_FRACTION,
            )
            onDrawBehind { drawRect(brush) }
        }
}
