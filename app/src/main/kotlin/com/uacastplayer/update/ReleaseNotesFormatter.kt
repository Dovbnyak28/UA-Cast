package com.uacastplayer.update

/** Keeps release text readable in the update offer without rendering untrusted Markdown or links. */
object ReleaseNotesFormatter {
    private const val MAX_INPUT_CHARS = 4_000
    private const val MAX_DISPLAY_CHARS = 1_800
    private const val MAX_LINES = 60
    private val heading = Regex("^#{1,6}\\s*")
    private val bullet = Regex("^[-*+]\\s+")
    private val markdownLink = Regex("""\[([^\]]{1,120})\]\([^\)]{1,500}\)""")

    fun fromMarkdown(markdown: String?): String? {
        if (markdown.isNullOrBlank()) return null
        val text = markdown.take(MAX_INPUT_CHARS)
            .lineSequence()
            .take(MAX_LINES)
            .map { line ->
                line.trim()
                    .replace(heading, "")
                    .replace(bullet, "• ")
                    .replace(markdownLink, "$1")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .filterNot(Char::isISOControl)
            }
            .filter(String::isNotBlank)
            .joinToString("\n")
        return text.takeIf(String::isNotBlank)?.let { readable ->
            if (readable.length > MAX_DISPLAY_CHARS) {
                readable.take(MAX_DISPLAY_CHARS).trimEnd() + "…"
            } else {
                readable
            }
        }
    }
}
