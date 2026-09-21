package com.uacastplayer.core.security

/**
 * How a stored licence and its authentication tag live together as one value.
 *
 * **One value, not two, and that is the whole design.** The obvious shape - the licence under its
 * old keys plus a MAC beside them - is defeated by deleting the MAC: an attacker with the file open
 * removes one line and the app is back to trusting whatever the others say. Keeping them in a
 * single string means there is no "licence without a tag" state to fall into. Deleting the tag
 * deletes the licence, and a device with no licence is a device on the free tier, which is where
 * tampering should land.
 *
 * It does not make the record unforgeable to somebody holding the key. The key lives in the Android
 * Keystore and cannot be extracted (see [com.uacastplayer.data.security.LicenseIntegrity]), so it
 * cannot be forged *offline* - but
 * the same person can patch the check out of the APK, which the security audit records as UAC-05
 * and no client-side measure answers. This raises the cost of the easy attack; it does not claim to
 * end the argument.
 *
 * Kept pure so the format is testable without a Keystore, an Android runtime, or a device.
 */
object LicenseRecordCodec {

    private const val SEPARATOR = '|'

    /**
     * The exact bytes the tag is computed over.
     *
     * Every field that decides access is in it, in a fixed order, with nulls spelled out rather
     * than omitted - so that "no expiry" and "expiry deleted" cannot produce the same payload. A
     * format that let two different licences agree on one payload would be a way to swap one for
     * the other with its tag intact.
     */
    fun payload(tier: String, expiresAtMillis: Long?, source: String?): String =
        listOf(tier, expiresAtMillis?.toString() ?: "-", source ?: "-").joinToString(SEPARATOR.toString())

    /** The stored form: the payload and its tag, in the one value. */
    fun encode(payload: String, mac: String): String = "$payload$SEPARATOR$mac"

    /**
     * Splits a stored value back into payload and tag, or null when it is not one.
     *
     * The tag is the last field and the payload is everything before it, so a `source` containing
     * the separator cannot shift the boundary - which a naive split on every separator would let it
     * do, and which would let a chosen playlist name move the tag.
     *
     * **An empty tag is a record, not rubbish.** [encode] is called with one whenever the device
     * could not produce a tag at all, so rejecting it here threw away the licence of every user
     * whose Keystore will not co-operate - the exact case the writer went out of its way to keep.
     * Deciding whether an untagged record may be *believed* is the caller's job and needs a fact
     * this codec does not have: whether this device can tag at all. Parsing it is not believing it.
     */
    fun decode(stored: String): Pair<String, String>? {
        val cut = stored.lastIndexOf(SEPARATOR)
        if (cut <= 0) return null
        return stored.substring(0, cut) to stored.substring(cut + 1)
    }
}
