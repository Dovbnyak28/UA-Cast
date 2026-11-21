package com.uacastplayer.playlist

import java.text.Normalizer

/**
 * Maps the free-text `group-title` values seen in real playlists onto a small set of stable,
 * translatable categories. Anything not in the alias dictionary is kept as a [ChannelGroup.Custom]
 * rather than being discarded - it's still real provider data, just not one we can localize.
 */
object ChannelGroupNormalizer {

    private val aliases: Map<String, String> = buildMap {
        put(ChannelGroup.KEY_MOVIES, "movies|movie|кино|фильмы|фильм|кіно|фільми|peliculas|películas|cine")
        put(ChannelGroup.KEY_SERIES, "series|сериалы|сериал|серіали|serie")
        put(ChannelGroup.KEY_NEWS, "news|новости|новини|noticias")
        put(ChannelGroup.KEY_SPORTS, "sports|sport|спорт|deportes|deporte")
        put(
            ChannelGroup.KEY_KIDS,
            "kids|cartoons|дети|детские|дитячі|діти|мультфильмы|мультфільми|infantil|niños|ninos",
        )
        put(ChannelGroup.KEY_MUSIC, "music|музыка|музика|musica|música")
        put(
            ChannelGroup.KEY_DOCUMENTARY,
            "documentary|documentaries|документальные|документальное|документальні|documental|documentales",
        )
        put(ChannelGroup.KEY_ENTERTAINMENT, "entertainment|развлекательные|розважальні|entretenimiento")
        put(ChannelGroup.KEY_SCIENCE, "science|наука|познавательные|наукові|пізнавальні|ciencia")
        put(ChannelGroup.KEY_RELIGION, "religion|религия|релігія|religión")
        put(ChannelGroup.KEY_REGIONAL, "regional|local|региональные|регіональні")
        // Expand values above into a reverse lookup below.
    }.let { grouped ->
        buildMap {
            for ((key, aliasList) in grouped) {
                for (alias in aliasList.split('|')) {
                    put(alias, key)
                }
            }
        }
    }

    fun normalize(rawGroupTitle: String?): ChannelGroup {
        val trimmed = rawGroupTitle?.trim()
        if (trimmed.isNullOrEmpty()) return ChannelGroup.Ungrouped

        val normalizedKey = normalizeKey(trimmed)
        val knownKey = aliases[normalizedKey]
        return if (knownKey != null) ChannelGroup.Known(knownKey) else ChannelGroup.Custom(trimmed)
    }

    private fun normalizeKey(value: String): String {
        val nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC)
        return nfkc.lowercase().replace('ё', 'е').trim()
    }
}
