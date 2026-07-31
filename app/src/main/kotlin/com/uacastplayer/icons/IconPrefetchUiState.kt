package com.uacastplayer.icons

data class IconPrefetchUiState(
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val wifiOnly: Boolean = true,
    val updateReminderDue: Boolean = false,
    // Incremented once per successful prefetch run - see ChannelIcon's refreshKey. New icon files
    // may have landed on disk since the last resolve, so this is the signal to try again for
    // channels that previously resolved to nothing.
    val completedRuns: Int = 0,
)
