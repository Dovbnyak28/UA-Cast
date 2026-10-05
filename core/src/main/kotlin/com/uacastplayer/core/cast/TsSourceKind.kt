package com.uacastplayer.core.cast

/**
 * What kind of content a probed stream turned out to be, as far as Cast delivery routing cares -
 * see [com.uacastplayer.data.cast.TsFirstSegmentDiagnostic] for how this gets determined and
 * `CastDeliveryStrategy.onDiagnosticResult` for how it drives routing. An HLS-vs-raw-TS origin
 * can't be told apart from the URL alone (tokenized/extensionless IPTV URLs are the norm), so this
 * always comes from actually sniffing the response bytes.
 */
enum class TsSourceKind { Hls, RawTs, Unknown }
