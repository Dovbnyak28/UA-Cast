package com.uacastplayer.ui.epg

import androidx.compose.runtime.compositionLocalOf
import androidx.annotation.StringRes
import com.uacastplayer.R

/** App composition supplies the EPG owner's action to both guide entry points. No UI retry loop. */
val LocalEpgRefresh = compositionLocalOf<(() -> Unit)?> { null }

@StringRes
internal fun epgGuideEmptyMessage(hasData: Boolean, isLoading: Boolean, hasError: Boolean): Int = when {
    isLoading -> R.string.epg_guide_loading
    hasError -> R.string.epg_guide_error
    hasData -> R.string.epg_guide_no_match
    else -> R.string.epg_guide_no_data
}
