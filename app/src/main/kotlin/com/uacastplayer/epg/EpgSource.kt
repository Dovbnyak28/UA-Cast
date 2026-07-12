package com.uacastplayer.epg

/**
 * The 5 cdn.epg.one feed variants. The exact paths below are placeholders pending confirmation
 * of the real endpoints - everything else (selection UI, persistence, download/parse/cache) is
 * fully wired, so dropping in the real URLs later is a one-line change per entry.
 */
enum class EpgSource(val id: String, val url: String) {
    VARIANT_1("epg_one_1", "https://cdn.epg.one/schedule/1.xml.gz"),
    VARIANT_2("epg_one_2", "https://cdn.epg.one/schedule/2.xml.gz"),
    VARIANT_3("epg_one_3", "https://cdn.epg.one/schedule/3.xml.gz"),
    VARIANT_4("epg_one_4", "https://cdn.epg.one/schedule/4.xml.gz"),
    VARIANT_5("epg_one_5", "https://cdn.epg.one/schedule/5.xml.gz");

    companion object {
        val DEFAULT: EpgSource = VARIANT_1

        fun fromId(id: String?): EpgSource = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
