package com.uacastplayer.dlna

import okhttp3.MediaType.Companion.toMediaType

/**
 * Some renderers reject a SOAP POST that does not announce a user agent, and the DLNA convention is
 * to identify the OS and the DLNA doc version. Sent on every control request - AVTransport and
 * RenderingControl alike - so a renderer that gates on it does not fail only on some of them.
 */
internal const val DLNA_USER_AGENT = "Android/11 UPnP/1.0 DLNADOC/1.50 UACastPlayer"

/** The content type every UPnP control request is sent as. */
internal val SOAP_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()

/**
 * How much of a renderer's control response this app is willing to hold in memory.
 *
 * A UPnP control response - a `GetVolume` result, or a SOAP fault carrying an error code - is a
 * small fixed document, a few hundred bytes. But it arrives from a device on the LAN that nobody
 * here wrote, over a socket that answers with whatever it likes, and `ResponseBody.string()` reads
 * to the end of the stream: a renderer with a broken error path, or one that answers a SOAP POST
 * with a video, would be copied into the heap in full. Every other network read in this app is
 * bounded (see `MAX_DEVICE_DESCRIPTION_BYTES`, `MAX_PLAYLIST_BYTES`, `MAX_EPG_BYTES`); these were
 * the two that were not.
 *
 * 64 KB is roughly two orders of magnitude more than any real response and still nothing to the
 * heap, so it cannot truncate a document that matters.
 */
internal const val MAX_SOAP_RESPONSE_BYTES = 64L * 1024
