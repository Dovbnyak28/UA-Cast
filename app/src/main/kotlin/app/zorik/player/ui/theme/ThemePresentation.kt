package app.zorik.player.ui.theme

/** Phone-only appearance choices do not appear as ineffective settings on TV. */
internal fun availableAppThemes(television: Boolean): List<AppTheme> =
    AppTheme.entries.filter { !television || it !in listOf(AppTheme.DAYLIGHT, AppTheme.SYSTEM) }

internal fun themeForScreen(theme: AppTheme, television: Boolean): AppTheme =
    if (television && theme in listOf(AppTheme.DAYLIGHT, AppTheme.SYSTEM)) AppTheme.MIDNIGHT else theme

/** Resolve for rendering only; keep SYSTEM as the saved/presented preference. */
internal fun resolvedAppTheme(theme: AppTheme, systemDark: Boolean): AppTheme = when (theme) {
    AppTheme.SYSTEM -> if (systemDark) AppTheme.MIDNIGHT else AppTheme.DAYLIGHT
    else -> theme
}
