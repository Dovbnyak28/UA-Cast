package com.uacastplayer.parentalcontrol

private const val PIN_LENGTH = 4

/** Pure rules for the parental-control PIN, kept separate from [com.uacastplayer.core.security.PinHasher]
 * so the format check (what a caller should even bother hashing) doesn't depend on any crypto API. */
object ParentalControlPinPolicy {

    /** Exactly [PIN_LENGTH] ASCII digits - short enough to type on a TV remote's number pad, the
     * same length convention as a phone's SIM PIN or a TV's parental-control code. */
    fun isValidFormat(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all(Char::isDigit)
}
