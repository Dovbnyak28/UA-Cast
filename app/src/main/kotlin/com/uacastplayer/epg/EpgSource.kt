package com.uacastplayer.epg

/**
 * The 5 epg.it999.ru feed variants, fetched over HTTPS (verified reachable on all 5 paths).
 * epg2.xml.gz/epg.xml.gz/pp.xml.gz are gzip-compressed; epg2.xml/epg.xml are the same simplified
 * feeds already inflated, served as plain XML - EpgRepository sniffs the gzip magic bytes rather
 * than trusting the file extension, so both are handled uniformly.
 */
enum class EpgSource(val id: String, val url: String) {
    RECT_TRANSPARENT("epg_it999_rect_transparent", "https://epg.it999.ru/epg2.xml.gz"),
    SQUARE_DARK("epg_it999_square_dark", "https://epg.it999.ru/epg.xml.gz"),
    PERFECT_PLAYER("epg_it999_pp", "https://epg.it999.ru/pp.xml.gz"),
    RECT_TRANSPARENT_SIMPLE("epg_it999_rect_transparent_simple", "https://epg.it999.ru/epg2.xml"),
    SQUARE_DARK_SIMPLE("epg_it999_square_dark_simple", "https://epg.it999.ru/epg.xml");

    companion object {
        val DEFAULT: EpgSource = RECT_TRANSPARENT

        fun fromId(id: String?): EpgSource = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
