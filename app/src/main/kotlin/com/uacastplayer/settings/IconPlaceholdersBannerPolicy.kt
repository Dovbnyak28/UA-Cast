package com.uacastplayer.settings

import com.uacastplayer.data.prefs.IconDisplayMode

/**
 * Whether the Channels-tab banner explaining "icons are off to save resources" should show. Only
 * relevant when PLACEHOLDERS came from [com.uacastplayer.performance.DeviceTierDefaults] rather
 * than an explicit user choice - a user who picked PLACEHOLDERS themselves already knows why
 * icons are off and doesn't need to be told. [hasSeenHint] makes it one-time even while both of
 * those stay true, whether the user dismissed it or acted on it (see AppViewModel).
 */
object IconPlaceholdersBannerPolicy {
    fun shouldShow(iconDisplayMode: IconDisplayMode, isTierDefault: Boolean, hasSeenHint: Boolean): Boolean =
        iconDisplayMode == IconDisplayMode.PLACEHOLDERS && isTierDefault && !hasSeenHint
}
