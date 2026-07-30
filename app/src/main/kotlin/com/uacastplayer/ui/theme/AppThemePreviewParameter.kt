package com.uacastplayer.ui.theme

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/**
 * Feeds every [AppTheme] value to a `@Preview` function via `@PreviewParameter`, so one preview
 * function renders once per theme in the tooling pane instead of only ever showing whichever
 * theme got hardcoded into the `UaCastTheme(...)` call - see docs/DESIGN_SYSTEM.md "Themes" for why
 * Cinema (the actual default - see [AppTheme.DEFAULT]) needs the same coverage Azure gets, not just
 * the one the previews happened to be written against.
 */
class AppThemePreviewParameter : PreviewParameterProvider<AppTheme> {
    override val values = AppTheme.entries.asSequence()
}
