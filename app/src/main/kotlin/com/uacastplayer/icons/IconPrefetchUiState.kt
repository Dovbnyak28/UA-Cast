package com.uacastplayer.icons

data class IconPrefetchUiState(
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val wifiOnly: Boolean = true,
    val updateReminderDue: Boolean = false,
)
